package com.example.steambeta.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionScopeTest {
    @Test void lateResponseCannotRepopulateAfterLogoutAndAnotherLogin() {
        var scope=new SessionScope();var screen=new AtomicReference<>("Alba");
        long oldSession=scope.reset(),oldSelection=scope.select();
        var response=new CompletableFuture<String>();
        response.thenAccept(data->{if(scope.current(oldSession,oldSelection))screen.set(data);});
        scope.reset();screen.set(""); // Logout clears identity and data immediately.
        scope.reset();screen.set("Bruno"); // New login has another cookie store and generation.
        response.complete("Alba's late activity list");
        assertEquals("Bruno",screen.get());
        assertFalse(scope.current(oldSession));
    }
    @Test void earlierSelectionCannotReplaceTheLatestMatter() {
        var scope=new SessionScope();long session=scope.reset();long first=scope.select();long second=scope.select();
        assertFalse(scope.current(session,first));assertTrue(scope.current(session,second));
    }
}
