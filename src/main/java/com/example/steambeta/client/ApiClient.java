package com.example.steambeta.client;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Cliente HTTP con soporte CSRF, cookies y contratos de SteamBeta Campus. */
public final class ApiClient {
    // Registros de identidad y perfil
    public record Identity(String studentId, String alias, String universityName) {}
    public record Profile(String accountId, String alias, String declaredUniversity, String declaredCareer,
                          String bio, String avatarUrl, String academicInterests, boolean institutionalIdentityVerified) {}

    // Registros académicos de biblioteca (V1)
    public record Subject(String offeringId, String code, String name, String termCode, String sectionCode,
                          long requiredCount, long completedCount, BigDecimal progressPercent) {}
    public record Activity(String activityId, String code, String name, boolean required, boolean completed) {}
    public record Detail(Subject summary, List<Activity> activities) {}

    // Registros de Comunidad y Publicaciones (V4 & V5)
    public record Community(String id, String slug, String name, String description, String rules, String role) {}
    public record Post(String id, String communityId, String authorAccountId, String authorAlias,
                       String title, String body, String createdAt, String status, long commentCount) {}
    public record PostPage(List<Post> posts, String nextBeforeTime, String nextBeforeId) {}
    public record Comment(String id, String postId, String authorAccountId, String authorAlias,
                          String body, String status, String createdAt, String updatedAt, boolean isSolution) {}
    public record Report(String id, String postId, String commentId, String reason, String reporterAlias,
                         String createdAt, String contentAuthorAlias, String content, String contentStatus) {}

    // FASE 2: Exploración, Invitaciones y Miembros
    public record CommunityOverview(String id, String slug, String name, String description, String category,
                                     String visibility, long memberCount, boolean isMember, String userRole) {}
    public record Invitation(String id, String communityId, String code, Integer maxUses, int usedCount,
                             String expiresAt, String createdAt) {}
    public record Member(String accountId, String alias, String role, String declaredUniversity,
                         String declaredCareer, String avatarUrl, String joinedAt) {}

    // FASE 3: Q&A Académico
    public record Question(String id, String communityId, String authorAccountId, String authorAlias,
                           String title, String body, String subjectTag, List<String> tags,
                           String createdAt, String status, long commentCount, boolean isResolved,
                           String resolvedCommentId) {}

    // FASE 4: Recursos Académicos
    public record ResourceItem(String id, String communityId, String uploaderAccountId, String uploaderAlias,
                               String title, String description, String resourceType, String externalUrl,
                               String originalFilename, Long fileSizeBytes, String mimeType,
                               String subjectName, String createdAt) {}

    // FASE 5: Calendario y Actividades
    public record EventItem(String id, String communityId, String creatorAccountId, String creatorAlias,
                            String title, String description, String eventType, String eventDate,
                            String status, String createdAt) {}
    public record UpcomingEvent(String id, String communityId, String communityName, String title,
                                String description, String eventType, String eventDate, String status) {}

    // FASE 6 & 7: Notificaciones y Estadísticas
    public record NotificationItem(String id, String communityId, String sourceActorAlias, String type,
                                   String title, String message, boolean read, String createdAt) {}
    public record CommunityStats(String communityId, long totalMembers, long totalPosts, long totalQuestions,
                                 long resolvedQuestions, long totalResources, long upcomingEvents) {}

    private record Csrf(String headerName, String token) {}

    public static final class Failure extends RuntimeException {
        private final int status;
        public Failure(int status) { super("HTTP request failed with status " + status); this.status = status; }
        public int status() { return status; }
    }

