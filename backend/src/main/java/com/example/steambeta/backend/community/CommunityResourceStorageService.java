package com.example.steambeta.backend.community;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
 * Almacenamiento administrado e independiente para archivos de Recursos de Comunidades (V8).
 * - Ruta completamente separada de Intercambio Universitario:
 *   ${user.home}/.steambeta/community-resource-storage
 * - Límite máximo de 25 MB (alineado con V8: 26,214,400 bytes).
 * - Valida extensión, firma binaria real (magic bytes), sanitización de nombre,
 *   protección contra path traversal y verificación OOXML (ZIP bombs + macros).
 */
@Service
public class CommunityResourceStorageService {

    private static final Logger log = LoggerFactory.getLogger(CommunityResourceStorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = 26_214_400L; // 25 MB (V8)

    private static final int MAX_ZIP_ENTRIES = 1000;
    private static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_ZIP_TOTAL_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_ZIP_PATH_DEPTH = 12;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "tif", "tiff"
    );

    private static final Map<String, String> CANONICAL_MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff")
    );

    public record StoredFile(
            String storageName,
            String originalFilename,
            String extension,
            String mimeType,
            long sizeBytes
    ) {}

    private final Path rootDir;

    public CommunityResourceStorageService(
            @Value("${steambeta.storage.community-resource-dir:${user.home}/.steambeta/community-resource-storage}") String storageDir) {
        this.rootDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public Path getRootDir() {
        return rootDir;
    }

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío o no fue enviado.");
        }
        long size = file.getSize();
        if (size <= 0 || size > MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo excede el tamaño máximo permitido de 25 MB.");
        }

        String sanitizedName = sanitizeFilename(file.getOriginalFilename());
        String ext = extractExtension(sanitizedName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato no admitido (." + ext + "). Permitidos: PDF, DOC, DOCX, PPT, PPTX, JPG, JPEG, PNG, GIF, WEBP, BMP, TIFF.");
        }

        byte[] header = readHeaderBytes(file, 16);
        if (!matchesMagicBytes(ext, header)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El contenido binario del archivo no coincide con el formato declarado (." + ext + ").");
        }

        if ("docx".equals(ext) || "pptx".equals(ext)) {
            validateOoxmlContainer(file, ext);
        }

        String storageName = UUID.randomUUID() + "." + ext;
        Path target = resolveSafe(storageName);

        try {
            Files.createDirectories(rootDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            deleteQuietly(storageName);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo almacenar el archivo en el servidor.", ex);
        }

        return new StoredFile(storageName, sanitizedName, ext, CANONICAL_MIME.get(ext), size);
    }

    public Resource loadAsResource(String storageName) {
        Path path = resolveSafe(storageName);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo físico del recurso no se encuentra disponible.");
        }
        return new FileSystemResource(path);
    }

    public void deleteQuietly(String storageName) {
        if (storageName == null || storageName.isBlank()) return;
        try {
            Files.deleteIfExists(resolveSafe(storageName));
        } catch (Exception ex) {
            log.warn("No se pudo eliminar el archivo comunitario '{}': {}", storageName, ex.getMessage());
        }
    }

    private void validateOoxmlContainer(MultipartFile file, String ext) {
        boolean hasContentTypes = false;
        boolean hasRequiredMainPart = false;
        String requiredPart = "docx".equals(ext) ? "word/document.xml" : "ppt/presentation.xml";

        int entryCount = 0;
        long totalUncompressed = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo comprimido contiene demasiadas entradas internas.");
                }
                String name = entry.getName();
                if (name == null || name.isBlank() || name.contains("..") || name.startsWith("/") || name.startsWith("\\") || name.contains(":")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta interna peligrosa detectada en el documento OOXML.");
                }
                if (name.split("/").length > MAX_ZIP_PATH_DEPTH) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profundidad de directorios excesiva en el documento.");
                }
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith("vbaproject.bin") || lower.endsWith(".bin") || lower.endsWith(".exe")
                        || lower.endsWith(".bat") || lower.endsWith(".cmd") || lower.endsWith(".ps1") || lower.endsWith(".js")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documentos con macros o ejecutables incrustados no están permitidos.");
                }
                if ("[content_types].xml".equals(lower)) hasContentTypes = true;
                if (requiredPart.equals(lower)) hasRequiredMainPart = true;

                long entryBytes = 0;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > MAX_ZIP_ENTRY_BYTES || totalUncompressed > MAX_ZIP_TOTAL_BYTES) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El documento comprimido excede el límite seguro de descompresión.");
                    }
                }
                zis.closeEntry();
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo ." + ext + " está dañado o no es un documento OOXML válido.");
        }

        if (!hasContentTypes || !hasRequiredMainPart) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo ." + ext + " no posee estructura válida de documento Office.");
        }
    }

    private Path resolveSafe(String storageName) {
        if (storageName == null || !storageName.matches("^[0-9a-fA-F-]{36}\\.[a-z0-9]{2,5}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de archivo inválido.");
        }
        Path resolved = rootDir.resolve(storageName).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Acceso fuera del directorio de recursos bloqueado.");
        }
        return resolved;
    }

    private static byte[] readHeaderBytes(MultipartFile file, int maxLen) {
        byte[] buf = new byte[maxLen];
        try (InputStream in = file.getInputStream()) {
            int offset = 0;
            while (offset < maxLen) {
                int r = in.read(buf, offset, maxLen - offset);
                if (r < 0) break;
                offset += r;
            }
            byte[] actual = new byte[offset];
            System.arraycopy(buf, 0, actual, 0, offset);
            return actual;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el archivo enviado.");
        }
    }

    private static boolean matchesMagicBytes(String ext, byte[] h) {
        if (h == null || h.length < 4) return false;
        return switch (ext) {
            case "pdf" -> h.length >= 5 && new String(h, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
            case "doc", "ppt" -> h.length >= 8
                    && (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0
                    && (h[4] & 0xFF) == 0xA1 && (h[5] & 0xFF) == 0xB1 && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0xE1;
            case "docx", "pptx" -> h[0] == 'P' && h[1] == 'K' && ((h[2] == 3 && h[3] == 4) || (h[2] == 5 && h[3] == 6));
            case "jpg", "jpeg" -> (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
            case "png" -> h.length >= 8
                    && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                    && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A;
            case "gif" -> h.length >= 6 && (new String(h, 0, 6, StandardCharsets.US_ASCII).equals("GIF87a")
                    || new String(h, 0, 6, StandardCharsets.US_ASCII).equals("GIF89a"));
            case "webp" -> h.length >= 12
                    && new String(h, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(h, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case "bmp" -> h[0] == 'B' && h[1] == 'M';
            case "tif", "tiff" -> (h[0] == 'I' && h[1] == 'I' && h[2] == 42 && h[3] == 0)
                    || (h[0] == 'M' && h[1] == 'M' && h[2] == 0 && h[3] == 42);
            default -> false;
        };
    }

    public static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe tener un nombre válido.");
        }
        String base = raw.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[\\p{Cntrl}\"'<>|?*:]", "_").strip();
        if (base.isBlank() || base.equals(".") || base.equals("..") || base.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo inválido.");
        }
        if (base.length() > 180) {
            int dot = base.lastIndexOf('.');
            String ext = dot > 0 ? base.substring(dot) : "";
            base = base.substring(0, Math.max(1, 180 - ext.length())) + ext;
        }
        return base;
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
