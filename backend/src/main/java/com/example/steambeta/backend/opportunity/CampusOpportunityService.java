package com.example.steambeta.backend.opportunity;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CampusOpportunityService {
    public record OpportunityItem(
            UUID id,
            UUID ownerAccountId,
            String ownerAlias,
            String title,
            String description,
            String category, // SCHOLARSHIP, INTERNSHIP, COURSE, CONTEST, CALL, VOLUNTEER, EVENT, JOB
            String sourceInstitution,
            String sourceUrl,
            Instant deadline,
            Instant createdAt
    ) {}

    public record OpportunityDraft(
            String title,
            String description,
            String category,
            String sourceInstitution,
            String sourceUrl,
            Instant deadline
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public CampusOpportunityService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public List<OpportunityItem> list(String categoryFilter) {
        UUID actorId = accounts.current().accountId();
        String cat = categoryFilter == null || categoryFilter.isBlank() ? null : categoryFilter.strip().toUpperCase(Locale.ROOT);
        return jdbc.query(
                "select * from replica.list_campus_opportunities_v15(?::uuid, ?::text)",
                (rs, row) -> mapOpportunity(rs),
                actorId, cat
        );
    }

    @Transactional
    public OpportunityItem create(OpportunityDraft draft) {
        if (draft == null || draft.title() == null || draft.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título de la convocatoria es obligatorio.");
        }
        UUID actorId = accounts.current().accountId();
        String cat = draft.category() == null ? "SCHOLARSHIP" : draft.category().strip().toUpperCase(Locale.ROOT);
        Timestamp dead = draft.deadline() == null ? null : Timestamp.from(draft.deadline());

        return jdbc.queryForObject(
                "select * from replica.create_campus_opportunity_v15(?::uuid, ?::text, ?::text, ?::text, ?::text, ?::text, ?::timestamptz)",
                (rs, row) -> mapOpportunity(rs),
                actorId, draft.title().strip(), draft.description(), cat,
                draft.sourceInstitution(), draft.sourceUrl(), dead
        );
    }

    @Transactional
    public OpportunityItem update(UUID opportunityId, OpportunityDraft draft) {
        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Los datos de actualización están vacíos.");
        }
        UUID actorId = accounts.current().accountId();
        String cat = draft.category() == null ? null : draft.category().strip().toUpperCase(Locale.ROOT);
        Timestamp dead = draft.deadline() == null ? null : Timestamp.from(draft.deadline());

        return jdbc.queryForObject(
                "select * from replica.update_campus_opportunity_v15(?::uuid, ?::uuid, ?::text, ?::text, ?::text, ?::text, ?::text, ?::timestamptz)",
                (rs, row) -> mapOpportunity(rs),
                actorId, opportunityId, draft.title(), draft.description(), cat,
                draft.sourceInstitution(), draft.sourceUrl(), dead
        );
    }

    @Transactional
    public void delete(UUID opportunityId) {
        UUID actorId = accounts.current().accountId();
        jdbc.query("select replica.delete_campus_opportunity_v15(?::uuid, ?::uuid)", rs -> null, actorId, opportunityId);
    }

    private static OpportunityItem mapOpportunity(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OpportunityItem(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_account_id", UUID.class),
                rs.getString("owner_alias"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("source_institution"),
                rs.getString("source_url"),
                rs.getTimestamp("deadline") == null ? null : rs.getTimestamp("deadline").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
