package com.example.steambeta.backend.auth;

import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AccountLookup implements UserDetailsService {
    private final JdbcTemplate jdbc;
    public AccountLookup(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public UserDetails loadUserByUsername(String identifier) {
        String normalized=identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._@-]{2,79}")) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        var rows=jdbc.query("select * from replica.lookup_access_account(?)", (rs,row)->new AccountPrincipal(
                rs.getString("login_identifier"),rs.getString("password_hash"),rs.getBoolean("enabled"),
                rs.getObject("university_id",UUID.class),rs.getObject("student_id",UUID.class)),normalized);
        if(rows.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
        return rows.getFirst();
    }
}
