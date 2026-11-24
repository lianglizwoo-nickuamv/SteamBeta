package com.example.steambeta.backend.exchange;

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
 * Almacenamiento administrado de archivos adjuntos para Intercambio Universitario.
 * - Almacena archivos fuera del repositorio en una ruta privada configurable.
 * - Nunca persiste rutas del equipo del cliente; usa identificadores UUID internos.
 * - Valida extensión, tamaño (máx. 25 MB), nombre, firma binaria real (magic bytes)
 *   y estructura OOXML interna para DOCX/PPTX (prevención de ZIP bombs y macros).
 * - Bloquea ejecutables, scripts y path traversal.
 */
@Service
public class ExchangeStorageService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeStorageService.class);

    public static final long MAX_FILE_SIZE_BYTES = 26_214_400L; // 25 MB
    public static final int MAX_FILES_PER_ITEM = 5;

    private static final int MAX_ZIP_ENTRIES = 1000;
    private static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024L * 1024L; // 20 MB por entrada
    private static final long MAX_ZIP_TOTAL_BYTES = 50L * 1024L * 1024L; // 50 MB descomprimido total
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
            String storageKey,
            String originalFilename,
            String extension,
            String mimeType,
            long sizeBytes
    ) {}

    private final Path rootDir;

    public ExchangeStorageService(
            @Value("${steambeta.storage.exchange-dir:${user.home}/.steambeta/exchange-storage}") String storageDir) {
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
                    "El contenido real del archivo no coincide con el formato declarado (." + ext + ").");
        }

        if ("docx".equals(ext) || "pptx".equals(ext)) {
            validateOoxmlContainer(file, ext);
        }

        String storageKey = UUID.randomUUID() + "." + ext;
        Path target = resolveSafe(storageKey);

        try {
            Files.createDirectories(rootDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            deleteQuietly(storageKey);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo almacenar el archivo en el servidor.", ex);
        }

        return new StoredFile(storageKey, sanitizedName, ext, CANONICAL_MIME.get(ext), size);
    }

    public Resource loadAsResource(String storageKey) {
        Path path = resolveSafe(storageKey);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo físico no se encuentra disponible.");
        }
        return new FileSystemResource(path);
    }

    public void deleteQuietly(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            Path path = resolveSafe(storageKey);
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.warn("No se pudo eliminar el archivo físico '{}' (posible huérfano para limpieza): {}", storageKey, ex.getMessage());
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
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El archivo " + ext.toUpperCase(Locale.ROOT) + " excede el número máximo de entradas internas permitidas.");
                }

                String rawName = entry.getName() == null ? "" : entry.getName().replace('\\', '/');
                if (rawName.contains("..") || rawName.startsWith("/")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El archivo contiene rutas internas inválidas.");
                }
                int depth = rawName.split("/").length;
                if (depth > MAX_ZIP_PATH_DEPTH) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El archivo excede la profundidad máxima de carpetas internas permitida.");
                }

                String lower = rawName.toLowerCase(Locale.ROOT);
                if (lower.contains("vbaproject.bin") || lower.endsWith(".bin") && lower.contains("vba")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No se admiten documentos con macros habilitadas (vbaProject.bin).");
                }

                long entryBytes = 0;
                StringBuilder contentTypesText = "[content_types].xml".equals(lower) ? new StringBuilder() : null;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    entryBytes += read;
                    totalUncompressed += read;
                    if (entryBytes > MAX_ZIP_ENTRY_BYTES || totalUncompressed > MAX_ZIP_TOTAL_BYTES) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "El archivo supera el límite seguro de tamaño descomprimido.");
                    }
                    if (contentTypesText != null && contentTypesText.length() < 65536) {
                        contentTypesText.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }

                if ("[content_types].xml".equals(lower)) {
                    hasContentTypes = true;
                    String ctLower = contentTypesText.toString().toLowerCase(Locale.ROOT);
                    if (ctLower.contains("macroenabled") || ctLower.contains("vbaproject")) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "No se admiten documentos con tipos de contenido habilitados para macros.");
                    }
                } else if (requiredPart.equals(lower)) {
                    hasRequiredMainPart = true;
                }

                zis.closeEntry();
            }
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo " + ext.toUpperCase(Locale.ROOT) + " está dañado o no es un contenedor OOXML válido.", ex);
        }

        if (!hasContentTypes || !hasRequiredMainPart) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo no corresponde a una estructura válida de " + ext.toUpperCase(Locale.ROOT)
                            + " (falta [Content_Types].xml o " + requiredPart + ").");
        }
    }

    private Path resolveSafe(String storageKey) {
        if (storageKey == null || !storageKey.matches("^[a-f0-9-]{36}\\.[a-z0-9]{2,5}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de archivo inválido.");
        }
        Path resolved = rootDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso de ruta denegado.");
        }
        return resolved;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe incluir una extensión válida.");
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static byte[] readHeaderBytes(MultipartFile file, int maxBytes) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(maxBytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el contenido del archivo.", ex);
        }
    }

    private static boolean matchesMagicBytes(String ext, byte[] b) {
        if (b == null || b.length < 4) return false;
        return switch (ext) {
            case "pdf" -> b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46; // %PDF
            case "docx", "pptx" -> b[0] == 0x50 && b[1] == 0x4B && (b[2] == 0x03 || b[2] == 0x05 || b[2] == 0x07); // PK zip
            case "doc", "ppt" -> b.length >= 8 && (b[0] & 0xFF) == 0xD0 && (b[1] & 0xFF) == 0xCF
                    && (b[2] & 0xFF) == 0x11 && (b[3] & 0xFF) == 0xE0; // OLE2
            case "jpg", "jpeg" -> (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "png" -> b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47;
            case "gif" -> b.length >= 6 && b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38;
            case "webp" -> b.length >= 12 && b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46
                    && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
            case "bmp" -> b[0] == 0x42 && b[1] == 0x4D;
            case "tif", "tiff" -> (b[0] == 0x49 && b[1] == 0x49 && b[2] == 0x2A && b[3] == 0x00)
                    || (b[0] == 0x4D && b[1] == 0x4D && b[2] == 0x00 && b[3] == 0x2A);
            default -> false;
        };
    }
}
