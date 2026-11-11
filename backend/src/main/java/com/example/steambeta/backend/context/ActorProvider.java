package com.example.steambeta.backend.context;

import java.util.Objects;
import java.util.UUID;

/** Trusted server-side authentication boundary. Never bind this type from JSON.
 * Production identity comes exclusively from the authenticated Spring Security principal.
 */
public interface ActorProvider {
    Actor requireValidatedActor();

    record Actor(UUID universityId, UUID studentId) {
        public Actor {
            Objects.requireNonNull(universityId, "universityId");
            Objects.requireNonNull(studentId, "studentId");
        }
    }
}
