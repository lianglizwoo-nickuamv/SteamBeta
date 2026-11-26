package com.example.steambeta.backend.admin;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Principal de sesión independiente para SteamBeta Admin.
 * No extiende ni comparte identidad con AccountPrincipal (estudiantes).
 */
public final class AdminConsolePrincipal implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ROLE_ADMIN_CONSOLE = "ROLE_ADMIN_CONSOLE";

    private final Instant authenticatedAt;

    public AdminConsolePrincipal(Instant authenticatedAt) {
        this.authenticatedAt = authenticatedAt;
    }

    public Instant authenticatedAt() {
        return authenticatedAt;
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority(ROLE_ADMIN_CONSOLE));
    }
}
