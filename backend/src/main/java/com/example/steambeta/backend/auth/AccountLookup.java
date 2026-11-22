package com.example.steambeta.backend.auth;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountLookup implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public AccountLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        String normalized = identifier.strip().toLowerCase(Locale.ROOT);

        // Regla de seguridad RLS: Si se intenta iniciar sesión con correo (@),
        // verificar el estado mediante la función SECURITY DEFINER replica.check_email_verification_status(?).
        // Si el correo existe pero NO está verificado, rechazar explícitamente con "Email unverified".
        if (normalized.contains("@")) {
            try {
                var verifiedRows = jdbc.query(
                        "select replica.check_email_verification_status(?)",
                        (rs, rowNum) -> rs.getObject(1, Boolean.class),
                        normalized
                );
                if (!verifiedRows.isEmpty() && Boolean.FALSE.equals(verifiedRows.get(0))) {
                    throw new UsernameNotFoundException("Email unverified");
                }
            } catch (UsernameNotFoundException unverified) {
                throw unverified;
            } catch (Exception ignored) {
                // Compatibilidad defensiva si la función aún no está aplicada en el esquema local
            }
        }

        // Búsqueda autorizada mediante la función SECURITY DEFINER replica.lookup_access_account(?)
        // Retorna las 6 columnas base más el rol global consolidado (global_role), sin violar RLS
        var rows = jdbc.query("select * from replica.lookup_access_account(?)", (rs, row) -> {
            UUID accountId = rs.getObject("account_id", UUID.class);
            String login = rs.getString("login_identifier");
            String hash = rs.getString("password_hash");
            boolean enabled = rs.getBoolean("enabled");
            UUID universityId = rs.getObject("university_id", UUID.class);
            UUID studentId = rs.getObject("student_id", UUID.class);

            String role = "USER";
            try {
                String dbRole = rs.getString("global_role");
                if (dbRole != null && !dbRole.isBlank()) {
                    role = dbRole;
                }
            } catch (Exception ignored) {
                // Si la firma en base de datos aún no incluye global_role
            }

            return new AccountPrincipal(accountId, login, hash, enabled, universityId, studentId, role);
        }, normalized);

        if (rows.isEmpty()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return rows.get(0);
    }
}
