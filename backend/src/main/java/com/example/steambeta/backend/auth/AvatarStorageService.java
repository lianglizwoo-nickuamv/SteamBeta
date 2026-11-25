package com.example.steambeta.backend.auth;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Almacenamiento administrado y seguro para fotografías de perfil estudiantil.
 * - Directorio privado separado de los adjuntos de Intercambio Universitario.
 * - Admite exclusivamente JPG, JPEG, PNG y WEBP (máximo 5 MB).
 * - Valida firma binaria real (magic bytes), decodificación real de imagen
 *   y límites de dimensiones (máximo 4096x4096 px) para evitar abuso de memoria.
 * - Rechaza SVG, ejecutables, documentos y archivos disfrazados.
 * - Usa nombres internos aleatorios UUID y bloquea path traversal.
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

    public static final long MAX_AVATAR_BYTES = 5L * 1024L * 1024L; // 5 MB
    public static final int MAX_DIMENSION_PX = 4096;
    public static final String AVATAR_URL_PREFIX = "/api/avatars/";

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private static final Map<String, String> CANONICAL_MIME = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp"
    );

    public record StoredAvatar(String storageKey, String avatarUrl, String mimeType, long sizeBytes) {}

    private final Path rootDir;

    public AvatarStorageService(
            @Value("${steambeta.storage.avatar-dir:${user.home}/.steambeta/avatar-storage}") String storageDir) {
        this.rootDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public Path getRootDir() {
        return rootDir;
    }

    public StoredAvatar store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se seleccionó ninguna fotografía.");
        }
        long size = file.getSize();
        if (size <= 0 || size > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fotografía supera el límite máximo permitido de 5 MB.");
        }

        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        String ext = extractExtension(sanitizedName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no permitido (." + ext + "). Solo se admiten imágenes JPG, JPEG, PNG o WEBP.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo de imagen.", ex);
        }

        if (bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fotografía supera el límite máximo permitido de 5 MB.");
        }

        if (!matchesMagicBytes(ext, bytes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El contenido binario del archivo no corresponde a una imagen " + ext.toUpperCase(Locale.ROOT) + " válida.");
        }

        validateDecodableAndDimensions(ext, bytes);

        String normalizedExt = "jpeg".equals(ext) ? "jpg" : ext;
        String storageKey = UUID.randomUUID() + "." + normalizedExt;
        Path target = resolveSafe(storageKey);

        try {
            Files.createDirectories(rootDir);
            Files.write(target, bytes);
        } catch (IOException ex) {
            deleteQuietly(storageKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo guardar la fotografía en el servidor.", ex);
        }

        return new StoredAvatar(storageKey, AVATAR_URL_PREFIX + storageKey, CANONICAL_MIME.get(normalizedExt), bytes.length);
    }

    public Resource loadByAvatarUrl(String avatarUrl) {
        String key = extractStorageKey(avatarUrl);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no disponible.");
        }
        Path path = resolveSafe(key);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fotografía no disponible.");
        }
        return new FileSystemResource(path);
    }

    public String mimeTypeForAvatarUrl(String avatarUrl) {
        String key = extractStorageKey(avatarUrl);
        if (key == null) return "application/octet-stream";
        int dot = key.lastIndexOf('.');
        String ext = dot > 0 ? key.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        return CANONICAL_MIME.getOrDefault(ext, "application/octet-stream");
    }

    public void deleteByAvatarUrlQuietly(String avatarUrl) {
        String key = extractStorageKey(avatarUrl);
        if (key != null) {
            deleteQuietly(key);
        }
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            Path path = resolveSafe(storageKey);
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("No se pudo eliminar la fotografía física '{}': {}", storageKey, ex.getMessage());
        }
    }

    public String extractStorageKey(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return null;
        String trimmed = avatarUrl.strip();
        if (!trimmed.startsWith(AVATAR_URL_PREFIX)) return null;
        String key = trimmed.substring(AVATAR_URL_PREFIX.length());
        if (!key.matches("^[a-f0-9-]{36}\\.(jpg|jpeg|png|webp)$")) return null;
        return key;
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || !storageKey.matches("^[a-f0-9-]{36}\\.(jpg|jpeg|png|webp)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de fotografía inválido.");
        }
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso de ruta denegado.");
        }
        return resolved;
    }

    private static void validateDecodableAndDimensions(String ext, byte[] bytes) {
        if ("webp".equals(ext)) {
            int[] dims = parseWebpDimensions(bytes);
            if (dims == null || dims[0] < 1 || dims[1] < 1
                    || dims[0] > MAX_DIMENSION_PX || dims[1] > MAX_DIMENSION_PX) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La imagen WEBP está dañada o supera las dimensiones máximas permitidas (4096×4096 px).");
            }
            return;
        }

        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El archivo no es una imagen decodificable válida.");
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w < 1 || h < 1 || w > MAX_DIMENSION_PX || h > MAX_DIMENSION_PX) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Las dimensiones de la fotografía superan el máximo permitido (4096×4096 px).");
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se pudo decodificar la fotografía; el archivo puede estar dañado o alterado.", ex);
        }
    }

    private static int[] parseWebpDimensions(byte[] b) {
        if (b == null || b.length < 30) return null;
        String chunk = new String(new byte[]{b[12], b[13], b[14], b[15]});
        if ("VP8X".equals(chunk) && b.length >= 30) {
            int w = 1 + ((b[24] & 0xFF) | ((b[25] & 0xFF) << 8) | ((b[26] & 0xFF) << 16));
            int h = 1 + ((b[27] & 0xFF) | ((b[28] & 0xFF) << 8) | ((b[29] & 0xFF) << 16));
            return new int[]{w, h};
        }
        if ("VP8 ".equals(chunk) && b.length >= 30) {
            if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) return null;
            int w = ((b[26] & 0xFF) | ((b[27] & 0xFF) << 8)) & 0x3FFF;
            int h = ((b[28] & 0xFF) | ((b[29] & 0xFF) << 8)) & 0x3FFF;
            return new int[]{w, h};
        }
        if ("VP8L".equals(chunk) && b.length >= 25) {
            if ((b[20] & 0xFF) != 0x2F) return null;
            int b1 = b[21] & 0xFF;
            int b2 = b[22] & 0xFF;
            int b3 = b[23] & 0xFF;
            int b4 = b[24] & 0xFF;
            int w = 1 + (((b2 & 0x3F) << 8) | b1);
            int h = 1 + (((b4 & 0x0F) << 10) | (b3 << 2) | ((b2 & 0xC0) >> 6));
            return new int[]{w, h};
        }
        return null;
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener un nombre válido.");
        }
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[\\p{Cntrl}]", "").strip();
        if (base.isEmpty() || base.length() > 255 || base.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido.");
        }
        return base;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener una extensión de imagen válida.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matchesMagicBytes(String ext, byte[] b) {
        if (b == null || b.length < 12) return false;
        return switch (ext) {
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                    && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
            case "webp" -> b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                    && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
            default -> false;
        };
    }
}
