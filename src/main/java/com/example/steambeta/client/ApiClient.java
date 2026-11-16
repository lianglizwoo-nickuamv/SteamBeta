package com.example.steambeta.client;

import com.google.gson.Gson;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** One instance, cookie store and CSRF token per login attempt/session. No disk persistence. */
public final class ApiClient {
    public record Identity(String studentId,String alias,String universityName) {}
    public record Profile(String accountId,String alias,String declaredUniversity,String declaredCareer,boolean institutionalIdentityVerified) {}
    public record Subject(String offeringId,String code,String name,String termCode,String sectionCode,
                          long requiredCount,long completedCount,BigDecimal progressPercent) {}
    public record Activity(String activityId,String code,String name,boolean required,boolean completed) {}
    public record Detail(Subject summary,List<Activity> activities) {}
    public record Community(String id,String slug,String name,String description,String rules,String role) {}
    public record Post(String id,String communityId,String authorAccountId,String authorAlias,
                       String title,String body,String createdAt,String status,long commentCount) {}
    public record PostPage(List<Post> posts,String nextBeforeTime,String nextBeforeId) {}
    public record Comment(String id,String postId,String authorAccountId,String authorAlias,String body,String status,String createdAt,String updatedAt) {}
    public record Report(String id,String postId,String commentId,String reason,String reporterAlias,String createdAt,String contentAuthorAlias,String content,String contentStatus) {}
    private record Csrf(String headerName,String token) {}
    public static final class Failure extends RuntimeException {
        private final int status;
        public Failure(int status) { super("HTTP request failed"); this.status=status; }
        public int status() { return status; }
    }
    private final URI base;
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(4)).build();
    private final Gson json=new Gson();
    private Csrf csrf;
    private String accountId;
    public ApiClient() {
        this(System.getProperty("replica.api.url",System.getenv().getOrDefault("REPLICA_API_URL","http://localhost:8080")));
    }
    public ApiClient(String url) {
        base=URI.create(url.replaceAll("/+$","")+"/");
        if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid backend URL");
    }
    private String request(String path,String body) throws Exception {
        return request(path, body, "application/x-www-form-urlencoded", body == null ? "GET" : "POST");
    }
    private String request(String path,String body,String contentType) throws Exception {
        return request(path, body, contentType, "POST");
    }
    private String request(String path,String body,String contentType,String method) throws Exception {
        long timeout=Long.parseLong(System.getenv().getOrDefault("REPLICA_HTTP_TIMEOUT_SECONDS","12"));
        var builder=HttpRequest.newBuilder(base.resolve(path.substring(1))).timeout(Duration.ofSeconds(timeout))
                .header("Accept","application/json");
        if(method.equals("GET")) builder.GET();
        else {
            if(csrf==null) throw new IllegalStateException("CSRF token required");
            builder.header(csrf.headerName(),csrf.token());
            if(body!=null) builder.header("Content-Type",contentType);
            builder.method(method, body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        }
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200 || response.statusCode()>=300) throw new Failure(response.statusCode());
        return response.body();
    }
    public Identity login(String login,String password) throws Exception {
        csrf=json.fromJson(request("/api/auth/csrf",null),Csrf.class);
        var identity=json.fromJson(request("/api/auth/login","login="+encode(login)+"&password="+encode(password)),Identity.class);
        // Spring Security clears the old token on successful authentication.
        csrf=json.fromJson(request("/api/auth/csrf",null),Csrf.class);
        accountId=profile().accountId();
        return identity;
    }
    public void register(String login,String password,String alias,String university,String career) throws Exception {
        csrf=json.fromJson(request("/api/auth/csrf",null),Csrf.class);
        var data=new LinkedHashMap<String,String>();
        data.put("login",login);data.put("password",password);data.put("alias",alias);
        data.put("declaredUniversity",university);data.put("declaredCareer",career);
        request("/api/auth/register",json.toJson(data),"application/json");
    }
    public List<Community> communities() throws Exception {
        return List.of(json.fromJson(request("/api/me/communities",null),Community[].class));
    }
    public Profile profile() throws Exception { return json.fromJson(request("/api/account",null),Profile.class); }
    public PostPage posts(String communityId,String beforeTime,String beforeId) throws Exception {
        String path="/api/communities/"+UUID.fromString(communityId)+"/posts?limit=10";
        if(beforeTime!=null && beforeId!=null) {
            path+="&beforeTime="+encode(beforeTime)+"&beforeId="+UUID.fromString(beforeId);
        }
        return json.fromJson(request(path,null),PostPage.class);
    }
    public Post createPost(String communityId,UUID requestKey,String title,String body) throws Exception {
        var data=new LinkedHashMap<String,String>();
        data.put("requestKey",requestKey.toString());data.put("title",title);data.put("body",body);
        return json.fromJson(request("/api/communities/"+UUID.fromString(communityId)+"/posts",
                json.toJson(data),"application/json"),Post.class);
    }
    public List<Comment> comments(String communityId,String postId) throws Exception {
        return List.of(json.fromJson(request("/api/communities/"+UUID.fromString(communityId)+"/posts/"+UUID.fromString(postId)+"/comments",null),Comment[].class));
    }
    public Comment comment(String communityId,String postId,String body) throws Exception {
        return json.fromJson(request("/api/communities/"+UUID.fromString(communityId)+"/posts/"+UUID.fromString(postId)+"/comments",json.toJson(Map.of("body",body)),"application/json"),Comment.class);
    }
    public void editComment(String communityId,String commentId,String body) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/comments/"+UUID.fromString(commentId),json.toJson(Map.of("body",body)),"application/json","PATCH");
    }
    public void deleteComment(String communityId,String commentId) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/comments/"+UUID.fromString(commentId),null,"application/json","DELETE");
    }
    public void editPost(String communityId,String postId,String title,String body) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/posts/"+UUID.fromString(postId),json.toJson(Map.of("title",title,"body",body)),"application/json","PATCH");
    }
    public void deletePost(String communityId,String postId) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/posts/"+UUID.fromString(postId),null,"application/json","DELETE");
    }
    public void report(String communityId,String targetType,String targetId,String reason) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/reports",json.toJson(Map.of("targetType",targetType,"targetId",UUID.fromString(targetId),"reason",reason)),"application/json");
    }
    public List<Report> reports(String communityId) throws Exception {
        return List.of(json.fromJson(request("/api/communities/"+UUID.fromString(communityId)+"/moderation/reports",null),Report[].class));
    }
    public void hide(String communityId,String targetType,String targetId,String reason) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/moderation/hide",json.toJson(Map.of("targetType",targetType,"targetId",UUID.fromString(targetId),"reason",reason)),"application/json");
    }
    public void resolveReport(String communityId,String reportId,String decision,String note) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/moderation/reports/"+UUID.fromString(reportId)+"/resolution",json.toJson(Map.of("decision",decision,"note",note)),"application/json");
    }
    public void hideReported(String communityId,String reportId,String note) throws Exception {
        request("/api/communities/"+UUID.fromString(communityId)+"/moderation/reports/"+UUID.fromString(reportId)+"/hide",json.toJson(Map.of("decision","RESOLVED","note",note)),"application/json");
    }
    public List<Subject> library() throws Exception {
        return List.of(json.fromJson(request("/api/me/library",null),Subject[].class));
    }
    public Detail detail(String offeringId) throws Exception {
        return json.fromJson(request("/api/me/library/"+UUID.fromString(offeringId),null),Detail.class);
    }
    public void logout() throws Exception { try { request("/api/auth/logout",""); } finally { clear(); } }
    public String accountId() { return accountId; }
    public void clear() { cookies.getCookieStore().removeAll(); csrf=null; accountId=null; }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
