package com.example.steambeta.backend.context;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionContext {
    private final EntityManager entityManager;
    public TransactionContext(EntityManager entityManager) { this.entityManager = entityManager; }

    void apply(ActorProvider.Actor actor) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Actor context requires an active transaction");
        }
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("""
                select set_config('replica.university_id', ?, true),
                       set_config('replica.student_id', ?, true)
                """)) {
                statement.setString(1, actor.universityId().toString());
                statement.setString(2, actor.studentId().toString());
                statement.execute();
            }
        });
    }
}
