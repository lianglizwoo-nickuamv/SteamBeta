package com.example.steambeta.backend.auth;

import com.example.steambeta.backend.context.ActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SecurityActorProvider implements ActorProvider {
    @Override public Actor requireValidatedActor() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AccountPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Session required");
        }
        if (!principal.hasAcademicLink()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "academic_link_required");
        }
        return new Actor(principal.universityId(),principal.studentId());
    }
}
