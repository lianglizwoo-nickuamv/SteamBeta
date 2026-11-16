package com.example.steambeta.backend.community;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {
    public record Community(UUID id, String slug, String name, String description, String rules, String role) {}
    public record Post(UUID id, UUID communityId, UUID authorAccountId, String authorAlias,
                       String title, String body, Instant createdAt) {}
    public record Page(List<Post> posts, Instant nextBeforeTime, UUID nextBeforeId) {}
    public record Draft(UUID requestKey, String title, String body) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public CommunityService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    @Transactional
    public List<Community> mine() {
        return jdbc.query("select * from replica.my_communities(?)", (rs, row) ->
                new Community(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                        rs.getString("description"), rs.getString("rules"), rs.getString("role")),
                accounts.current().accountId());
    }

    @Transactional
    public Page posts(UUID communityId, Instant beforeTime, UUID beforeId, int limit) {
        if (limit < 1 || limit > 50 || (beforeTime == null) != (beforeId == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_page");
        }
        var rows = jdbc.query("select * from replica.community_posts(?, ?, ?, ?, ?)",
                (rs, row) -> post(rs), accounts.current().accountId(), communityId,
                beforeTime == null ? null : Timestamp.from(beforeTime), beforeId, limit + 1);
        if (rows.size() <= limit) {
            return new Page(rows, null, null);
        }
        var visible = List.copyOf(rows.subList(0, limit));
        var last = visible.getLast();
        return new Page(visible, last.createdAt(), last.id());
    }

    @Transactional
    public Post create(UUID communityId, Draft draft) {
        if (draft == null || draft.requestKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request_key_required");
        }
        String title = text(draft.title(), 3, 160);
        String body = text(draft.body(), 1, 10000);
        return jdbc.queryForObject("select * from replica.create_community_post(?, ?, ?, ?, ?)",
                (rs, row) -> post(rs), accounts.current().accountId(), communityId,
                draft.requestKey(), title, body);
    }

    private static Post post(ResultSet rs) throws SQLException {
        return new Post(rs.getObject("id", UUID.class), rs.getObject("community_id", UUID.class),
                rs.getObject("author_account_id", UUID.class), rs.getString("author_alias"),
                rs.getString("title"), rs.getString("body"), rs.getTimestamp("created_at").toInstant());
    }

    private static String text(String value, int minimum, int maximum) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_post");
        }
        String result = value.strip();
        int length = result.codePointCount(0, result.length());
        if (length < minimum || length > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_post");
        }
        return result;
    }
}
