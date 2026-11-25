package com.example.steambeta.client;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Cliente HTTP con soporte CSRF, cookies, entornos centrales y contratos de SteamBeta Campus. */
public final class ApiClient {
    // Identidad y perfil
    public record Identity(String studentId, String alias, String universityName) {}
    public record Profile(String accountId, String login, String email, boolean emailVerified,
                          boolean emailPublic, String alias, String declaredUniversity, String declaredCareer,
                          Integer declaredSemester, String bio, String avatarUrl, String academicInterests,
                          String globalRole, boolean institutionalIdentityVerified,
                          int communitiesCount, int postsCount) {
        public boolean isGlobalModerator() {
            return "GLOBAL_MODERATOR".equalsIgnoreCase(globalRole) || "ADMIN".equalsIgnoreCase(globalRole);
        }
    }

    // Registros académicos de biblioteca (V1)
    public record Subject(String offeringId, String code, String name, String termCode, String sectionCode,
                          long requiredCount, long completedCount, BigDecimal progressPercent) {}
    public record Activity(String activityId, String code, String name, boolean required, boolean completed) {}
    public record Detail(Subject summary, List<Activity> activities) {}

    // Comunidad y Publicaciones (V4, V5 & V14 capacidad/acceso)
    public record Community(String id, String slug, String name, String description, String rules, String role,
                            String initialInvitationCode, long memberCount, int maxMembers, int availableSlots,
                            String category, String visibility) {
        public Community(String id, String slug, String name, String description, String rules, String role) {
            this(id, slug, name, description, rules, role, null, 0, 50, 50, null, null);
        }
        public Community(String id, String slug, String name, String description, String rules, String role, String initialInvitationCode) {
            this(id, slug, name, description, rules, role, initialInvitationCode, 0, 50, 50, null, null);
        }
        public Community(String id, String slug, String name, String description, String rules, String role,
                         String initialInvitationCode, long memberCount, int maxMembers, int availableSlots) {
            this(id, slug, name, description, rules, role, initialInvitationCode, memberCount, maxMembers, availableSlots, null, null);
        }
    }
    public record Post(String id, String communityId, String authorAccountId, String authorAlias,
                       String title, String body, String createdAt, String status, long commentCount) {}
    public record PostPage(List<Post> posts, String nextBeforeTime, String nextBeforeId) {}
    public record Comment(String id, String postId, String authorAccountId, String authorAlias,
                          String body, String status, String createdAt, String updatedAt, boolean isSolution) {}
    public record Report(String id, String postId, String commentId, String reason, String reporterAlias,
                         String createdAt, String contentAuthorAlias, String content, String contentStatus) {}

    // Exploración, Invitaciones y Miembros (FASE 2 & V14)
    public record CommunityOverview(String id, String slug, String name, String description, String category,
                                     String visibility, long memberCount, int maxMembers, int availableSlots,
                                     boolean isMember, String userRole) {}
    public record Invitation(String id, String communityId, String code, Integer maxUses, int usedCount,
                             String expiresAt, String createdAt) {}
    public record Member(String accountId, String alias, String role, String declaredUniversity,
                         String declaredCareer, String avatarUrl, String joinedAt) {}

    // Q&A Académico (FASE 3)
    public record Question(String id, String communityId, String authorAccountId, String authorAlias,
                           String title, String body, String subjectTag, List<String> tags,
                           String createdAt, String status, long commentCount, boolean isResolved,
                           String resolvedCommentId) {}

    // Recursos Académicos (FASE 4)
    public record ResourceItem(String id, String communityId, String uploaderAccountId, String uploaderAlias,
                               String title, String description, String resourceType, String externalUrl,
                               String originalFilename, Long fileSizeBytes, String mimeType,
                               String subjectName, String createdAt) {}

    // Calendario y Actividades (FASE 5)
    public record EventItem(String id, String communityId, String creatorAccountId, String creatorAlias,
                            String title, String description, String eventType, String eventDate,
                            String status, String createdAt) {}
    public record UpcomingEvent(String id, String communityId, String communityName, String title,
                                String description, String eventType, String eventDate, String status) {}

