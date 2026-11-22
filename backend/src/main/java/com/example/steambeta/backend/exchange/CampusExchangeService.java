package com.example.steambeta.backend.exchange;

import com.example.steambeta.backend.auth.AccountService;
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
public class CampusExchangeService {
    public record ExchangeItem(
            UUID id,
            UUID ownerAccountId,
            String ownerAlias,
            UUID communityId,
            String title,
            String description,
            String category, // BOOK, CALCULATOR, LAB_EQUIPMENT, NOTES, TOOL, OTHER
            String exchangeType, // GIFT, LOAN, EXCHANGE
            String status, // AVAILABLE, RESERVED, COMPLETED, REPORTED
            String contactInfo,
            Instant createdAt
    ) {}

    public record ExchangeDraft(
            UUID communityId,
            String title,
            String description,
            String category,
            String exchangeType,
            String contactInfo
    ) {}

    public record StatusUpdate(
            String status
    ) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public CampusExchangeService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    public List<ExchangeItem> list(String categoryFilter) {
        String sql = "select e.id, e.owner_account_id, a.alias as owner_alias, e.community_id, " +
                "e.title, e.description, e.category, e.exchange_type, e.status, e.contact_info, e.created_at " +
                "from replica.campus_exchange_item e " +
                "join replica.account_profile a on a.account_id = e.owner_account_id " +
                "where e.status in ('AVAILABLE', 'RESERVED') " +
                (categoryFilter != null && !categoryFilter.isBlank() ? "and e.category = ? " : "") +
                "order by e.created_at desc limit 50";
        try {
            if (categoryFilter != null && !categoryFilter.isBlank()) {
                return jdbc.query(sql, (rs, row) -> mapItem(rs), categoryFilter.strip().toUpperCase(Locale.ROOT));
            } else {
                return jdbc.query(sql, (rs, row) -> mapItem(rs));
            }
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Transactional
    public ExchangeItem create(ExchangeDraft draft) {
        if (draft == null || draft.title() == null || draft.contactInfo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_item");
        }
        UUID ownerId = accounts.current().accountId();
        UUID newId = UUID.randomUUID();
        String cat = draft.category() == null ? "BOOK" : draft.category().strip().toUpperCase(Locale.ROOT);
        String type = draft.exchangeType() == null ? "EXCHANGE" : draft.exchangeType().strip().toUpperCase(Locale.ROOT);

        try {
            jdbc.update("insert into replica.campus_exchange_item(id, owner_account_id, community_id, title, description, category, exchange_type, status, contact_info) " +
                    "values (?, ?, ?, ?, ?, ?, ?, 'AVAILABLE', ?)",
                    newId, ownerId, draft.communityId(), draft.title().strip(), draft.description(), cat, type, draft.contactInfo().strip());

            return new ExchangeItem(newId, ownerId, accounts.fullProfile().alias(), draft.communityId(),
                    draft.title().strip(), draft.description(), cat, type, "AVAILABLE", draft.contactInfo().strip(), Instant.now());
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "could_not_publish_exchange_item");
        }
    }

    @Transactional
    public void updateStatus(UUID itemId, StatusUpdate update) {
        if (update == null || update.status() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_status");
        UUID ownerId = accounts.current().accountId();
        String newStatus = update.status().strip().toUpperCase(Locale.ROOT);

        jdbc.update("update replica.campus_exchange_item set status = ? where id = ? and owner_account_id = ?",
                newStatus, itemId, ownerId);
    }

    private static ExchangeItem mapItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExchangeItem(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_account_id", UUID.class),
                rs.getString("owner_alias"),
                rs.getObject("community_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("category"),
                rs.getString("exchange_type"),
                rs.getString("status"),
                rs.getString("contact_info"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
