package com.example.steambeta.backend.context;

import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** One actor per fresh persistence context. No shared result cache. */
@Component
public class ActorTransactions {
    private final ObjectProvider<ActorProvider> actors;
    private final TransactionContext context;
    private final TransactionTemplate transactions;

    public ActorTransactions(ObjectProvider<ActorProvider> actors, TransactionContext context,
                             PlatformTransactionManager manager) {
        this.actors = actors;
        this.context = context;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> operation) {
        var actor = actors.getObject().requireValidatedActor();
        return transactions.execute(status -> {
            context.apply(actor);
            return operation.get();
        });
    }
}
