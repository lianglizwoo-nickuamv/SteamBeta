package com.example.steambeta.backend.community;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CommunityController.class)
public class CommunityErrors {
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseError(DataAccessException failure) {
        String state = failure.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : "";
        return switch (state == null ? "" : state) {
            case "P1401" -> ResponseEntity.status(401).body(Map.of("error", "session_required"));
            case "P1404" -> ResponseEntity.status(404).body(Map.of("error", "community_unavailable"));
            case "P1409" -> ResponseEntity.status(409).body(Map.of("error", "post_request_conflict"));
            case "22023", "23514", "23502" -> ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
            default -> ResponseEntity.internalServerError().body(Map.of("error", "community_unavailable"));
        };
    }
}
