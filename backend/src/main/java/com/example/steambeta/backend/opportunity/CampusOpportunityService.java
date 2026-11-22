package com.example.steambeta.backend.opportunity;

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

    public CampusOpportunityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OpportunityItem> list(String categoryFilter) {
        String sql = "select id, title, description, category, source_institution, source_url, deadline, created_at " +
                "from replica.campus_opportunity where active and (deadline is null or deadline >= clock_timestamp()) " +
                (categoryFilter != null && !categoryFilter.isBlank() ? "and category = ? " : "") +
                "order by created_at desc limit 50";
        try {
            if (categoryFilter != null && !categoryFilter.isBlank()) {
                return jdbc.query(sql, (rs, row) -> mapOpportunity(rs), categoryFilter.strip().toUpperCase(Locale.ROOT));
            } else {
                return jdbc.query(sql, (rs, row) -> mapOpportunity(rs));
            }
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public OpportunityItem create(OpportunityDraft draft) {
        if (draft == null || draft.title() == null || draft.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_opportunity");
        }
        UUID newId = UUID.randomUUID();
        String cat = draft.category() == null ? "COURSE" : draft.category().strip().toUpperCase(Locale.ROOT);
        Timestamp dead = draft.deadline() == null ? null : Timestamp.from(draft.deadline());

        try {
            jdbc.update("insert into replica.campus_opportunity(id, title, description, category, source_institution, source_url, deadline, active) " +
                    "values (?, ?, ?, ?, ?, ?, ?, true)",
                    newId, draft.title().strip(), draft.description(), cat, draft.sourceInstitution(), draft.sourceUrl(), dead);

            return new OpportunityItem(newId, draft.title().strip(), draft.description(), cat,
                    draft.sourceInstitution(), draft.sourceUrl(), draft.deadline(), Instant.now());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could_not_create_opportunity");
        }
    }

    private static OpportunityItem mapOpportunity(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OpportunityItem(
                rs.getObject("id", UUID.class),
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
