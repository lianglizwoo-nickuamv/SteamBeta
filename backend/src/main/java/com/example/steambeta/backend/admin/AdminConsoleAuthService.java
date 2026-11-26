package com.example.steambeta.backend.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Autenticación independiente para STEAMBETA ADMIN mediante clave privada compartida.
 * - Sin clave predeterminada en código.
 * - Verificación en tiempo constante sobre digestión SHA-256 (MessageDigest.isEqual).
 * - Protección contra fuerza bruta por dirección de origen.
 * - Sesión administrativa aislada con ROLE_ADMIN_CONSOLE (sin permisos de estudiante).
 */
@Service
public class AdminConsoleAuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final byte[] expectedHash;
    private final Map<String, Deque<Instant>> failedAttempts = new ConcurrentHashMap<>();

    public AdminConsoleAuthService(
            @Value("${steambeta.admin.key-sha256:}") String keySha256Hex,
            @Value("${steambeta.admin.key:}") String rawKey) {
        byte[] parsed = null;
        if (keySha256Hex != null && !keySha256Hex.isBlank()) {
            String clean = keySha256Hex.strip().toLowerCase();
            if (clean.matches("[0-9a-f]{64}")) {
                parsed = HexFormat.of().parseHex(clean);
            }
        } else if (rawKey != null && !rawKey.isBlank()) {
            parsed = sha256(rawKey.strip());
        }
        this.expectedHash = parsed;
    }

    public boolean isConfigured() {
        return expectedHash != null;
    }

    public AdminConsolePrincipal authenticate(String providedKey, HttpServletRequest request, HttpServletResponse response) {
        String sourceKey = resolveClientKey(request);
        checkRateLimit(sourceKey);

        if (expectedHash == null || providedKey == null || providedKey.isBlank()) {
            recordFailure(sourceKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "admin_access_denied");
        }

        byte[] candidateHash = sha256(providedKey.strip());
        if (!MessageDigest.isEqual(expectedHash, candidateHash)) {
            recordFailure(sourceKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "admin_access_denied");
        }

        failedAttempts.remove(sourceKey);

        // Invalidar cualquier sesión previa (p. ej. de estudiante) para aislamiento absoluto
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession adminSession = request.getSession(true);

        AdminConsolePrincipal principal = new AdminConsolePrincipal(Instant.now());
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        adminSession.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return principal;
    }

    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public AdminConsolePrincipal requireAdminSession() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AdminConsolePrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "admin_session_required");
        }
        return principal;
    }

    private void checkRateLimit(String key) {
        Deque<Instant> attempts = failedAttempts.get(key);
        if (attempts == null) return;
        Instant cutoff = Instant.now().minus(WINDOW);
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "too_many_admin_attempts");
            }
        }
    }

    private void recordFailure(String key) {
        Deque<Instant> attempts = failedAttempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
                attempts.pollFirst();
            }
            attempts.addLast(now);
        }
    }

    private static String resolveClientKey(HttpServletRequest request) {
        String addr = request.getRemoteAddr();
        return (addr != null && !addr.isBlank()) ? addr : "unknown";
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