    // Notificaciones y Estadísticas (FASE 6 & 7)
    public record NotificationItem(String id, String communityId, String sourceActorAlias, String type,
                                   String title, String message, boolean read, String createdAt) {}
    public record CommunityStats(String communityId, long totalMembers, long totalPosts, long totalQuestions,
                                 long resolvedQuestions, long totalResources, long upcomingEvents) {}

    // Control Center de Moderación Global (V11)
    public record StudentOverview(String accountId, String loginIdentifier, String alias,
                                  String declaredUniversity, String declaredCareer, boolean enabled,
                                  String globalRole, String createdAt, long activeSanctionsCount,
                                  long communitiesCount, long postsCount) {}
    public record AuditLogEntry(String id, String moderatorAlias, String targetLogin,
                                String action, String reason, String createdAt) {}
    public record SanctionItem(String id, String action, String reason, String evidenceContext,
                               String expiresAt, String createdAt, boolean active,
                               String appealStatus, String appealNote) {}

    // Organizador Académico (V11)
    public record TaskItem(String id, String accountId, String communityId, String title,
                           String description, String status, String priority,
                           String dueDate, String projectName, String createdAt) {}

    // Equipos de Estudio (V11)
    public record StudyProfile(String accountId, String alias, String declaredUniversity,
                               String declaredCareer, List<String> subjects, List<String> interests,
                               String canHelpWith, String needsHelpWith, String availability, boolean visible) {}
    public record StudyMatchDetail(String accountId, String alias, String declaredUniversity,
                                   String declaredCareer, List<String> subjects, List<String> interests,
                                   String canHelpWith, String needsHelpWith, String availability,
                                   String bio, boolean contactEmailEnabled, String publicEmail,
                                   String avatarUrl) {}

    // Oportunidades Campus (V11 & V15)
    public record OpportunityItem(String id, String ownerAccountId, String ownerAlias,
                                   String title, String description, String category,
                                   String sourceInstitution, String sourceUrl, String deadline, String createdAt) {}

    // Intercambio de Materiales y Adjuntos (V11 & V15)
    public record ExchangeItem(String id, String ownerAccountId, String ownerAlias, String communityId,
                               String title, String description, String category, String exchangeType,
                               String status, String contactInfo, String createdAt, long attachmentCount) {}
    public record ExchangeAttachment(String id, String exchangeItemId, String originalFilename,
                                     String extension, String mimeType, long fileSizeBytes, String createdAt) {}


    private record Csrf(String headerName, String token) {}

    public static final class Failure extends RuntimeException {
        private final int status;
        private final String responseBody;

        public Failure(int status) {
            super("HTTP request failed with status " + status);
            this.status = status;
            this.responseBody = "";
        }

        public Failure(int status, String body) {
            super(extractMessage(status, body));
            this.status = status;
            this.responseBody = body != null ? body : "";
        }

        private static String extractMessage(int status, String body) {
            if (body != null && !body.isBlank()) {
                try {
                    var map = new Gson().fromJson(body, java.util.Map.class);
                    if (map != null) {
                        if (map.containsKey("message") && map.get("message") != null) {
                            return String.valueOf(map.get("message"));
                        }
                        if (map.containsKey("error") && map.get("error") != null) {
                            return String.valueOf(map.get("error"));
                        }
                    }
                } catch (Exception ignored) {}
            }
            return "Error HTTP " + status;
        }

        public int status() { return status; }
        public String responseBody() { return responseBody; }
    }

    private final URI base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private HttpClient http;
    private final Gson json = new Gson();
    private Csrf csrf;
    private String accountId;
    private Profile cachedProfile;

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int AUTH_TOTAL_TIMEOUT_SECONDS = 15;

