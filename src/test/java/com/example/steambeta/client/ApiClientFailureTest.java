package com.example.steambeta.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientFailureTest {
    @Test void unreachableBackendFailsPromptlyForTheUiWorkerToDisplay() {
        var client = new ApiClient("http://127.0.0.1:1");
        long started = System.nanoTime();
        assertThrows(Exception.class, client::communities);
        assertTrue((System.nanoTime() - started) < 5_000_000_000L, "connection refusal should return promptly");
    }
}