    private final URI base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(4)).build();
    private final Gson json = new Gson();
    private Csrf csrf;
    private String accountId;

    public ApiClient() {
        this(System.getProperty("replica.api.url", System.getenv().getOrDefault("REPLICA_API_URL", "http://localhost:8080")));
    }

    public ApiClient(String url) {
        base = URI.create(url.replaceAll("/+$", "") + "/");
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null)
            throw new IllegalArgumentException("Invalid backend URL");
    }

    private String request(String path, String body) throws Exception {
        return request(path, body, "application/x-www-form-urlencoded", body == null ? "GET" : "POST");
    }

    private String request(String path, String body, String contentType) throws Exception {
        return request(path, body, contentType, "POST");
    }

    private String request(String path, String body, String contentType, String method) throws Exception {
        long timeout = Long.parseLong(System.getenv().getOrDefault("REPLICA_HTTP_TIMEOUT_SECONDS", "12"));
        var builder = HttpRequest.newBuilder(base.resolve(path.substring(1))).timeout(Duration.ofSeconds(timeout))
                .header("Accept", "application/json");
        if (method.equals("GET")) {
            builder.GET();
        } else {
            if (csrf == null) throw new IllegalStateException("CSRF token required");
            builder.header(csrf.headerName(), csrf.token());
            if (body != null) builder.header("Content-Type", contentType);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        }
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Failure(response.statusCode());
        return response.body();
    }

    // =========================================================================
    // Autenticación y Perfil
    // =========================================================================

    public Identity login(String login, String password) throws Exception {
        csrf = json.fromJson(request("/api/auth/csrf", null), Csrf.class);
        var identity = json.fromJson(request("/api/auth/login", "login=" + encode(login) + "&password=" + encode(password)), Identity.class);
        csrf = json.fromJson(request("/api/auth/csrf", null), Csrf.class);
        accountId = profile().accountId();
        return identity;
    }

    public void register(String login, String password, String alias, String university, String career) throws Exception {
        csrf = json.fromJson(request("/api/auth/csrf", null), Csrf.class);
        var data = new LinkedHashMap<String, String>();
        data.put("login", login); data.put("password", password); data.put("alias", alias);
        data.put("declaredUniversity", university); data.put("declaredCareer", career);
        request("/api/auth/register", json.toJson(data), "application/json");
    }

    public Profile profile() throws Exception {
        try {
            return json.fromJson(request("/api/account/full", null), Profile.class);
        } catch (Exception fallback) {
            return json.fromJson(request("/api/account", null), Profile.class);
        }
    }

    public Profile updateProfile(String alias, String university, String career, String bio, String avatarUrl, String interests) throws Exception {
        var data = new LinkedHashMap<String, String>();
        data.put("alias", alias); data.put("declaredUniversity", university); data.put("declaredCareer", career);
        data.put("bio", bio); data.put("avatarUrl", avatarUrl); data.put("academicInterests", interests);
        return json.fromJson(request("/api/account", json.toJson(data), "application/json", "PATCH"), Profile.class);
    }

    // =========================================================================
    // Comunidades y Membresías (V4 & FASE 2)
    // =========================================================================

    public List<Community> communities() throws Exception {
        return List.of(json.fromJson(request("/api/me/communities", null), Community[].class));
    }

    public Community createCommunity(String slug, String name, String description, String rules, String category, String visibility) throws Exception {
        var data = new LinkedHashMap<String, String>();
        data.put("slug", slug); data.put("name", name); data.put("description", description);
        data.put("rules", rules); data.put("category", category); data.put("visibility", visibility);
        return json.fromJson(request("/api/communities", json.toJson(data), "application/json"), Community.class);
    }

    public List<CommunityOverview> exploreCommunities() throws Exception {
        return List.of(json.fromJson(request("/api/communities/explore", null), CommunityOverview[].class));
    }

    public Invitation createInvitation(String communityId, String code, Integer maxUses, String expiresAt) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", code); data.put("maxUses", maxUses); data.put("expiresAt", expiresAt);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/invitations",
                json.toJson(data), "application/json"), Invitation.class);
    }

    public Community joinCommunity(String code) throws Exception {
        var data = Map.of("code", code);
        return json.fromJson(request("/api/communities/join", json.toJson(data), "application/json"), Community.class);
    }

    public List<Member> members(String communityId) throws Exception {
        return List.of(json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/members", null), Member[].class));
    }

    public void updateMemberRole(String communityId, String targetAccountId, String newRole) throws Exception {
        var data = Map.of("role", newRole);
        request("/api/communities/" + UUID.fromString(communityId) + "/members/" + UUID.fromString(targetAccountId) + "/role",
                json.toJson(data), "application/json", "PUT");
    }

    public void removeMember(String communityId, String targetAccountId) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/members/" + UUID.fromString(targetAccountId),
                null, "application/json", "DELETE");
    }

    // =========================================================================
    // Publicaciones y Comentarios (V4 & V5)
    // =========================================================================

    public PostPage posts(String communityId, String beforeTime, String beforeId) throws Exception {
        String path = "/api/communities/" + UUID.fromString(communityId) + "/posts?limit=10";
        if (beforeTime != null && beforeId != null) {
            path += "&beforeTime=" + encode(beforeTime) + "&beforeId=" + UUID.fromString(beforeId);
        }
        return json.fromJson(request(path, null), PostPage.class);
    }

    public Post createPost(String communityId, UUID requestKey, String title, String body) throws Exception {
        var data = new LinkedHashMap<String, String>();
        data.put("requestKey", requestKey.toString()); data.put("title", title); data.put("body", body);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/posts",
                json.toJson(data), "application/json"), Post.class);
    }

    public void editPost(String communityId, String postId, String title, String body) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/posts/" + UUID.fromString(postId),
                json.toJson(Map.of("title", title, "body", body)), "application/json", "PATCH");
    }

    public void deletePost(String communityId, String postId) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/posts/" + UUID.fromString(postId),
                null, "application/json", "DELETE");
    }

    public List<Comment> comments(String communityId, String postId) throws Exception {
        return List.of(json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/posts/" + UUID.fromString(postId) + "/comments", null), Comment[].class));
    }

    public Comment comment(String communityId, String postId, String body) throws Exception {
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/posts/" + UUID.fromString(postId) + "/comments",
                json.toJson(Map.of("body", body)), "application/json"), Comment.class);
    }

    public void editComment(String communityId, String commentId, String body) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/comments/" + UUID.fromString(commentId),
                json.toJson(Map.of("body", body)), "application/json", "PATCH");
    }

    public void deleteComment(String communityId, String commentId) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/comments/" + UUID.fromString(commentId),
                null, "application/json", "DELETE");
    }

    // =========================================================================
    // Moderación y Reportes (V5)
    // =========================================================================

    public void report(String communityId, String targetType, String targetId, String reason) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/reports",
                json.toJson(Map.of("targetType", targetType, "targetId", UUID.fromString(targetId), "reason", reason)), "application/json");
    }

    public List<Report> reports(String communityId) throws Exception {
        return List.of(json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/moderation/reports", null), Report[].class));
    }

    public void hide(String communityId, String targetType, String targetId, String reason) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/moderation/hide",
                json.toJson(Map.of("targetType", targetType, "targetId", UUID.fromString(targetId), "reason", reason)), "application/json");
    }

    public void resolveReport(String communityId, String reportId, String decision, String note) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/moderation/reports/" + UUID.fromString(reportId) + "/resolution",
                json.toJson(Map.of("decision", decision, "note", note)), "application/json");
    }

    public void hideReported(String communityId, String reportId, String note) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/moderation/reports/" + UUID.fromString(reportId) + "/hide",
                json.toJson(Map.of("decision", "RESOLVED", "note", note)), "application/json");
    }

    // =========================================================================
    // FASE 3: Q&A Académico
    // =========================================================================

    public Question createQuestion(String communityId, UUID requestKey, String title, String body, String subject, List<String> tags) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("requestKey", requestKey.toString()); data.put("title", title); data.put("body", body);
        data.put("subjectTag", subject); data.put("tags", tags);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/questions",
                json.toJson(data), "application/json"), Question.class);
    }

    public List<Question> questions(String communityId, String status, String subject, String search) throws Exception {
        StringBuilder path = new StringBuilder("/api/communities/" + UUID.fromString(communityId) + "/questions?limit=30");
        if (status != null && !status.isBlank()) path.append("&status=").append(encode(status));
        if (subject != null && !subject.isBlank()) path.append("&subject=").append(encode(subject));
        if (search != null && !search.isBlank()) path.append("&search=").append(encode(search));
        return List.of(json.fromJson(request(path.toString(), null), Question[].class));
    }

    public void acceptSolution(String communityId, String postId, String commentId) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/posts/" + UUID.fromString(postId) + "/solution/" + UUID.fromString(commentId),
                "", "application/json");
    }

    // =========================================================================
    // FASE 4: Recursos Académicos
    // =========================================================================

    public ResourceItem publishResource(String communityId, String title, String description, String resourceType,
                                        String externalUrl, String originalFilename, Long sizeBytes,
                                        String mimeType, String subjectName) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", title); data.put("description", description); data.put("resourceType", resourceType);
        data.put("externalUrl", externalUrl); data.put("originalFilename", originalFilename);
        data.put("fileSizeBytes", sizeBytes); data.put("mimeType", mimeType); data.put("subjectName", subjectName);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/resources",
                json.toJson(data), "application/json"), ResourceItem.class);
    }

    public List<ResourceItem> resources(String communityId, String type, String subject, String search) throws Exception {
        StringBuilder path = new StringBuilder("/api/communities/" + UUID.fromString(communityId) + "/resources?");
        if (type != null && !type.isBlank()) path.append("type=").append(encode(type)).append("&");
        if (subject != null && !subject.isBlank()) path.append("subject=").append(encode(subject)).append("&");
        if (search != null && !search.isBlank()) path.append("search=").append(encode(search)).append("&");
        return List.of(json.fromJson(request(path.toString(), null), ResourceItem[].class));
    }

    // =========================================================================
    // FASE 5: Calendario y Actividades
    // =========================================================================

    public EventItem createEvent(String communityId, String title, String description, String eventType, String eventDateIso) throws Exception {
        var data = new LinkedHashMap<String, String>();
        data.put("title", title); data.put("description", description);
        data.put("eventType", eventType); data.put("eventDate", eventDateIso);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/events",
                json.toJson(data), "application/json"), EventItem.class);
    }

    public List<EventItem> events(String communityId, String fromIso, String toIso) throws Exception {
        StringBuilder path = new StringBuilder("/api/communities/" + UUID.fromString(communityId) + "/events?");
        if (fromIso != null) path.append("from=").append(encode(fromIso)).append("&");
        if (toIso != null) path.append("to=").append(encode(toIso)).append("&");
        return List.of(json.fromJson(request(path.toString(), null), EventItem[].class));
    }

    public List<UpcomingEvent> upcomingEvents() throws Exception {
        return List.of(json.fromJson(request("/api/me/events/upcoming", null), UpcomingEvent[].class));
    }

    // =========================================================================
    // FASE 6 & 7: Notificaciones y Estadísticas
    // =========================================================================

    public List<NotificationItem> notifications(int limit) throws Exception {
        return List.of(json.fromJson(request("/api/me/notifications?limit=" + limit, null), NotificationItem[].class));
    }

    public void markNotificationRead(String id) throws Exception {
        request("/api/me/notifications/" + UUID.fromString(id) + "/read", "", "application/json");
    }

    public long unreadNotificationsCount() throws Exception {
        String res = request("/api/me/notifications/unread-count", null);
        try {
            return Long.parseLong(res.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public CommunityStats communityStats(String communityId) throws Exception {
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/stats", null), CommunityStats.class);
    }

    // =========================================================================
    // Biblioteca Académica Existente (V1)
    // =========================================================================

    public List<Subject> library() throws Exception {
        return List.of(json.fromJson(request("/api/me/library", null), Subject[].class));
    }

    public Detail detail(String offeringId) throws Exception {
        return json.fromJson(request("/api/me/library/" + UUID.fromString(offeringId), null), Detail.class);
    }

    public void logout() throws Exception {
        try { request("/api/auth/logout", ""); } finally { clear(); }
    }

    public String accountId() { return accountId; }

    public void clear() {
        cookies.getCookieStore().removeAll();
        csrf = null;
        accountId = null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
