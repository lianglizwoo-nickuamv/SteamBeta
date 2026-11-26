package com.example.steambeta.backend.community;

import com.example.steambeta.backend.auth.AccountService;
import java.security.SecureRandom;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommunityService {
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateUniqueInvitationCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // Entidades base existentes (ampliadas con ocupación, capacidad, categoría y visibilidad V14)
    public record Community(UUID id, String slug, String name, String description, String rules, String role,
                            String initialInvitationCode, long memberCount, int maxMembers, int availableSlots,
                            String category, String visibility) {
        public Community(UUID id, String slug, String name, String description, String rules, String role) {
            this(id, slug, name, description, rules, role, null, 0, 50, 50, null, null);
        }
        public Community(UUID id, String slug, String name, String description, String rules, String role, String initialInvitationCode) {
            this(id, slug, name, description, rules, role, initialInvitationCode, 0, 50, 50, null, null);
        }
        public Community(UUID id, String slug, String name, String description, String rules, String role,
                         String initialInvitationCode, long memberCount, int maxMembers, int availableSlots) {
            this(id, slug, name, description, rules, role, initialInvitationCode, memberCount, maxMembers, availableSlots, null, null);
        }
    }
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

    // FASE 2: Gestión de Comunidades, Invitaciones y Miembros (ampliado V14 con capacidad máxima)
    public record CommunityCreate(String slug, String name, String description, String rules, String category,
                                  String visibility, Integer maxMembers) {
        public CommunityCreate(String slug, String name, String description, String rules, String category, String visibility) {
            this(slug, name, description, rules, category, visibility, 50);
        }
    }
    public record CommunityOverview(UUID id, String slug, String name, String description, String category,
                                     String visibility, long memberCount, int maxMembers, int availableSlots,
                                     boolean isMember, String userRole) {}
    public record InvitationDraft(String code, Integer maxUses, Instant expiresAt) {}
    public record Invitation(UUID id, UUID communityId, String code, Integer maxUses, int usedCount,
                             Instant expiresAt, Instant createdAt) {}
    public record JoinDraft(String code, UUID communityId) {
        public JoinDraft(String code) {
            this(code, null);
        }
    }
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
    public record EventDraft(String title, String description, String eventType, Instant eventDate, String eventDateIso) {
        public EventDraft(String title, String description, String eventType, Instant eventDate) {
            this(title, description, eventType, eventDate, null);
        }
    }
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

    public record ResourceDownload(Resource resource, String originalFilename, String mimeType, long sizeBytes) {}

    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final CommunityResourceStorageService resourceStorage;

    public CommunityService(JdbcTemplate jdbc, AccountService accounts,
                            CommunityResourceStorageService resourceStorage) {
        this.jdbc = jdbc;
        this.accounts = accounts;
        this.resourceStorage = resourceStorage;
    }

    // =========================================================================
    // Métodos Base de Comunidad (V4 & V5)
    // =========================================================================

    @Transactional
    public List<Community> mine() {
        return jdbc.query("select * from replica.my_communities_v14(?::uuid)", (rs, row) ->
                new Community(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("rules"),
                        rs.getString("role"),
                        null,
                        rs.getLong("member_count"),
                        rs.getInt("max_members"),
                        rs.getInt("available_slots"),
                        rs.getString("category"),
                        rs.getString("visibility")
                ), accounts.current().accountId());
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
        UUID actor = accounts.current().accountId();
        jdbc.query("select replica.delete_community_post(?, ?, ?)", rs -> null, actor, community, id);
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
        UUID actor = accounts.current().accountId();
        jdbc.query("select replica.delete_community_comment(?, ?, ?)", rs -> null, actor, community, id);
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
        int maxMembers = request.maxMembers() == null ? 50 : request.maxMembers();
        if (maxMembers < 2 || maxMembers > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La capacidad máxima de integrantes debe estar entre 2 y 50.");
        }

        Community created = jdbc.queryForObject(
                "select * from replica.create_community_v14(?::uuid, ?::text, ?::text, ?::text, ?::text, ?::text, ?::text, ?::integer)",
                (rs, row) -> new Community(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("rules"),
                        rs.getString("role"),
                        null,
                        rs.getLong("member_count"),
                        rs.getInt("max_members"),
                        rs.getInt("available_slots"),
                        rs.getString("category"),
                        rs.getString("visibility")
                ), accounts.current().accountId(), slug, name, description, rules, category, visibility, maxMembers);

        String initialCode = null;
        if ("INVITE_ONLY".equals(visibility) || "PRIVATE".equals(visibility)) {
            initialCode = generateUniqueInvitationCode();
            Timestamp expires = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));
            jdbc.queryForObject(
                    "select * from replica.create_community_invitation(?::uuid, ?::uuid, ?::text, ?::integer, ?::timestamptz)",
                    (rs, row) -> rs.getString("code"),
                    accounts.current().accountId(), created.id(), initialCode, 50, expires);
        }

        return new Community(created.id(), created.slug(), created.name(), created.description(), created.rules(),
                created.role(), initialCode, created.memberCount(), created.maxMembers(), created.availableSlots(),
                created.category(), created.visibility());
    }

    @Transactional
    public List<CommunityOverview> exploreCommunities() {
        return exploreCommunities(null, null);
    }

    @Transactional
    public List<CommunityOverview> exploreCommunities(String query, String category) {
        String cleanQuery = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String cleanCategory = category == null ? "" : category.strip().toUpperCase(Locale.ROOT);

        var all = jdbc.query("select * from replica.explore_communities_v14(?::uuid)", (rs, row) ->
                new CommunityOverview(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("category"),
                        rs.getString("visibility"),
                        rs.getLong("member_count"),
                        rs.getInt("max_members"),
                        rs.getInt("available_slots"),
                        rs.getBoolean("is_member"),
                        rs.getString("user_role")
                ), accounts.current().accountId());

        return all.stream()
                .filter(c -> "PUBLIC".equalsIgnoreCase(c.visibility()) || "INVITE_ONLY".equalsIgnoreCase(c.visibility()))
                .filter(c -> cleanCategory.isBlank() || cleanCategory.equalsIgnoreCase(c.category()))
                .filter(c -> cleanQuery.isBlank()
                        || (c.name() != null && c.name().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (c.description() != null && c.description().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (c.slug() != null && c.slug().toLowerCase(Locale.ROOT).contains(cleanQuery))
                        || (c.category() != null && c.category().toLowerCase(Locale.ROOT).contains(cleanQuery)))
                .toList();
    }

    @Transactional
    public Invitation createInvitation(UUID communityId, InvitationDraft draft) {
        String code;
        Integer maxUses = 50;
        Timestamp expires = Timestamp.from(Instant.now().plus(30, ChronoUnit.DAYS));

        if (draft != null) {
            code = (draft.code() != null && !draft.code().isBlank())
                    ? draft.code().strip().toUpperCase(Locale.ROOT)
                    : generateUniqueInvitationCode();
            if (draft.maxUses() != null && draft.maxUses() > 0) {
                maxUses = draft.maxUses();
            }
            if (draft.expiresAt() != null) {
                expires = Timestamp.from(draft.expiresAt());
            }
        } else {
            code = generateUniqueInvitationCode();
        }

        return jdbc.queryForObject("select * from replica.create_community_invitation(?::uuid, ?::uuid, ?::text, ?::integer, ?::timestamptz)",
                (rs, row) -> new Invitation(
                        rs.getObject("id", UUID.class),
                        rs.getObject("community_id", UUID.class),
                        rs.getString("code"),
                        rs.getObject("max_uses", Integer.class),
                        rs.getInt("used_count"),
                        rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("created_at").toInstant()
                ), accounts.current().accountId(), communityId, code, maxUses, expires);
    }

    @Transactional
    public Community joinByCode(JoinDraft draft) {
        if (draft == null || draft.code() == null || draft.code().isBlank()) throw bad();
        String code = draft.code().strip().toUpperCase(Locale.ROOT);
        return jdbc.queryForObject("select * from replica.join_community_by_code_v14(?::uuid, ?::text, ?::uuid)",
                (rs, row) -> new Community(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("rules"),
                        rs.getString("role"),
                        null,
                        rs.getLong("member_count"),
                        rs.getInt("max_members"),
                        rs.getInt("available_slots"),
                        rs.getString("category"),
                        rs.getString("visibility")
                ), accounts.current().accountId(), code, draft.communityId());
    }

    @Transactional
    public Community joinPublicCommunity(UUID communityId) {
        if (communityId == null) throw bad();
        return jdbc.queryForObject("select * from replica.join_public_community_v14(?::uuid, ?::uuid)",
                (rs, row) -> new Community(
                        rs.getObject("id", UUID.class),
                        rs.getString("slug"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("rules"),
                        rs.getString("role"),
                        null,
                        rs.getLong("member_count"),
                        rs.getInt("max_members"),
                        rs.getInt("available_slots"),
                        rs.getString("category"),
                        rs.getString("visibility")
                ), accounts.current().accountId(), communityId);
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

    /**
     * Permite que el estudiante autenticado abandone su propia membresía en la comunidad.
     * La identidad del actor se obtiene exclusivamente de la sesión autenticada.
     * Reutiliza replica.remove_community_member(p_actor, p_community, p_target_account),
     * respetando la restricción del último administrador.
     */
    @Transactional
    public void leaveCommunity(UUID communityId) {
        if (communityId == null) throw bad();
        UUID actor = accounts.current().accountId();
        jdbc.queryForObject("select replica.remove_community_member(?, ?, ?)", Object.class,
                actor, communityId, actor);
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
    public ResourceItem publishResourceWithFile(UUID communityId, String rawTitle, String rawDescription,
                                                String rawType, String rawSubject, MultipartFile file) {
        String title = text(rawTitle, 3, 160);
        String desc = (rawDescription == null || rawDescription.isBlank()) ? null : rawDescription.strip();
        if (desc != null && desc.length() > 2000) throw bad();
        String type = text(rawType, 3, 30).toUpperCase(Locale.ROOT);
        if (!Set.of("DOCUMENT", "PDF", "SLIDES", "LAB_GUIDE").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de recurso inválido para archivo descargable.");
        }
        String subj = (rawSubject == null || rawSubject.isBlank()) ? null : rawSubject.strip();
        if (subj != null && (subj.length() < 2 || subj.length() > 80)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La asignatura debe tener entre 2 y 80 caracteres.");
        }

        UUID actorId = accounts.current().accountId();
        var stored = resourceStorage.store(file);
        try {
            return jdbc.queryForObject("select * from replica.publish_community_resource(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    (rs, row) -> new ResourceItem(
                            rs.getObject("id", UUID.class),
                            rs.getObject("community_id", UUID.class),
                            actorId,
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
                    ), actorId, communityId, title, desc, type, null,
                    stored.storageName(), stored.originalFilename(), stored.sizeBytes(), stored.mimeType(), subj);
        } catch (RuntimeException ex) {
            resourceStorage.deleteQuietly(stored.storageName());
            throw ex;
        }
    }

    @Transactional
    public ResourceDownload downloadResource(UUID communityId, UUID resourceId) {
        UUID actorId = accounts.current().accountId();
        record DownloadMeta(String storageName, String originalFilename, String mimeType, long sizeBytes) {}
        DownloadMeta meta = jdbc.queryForObject("select * from replica.get_resource_download(?, ?, ?)",
                (rs, row) -> new DownloadMeta(
                        rs.getString("file_storage_name"),
                        rs.getString("original_filename"),
                        rs.getString("mime_type"),
                        rs.getLong("file_size_bytes")
                ), actorId, communityId, resourceId);
        if (meta == null || meta.storageName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso descargable no disponible.");
        }
        Resource fileRes = resourceStorage.loadAsResource(meta.storageName());
        String mime = (meta.mimeType() != null && !meta.mimeType().isBlank()) ? meta.mimeType() : "application/octet-stream";
        String orig = (meta.originalFilename() != null && !meta.originalFilename().isBlank()) ? meta.originalFilename() : "recurso";
        return new ResourceDownload(fileRes, orig, mime, meta.sizeBytes());
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
        if (draft == null) throw bad();
        Instant date = draft.eventDate();
        if (date == null && draft.eventDateIso() != null && !draft.eventDateIso().isBlank()) {
            try {
                date = Instant.parse(draft.eventDateIso());
            } catch (Exception ignored) {}
        }
        if (date == null) throw bad();

        String title = text(draft.title(), 3, 160);
        String desc = draft.description() == null ? null : draft.description().strip();
        String type = text(draft.eventType(), 3, 30).toUpperCase(Locale.ROOT);
        Timestamp eventTimestamp = Timestamp.from(date);

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
