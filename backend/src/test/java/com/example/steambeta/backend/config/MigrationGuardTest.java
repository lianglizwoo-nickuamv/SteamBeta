package com.example.steambeta.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class MigrationGuardTest {
    @Test void normalModeNeedsNoMigrationSecret() {
        assertDoesNotThrow(() -> MigrationGuard.requireExplicitMigrationIdentity(new MockEnvironment())
                .postProcessBeanFactory(null));
    }
    @Test void optInRequiresBothNonblankSecretsAndDistinctIdentity() {
        var env = new MockEnvironment().withProperty("spring.flyway.enabled", "true");
        assertThrows(IllegalStateException.class, () -> MigrationGuard.requireExplicitMigrationIdentity(env).postProcessBeanFactory(null));
        env.setProperty("REPLICA_MIGRATION_USER", "owner");
        env.setProperty("REPLICA_MIGRATION_PASSWORD", " ");
        assertThrows(IllegalStateException.class, () -> MigrationGuard.requireExplicitMigrationIdentity(env).postProcessBeanFactory(null));
        env.setProperty("REPLICA_MIGRATION_PASSWORD", "fixture-only");
        env.setProperty("REPLICA_DB_USER", "owner");
        assertThrows(IllegalStateException.class, () -> MigrationGuard.requireExplicitMigrationIdentity(env).postProcessBeanFactory(null));
        env.setProperty("REPLICA_DB_USER", "runtime");
        assertDoesNotThrow(() -> MigrationGuard.requireExplicitMigrationIdentity(env).postProcessBeanFactory(null));
    }
}
