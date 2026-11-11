package com.example.steambeta.backend.auth;

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
    private String passwordHash;
    private final boolean enabled;
    private final UUID universityId;
    private final UUID studentId;
    public AccountPrincipal(String login, String hash, boolean enabled, UUID universityId, UUID studentId) {
        this.login=login; this.passwordHash=hash; this.enabled=enabled;
        this.universityId=universityId; this.studentId=studentId;
    }
    public UUID universityId() { return universityId; }
    public UUID studentId() { return studentId; }
    @Override public String getUsername() { return login; }
    @Override public String getPassword() { return passwordHash; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
    @Override public void eraseCredentials() { passwordHash=null; }
}
