package com.example.steambeta.backend.community;

import com.example.steambeta.backend.auth.AccountService;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {
    // Entidades base existentes
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

    // FASE 2: Gestión de Comunidades, Invitaciones y Miembros
    public record CommunityCreate(String slug, String name, String description, String rules, String category, String visibility) {}
    public record CommunityOverview(UUID id, String slug, String name, String description, String category,
                                     String visibility, long memberCount, boolean isMember, String userRole) {}
    public record InvitationDraft(String code, Integer maxUses, Instant expiresAt) {}
    public record Invitation(UUID id, UUID communityId, String code, Integer maxUses, int usedCount,
                             Instant expiresAt, Instant createdAt) {}
    public record JoinDraft(String code) {}
    public record Member(UUID accountId, String alias, String role, String declaredUniversity,
                         String declaredCareer, String avatarUrl, Instant joinedAt) {}
    public record RoleUpdate(String role) {}

    // FASE 3: Q&A Académico
    public record QuestionDraft(UUID requestKey, String title, String body, String subjectTag, List<String> tags) {}
    public record Question(UUID id, UUID communityId, UUID authorAccountId, String authorAlias, String title,
                           String body, String subjectTag, List<String> tags, Instant createdAt, String status,
                           long commentCount, boolean isResolved, UUID resolvedCommentId) {}

    // FASE 4: Recursos Académicos
    public record ResourceDraft(String title, String description, String resourceType, String externalUrl,
                                String fileStorageName, String originalFilename, Long fileSizeBytes,
                                String mimeType, String subjectName) {}
    public record ResourceItem(UUID id, UUID communityId, UUID uploaderAccountId, String uploaderAlias,
                               String title, String description, String resourceType, String externalUrl,
                               String originalFilename, Long fileSizeBytes, String mimeType,
                               String subjectName, Instant createdAt) {}

    // FASE 5: Calendario y Actividades
    public record EventDraft(String title, String description, String eventType, Instant eventDate) {}
    public record EventItem(UUID id, UUID communityId, UUID creatorAccountId, String creatorAlias,
                            String title, String description, String eventType, Instant eventDate,
                            String status, Instant createdAt) {}
    public record UpcomingEvent(UUID id, UUID communityId, String communityName, String title,
                                String description, String eventType, Instant eventDate, String status) {}

    // FASE 6 & 7: Notificaciones y Estadísticas
    public record NotificationItem(UUID id, UUID communityId, String sourceActorAlias, String type,
                                   String title, String message, boolean read, Instant createdAt) {}
    public record CommunityStats(UUID communityId, long totalMembers, long totalPosts, long totalQuestions,
                                 long resolvedQuestions, long totalResources, long upcomingEvents) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;

    public CommunityService(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc;
        this.accounts = accounts;
    }

    // =========================================================================
    // Métodos Base de Comunidad (V4 & V5)
    // =========================================================================

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

    @Transactional
    public void deletePost(UUID community, UUID id) {
        jdbc.queryForObject("select replica.delete_community_post(?, ?, ?)", Object.class, accounts.current().accountId(), community, id);
    }

    @Transactional
    public List<Comment> comments(UUID community, UUID post) {
        return jdbc.query("select * from replica.community_comments(?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, post);
    }

    @Transactional
    public Comment createComment(UUID community, UUID post, CommentDraft draft) {
        return jdbc.queryForObject("select * from replica.create_community_comment(?, ?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, post, text(draft == null ? null : draft.body(), 1, 5000));
    }

    @Transactional
    public Comment editComment(UUID community, UUID id, CommentDraft draft) {
        return jdbc.queryForObject("select * from replica.edit_community_comment(?, ?, ?, ?)", CommunityService::comment,
                accounts.current().accountId(), community, id, text(draft == null ? null : draft.body(), 1, 5000));
    }

    @Transactional
    public void deleteComment(UUID community, UUID id) {
        jdbc.queryForObject("select replica.delete_community_comment(?, ?, ?)", Object.class, accounts.current().accountId(), community, id);
    }

    @Transactional
    public UUID report(UUID community, ReportDraft draft) {
        if (draft == null || draft.targetId() == null) throw bad();
        UUID post = "POST".equals(draft.targetType()) ? draft.targetId() : null;
        UUID comment = "COMMENT".equals(draft.targetType()) ? draft.targetId() : null;
        if (post == null && comment == null) throw bad();
        return jdbc.queryForObject("select replica.report_community_content(?, ?, ?, ?, ?)", UUID.class,
                accounts.current().accountId(), community, post, comment, text(draft.reason(), 3, 1000));
    }

    @Transactional
    public List<Report> reports(UUID community) {
        return jdbc.query("select * from replica.moderation_reports(?, ?)", (rs, row) -> new Report(
                rs.getObject("id", UUID.class), rs.getObject("post_id", UUID.class), rs.getObject("comment_id", UUID.class),
                rs.getString("reason"), rs.getString("reporter_alias"), rs.getTimestamp("created_at").toInstant(),
                rs.getString("content_author_alias"), rs.getString("content"), rs.getString("content_status")),
                accounts.current().accountId(), community);
    }

    @Transactional
    public void hide(UUID community, Moderation request) {
        if (request == null || request.targetId() == null) throw bad();
        UUID post = "POST".equals(request.targetType()) ? request.targetId() : null;
        UUID comment = "COMMENT".equals(request.targetType()) ? request.targetId() : null;
        if (post == null && comment == null) throw bad();
        jdbc.queryForObject("select replica.moderate_community_content(?, ?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, post, comment, text(request.reason(), 3, 1000));
    }

    @Transactional
    public void resolve(UUID community, UUID report, Resolution resolution) {
        if (resolution == null || !List.of("RESOLVED", "DISMISSED").contains(resolution.decision())) throw bad();
        jdbc.queryForObject("select replica.resolve_community_report(?, ?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, report, resolution.decision(), text(resolution.note(), 3, 1000));
    }

    @Transactional
    public void hideReported(UUID community, UUID report, Resolution resolution) {
        if (resolution == null) throw bad();
        jdbc.queryForObject("select replica.hide_reported_content(?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), community, report, text(resolution.note(), 3, 1000));
    }

    // =========================================================================
    // FASE 2: Creación, Invitaciones y Administración de Miembros
    // =========================================================================

    @Transactional
    public Community createCommunity(CommunityCreate request) {
        if (request == null) throw bad();
        String slug = text(request.slug(), 3, 80).toLowerCase(Locale.ROOT);
        String name = text(request.name(), 3, 100);
        String description = request.description() == null ? "" : request.description().strip();
        String rules = request.rules() == null || request.rules().isBlank()
                ? "Respeta a los miembros y colabora constructivamente." : request.rules().strip();
        String category = request.category() == null ? "ACADEMICA" : request.category().strip().toUpperCase(Locale.ROOT);
        String visibility = request.visibility() == null ? "PUBLIC" : request.visibility().strip().toUpperCase(Locale.ROOT);

        return jdbc.queryForObject("select * from replica.create_community(?, ?, ?, ?, ?, ?, ?)",
                (rs, row) -> new Community(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("rules"),
                        rs.getString("role")
                ), accounts.current().accountId(), slug, name, description, rules, category, visibility);
    }

    @Transactional
    public List<CommunityOverview> exploreCommunities() {
        return jdbc.query("select * from replica.explore_communities(?)", (rs, row) ->
                new CommunityOverview(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("visibility"),
                        rs.getLong("member_count"),
                        rs.getBoolean("is_member"),
                        rs.getString("user_role")
                ), accounts.current().accountId());
    }

    @Transactional
    public Invitation createInvitation(UUID communityId, InvitationDraft draft) {
        if (draft == null || draft.code() == null) throw bad();
        String code = draft.code().strip().toUpperCase(Locale.ROOT);
        Timestamp expires = draft.expiresAt() == null ? null : Timestamp.from(draft.expiresAt());
        return jdbc.queryForObject("select * from replica.create_community_invitation(?, ?, ?, ?, ?)",
                (rs, row) -> new Invitation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getString("code"),
                        rs.getObject("max_uses", Integer.class),
                        rs.getInt("used_count"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, code, draft.maxUses(), expires);
    }

    @Transactional
    public Community joinByCode(JoinDraft draft) {
        if (draft == null || draft.code() == null) throw bad();
        String code = draft.code().strip().toUpperCase(Locale.ROOT);
        return jdbc.queryForObject("select * from replica.join_community_by_code(?, ?)",
                (rs, row) -> new Community(
                        rs.getObject("community_id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        "",
                        rs.getString("role")
                ), accounts.current().accountId(), code);
    }

    @Transactional
    public List<Member> members(UUID communityId) {
        return jdbc.query("select * from replica.community_members(?, ?)", (rs, row) ->
                new Member(
                        rs.getObject("account_id", UUID.class),
                        rs.getString("alias"),
                        rs.getString("role"),
                        rs.getString("declared_university"),
                        rs.getString("declared_career"),
                        rs.getString("avatar_url"),
                        rs.getTimestamp("joined_at").toInstant()
                ), accounts.current().accountId(), communityId);
    }

    @Transactional
    public void updateMemberRole(UUID communityId, UUID targetAccount, RoleUpdate update) {
        if (update == null || update.role() == null) throw bad();
        jdbc.queryForObject("select replica.update_member_role(?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), communityId, targetAccount, update.role().strip().toUpperCase(Locale.ROOT));
    }

    @Transactional
    public void removeMember(UUID communityId, UUID targetAccount) {
        jdbc.queryForObject("select replica.remove_community_member(?, ?, ?)", Object.class,
                accounts.current().accountId(), communityId, targetAccount);
    }

    // =========================================================================
    // FASE 3: Q&A Académico
    // =========================================================================

    @Transactional
    public Question createQuestion(UUID communityId, QuestionDraft draft) {
        if (draft == null || draft.requestKey() == null) throw bad();
        String title = text(draft.title(), 3, 160);
        String body = text(draft.body(), 1, 10000);
        String subject = draft.subjectTag() == null || draft.subjectTag().isBlank() ? null : text(draft.subjectTag(), 2, 80);
        String[] tags = draft.tags() == null ? new String[0] : draft.tags().toArray(new String[0]);

        return jdbc.queryForObject("select * from replica.create_community_question(?, ?, ?, ?, ?, ?, ?)",
                (rs, row) -> {
                    Array tagArr = rs.getArray("tags");
                    List<String> tagList = tagArr == null ? List.of() : Arrays.asList((String[]) tagArr.getArray());
                    return new Question(
                            rs.getObject("id", UUID.class),
                            rs.getObject("community_id", UUID.class),
                            rs.getObject("author_account_id", UUID.class),
                            rs.getString("author_alias"),
                            rs.getString("title"),
                            rs.getString("body"),
                            rs.getString("subject_tag"),
                            tagList,
                            rs.getTimestamp("created_at").toInstant(),
                            "ACTIVE", 0, false, null
                    );
                }, accounts.current().accountId(), communityId, draft.requestKey(), title, body, subject, tags);
    }

    @Transactional
    public void acceptSolution(UUID communityId, UUID postId, UUID commentId) {
        jdbc.queryForObject("select replica.accept_answer(?, ?, ?, ?)", Object.class,
                accounts.current().accountId(), communityId, postId, commentId);
    }

    @Transactional
    public List<Question> questions(UUID communityId, String status, String subject, String search, int limit) {
        String filter = (status == null || status.isBlank()) ? "ALL" : status.strip().toUpperCase(Locale.ROOT);
        int finalLimit = (limit < 1 || limit > 50) ? 20 : limit;
        return jdbc.query("select * from replica.community_questions(?, ?, ?, ?, ?, ?)", (rs, row) -> {
            Array tagArr = rs.getArray("tags");
            List<String> tagList = tagArr == null ? List.of() : Arrays.asList((String[]) tagArr.getArray());
            return new Question(
                    rs.getObject("id", UUID.class),
                    rs.getObject("community_id", UUID.class),
                    rs.getObject("author_account_id", UUID.class),
                    rs.getString("author_alias"),
                    rs.getString("title"),
                    rs.getString("body"),
                    rs.getString("subject_tag"),
                    tagList,
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getString("status"),
                    rs.getLong("comment_count"),
                    rs.getBoolean("is_resolved"),
                    rs.getObject("resolved_comment_id", UUID.class)
            );
        }, accounts.current().accountId(), communityId, filter, subject, search, finalLimit);
    }

    // =========================================================================
    // FASE 4: Recursos Académicos
    // =========================================================================

    @Transactional
    public ResourceItem publishResource(UUID communityId, ResourceDraft draft) {
        if (draft == null) throw bad();
        String title = text(draft.title(), 3, 160);
        String desc = draft.description() == null ? null : draft.description().strip();
        String type = text(draft.resourceType(), 3, 30).toUpperCase(Locale.ROOT);
        String url = draft.externalUrl() == null || draft.externalUrl().isBlank() ? null : draft.externalUrl().strip();
        String subj = draft.subjectName() == null || draft.subjectName().isBlank() ? null : draft.subjectName().strip();

        return jdbc.queryForObject("select * from replica.publish_community_resource(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (rs, row) -> new ResourceItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        accounts.current().accountId(),
                        rs.getString("uploader_alias"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("resource_type"),
                        rs.getString("external_url"),
                        rs.getString("original_filename"),
                        rs.getObject("file_size_bytes", Long.class),
                        rs.getString("mime_type"),
                        rs.getString("subject_name"),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, title, desc, type, url,
                draft.fileStorageName(), draft.originalFilename(), draft.fileSizeBytes(), draft.mimeType(), subj);
    }

    @Transactional
    public List<ResourceItem> resources(UUID communityId, String type, String subject, String search) {
        return jdbc.query("select * from replica.community_resources(?, ?, ?, ?, ?)", (rs, row) ->
                new ResourceItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getObject("uploader_account_id", UUID.class),
                        rs.getString("uploader_alias"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("resource_type"),
                        rs.getString("external_url"),
                        rs.getString("original_filename"),
                        rs.getObject("file_size_bytes", Long.class),
                        rs.getString("mime_type"),
                        rs.getString("subject_name"),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, type, subject, search);
    }

    // =========================================================================
    // FASE 5: Calendario y Actividades
    // =========================================================================

    @Transactional
    public EventItem createEvent(UUID communityId, EventDraft draft) {
        if (draft == null || draft.eventDate() == null) throw bad();
        String title = text(draft.title(), 3, 160);
        String desc = draft.description() == null ? null : draft.description().strip();
        String type = text(draft.eventType(), 3, 30).toUpperCase(Locale.ROOT);
        Timestamp eventTimestamp = Timestamp.from(draft.eventDate());

        return jdbc.queryForObject("select * from replica.create_community_event(?, ?, ?, ?, ?, ?)",
                (rs, row) -> new EventItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        accounts.current().accountId(),
                        rs.getString("creator_alias"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("event_type"),
                        rs.getTimestamp("event_date").toInstant(),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, title, desc, type, eventTimestamp);
    }

    @Transactional
    public List<EventItem> events(UUID communityId, Instant fromDate, Instant toDate) {
        Timestamp from = fromDate == null ? null : Timestamp.from(fromDate);
        Timestamp to = toDate == null ? null : Timestamp.from(toDate);
        return jdbc.query("select * from replica.community_events(?, ?, ?, ?)", (rs, row) ->
                new EventItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getObject("creator_account_id", UUID.class),
                        rs.getString("creator_alias"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("event_type"),
                        rs.getTimestamp("event_date").toInstant(),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, from, to);
    }

    @Transactional
    public List<UpcomingEvent> myUpcomingEvents(int limit) {
        int finalLimit = (limit < 1 || limit > 20) ? 10 : limit;
        return jdbc.query("select * from replica.my_upcoming_events(?, ?)", (rs, row) ->
                new UpcomingEvent(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getString("community_name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("event_type"),
                        rs.getTimestamp("event_date").toInstant(),
                        rs.getString("status")
                ), accounts.current().accountId(), finalLimit);
    }

    // =========================================================================
    // FASE 6 & 7: Notificaciones y Estadísticas
    // =========================================================================

    @Transactional
    public List<NotificationItem> myNotifications(int limit) {
        int finalLimit = (limit < 1 || limit > 50) ? 20 : limit;
        return jdbc.query("select * from replica.my_notifications(?, ?)", (rs, row) ->
                new NotificationItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getString("source_actor_alias"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getBoolean("read"),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), finalLimit);
    }

    @Transactional
    public void markNotificationRead(UUID notificationId) {
        jdbc.queryForObject("select replica.mark_notification_read(?, ?)", Object.class,
                accounts.current().accountId(), notificationId);
    }

    @Transactional
    public long unreadNotificationsCount() {
        Long count = jdbc.queryForObject("select replica.unread_notifications_count(?)", Long.class,
                accounts.current().accountId());
        return count == null ? 0 : count;
    }

    @Transactional
    public CommunityStats stats(UUID communityId) {
        return jdbc.queryForObject("select * from replica.community_aggregate_stats(?, ?)", (rs, row) ->
                new CommunityStats(
                        rs.getObject("community_id", UUID.class),
                        rs.getLong("total_members"),
                        rs.getLong("total_posts"),
                        rs.getLong("total_questions"),
                        rs.getLong("resolved_questions"),
                        rs.getLong("total_resources"),
                        rs.getLong("upcoming_events")
                ), accounts.current().accountId(), communityId);
    }

    // =========================================================================
    // Métodos Auxiliares
    // =========================================================================

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

    private static ResponseStatusException bad() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_request");
    }

    private static String text(String value, int minimum, int maximum) {
        if (value == null) throw bad();
        String result = value.strip();
        int length = result.codePointCount(0, result.length());
        if (length < minimum || length > maximum) throw bad();
        return result;
    }
}
