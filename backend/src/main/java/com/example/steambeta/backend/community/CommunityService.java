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
    public record Post(UUID id, UUID communityId, UUID authorAccountId, String authorAlias, String title,
                       String body, Instant createdAt, String status, long commentCount) {}
    public record Page(List<Post> posts, Instant nextBeforeTime, UUID nextBeforeId) {}
    public record Draft(UUID requestKey, String title, String body) {}
    public record Comment(UUID id, UUID postId, UUID authorAccountId, String authorAlias, String body,
                          String status, Instant createdAt, Instant updatedAt) {}
    public record CommentDraft(String body) {}
    public record PostEdit(String title, String body) {}
    public record ReportDraft(String targetType, UUID targetId, String reason) {}
    public record Report(UUID id, UUID postId, UUID commentId, String reason, String reporterAlias,
                         Instant createdAt, String contentAuthorAlias, String content, String contentStatus) {}
    public record Resolution(String decision, String note) {}
    public record Moderation(String targetType, UUID targetId, String reason) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public CommunityService(JdbcTemplate jdbc, AccountService accounts) { this.jdbc = jdbc; this.accounts = accounts; }

    @Transactional
    public List<Community> mine() {
        return jdbc.query("select * from replica.my_communities(?)", (rs, row) ->
                new Community(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                        rs.getString("description"), rs.getString("rules"), rs.getString("role")), accounts.current().accountId());
    }

    @Transactional
    public Page posts(UUID communityId, Instant beforeTime, UUID beforeId, int limit) {
        if (limit < 1 || limit > 50 || (beforeTime == null) != (beforeId == null)) throw bad();
        var rows = jdbc.query("select * from replica.community_posts(?, ?, ?, ?, ?)", (rs, row) -> new Post(
                        rs.getObject("id", UUID.class), rs.getObject("community_id", UUID.class),
                        rs.getObject("author_account_id", UUID.class), rs.getString("author_alias"), rs.getString("title"),
                        rs.getString("body"), rs.getTimestamp("created_at").toInstant(), rs.getString("status"), rs.getLong("comment_count")),
                accounts.current().accountId(), communityId, beforeTime == null ? null : Timestamp.from(beforeTime), beforeId, limit + 1);
        if (rows.size() <= limit) return new Page(rows, null, null);
        var visible = List.copyOf(rows.subList(0, limit));
        var last = visible.getLast();
        return new Page(visible, last.createdAt(), last.id());
    }

    @Transactional
    public Post create(UUID communityId, Draft draft) {
        if (draft == null || draft.requestKey() == null) throw bad();
        return jdbc.queryForObject("select * from replica.create_community_post(?, ?, ?, ?, ?)",
                (rs, row) -> post(rs), accounts.current().accountId(), communityId,
                draft.requestKey(), text(draft.title(), 3, 160), text(draft.body(), 1, 10000));
    }

    @Transactional
    public Post editPost(UUID community, UUID id, PostEdit edit) {
        if (edit == null) throw bad();
        return jdbc.queryForObject("select * from replica.edit_community_post(?, ?, ?, ?, ?)",
                (rs, row) -> post(rs), accounts.current().accountId(), community, id,
                text(edit.title(), 3, 160), text(edit.body(), 1, 10000));
    }

    @Transactional public void deletePost(UUID community, UUID id) {
        jdbc.queryForObject("select replica.delete_community_post(?, ?, ?)", Object.class, accounts.current().accountId(), community, id);
    }

    @Transactional public List<Comment> comments(UUID community, UUID post) {
        return jdbc.query("select * from replica.community_comments(?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, post);
    }

    @Transactional public Comment createComment(UUID community, UUID post, CommentDraft draft) {
        return jdbc.queryForObject("select * from replica.create_community_comment(?, ?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, post, text(draft == null ? null : draft.body(), 1, 5000));
    }

    @Transactional public Comment editComment(UUID community, UUID id, CommentDraft draft) {
        return jdbc.queryForObject("select * from replica.edit_community_comment(?, ?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, id, text(draft == null ? null : draft.body(), 1, 5000));
    }

    @Transactional public void deleteComment(UUID community, UUID id) {
        jdbc.queryForObject("select replica.delete_community_comment(?, ?, ?)", Object.class, accounts.current().accountId(), community, id);
    }

    @Transactional public UUID report(UUID community, ReportDraft draft) {
        if (draft == null || draft.targetId() == null) throw bad();
        UUID post = "POST".equals(draft.targetType()) ? draft.targetId() : null;
        UUID comment = "COMMENT".equals(draft.targetType()) ? draft.targetId() : null;
        if (post == null && comment == null) throw bad();
        return jdbc.queryForObject("select replica.report_community_content(?, ?, ?, ?, ?)", UUID.class,
                accounts.current().accountId(), community, post, comment, text(draft.reason(), 3, 1000));
    }

    @Transactional public List<Report> reports(UUID community) {
        return jdbc.query("select * from replica.moderation_reports(?, ?)", (rs, row) -> new Report(
                rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class), rs.getObject("comment_id", UUID.class),
                rs.getString("reason"), rs.getString("reporter_alias"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("content_author_alias"), rs.getString("content"), rs.getString("content_status")),
                accounts.current().accountId(), community);
    }

    @Transactional public void hide(UUID community, Moderation request) {
        if (request == null || request.targetId() == null) throw bad();
        UUID post = "POST".equals(request.targetType()) ? request.targetId() : null;
        UUID comment = "COMMENT".equals(request.targetType()) ? request.targetId() : null;
        if (post == null && comment == null) throw bad();
        jdbc.queryForObject("select replica.moderate_community_content(?, ?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, post, comment, text(request.reason(), 3, 1000));
    }

    @Transactional public void resolve(UUID community, UUID report, Resolution resolution) {
        if (resolution == null || !List.of("RESOLVED", "DISMISSED").contains(resolution.decision())) throw bad();
        jdbc.queryForObject("select replica.resolve_community_report(?, ?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, report, resolution.decision(), text(resolution.note(), 3, 1000));
    }

    @Transactional public void hideReported(UUID community, UUID report, Resolution resolution) {
        if (resolution == null) throw bad();
        jdbc.queryForObject("select replica.hide_reported_content(?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, report, text(resolution.note(), 3, 1000));
    }

    private static Post post(ResultSet rs) throws SQLException {
        return new Post(rs.getObject("id", UUID.class), rs.getObject("community_id", UUID.class),
                rs.getObject("author_account_id", UUID.class), rs.getString("author_alias"), rs.getString("title"),
                rs.getString("body"), rs.getTimestamp("created_at").toInstant(), "ACTIVE", 0);
    }
    private static Comment comment(ResultSet rs, int row) throws SQLException {
        return new Comment(rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class),
                rs.getObject("author_account_id", UUID.class), rs.getString("author_alias"), rs.getString("body"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private static ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_request"); }
    private static String text(String value, int minimum, int maximum) {
        if (value == null) throw bad();
        String result = value.strip(); int length = result.codePointCount(0, result.length());
        if (length < minimum || length > maximum) throw bad();
        return result;
    }
}
