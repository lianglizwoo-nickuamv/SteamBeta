package com.example.steambeta.backend.auth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Internal principal only; never serialize it into an API response. */
public final class AccountPrincipal implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final String login;
    private final UUID accountId;
    private String passwordHash;
    private final boolean enabled;
    private final UUID universityId;
    private final UUID studentId;
    private final String globalRole;

    public AccountPrincipal(UUID accountId, String login, String hash, boolean enabled, UUID universityId, UUID studentId) {
        this(accountId, login, hash, enabled, universityId, studentId, "USER");
    }

    public AccountPrincipal(UUID accountId, String login, String hash, boolean enabled, UUID universityId, UUID studentId, String globalRole) {
        this.accountId = accountId;
        this.login = login;
        this.passwordHash = hash;
        this.enabled = enabled;
        this.universityId = universityId;
        this.studentId = studentId;
        this.globalRole = (globalRole == null || globalRole.isBlank()) ? "USER" : globalRole.strip().toUpperCase();
    }

    public UUID universityId() { return universityId; }
    public UUID studentId() { return studentId; }
    public UUID accountId() { return accountId; }
    public String globalRole() { return globalRole; }
    public boolean isGlobalModerator() { return "GLOBAL_MODERATOR".equals(globalRole) || "ADMIN".equals(globalRole); }
    public boolean hasAcademicLink() { return universityId != null && studentId != null; }

    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (isGlobalModerator()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_GLOBAL_MODERATOR"));
        }
        if ("ADMIN".equals(globalRole)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return authorities;
    }

    @Override public void eraseCredentials() { passwordHash = null; }
}
