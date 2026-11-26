package com.example.steambeta.backend.admin;

import com.example.steambeta.backend.auth.AvatarStorageService;
import com.example.steambeta.backend.exchange.ExchangeStorageService;
import java.sql.Array;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Servicio administrativo para vista previa y borrado físico real de cuentas estudiantiles.
 * - Reasigna aportes compartidos e históricos a la identidad técnica anónima ('steambeta.content.anonymous' / 'Usuario eliminado').
 * - Ejecuta DELETE físico real de la cuenta en replica.access_account y replica.account_profile vía SECURITY DEFINER.
 * - Elimina avatar personal y adjuntos propios de intercambio únicamente DESPUÉS de confirmar el commit.
 * - Nunca elimina archivos ubicados en community-resource-storage.
 */
@Service
public class AdminStudentRemovalService {

    private static final Logger log = LoggerFactory.getLogger(AdminStudentRemovalService.class);

    public record StudentRemovalPreview(
            UUID accountId,
            String alias,
            String loginIdentifier,
            String email,
            Instant createdAt,
            long tasksCount,
            long manualSubjectsCount,
            boolean studyProfileExists,
            long notificationsCount,
            long emailTokensCount,
            long exchangeItemsCount,
            long exchangeAttachmentsCount,
            boolean hasAvatar,
            long communitiesCount,
            long postsCount,
            long questionsCount,
            long commentsCount,
            long resourcesCount,
            long opportunitiesCount,
            long eventsCount,
            long sanctionsCount,
            long auditLogsCount
    ) {}

    public record StudentDeletionResult(
            UUID deletedAccountId,
            boolean physicallyDeleted,
            int externalPersonalFilesCleaned,
            int externalPersonalFilesFailed,
            String summaryMessage
    ) {}

    private record DbDeleteOutcome(
            UUID deletedAccountId,
            UUID anonymousActorId,
            String removedAvatarUrl,
            List<String> removedExchangeKeys
    ) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate txTemplate;
    private final AdminConsoleDataService adminData;
    private final AvatarStorageService avatarStorage;
    private final ExchangeStorageService exchangeStorage;

    public AdminStudentRemovalService(JdbcTemplate jdbc,
                                      TransactionTemplate txTemplate,
                                      AdminConsoleDataService adminData,
                                      AvatarStorageService avatarStorage,
                                      ExchangeStorageService exchangeStorage) {
        this.jdbc = jdbc;
        this.txTemplate = txTemplate;
        this.adminData = adminData;
        this.avatarStorage = avatarStorage;
        this.exchangeStorage = exchangeStorage;
    }

