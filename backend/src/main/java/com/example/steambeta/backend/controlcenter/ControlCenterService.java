package com.example.steambeta.backend.controlcenter;

import com.example.steambeta.backend.auth.AccountService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ControlCenterService {
    public record StudentOverview(
            UUID accountId,
            String loginIdentifier,
            String alias,
            String declaredUniversity,
            String declaredCareer,
            boolean enabled,
            String globalRole,
            Instant createdAt,
            long activeSanctionsCount,
            long communitiesCount,
            long postsCount) {
    }

    public record SanctionDraft(
            String action, // WARNING, FORMAL_WARNING, TEMPORARY_SUSPENSION, EXTENDED_SUSPENSION,
                           // ACCOUNT_DEACTIVATED
            String reason,
            String evidenceContext,
            Integer durationHours) {
    }

    public record AuditLogEntry(
            UUID id,
            String moderatorAlias,
            String targetLogin,
            String action,
            String reason,
            Instant createdAt) {
    }

    public record AppealResolution(
            String decision, // ACCEPTED, REJECTED
            String note) {
    }

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public ControlCenterService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    private void requireGlobalModerator() {
        var current = accounts.current();
        if (!current.isGlobalModerator()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "global_moderator_required");
        }
    }

    public List<StudentOverview> searchStudents(String query, int limit) {
        requireGlobalModerator();
        int finalLimit = (limit < 1 || limit > 200) ? 20 : limit;
        try {
            return jdbc
                    .query("select * from replica.control_center_search_students(?, ?, ?)",
                            (rs, row) -> new StudentOverview(
                                    rs.getObject("account_id", UUID.class),
                                    rs.getString("login_identifier"),
                                    rs.getString("alias"),
                                    rs.getString("declared_university"),
                                    rs.getString("declared_career"),
                                    rs.getBoolean("enabled"),
                                    rs.getString("global_role"),
                                    rs.getTimestamp("created_at").toInstant(),
                                    rs.getLong("active_sanctions_count"),
                                    rs.getLong("communities_count"),
                                    rs.getLong("posts_count")),
                            accounts.current().accountId(), query, finalLimit)
                    .stream()
                    .filter(s -> s.loginIdentifier() == null
                            || (!"steambeta.admin.system".equalsIgnoreCase(s.loginIdentifier().strip())
                                && !"steambeta.content.anonymous".equalsIgnoreCase(s.loginIdentifier().strip())
                                    && !s.loginIdentifier().strip().toLowerCase(Locale.ROOT).startsWith("deleted.")))
                    .toList();
        } catch (Exception ex) {
            // Fallback si V11 no ha sido corrida aún en la BD local
            return List.of();
        }
    }

    @Transactional
    public UUID applySanction(UUID targetAccountId, SanctionDraft draft) {
        requireGlobalModerator();
        if (draft == null || draft.action() == null || draft.reason() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_sanction");
        }
        try {
            return jdbc.queryForObject("select replica.control_center_apply_sanction(?, ?, ?, ?, ?, ?)", UUID.class,
                    accounts.current().accountId(), targetAccountId, draft.action(), draft.reason(),
                    draft.evidenceContext(), draft.durationHours());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "could_not_apply_sanction: " + ex.getMessage());
        }
    }

    public List<AuditLogEntry> auditLogs(int limit) {
        requireGlobalModerator();
        int finalLimit = (limit < 1 || limit > 100) ? 50 : limit;
        try {
            return jdbc.query("select * from replica.control_center_audit_logs(?, ?)", (rs, row) -> new AuditLogEntry(
                    rs.getObject("id", UUID.class),
                    rs.getString("moderator_alias"),
                    rs.getString("target_login"),
                    rs.getString("action"),
                    rs.getString("reason"),
                    rs.getTimestamp("created_at").toInstant()), accounts.current().accountId(), finalLimit);
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public void resolveAppeal(UUID sanctionId, AppealResolution resolution) {
        requireGlobalModerator();
        if (resolution == null || !List.of("ACCEPTED", "REJECTED").contains(resolution.decision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_resolution");
        }
        UUID actor = accounts.current().accountId();
        boolean accepted = "ACCEPTED".equals(resolution.decision());

        jdbc.update(
                "update replica.account_sanction set appeal_status = ?, appeal_resolved_by = ?, appeal_resolved_at = clock_timestamp(), active = ? "
                        +
                        "where id = ? and appeal_status = 'REQUESTED'",
                resolution.decision(), actor, !accepted, sanctionId);

        // Si se acepta la apelación y la cuenta estaba desactivada, reactivarla
        if (accepted) {
            jdbc.update("update replica.access_account a set enabled = true from replica.account_sanction s " +
                    "where s.id = ? and a.id = s.target_account_id", sanctionId);
        }

        jdbc.update("insert into replica.global_moderation_audit_log(moderator_account_id, action, reason) " +
                "values (?, ?, ?)", actor, "RESOLVE_APPEAL_" + resolution.decision(), resolution.note());
    }
}