    private synchronized HttpClient getHttp() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .build();
        }
        return http;
    }

    public ApiClient() {
        this(EnvironmentConfig.getApiUrl());
    }

    public ApiClient(String url) {
        base = URI.create(url.replaceAll("/+$", "") + "/");
        if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null)
            throw new IllegalArgumentException("Invalid backend URL: " + url);
    }

    public URI getBaseUri() { return base; }

    private String request(String path, String body) throws Exception {
        return request(path, body, "application/x-www-form-urlencoded", body == null ? "GET" : "POST");
    }

    private String request(String path, String body, String contentType) throws Exception {
        return request(path, body, contentType, "POST");
    }

    private String request(String path, String body, String contentType, String method) throws Exception {
        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "15"));
        return requestWithTimeout(path, body, contentType, method, Duration.ofSeconds(Math.max(1, Math.min(timeout, 30))));
    }

    private String requestWithTimeout(String path, String body, String contentType, String method, Duration timeout) throws Exception {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new HttpTimeoutException("Tiempo máximo de espera agotado");
        }
        var builder = HttpRequest.newBuilder(base.resolve(path.substring(1))).timeout(timeout)
                .header("Accept", "application/json");
        if (method.equals("GET")) {
            builder.GET();
        } else {
            if (csrf == null) throw new IllegalStateException("CSRF token required");
            builder.header(csrf.headerName(), csrf.token());
            if (body != null) builder.header("Content-Type", contentType);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        }
        var response = getHttp().send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new Failure(response.statusCode(), response.body());
        return response.body();
    }

    // =========================================================================
    // Autenticación y Perfil
    // =========================================================================

    public Identity login(String login, String password) throws Exception {
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(AUTH_TOTAL_TIMEOUT_SECONDS).toNanos();
        csrf = json.fromJson(requestWithTimeout("/api/auth/csrf", null, "application/x-www-form-urlencoded", "GET", remainingTimeout(deadlineNanos)), Csrf.class);
        var identity = json.fromJson(
                requestWithTimeout("/api/auth/login", "login=" + encode(login) + "&password=" + encode(password),
                        "application/x-www-form-urlencoded", "POST", remainingTimeout(deadlineNanos)),
                Identity.class);
        csrf = json.fromJson(requestWithTimeout("/api/auth/csrf", null, "application/x-www-form-urlencoded", "GET", remainingTimeout(deadlineNanos)), Csrf.class);
        cachedProfile = profileWithDeadline(deadlineNanos);
        accountId = cachedProfile.accountId();
        return identity;
    }

    private static Duration remainingTimeout(long deadlineNanos) throws HttpTimeoutException {
        long remainingMs = (deadlineNanos - System.nanoTime()) / 1_000_000L;
        if (remainingMs <= 0) {
            throw new HttpTimeoutException("La solicitud de autenticación superó el límite de 15 segundos.");
        }
        return Duration.ofMillis(remainingMs);
    }

    private Profile profileWithDeadline(long deadlineNanos) throws Exception {
        try {
            cachedProfile = json.fromJson(
                    requestWithTimeout("/api/account/full", null, "application/json", "GET", remainingTimeout(deadlineNanos)),
                    Profile.class);
            return cachedProfile;
        } catch (Failure f) {
            if (f.status() == 404) {
                cachedProfile = json.fromJson(
                        requestWithTimeout("/api/account", null, "application/json", "GET", remainingTimeout(deadlineNanos)),
                        Profile.class);
                return cachedProfile;
            }
            throw f;
        }
    }

    public void register(String alias, String login, String email, String password, String confirmPassword) throws Exception {
        csrf = json.fromJson(request("/api/auth/csrf", null), Csrf.class);
        var data = new LinkedHashMap<String, String>();
        data.put("alias", alias);
        data.put("login", login);
        data.put("email", email);
        data.put("password", password);
        data.put("confirmPassword", confirmPassword);
        request("/api/auth/register", json.toJson(data), "application/json");
    }

    public Profile profile() throws Exception {
        try {
            cachedProfile = json.fromJson(request("/api/account/full", null), Profile.class);
            return cachedProfile;
        } catch (Exception fallback) {
            cachedProfile = json.fromJson(request("/api/account", null), Profile.class);
            return cachedProfile;
        }
    }

    public Profile getCachedProfile() { return cachedProfile; }

    public Profile updateProfile(String alias, String university, String career, Integer semester,
                                 String bio, String avatarUrl, String interests, Boolean emailPublic) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("alias", alias);
        data.put("declaredUniversity", university);
        data.put("declaredCareer", career);
        data.put("declaredSemester", semester);
        data.put("bio", bio);
        data.put("avatarUrl", avatarUrl);
        data.put("academicInterests", interests);
        data.put("emailPublic", emailPublic);
        cachedProfile = json.fromJson(request("/api/account", json.toJson(data), "application/json", "PATCH"), Profile.class);
        return cachedProfile;
    }

    public Profile updateProfile(String alias, String university, String career, String bio, String avatarUrl, String interests) throws Exception {
        return updateProfile(alias, university, career, null, bio, avatarUrl, interests, false);
    }

    public List<SanctionItem> mySanctions() throws Exception {
        return List.of(json.fromJson(request("/api/account/sanctions", null), SanctionItem[].class));
    }

    public void appealSanction(String sanctionId, String note) throws Exception {
        var data = Map.of("note", note);
        request("/api/account/sanctions/" + UUID.fromString(sanctionId) + "/appeal", json.toJson(data), "application/json");
    }

    // =========================================================================
    // Control Center (Moderación Global)
    // =========================================================================

    public List<StudentOverview> controlCenterSearchStudents(String query) throws Exception {
        String path = "/api/control-center/students?limit=25";
        if (query != null && !query.isBlank()) path += "&query=" + encode(query);
        return List.of(json.fromJson(request(path, null), StudentOverview[].class));
    }

    public void controlCenterApplySanction(String targetAccountId, String action, String reason, String evidence, Integer durationHours) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("action", action);
        data.put("reason", reason);
        data.put("evidenceContext", evidence);
        data.put("durationHours", durationHours);
        request("/api/control-center/students/" + UUID.fromString(targetAccountId) + "/sanctions",
                json.toJson(data), "application/json");
    }

    public List<AuditLogEntry> controlCenterAuditLogs() throws Exception {
        return List.of(json.fromJson(request("/api/control-center/audit-logs?limit=40", null), AuditLogEntry[].class));
    }

    public void controlCenterResolveAppeal(String sanctionId, String decision, String note) throws Exception {
        var data = Map.of("decision", decision, "note", note);
        request("/api/control-center/appeals/" + UUID.fromString(sanctionId) + "/resolution", json.toJson(data), "application/json");
    }

    // =========================================================================
    // Organizador Académico (Tareas y Proyectos)
    // =========================================================================

    public List<TaskItem> myTasks(String statusFilter) throws Exception {
        return myTasks(statusFilter, null);
    }

    public List<TaskItem> myTasks(String statusFilter, String priorityFilter) throws Exception {
        StringBuilder path = new StringBuilder("/api/me/tasks");
        boolean has = false;
        if (statusFilter != null && !statusFilter.isBlank()) {
            path.append("?status=").append(encode(statusFilter));
            has = true;
        }
        if (priorityFilter != null && !priorityFilter.isBlank()) {
            path.append(has ? "&" : "?").append("priority=").append(encode(priorityFilter));
        }
        return List.of(json.fromJson(request(path.toString(), null), TaskItem[].class));
    }

    public TaskItem createTask(String title, String description, String priority, String dueDateIso, String projectName) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", title);
        data.put("description", description);
        data.put("priority", priority);
        data.put("dueDateIso", dueDateIso);
        data.put("projectName", projectName);
        return json.fromJson(request("/api/me/tasks", json.toJson(data), "application/json"), TaskItem.class);
    }

    public void updateTask(String taskId, String status, String priority, String dueDateIso) throws Exception {
        updateTaskFull(taskId, null, null, status, priority, dueDateIso, null, false);
    }

    public TaskItem updateTaskFull(String taskId, String title, String description, String status,
                                   String priority, String dueDateIso, String projectName, boolean clearDueDate) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        if (title != null) data.put("title", title);
        if (description != null) data.put("description", description);
        if (status != null) data.put("status", status);
        if (priority != null) data.put("priority", priority);
        if (dueDateIso != null) data.put("dueDateIso", dueDateIso);
        if (projectName != null) data.put("projectName", projectName);
        if (clearDueDate) data.put("clearDueDate", true);
        String body = request("/api/me/tasks/" + UUID.fromString(taskId), json.toJson(data), "application/json", "PATCH");
        return body == null || body.isBlank() ? null : json.fromJson(body, TaskItem.class);
    }

    public void deleteTask(String taskId) throws Exception {
        request("/api/me/tasks/" + UUID.fromString(taskId), null, "application/json", "DELETE");
    }

    // =========================================================================
    // Equipos de Estudio
    // =========================================================================

    public StudyProfile studyProfile() throws Exception {
        return json.fromJson(request("/api/study-teams/profile", null), StudyProfile.class);
    }

    public StudyProfile updateStudyProfile(List<String> subjects, List<String> interests, String canHelpWith, String needsHelpWith, String availability, boolean visible) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("subjects", subjects);
        data.put("interests", interests);
        data.put("canHelpWith", canHelpWith);
        data.put("needsHelpWith", needsHelpWith);
        data.put("availability", availability);
        data.put("visible", visible);
        return json.fromJson(request("/api/study-teams/profile", json.toJson(data), "application/json", "PUT"), StudyProfile.class);
    }

    public List<StudyProfile> findStudyMatches(String query) throws Exception {
        String path = "/api/study-teams/matches";
        if (query != null && !query.isBlank()) path += "?q=" + encode(query.strip());
        return List.of(json.fromJson(request(path, null), StudyProfile[].class));
    }

    public StudyMatchDetail studyMatchDetail(String accountId) throws Exception {
        return json.fromJson(request("/api/study-teams/matches/" + UUID.fromString(accountId), null), StudyMatchDetail.class);
    }

    // =========================================================================
    // Oportunidades Campus
    // =========================================================================

    public List<OpportunityItem> opportunities(String category) throws Exception {
        String path = "/api/opportunities";
        if (category != null && !category.isBlank()) path += "?category=" + encode(category);
        return List.of(json.fromJson(request(path, null), OpportunityItem[].class));
    }

    public OpportunityItem createOpportunity(String title, String description, String category, String institution, String url, String deadlineIso) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", title);
        data.put("description", description);
        data.put("category", category);
        data.put("sourceInstitution", institution);
        data.put("sourceUrl", url);
        data.put("deadline", deadlineIso);
        return json.fromJson(request("/api/opportunities", json.toJson(data), "application/json"), OpportunityItem.class);
    }

    public OpportunityItem updateOpportunity(String id, String title, String description, String category, String institution, String url, String deadlineIso) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", title);
        data.put("description", description);
        data.put("category", category);
        data.put("sourceInstitution", institution);
        data.put("sourceUrl", url);
        data.put("deadline", deadlineIso);
        return json.fromJson(request("/api/opportunities/" + UUID.fromString(id), json.toJson(data), "application/json", "PATCH"), OpportunityItem.class);
    }

    public void deleteOpportunity(String id) throws Exception {
        request("/api/opportunities/" + UUID.fromString(id), null, "application/json", "DELETE");
    }

    // =========================================================================
    // Intercambio Universitario de Materiales y Adjuntos
    // =========================================================================

    public List<ExchangeItem> exchangeItems(String category) throws Exception {
        return exchangeItems(category, false);
    }

    public List<ExchangeItem> exchangeItems(String category, boolean mineOnly) throws Exception {
        StringBuilder path = new StringBuilder("/api/exchange/items?mine=").append(mineOnly);
        if (category != null && !category.isBlank()) path.append("&category=").append(encode(category));
        return List.of(json.fromJson(request(path.toString(), null), ExchangeItem[].class));
    }

    public ExchangeItem createExchangeItem(String title, String description, String category, String type, String contactInfo) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("title", title);
        data.put("description", description);
        data.put("category", category);
        data.put("exchangeType", type);
        data.put("contactInfo", contactInfo);
        return json.fromJson(request("/api/exchange/items", json.toJson(data), "application/json"), ExchangeItem.class);
    }

    public ExchangeItem updateExchangeItem(String itemId, String title, String description, String category,
                                           String type, String status, String contactInfo) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        if (title != null) data.put("title", title);
        if (description != null) data.put("description", description);
        if (category != null) data.put("category", category);
        if (type != null) data.put("exchangeType", type);
        if (status != null) data.put("status", status);
        if (contactInfo != null) data.put("contactInfo", contactInfo);
        return json.fromJson(request("/api/exchange/items/" + UUID.fromString(itemId), json.toJson(data), "application/json", "PATCH"), ExchangeItem.class);
    }

    public void updateExchangeStatus(String itemId, String status) throws Exception {
        var data = Map.of("status", status);
        request("/api/exchange/items/" + UUID.fromString(itemId) + "/status", json.toJson(data), "application/json", "PATCH");
    }

    public void deleteExchangeItem(String itemId) throws Exception {
        request("/api/exchange/items/" + UUID.fromString(itemId), null, "application/json", "DELETE");
    }

    public List<ExchangeAttachment> exchangeAttachments(String itemId) throws Exception {
        return List.of(json.fromJson(
                request("/api/exchange/items/" + UUID.fromString(itemId) + "/attachments", null),
                ExchangeAttachment[].class));
    }

    public ExchangeAttachment uploadExchangeAttachment(String itemId, Path file) throws Exception {
        if (csrf == null) throw new IllegalStateException("CSRF token required");
        String boundary = "----SteamBetaBoundary" + UUID.randomUUID().toString().replace("-", "");
        String filename = file.getFileName().toString().replace("\"", "");
        String probed = Files.probeContentType(file);
        String mime = (probed != null && !probed.isBlank()) ? probed : "application/octet-stream";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        baos.write(Files.readAllBytes(file));
        baos.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "30"));
        var req = HttpRequest.newBuilder(base.resolve("api/exchange/items/" + UUID.fromString(itemId) + "/attachments"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Accept", "application/json")
                .header(csrf.headerName(), csrf.token())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                .build();
        var response = getHttp().send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Failure(response.statusCode(), response.body());
        }
        return json.fromJson(response.body(), ExchangeAttachment.class);
    }

    public byte[] downloadExchangeAttachmentBytes(String attachmentId) throws Exception {
        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "30"));
        var req = HttpRequest.newBuilder(base.resolve("api/exchange/attachments/" + UUID.fromString(attachmentId) + "/download"))
                .timeout(Duration.ofSeconds(timeout))
                .GET()
                .build();
        var response = getHttp().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Failure(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
    }

    public void downloadExchangeAttachmentTo(String attachmentId, Path destination) throws Exception {
        byte[] bytes = downloadExchangeAttachmentBytes(attachmentId);
        Files.write(destination, bytes);
    }

    public void deleteExchangeAttachment(String attachmentId) throws Exception {
        request("/api/exchange/attachments/" + UUID.fromString(attachmentId), null, "application/json", "DELETE");
    }

    // =========================================================================
    // Centro de Reportes Personales (JasperReports)
    // =========================================================================

    public byte[] downloadPersonalReport(String reportKey, String format, String fromIsoDate, String toIsoDate) throws Exception {
        StringBuilder path = new StringBuilder("api/me/reports/")
                .append(encode(reportKey))
                .append("?format=").append(encode(format != null ? format : "PDF"));
        if (fromIsoDate != null && !fromIsoDate.isBlank()) {
            path.append("&from=").append(encode(fromIsoDate.strip()));
        }
        if (toIsoDate != null && !toIsoDate.isBlank()) {
            path.append("&to=").append(encode(toIsoDate.strip()));
        }
        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "30"));
        var req = HttpRequest.newBuilder(base.resolve(path.toString()))
                .timeout(Duration.ofSeconds(Math.max(15, timeout)))
                .GET()
                .build();
        var response = getHttp().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Failure(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
    }

    // =========================================================================
    // Comunidades y Membresías (V4 & FASE 2)
    // =========================================================================

    public Profile uploadAvatar(Path file) throws Exception {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Archivo de fotografía no encontrado.");
        }
        String boundary = "----SteamBetaAvatar" + UUID.randomUUID().toString().replace("-", "");
        String filename = file.getFileName().toString().replace("\"", "");
        String contentType = Files.probeContentType(file);
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        byte[] fileBytes = Files.readAllBytes(file);
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, payload, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, payload, headerBytes.length + fileBytes.length, footerBytes.length);

        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "30"));
        var reqBuilder = HttpRequest.newBuilder(base.resolve("api/account/avatar"))
                .timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        if (csrf != null) {
            reqBuilder.header(csrf.headerName(), csrf.token());
        }
        var response = getHttp().send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Failure(response.statusCode(), response.body());
        }
        Profile updated = json.fromJson(response.body(), Profile.class);
        this.cachedProfile = updated;
        return updated;
    }

    public Profile deleteAvatar() throws Exception {
        Profile updated = json.fromJson(request("/api/account/avatar", null, "application/json", "DELETE"), Profile.class);
        this.cachedProfile = updated;
        return updated;
    }

    public byte[] downloadAvatarBytes(String targetAccountId) throws Exception {
        if (targetAccountId == null || targetAccountId.isBlank()) return null;
        long timeout = Long.parseLong(System.getenv().getOrDefault("STEAMBETA_HTTP_TIMEOUT_SECONDS", "15"));
        var req = HttpRequest.newBuilder(base.resolve("api/accounts/" + UUID.fromString(targetAccountId) + "/avatar"))
                .timeout(Duration.ofSeconds(timeout))
                .GET()
                .build();
        var response = getHttp().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Failure(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
    }

    public List<Community> communities() throws Exception {
        return List.of(json.fromJson(request("/api/me/communities", null), Community[].class));
    }

    public static String toSlug(String text) {
        if (text == null || text.isBlank()) return "comunidad";
        String normalized = java.text.Normalizer.normalize(text.strip(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() < 3) slug = slug + "-comunidad";
        if (slug.length() > 80) slug = slug.substring(0, 80);
        return slug;
    }

    public Community createCommunity(String slug, String name, String description, String rules, String category, String visibility) throws Exception {
        return createCommunity(slug, name, description, rules, category, visibility, 50);
    }

    public Community createCommunity(String slug, String name, String description, String rules,
                                     String category, String visibility, Integer maxMembers) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("slug", slug); data.put("name", name); data.put("description", description);
        data.put("rules", rules); data.put("category", category); data.put("visibility", visibility);
        if (maxMembers != null) data.put("maxMembers", maxMembers);
        return json.fromJson(request("/api/communities", json.toJson(data), "application/json"), Community.class);
    }

    public List<CommunityOverview> exploreCommunities() throws Exception {
        return exploreCommunities(null, null);
    }

    public List<CommunityOverview> exploreCommunities(String query, String category) throws Exception {
        StringBuilder path = new StringBuilder("/api/communities/explore");
        boolean hasParam = false;
        if (query != null && !query.isBlank()) {
            path.append("?query=").append(encode(query.strip()));
            hasParam = true;
        }
        if (category != null && !category.isBlank()) {
            path.append(hasParam ? "&" : "?").append("category=").append(encode(category.strip()));
        }
        return List.of(json.fromJson(request(path.toString(), null), CommunityOverview[].class));
    }

    public Invitation createInvitation(String communityId, String code, Integer maxUses, String expiresAt) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", code); data.put("maxUses", maxUses); data.put("expiresAt", expiresAt);
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/invitations",
                json.toJson(data), "application/json"), Invitation.class);
    }

    public Community joinCommunity(String code) throws Exception {
        return joinCommunity(code, null);
    }

    public Community joinCommunity(String code, String communityId) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("code", code);
        if (communityId != null && !communityId.isBlank()) {
            data.put("communityId", UUID.fromString(communityId).toString());
        }
        return json.fromJson(request("/api/communities/join", json.toJson(data), "application/json"), Community.class);
    }

    public Community joinPublicCommunity(String communityId) throws Exception {
        return json.fromJson(request("/api/communities/" + UUID.fromString(communityId) + "/join-public",
                "", "application/json"), Community.class);
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

    /** Abandona la comunidad usando exclusivamente la sesión autenticada en el servidor. */
    public void leaveCommunity(String communityId) throws Exception {
        request("/api/communities/" + UUID.fromString(communityId) + "/membership",
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
    // Moderación y Reportes Comunitarios (V5)
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
    // Q&A Académico
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
    // Recursos Académicos
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
    // Calendario y Actividades
    // =========================================================================

    public EventItem createEvent(String communityId, String title, String description, String eventType, String eventDateIso) throws Exception {
        var data = new LinkedHashMap<String, String>();
        data.put("title", title);
        data.put("description", description);
        data.put("eventType", eventType);
        data.put("eventDate", eventDateIso);
        data.put("eventDateIso", eventDateIso);
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
    // Notificaciones y Estadísticas
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

    public record ManualSubject(
            String id,
            String accountId,
            String name,
            String code,
            int academicYear,
            String termLabel,
            String groupSection,
            String teacherName,
            Integer credits,
            String schedule,
            String notes,
            String source,
            String createdAt,
            String updatedAt
    ) {}

    public List<Subject> library() throws Exception {
        return List.of(json.fromJson(request("/api/me/library", null), Subject[].class));
    }

    public Detail detail(String offeringId) throws Exception {
        return json.fromJson(request("/api/me/library/" + UUID.fromString(offeringId), null), Detail.class);
    }

    public List<ManualSubject> manualSubjects(Integer academicYear, String termLabel) throws Exception {
        StringBuilder path = new StringBuilder("/api/me/library/manual-subjects");
        boolean hasQuery = false;
        if (academicYear != null) {
            path.append("?academicYear=").append(academicYear);
            hasQuery = true;
        }
        if (termLabel != null && !termLabel.isBlank()) {
            path.append(hasQuery ? "&" : "?").append("termLabel=").append(encode(termLabel.strip()));
        }
        return List.of(json.fromJson(request(path.toString(), null), ManualSubject[].class));
    }

    public ManualSubject createManualSubject(
            String name, String code, int academicYear, String termLabel,
            String groupSection, String teacherName, Integer credits, String schedule, String notes) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("name", name);
        data.put("code", code);
        data.put("academicYear", academicYear);
        data.put("termLabel", termLabel);
        data.put("groupSection", groupSection);
        data.put("teacherName", teacherName);
        data.put("credits", credits);
        data.put("schedule", schedule);
        data.put("notes", notes);
        return json.fromJson(request("/api/me/library/manual-subjects", json.toJson(data), "application/json"), ManualSubject.class);
    }

    public ManualSubject updateManualSubject(
            String id, String name, String code, int academicYear, String termLabel,
            String groupSection, String teacherName, Integer credits, String schedule, String notes) throws Exception {
        var data = new LinkedHashMap<String, Object>();
        data.put("name", name);
        data.put("code", code);
        data.put("academicYear", academicYear);
        data.put("termLabel", termLabel);
        data.put("groupSection", groupSection);
        data.put("teacherName", teacherName);
        data.put("credits", credits);
        data.put("schedule", schedule);
        data.put("notes", notes);
        return json.fromJson(request("/api/me/library/manual-subjects/" + UUID.fromString(id), json.toJson(data), "application/json", "PUT"), ManualSubject.class);
    }

    public void deleteManualSubject(String id) throws Exception {
        request("/api/me/library/manual-subjects/" + UUID.fromString(id), null, "application/json", "DELETE");
    }

    public void logout() throws Exception {
        try { request("/api/auth/logout", ""); } finally { clear(); }
    }

    public String accountId() { return accountId; }

    public void clear() {
        cookies.getCookieStore().removeAll();
        csrf = null;
        accountId = null;
        cachedProfile = null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