    public StudentRemovalPreview preview(UUID targetAccountId) {
        UUID actorId = requireAuthorizedActor();
        if (targetAccountId == null || targetAccountId.equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta objetivo inválida.");
        }
        try {
            var rows = jdbc.query(
                    "select * from replica.admin_preview_student_removal(?::uuid, ?::uuid)",
                    (rs, r) -> new StudentRemovalPreview(
                            rs.getObject("account_id", UUID.class),
                            rs.getString("alias"),
                            rs.getString("login_identifier"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getLong("tasks_count"),
                            rs.getLong("manual_subjects_count"),
                            rs.getBoolean("study_profile_exists"),
                            rs.getLong("notifications_count"),
                            rs.getLong("email_tokens_count"),
                            rs.getLong("exchange_items_count"),
                            rs.getLong("exchange_attachments_count"),
                            rs.getBoolean("has_avatar"),
                            rs.getLong("communities_count"),
                            rs.getLong("posts_count"),
                            rs.getLong("questions_count"),
                            rs.getLong("comments_count"),
                            rs.getLong("resources_count"),
                            rs.getLong("opportunities_count"),
                            rs.getLong("events_count"),
                            rs.getLong("sanctions_count"),
                            rs.getLong("audit_logs_count")
                    ),
                    actorId, targetAccountId);
            if (rows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado.");
            }
            return rows.get(0);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw mapDbError(ex);
        }
    }

    public StudentDeletionResult deletePhysically(UUID targetAccountId, String confirmationText, String reason) {
        UUID actorId = requireAuthorizedActor();
        if (targetAccountId == null || targetAccountId.equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se permite eliminar la identidad administrativa.");
        }
        if (confirmationText == null || !"ELIMINAR".equals(confirmationText.strip())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes escribir exactamente ELIMINAR para confirmar el borrado físico.");
        }

        String cleanReason = (reason == null || reason.isBlank())
                ? "Eliminación física controlada desde STEAMBETA ADMIN"
                : reason.strip();
        if (cleanReason.length() < 5 || cleanReason.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo debe tener entre 5 y 2000 caracteres.");
        }

        DbDeleteOutcome outcome;
        try {
            outcome = txTemplate.execute(status -> {
                var rows = jdbc.query(
                        "select * from replica.admin_delete_student_account(?::uuid, ?::uuid, ?::text)",
                        (rs, r) -> {
                            List<String> keys = new ArrayList<>();
                            Array sqlArr = rs.getArray("removed_exchange_storage_keys");
                            if (sqlArr != null) {
                                Object raw = sqlArr.getArray();
                                if (raw instanceof String[] arr) {
                                    for (String k : arr) {
                                        if (k != null && !k.isBlank()) keys.add(k);
                                    }
                                }
                            }
                            return new DbDeleteOutcome(
                                    rs.getObject("deleted_account_id", UUID.class),
                                    rs.getObject("anonymous_actor_id", UUID.class),
                                    rs.getString("removed_avatar_url"),
                                    keys);
                        },
                        actorId, targetAccountId, cleanReason);
                if (rows.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estudiante no encontrado.");
                }
                return rows.get(0);
            });
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            throw mapDbError(ex);
        }

        if (outcome == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "La transacción de eliminación no devolvió resultado.");
        }

        // Limpieza física de archivos personales exclusivos DESPUÉS de confirmar el commit en PostgreSQL
        // (NUNCA toca community-resource-storage)
        int[] stats = cleanupPersonalFilesPostCommit(outcome.removedAvatarUrl(), outcome.removedExchangeKeys());
        int cleaned = stats[0];
        int failed = stats[1];

        String msg = failed == 0
                ? "Cuenta eliminada físicamente. Los aportes comunitarios compartidos se conservan bajo el autor «Usuario eliminado»."
                : "Cuenta eliminada físicamente en la base de datos, pero no se pudieron eliminar " + failed
                  + " archivo(s) personal(es) del almacenamiento externo.";

        return new StudentDeletionResult(outcome.deletedAccountId(), true, cleaned, failed, msg);
    }

    private int[] cleanupPersonalFilesPostCommit(String avatarUrl, List<String> exchangeKeys) {
        int cleaned = 0;
        int failed = 0;
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            if (tryWithRetry(() -> avatarStorage.deleteByAvatarUrlQuietly(avatarUrl))) cleaned++;
            else failed++;
        }
        if (exchangeKeys != null) {
            for (String key : exchangeKeys) {
                if (tryWithRetry(() -> exchangeStorage.deleteQuietly(key))) cleaned++;
                else failed++;
            }
        }
        return new int[]{cleaned, failed};
    }

    private boolean tryWithRetry(Runnable action) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                action.run();
                return true;
            } catch (Exception ex) {
                if (attempt == 2) {
                    log.warn("Fallo al limpiar archivo personal post-commit tras reintento: {}", ex.getMessage());
                }
            }
        }
        return false;
    }

    private UUID requireAuthorizedActor() {
        var cap = adminData.capabilities();
        if (!cap.dbAuthorized() || adminData.technicalActorId() == null) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, cap.limitationReason());
        }
        return adminData.technicalActorId();
    }

    private static ResponseStatusException mapDbError(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("student_not_found")) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, "La cuenta seleccionada no existe o ya fue eliminada.");
        }
        if (msg.contains("cannot_delete_protected_account")) {
            return new ResponseStatusException(HttpStatus.FORBIDDEN, "No está permitido eliminar cuentas administrativas, de moderadores globales ni la identidad técnica anónima.");
        }
        if (msg.contains("anonymous_content_actor_missing")) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Falta la identidad técnica anónima (steambeta.content.anonymous) en PostgreSQL.");
        }
        if (msg.contains("invalid_target_account")) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cuenta objetivo no válida.");
        }
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo completar el borrado físico: " + msg);
    }
}
