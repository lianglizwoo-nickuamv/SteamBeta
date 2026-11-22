package com.example.steambeta.backend.community;

import java.sql.SQLException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(basePackages = "com.example.steambeta.backend.community")
public class CommunityErrors {

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseError(DataAccessException failure) {
        String state = failure.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : "";
        String sqlMsg = failure.getMostSpecificCause() instanceof SQLException sql ? sql.getMessage() : "";

        // Verificación de mensajes específicos de PostgreSQL
        if (sqlMsg != null) {
            if (sqlMsg.contains("invitation_expired")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invitation_expired", "message", "El código de invitación ha expirado (superó el período de vigencia de 30 días)."));
            }
            if (sqlMsg.contains("invitation_exhausted")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invitation_exhausted", "message", "El código de invitación ha alcanzado el límite máximo de usos permitidos (50/50)."));
            }
            if (sqlMsg.contains("invitation_not_found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "invitation_not_found", "message", "El código de invitación no existe o ha sido revocado."));
            }
            if (sqlMsg.contains("already_member")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "already_member", "message", "Ya eres miembro de esta comunidad universitaria."));
            }
            if (sqlMsg.contains("community_slug_conflict")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "community_slug_conflict", "message", "Ya existe una comunidad registrada con ese slug identificador."));
            }
            if (sqlMsg.contains("invalid_category")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_category", "message", "La categoría especificada no es válida. Opciones permitidas: ACADEMICA, ESTUDIO, PROYECTO, CARRERA, INTERES."));
            }
            if (sqlMsg.contains("invalid_visibility")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_visibility", "message", "La visibilidad especificada no es válida. Opciones permitidas: PUBLIC, INVITE_ONLY, PRIVATE."));
            }
            if (sqlMsg.contains("invalid_slug")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_slug", "message", "El slug identificador no es válido. Debe tener entre 3 y 80 caracteres en minúsculas y guiones (ej. club-de-algoritmos)."));
            }
            if (sqlMsg.contains("invalid_community_name")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_community_name", "message", "El nombre de la comunidad debe tener entre 3 y 100 caracteres."));
            }
            if (sqlMsg.contains("invalid_invitation_code") || sqlMsg.contains("invalid_code")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_invitation_code", "message", "El código de invitación debe contener entre 6 y 16 caracteres alfanuméricos en mayúsculas."));
            }
        }

        return switch (state == null ? "" : state) {
            case "P1401" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "session_required", "message", "Se requiere una sesión activa para realizar esta acción."));
            case "P1404" -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "community_unavailable", "message", "La comunidad o recurso solicitado no existe o no tienes acceso."));
            case "P1409" -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "request_conflict", "message", "Conflicto con la solicitud: el identificador o registro ya existe."));
            case "42501" -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "forbidden", "message", "Permisos insuficientes."));
            case "22023", "23514", "23502" -> ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_request", "message", "Parámetros de solicitud inválidos o incompletos."));
            default -> ResponseEntity.internalServerError()
                    .body(Map.of("error", "database_error", "message", "Error al procesar la operación en la base de datos de comunidades."));
        };
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatusError(ResponseStatusException ex) {
        String reason = ex.getReason() != null ? ex.getReason() : "Error en la solicitud.";
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getStatusCode().toString(), "message", reason));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegalArgumentError(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "invalid_argument", "message", ex.getMessage() != null ? ex.getMessage() : "Argumento inválido"));
    }
}
