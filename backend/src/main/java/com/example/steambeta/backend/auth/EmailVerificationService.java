package com.example.steambeta.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de verificación de correo electrónico para SteamBeta Campus.
 * 
 * Genera tokens aleatorios criptográficamente seguros mediante SecureRandom,
 * almacena únicamente su hash SHA-256 con fecha de expiración y administra
 * la configuración externa de proveedores SMTP vía variables de entorno.
 * Si el proveedor de correo no está configurado, no simula envíos falsos.
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private final JdbcTemplate jdbc;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${steambeta.mail.enabled:#{false}}")
    private boolean mailEnabled;

    @Value("${steambeta.mail.host:}")
    private String mailHost;

    @Value("${steambeta.mail.port:587}")
    private int mailPort;

    @Value("${steambeta.mail.from:no-reply@campus.steambeta.edu}")
    private String mailFrom;

    public record VerificationStatus(boolean mailConfigured, boolean emailVerified, String message) {}

    public EmailVerificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isMailConfigured() {
        return mailEnabled && mailHost != null && !mailHost.isBlank();
    }

    /**
     * Genera y registra un token de verificación seguro si la infraestructura de correo está habilitada.
     * Retorna el token en texto plano únicamente para el envío del correo (nunca se guarda en texto plano en la BD).
     */
    @Transactional
    public String createVerificationTokenIfConfigured(UUID accountId, String email) {
        if (!isMailConfigured()) {
            log.info("Servicio SMTP no configurado (steambeta.mail.host no definido). La cuenta {} queda registrada en PostgreSQL; verificación de correo en espera de configuración externa.", accountId);
            return null;
        }

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);
        String tokenHash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        try {
            jdbc.query(
                "select replica.create_email_verification_token(?, ?, ?)",
                (rs, rowNum) -> null,
                accountId, tokenHash, Timestamp.from(expiresAt)
            );
            log.info("Token de verificación generado para la cuenta {} (vigencia: 24 horas)", accountId);
            // Si el proveedor SMTP estuviera activo, aquí se despacharía el correo con rawToken
            return rawToken;
        } catch (Exception ex) {
            log.warn("No se pudo registrar token de verificación vía función controlada: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Valida y consume un token de verificación presentado por el estudiante de forma atómica
     * mediante la función SECURITY DEFINER replica.consume_email_verification_token(?).
     * Bloquea concurrentemente el token (FOR UPDATE), comprueba vigencia y actualiza el estado.
     */
    @Transactional
    public boolean verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String tokenHash = hashToken(rawToken.strip());

        try {
            var rows = jdbc.query(
                "select replica.consume_email_verification_token(?)",
                (rs, rowNum) -> rs.getBoolean(1),
                tokenHash
            );

            if (rows.isEmpty() || Boolean.FALSE.equals(rows.get(0))) {
                log.warn("Intento de verificación con token inválido, expirado o ya utilizado");
                return false;
            }

            log.info("Correo electrónico verificado exitosamente mediante función atómica");
            return true;
        } catch (Exception ex) {
            log.error("Error al procesar verificación de correo en PostgreSQL: {}", ex.getMessage());
            return false;
        }
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM", e);
        }
    }
}
