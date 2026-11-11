package com.example.steambeta.backend.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class MigrationGuard {
    @Bean
    static BeanFactoryPostProcessor requireExplicitMigrationIdentity(Environment environment) {
        return factory -> {
            if (environment.getProperty("spring.flyway.enabled", Boolean.class, false)) {
                String login = environment.getProperty("REPLICA_DB_USER", "");
                if (!login.matches("[a-z_][a-z0-9_]*")) {
                    throw new IllegalStateException("Migration requires a simple PostgreSQL runtime login identifier");
                }
                for (String key : new String[]{"REPLICA_MIGRATION_USER", "REPLICA_MIGRATION_PASSWORD"}) {
                    String value = environment.getProperty(key);
                    if (value == null || value.isBlank()) {
                        throw new IllegalStateException("Migration requires nonblank " + key);
                    }
                }
                if (environment.getProperty("REPLICA_MIGRATION_USER")
                        .equals(environment.getProperty("REPLICA_DB_USER"))) {
                    throw new IllegalStateException("Migration and runtime identities must differ");
                }
            }
        };
    }
}
