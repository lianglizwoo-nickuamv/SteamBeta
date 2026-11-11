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
    public record Subject(String offeringId,String code,String name,String termCode,String sectionCode,
                          long requiredCount,long completedCount,BigDecimal progressPercent) {}
    public record Activity(String activityId,String code,String name,boolean required,boolean completed) {}
    public record Detail(Subject summary,List<Activity> activities) {}
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
    public ApiClient() {
        this(System.getProperty("replica.api.url",System.getenv().getOrDefault("REPLICA_API_URL","http://localhost:8080")));
    }
    public ApiClient(String url) {
        base=URI.create(url.replaceAll("/+$","")+"/");
        if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null)
            throw new IllegalArgumentException("Invalid backend URL");
    }
    private String request(String path,String body) throws Exception {
        long timeout=Long.parseLong(System.getenv().getOrDefault("REPLICA_HTTP_TIMEOUT_SECONDS","12"));
        var builder=HttpRequest.newBuilder(base.resolve(path.substring(1))).timeout(Duration.ofSeconds(timeout))
                .header("Accept","application/json");
        if(body==null) builder.GET();
        else {
            if(csrf==null) throw new IllegalStateException("CSRF token required");
            builder.header(csrf.headerName(),csrf.token()).header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
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
        return identity;
    }
    public List<Subject> library() throws Exception {
        return List.of(json.fromJson(request("/api/me/library",null),Subject[].class));
    }
    public Detail detail(String offeringId) throws Exception {
        return json.fromJson(request("/api/me/library/"+UUID.fromString(offeringId),null),Detail.class);
    }
    public void logout() throws Exception { try { request("/api/auth/logout",""); } finally { clear(); } }
    public void clear() { cookies.getCookieStore().removeAll(); csrf=null; }
    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
}
