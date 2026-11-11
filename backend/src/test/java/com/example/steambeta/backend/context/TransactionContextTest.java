package com.example.steambeta.backend.context;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionContextTest {
    @Test void rejectsMissingTransactionBeforeAccessingConnection() {
        var context = new TransactionContext(null);
        assertThrows(IllegalStateException.class, () -> context.apply(
                new ActorProvider.Actor(UUID.randomUUID(), UUID.randomUUID())));
    }
}
