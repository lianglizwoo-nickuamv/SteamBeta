package com.example.steambeta.backend;

import com.example.steambeta.backend.context.*;
import com.example.steambeta.backend.persistence.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL only. Never uses the application database or silently skips integration. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersistenceIT {
    private PostgreSQLContainer container;
    private String url, adminUser, adminPassword;
    private final String password = UUID.randomUUID().toString();
    private ConfigurableApplicationContext app;
    private volatile CyclicBarrier transactionBarrier;
    private static final ThreadLocal<ActorProvider.Actor> ACTOR = new ThreadLocal<>();
    private final UUID upgradedAccount = UUID.randomUUID();
    private final UUID upgradedUniversity = UUID.randomUUID();
    private final UUID upgradedStudent = UUID.randomUUID();

    @Configuration(proxyBeanMethods = false)
    static class FixtureAuthentication {
        @Bean @org.springframework.context.annotation.Primary ActorProvider fixtureActor() {
            return () -> {
                if(ACTOR.get()!=null) return ACTOR.get();
                return new com.example.steambeta.backend.auth.SecurityActorProvider().requireValidatedActor();
            };
        }
    }

    @BeforeAll void migrateAndBootWithLimitedLogin() throws Exception {
        url = System.getenv("REPLICA_TEST_ADMIN_URL");
        if (url == null) {
            container = new PostgreSQLContainer("postgres:18.6")
                    .withDatabaseName("replica_test_persistence")
                    .withUsername("fixture_admin").withPassword(password);
            container.start();
            url = container.getJdbcUrl(); adminUser = container.getUsername();
            adminPassword = container.getPassword();
        } else {
            adminUser = Objects.requireNonNull(System.getenv("REPLICA_TEST_ADMIN_USER"));
            adminPassword = Objects.requireNonNull(System.getenv("REPLICA_TEST_ADMIN_PASSWORD"));
        }
        try (var c = admin(); var s = c.createStatement()) {
            try (var r = s.executeQuery("select current_database(), to_regnamespace('replica'), to_regnamespace('replica_meta')")) {
                assertTrue(r.next());
                assertTrue(r.getString(1).startsWith("replica_test_"), "Disposable DB name must start replica_test_");
                assertNull(r.getString(2), "Use a fresh disposable database; no cleanup/baseline is performed");
                assertNull(r.getString(3));
            }
            s.execute("create role replica_app nologin nosuperuser nobypassrls");
            s.execute("create role replica_academic nologin nosuperuser nobypassrls");
            s.execute("create role fixture_runtime login nosuperuser nobypassrls password '"+password+"' in role replica_app");
            s.execute("create role fixture_academic login nosuperuser nobypassrls password '"+password+"' in role replica_academic");
            s.execute("create role fixture_migrator login nosuperuser nobypassrls password '"+password+"'");
            s.execute("grant create on database " + c.getCatalog() + " to fixture_migrator");
        }
        // Exercise V3 with a populated V2 database, not only a fresh schema.
        org.flywaydb.core.Flyway.configure()
                .dataSource(url, "fixture_migrator", password)
                .defaultSchema("replica_meta").schemas("replica_meta")
                .placeholders(Map.of("backendLogin", "fixture_runtime"))
                .target("2").load().migrate();
        try (var c = admin()) {
            c.setAutoCommit(false);
            execute(c, "insert into replica.university(id,code,name) values (?,?,'Existing university')",
                    upgradedUniversity, upgradedUniversity.toString());
            execute(c, "insert into replica.student(id,university_id,alias) values (?,?,'Existing alias')",
                    upgradedStudent, upgradedUniversity);
            execute(c, """
                    insert into replica.access_account(id,login_identifier,password_hash,university_id,student_id)
                    values (?,?,'fixture-unused-hash',?,?)
                    """, upgradedAccount, "upgrade." + upgradedAccount, upgradedUniversity, upgradedStudent);
            c.commit();
        }
        try (var migrationApp = new SpringApplicationBuilder(BackendApplication.class)
                .run("--spring.profiles.active=migration",
                     "--REPLICA_DB_URL="+url,
                     "--REPLICA_DB_USER=fixture_runtime",
                     "--REPLICA_DB_PASSWORD="+password,
                     "--REPLICA_MIGRATION_USER=fixture_migrator",
                     "--REPLICA_MIGRATION_PASSWORD="+password)) {
            assertTrue(migrationApp.isActive());
        }
        app = new SpringApplicationBuilder(BackendApplication.class, FixtureAuthentication.class)
                .run("--spring.datasource.url="+url,
                     "--server.port=0", "--server.servlet.session.cookie.secure=false",
                     "--spring.datasource.username=fixture_runtime",
                     "--spring.datasource.password="+password,
                     "--spring.datasource.hikari.maximum-pool-size=1",
                     "--spring.flyway.enabled=false");
    }

    @AfterAll void close() {
        if (app != null) app.close();
        if (container != null) container.stop();
        ACTOR.remove();
    }
    private Connection admin() throws SQLException { return DriverManager.getConnection(url,adminUser,adminPassword); }
    private Connection login(String user) throws SQLException { return DriverManager.getConnection(url,user,password); }
    private <T> T as(UUID university, UUID student, Supplier<T> action) {
        ACTOR.set(new ActorProvider.Actor(university, student));
        try { return app.getBean(ActorTransactions.class).execute(action); }
        finally { ACTOR.remove(); }
    }
    private <T> T bean(Class<T> type) { return app.getBean(type); }
    private static void execute(Connection c, String sql, Object... values) throws SQLException {
        try (var s = c.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]);
            s.execute();
        }
    }
    private static Object scalar(Connection c, String sql, Object... values) throws SQLException {
        try(var s=c.prepareStatement(sql)) {
            for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]);
            try(var r=s.executeQuery()) { assertTrue(r.next()); return r.getObject(1); }
        }
    }
    private long count(String sql, Object... values) throws SQLException {
        try(var c=admin()) { return ((Number)scalar(c,sql,values)).longValue(); }
    }
    private static void context(Connection c, UUID university, UUID student) throws SQLException {
        execute(c,"select set_config('replica.university_id', ?, true), set_config('replica.student_id', ?, true)",
                university.toString(),student.toString());
    }
    private record Fixture(UUID university, UUID student, UUID peer, UUID subject, UUID offering,
                           UUID secondOffering, UUID emptyOffering, UUID activity, UUID merit, UUID permanent,
                           UUID item) {}
    private Fixture fixture() throws SQLException {
        var f=new Fixture(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        try(var c=admin()) {
            c.setAutoCommit(false);
            execute(c,"insert into replica.university(id,code,name) values (?,?,'University')",f.university,f.university.toString());
            for(var student:List.of(f.student,f.peer)) {
                execute(c,"insert into replica.student(id,university_id,alias) values (?,?,'Student')",student,f.university);
                execute(c,"insert into replica.wallet(university_id,student_id,balance) values (?,?,100)",f.university,student);
            }
            execute(c,"insert into replica.subject(id,university_id,code,name) values (?,?,'SUB','Subject')",f.subject,f.university);
            for(var offering:List.of(f.offering,f.secondOffering,f.emptyOffering)) {
                execute(c,"insert into replica.offering(id,university_id,subject_id,term_code,section_code) values (?,?,?,'2026',?)",
                        offering,f.university,f.subject,offering.toString());
            }
            execute(c,"insert into replica.activity(id,university_id,offering_id,code,name) values (?,?,?,'A','Activity')",f.activity,f.university,f.offering);
            context(c,f.university,f.student);
            for(var offering:List.of(f.offering,f.secondOffering,f.emptyOffering)) {
                scalar(c,"select replica.open_enrollment(?)",offering);
                for(var student:List.of(f.student,f.peer))
                    execute(c,"insert into replica.enrollment(university_id,student_id,offering_id) values (?,?,?)",f.university,student,offering);
            }
            execute(c,"insert into replica.activity_completion(university_id,student_id,offering_id,activity_id) values (?,?,?,?)",f.university,f.student,f.offering,f.activity);
            execute(c,"insert into replica.merit_definition(id,university_id,code,kind,scope,title) values (?,?,'M','ACHIEVEMENT','OFFERING','Edition merit')",f.merit,f.university);
            execute(c,"insert into replica.merit_definition(id,university_id,code,kind,scope,title) values (?,?,'P','BADGE','PERMANENT','Permanent merit')",f.permanent,f.university);
            execute(c,"insert into replica.store_item(id,university_id,sku,name,points_price,stock) values (?,?,'SKU','Shirt',10,1)",f.item,f.university);
            c.commit();
        }
        return f;
    }

    @Test void migrationAndAllReadMappingsRespectRlsAndCommunityBoundary() throws Exception {
        var f=fixture(); var other=fixture();
        award(f,f.offering,"activity-completed:"+f.activity,5,f.merit);
        redeem(f,f.student,UUID.randomUUID());
        assertEquals(1,count("select count(*) from replica_meta.flyway_schema_history where success and version='1'"));
        as(f.university,f.student,()->{
            assertEquals(1,bean(UniversityRepository.class).read(f.university).size());
            assertEquals(1,bean(StudentRepository.class).read(f.university,f.student).size());
            assertTrue(bean(StudentRepository.class).read(f.university,f.peer).isEmpty());
            assertEquals(1,bean(SubjectRepository.class).read(f.university).size());
            assertEquals(3,bean(OfferingRepository.class).read(f.university).size());
            assertEquals(3,bean(EnrollmentRepository.class).read(f.university,f.student).size());
            assertTrue(bean(EnrollmentRepository.class).read(f.university,f.peer).isEmpty());
            assertTrue(bean(EnrollmentRepository.class).read(other.university,other.student).isEmpty());
            assertEquals(1,bean(ActivityRepository.class).read(f.university,f.offering).size());
            assertEquals(1,bean(ActivityCompletionRepository.class).read(f.university,f.student).size());
            assertTrue(bean(ActivityCompletionRepository.class).read(f.university,f.peer).isEmpty());
            assertEquals(2,bean(MeritDefinitionRepository.class).read(f.university).size());
            assertEquals(MeritScope.OFFERING,bean(StudentMeritRepository.class).read(f.university,f.student).getFirst().getScope());
            assertTrue(bean(StudentMeritRepository.class).read(f.university,f.peer).isEmpty());
            assertEquals(5,bean(PointCreditRepository.class).read(f.university,f.student).getFirst().getPoints());
            assertEquals(95,bean(WalletRepository.class).read(f.university,f.student).getFirst().getBalance());
            assertTrue(bean(WalletRepository.class).read(other.university,other.student).isEmpty());
            assertTrue(bean(WalletRepository.class).read(f.university,f.peer).isEmpty());
            assertEquals(1,bean(StoreItemRepository.class).read(f.university).size());
            assertTrue(bean(StoreItemRepository.class).read(other.university).isEmpty());
            assertEquals(RedemptionStatus.PENDING_PICKUP,bean(RedemptionRepository.class).read(f.university,f.student).getFirst().getStatus());
            assertTrue(bean(RedemptionRepository.class).read(f.university,f.peer).isEmpty());
            var progress=bean(ViewRepository.class).progress(f.university,f.student);
            assertEquals(new BigDecimal("100.00"),progress.stream().filter(p->p.getOfferingId().equals(f.offering)).findFirst().orElseThrow().getProgressPercent());
            assertNull(progress.stream().filter(p->p.getOfferingId().equals(f.emptyOffering)).findFirst().orElseThrow().getProgressPercent());
            assertTrue(bean(ViewRepository.class).progress(f.university,f.peer).isEmpty());
            assertTrue(bean(ViewRepository.class).profile(other.student).isPresent());
            assertEquals(1,bean(ViewRepository.class).merits(f.student).size());
            return null;
        });
        as(f.university,f.peer,()->{
            assertTrue(bean(RedemptionRepository.class).read(f.university,f.student).isEmpty());
            assertTrue(bean(PointCreditRepository.class).read(f.university,f.student).isEmpty());
            return null;
        });
    }

    @Test void pooledConnectionLosesContextAfterCommitAndRollback() throws Exception {
        var f=fixture();
        int pid=as(f.university,f.student,()-> ((Number)bean(jakarta.persistence.EntityManager.class)
                .createNativeQuery("select pg_backend_pid()").getSingleResult()).intValue());
        assertThrows(IllegalArgumentException.class,()->as(f.university,f.student,()->{throw new IllegalArgumentException("fixture rollback");}));
        try(var c=bean(DataSource.class).getConnection()) {
            assertEquals(pid,((Number)scalar(c,"select pg_backend_pid()")).intValue());
            assertNull(scalar(c,"select nullif(current_setting('replica.university_id',true),'')"));
            assertNull(scalar(c,"select nullif(current_setting('replica.student_id',true),'')"));
            assertEquals(0,((Number)scalar(c,"select count(*) from replica.wallet")).intValue());
        }
    }

    private UUID redeem(Fixture f, UUID student, UUID key) throws SQLException {
        try(var c=login("fixture_runtime")) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED); c.setAutoCommit(false);
            context(c,f.university,student);
            awaitTransactionPeer();
            var id=(UUID)scalar(c,"select id from replica.redeem_item(?,?,?)",f.item,key,10L);
            c.commit(); return id;
        }
    }
    private UUID award(Fixture f, UUID offering, String source, long points, UUID merit) throws SQLException {
        try(var c=login("fixture_academic")) {
            c.setAutoCommit(false); context(c,f.university,f.student);
            awaitTransactionPeer();
            var id=(UUID)scalar(c,"select id from replica.award_merit(?,?,?,?,?)",f.student,merit,offering,source,points);
            c.commit(); return id;
        }
    }
    private <T> List<T> simultaneous(Callable<T> first, Callable<T> second) throws Exception {
        transactionBarrier = new CyclicBarrier(2);
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS));return first.call();});
            var b=executor.submit(()->{ready.countDown(); assertTrue(start.await(10,TimeUnit.SECONDS));return second.call();});
            assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        } finally { transactionBarrier = null; }
    }
    private void awaitTransactionPeer() {
        var barrier = transactionBarrier;
        if (barrier != null) {
            try { barrier.await(10, TimeUnit.SECONDS); }
            catch (Exception e) { throw new IllegalStateException("Concurrent transaction did not arrive", e); }
        }
    }
    private String redeemOutcome(Fixture f,UUID student) throws SQLException {
        try { redeem(f,student,UUID.randomUUID()); return "OK"; }
        catch(SQLException e) { return e.getSQLState(); }
    }
    @Test void lastUnitCreatesOneCouponAndOneCharge() throws Exception {
        var f=fixture();
        var outcomes=simultaneous(()->redeemOutcome(f,f.student),()->redeemOutcome(f,f.peer));
        assertEquals(Set.of("OK","P1006"),new HashSet<>(outcomes));
        assertEquals(1,count("select count(*) from replica.redemption where item_id=?",f.item));
        assertEquals(0,count("select stock from replica.store_item where id=?",f.item));
        assertEquals(190,count("select sum(balance) from replica.wallet where university_id=?",f.university));
    }
    @Test void sameRedemptionKeyChargesOnlyOnceAndRejectsChangedParameters() throws Exception {
        var f=fixture(); var key=UUID.randomUUID();
        var ids=simultaneous(()->redeem(f,f.student,key),()->redeem(f,f.student,key));
        assertEquals(ids.getFirst(),ids.getLast());
        assertEquals(1,count("select count(*) from replica.redemption where item_id=?",f.item));
        assertEquals(90,count("select balance from replica.wallet where student_id=?",f.student));
        assertEquals(0,count("select stock from replica.store_item where id=?",f.item));
        try(var c=login("fixture_runtime")) {
            c.setAutoCommit(false);context(c,f.university,f.student);
            assertEquals("P1003",assertThrows(SQLException.class,()->scalar(c,"select id from replica.redeem_item(?,?,?)",f.item,key,11L)).getSQLState());
            c.rollback();
        }
    }
    @Test void sameAcademicOriginCreditsOnce() throws Exception {
        var f=fixture(); var source="activity-completed:"+f.activity;
        var ids=simultaneous(()->award(f,f.offering,source,25,f.merit),()->award(f,f.offering,source,25,f.merit));
        assertEquals(ids.getFirst(),ids.getLast());
        assertEquals(1,count("select count(*) from replica.student_merit where student_id=?",f.student));
        assertEquals(1,count("select count(*) from replica.point_credit where student_id=?",f.student));
        assertEquals(25,count("select sum(points) from replica.point_credit where student_id=?",f.student));
        assertEquals(125,count("select balance from replica.wallet where student_id=?",f.student));
        assertEquals("P1103",assertThrows(SQLException.class,()->award(f,f.offering,source,26,f.merit)).getSQLState());
    }
    @Test void offeringMeritRepeatsAcrossEditionsAndZeroPointsCreatesNoCredit() throws Exception {
        var f=fixture();
        award(f,f.offering,"event:"+UUID.randomUUID(),10,f.merit);
        award(f,f.secondOffering,"event:"+UUID.randomUUID(),0,f.merit);
        award(f,f.offering,"event:"+UUID.randomUUID(),0,f.permanent);
        assertEquals("P1104",assertThrows(SQLException.class,()->award(f,f.secondOffering,"event:"+UUID.randomUUID(),0,f.permanent)).getSQLState());
        assertEquals(2,count("select count(*) from replica.student_merit where student_id=? and merit_id=?",f.student,f.merit));
        assertEquals(3,count("select count(*) from replica.student_merit where student_id=?",f.student));
        assertEquals(1,count("select count(*) from replica.point_credit where student_id=?",f.student));
        assertEquals(110,count("select balance from replica.wallet where student_id=?",f.student));
    }
    @Test void openedEnrollmentFreezesRequiredPlanButAllowsOptionalMaintenance() throws Exception {
        var f=fixture();
        try(var c=login("fixture_academic")) {
            c.setAutoCommit(false); context(c,f.university,f.student);
            var first=scalar(c,"select replica.open_enrollment(?)",f.offering);
            assertEquals(first,scalar(c,"select replica.open_enrollment(?)",f.offering));c.commit();
        }
        try(var c=admin()) {
            assertEquals("P1203",assertThrows(SQLException.class,()->execute(c,"update replica.activity set name='Changed' where id=?",f.activity)).getSQLState());
            assertEquals("P1203",assertThrows(SQLException.class,()->execute(c,"delete from replica.activity where id=?",f.activity)).getSQLState());
            assertEquals("P1203",assertThrows(SQLException.class,()->execute(c,"insert into replica.activity(university_id,offering_id,code,name) values (?,?,'NEW','New')",f.university,f.offering)).getSQLState());
            var optional=UUID.randomUUID();
            execute(c,"insert into replica.activity(id,university_id,offering_id,code,name,required) values (?,?,?,'OPT','Optional',false)",optional,f.university,f.offering);
            execute(c,"update replica.activity set name='Updated' where id=?",optional);
            assertEquals("P1203",assertThrows(SQLException.class,()->execute(c,"update replica.activity set required=true where id=?",optional)).getSQLState());
            execute(c,"delete from replica.activity where id=?",optional);
            assertEquals("P1201",assertThrows(SQLException.class,()->execute(c,"update replica.offering set enrollment_opened_at=null where id=?",f.offering)).getSQLState());
        }
        assertEquals(1,count("select count(*) from replica.activity where offering_id=?",f.offering));
    }
    private java.net.URI endpoint(String path) {
        return java.net.URI.create("http://127.0.0.1:"+app.getEnvironment().getProperty("local.server.port")+path);
    }
    private final class Browser {
        final java.net.CookieManager cookies=new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL);
        final java.net.http.HttpClient http=java.net.http.HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrfHeader,csrfToken;
        java.net.http.HttpResponse<String> get(String path) throws Exception {
            return http.send(java.net.http.HttpRequest.newBuilder(endpoint(path)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        java.net.http.HttpResponse<String> post(String path,String body,boolean csrf) throws Exception {
            var builder=java.net.http.HttpRequest.newBuilder(endpoint(path)).header("Content-Type","application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
            if(csrf)builder.header(csrfHeader,csrfToken);
            return http.send(builder.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        java.net.http.HttpResponse<String> register(String body, boolean includeCsrf) throws Exception {
            return jsonPost("/api/auth/register", body, includeCsrf);
        }
        java.net.http.HttpResponse<String> jsonPost(String path, String body, boolean includeCsrf) throws Exception {
            var request = java.net.http.HttpRequest.newBuilder(endpoint(path))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
            if (includeCsrf) request.header(csrfHeader, csrfToken);
            return http.send(request.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        java.net.http.HttpResponse<String> jsonMethod(String method,String path,String body,boolean includeCsrf) throws Exception {
            var request=java.net.http.HttpRequest.newBuilder(endpoint(path)).header("Content-Type","application/json")
                    .method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body));
            if(includeCsrf)request.header(csrfHeader,csrfToken);
            return http.send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response=get("/api/auth/csrf");assertEquals(200,response.statusCode());
            var node=bean(tools.jackson.databind.ObjectMapper.class).readTree(response.body());
            csrfHeader=node.get("headerName").asText();csrfToken=node.get("token").asText();
        }
        java.net.http.HttpResponse<String> login(String name,String secret) throws Exception {
            return post("/api/auth/login","login="+java.net.URLEncoder.encode(name,java.nio.charset.StandardCharsets.UTF_8)
                    +"&password="+java.net.URLEncoder.encode(secret,java.nio.charset.StandardCharsets.UTF_8),true);
        }
        String sessionId() { return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
    }
    private void accessAccount(Fixture f,String login,String secret,boolean enabled) throws Exception {
        try(var c=admin()) {
            execute(c,"insert into replica.access_account(login_identifier,password_hash,enabled,university_id,student_id) values (?,?,?,?,?)",
                    login,bean(org.springframework.security.crypto.password.PasswordEncoder.class).encode(secret),enabled,f.university,f.student);
        }
    }
    @Test void httpLoginCsrfFixationLogoutAndGenericFailures() throws Exception {
        var f=fixture();String login="fixture."+UUID.randomUUID(),secret=UUID.randomUUID().toString();
        accessAccount(f,login,secret,true);var browser=new Browser();
        var denied=browser.get("/api/me");assertEquals(401,denied.statusCode());assertTrue(denied.body().contains("session_required"));
        assertEquals(401,browser.get("/api/me/library").statusCode());
        assertEquals(403,browser.post("/api/auth/login","login=x&password=x",false).statusCode());
        browser.csrf();String anonymousSession=browser.sessionId();
        var cookie=browser.cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("JSESSIONID")).findFirst().orElseThrow();
        assertTrue(cookie.isHttpOnly());
        assertEquals(401,browser.login(login,"wrong").statusCode());
        String invalid=browser.login("missing.fixture","wrong").body();
        assertEquals(invalid,browser.login(login,"wrong").body());
        var accepted=browser.login("  "+login.toUpperCase(Locale.ROOT)+"  ",secret);
        assertEquals(200,accepted.statusCode());assertNotEquals(anonymousSession,browser.sessionId());
        assertTrue(accepted.headers().allValues("Set-Cookie").stream().anyMatch(v->v.contains("SameSite=Strict") && v.contains("HttpOnly")));
        assertFalse(accepted.body().contains(secret));assertFalse(accepted.body().contains("password"));
        var identity=bean(tools.jackson.databind.ObjectMapper.class).readTree(accepted.body());
        assertEquals(3,identity.size());assertEquals(f.student.toString(),identity.get("studentId").asText());
        assertEquals(403,browser.post("/api/auth/logout","",false).statusCode());
        assertEquals(403,browser.post("/api/auth/logout","",true).statusCode(),"Pre-login CSRF token must be retired");
        assertEquals(200,browser.get("/api/me").statusCode());
        browser.csrf();String oldSession=browser.sessionId();
        assertEquals(204,browser.post("/api/auth/logout","",true).statusCode());
        assertEquals(401,browser.get("/api/me").statusCode());
        var replay=java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(endpoint("/api/me"))
                .header("Cookie","JSESSIONID="+oldSession).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(401,replay.statusCode());
        var disabled=fixture();accessAccount(disabled,"disabled."+disabled.student,secret,false);
        browser.csrf();assertEquals(invalid,browser.login("disabled."+disabled.student,secret).body());
    }
    @Test void httpDemoLibraryIsolatedAcrossStudentsUniversitiesAndReusablePool() throws Exception {
        var passwords=Map.of("alba",UUID.randomUUID().toString(),"bruno",UUID.randomUUID().toString(),"celia",UUID.randomUUID().toString());
        try(var c=admin()) {
            c.setAutoCommit(false);
            var dataset=new com.example.steambeta.backend.demo.DemoDataset(c);
            dataset.load(bean(org.springframework.security.crypto.password.PasswordEncoder.class),passwords);
            dataset.load(bean(org.springframework.security.crypto.password.PasswordEncoder.class),passwords);
            c.commit();
        }
        var json=bean(tools.jackson.databind.ObjectMapper.class);
        for(var name:List.of("alba","bruno","celia","alba")) {
            var browser=new Browser();browser.csrf();assertEquals(200,browser.login("demo."+name,passwords.get(name)).statusCode());
            var list=browser.get("/api/me/library?studentId=untrusted&universityId=untrusted");assertEquals(200,list.statusCode());
            var rows=json.readTree(list.body());assertEquals(name.equals("alba")?3:1,rows.size());
            assertFalse(list.body().contains("password"));assertFalse(list.body().contains("sourceKey"));assertFalse(list.body().contains("balance"));
            Set<String> progress=new HashSet<>();
            for(var row:rows) {
                progress.add(row.get("progressPercent").isNull()?"null":row.get("progressPercent").decimalValue().stripTrailingZeros().toPlainString());
                var detail=browser.get("/api/me/library/"+row.get("offeringId").asText());assertEquals(200,detail.statusCode());
                var activities=json.readTree(detail.body()).get("activities");assertFalse(activities.isEmpty());
                assertTrue(activities.get(0).has("required"));assertTrue(activities.get(0).has("completed"));
                assertFalse(detail.body().contains("password_hash"));assertFalse(detail.body().contains("studentId"));
            }
            if(name.equals("alba"))assertEquals(Set.of("50","100","null"),progress);
            else if(name.equals("bruno"))assertEquals(Set.of("0"),progress);
            else assertEquals(Set.of("100"),progress);
            String foreign=com.example.steambeta.backend.demo.DemoDataset.id("offering:"+(name.equals("alba")?"redes":"programacion")).toString();
            assertEquals(404,browser.get("/api/me/library/"+foreign).statusCode());
            if(name.equals("alba"))assertEquals(404,browser.get("/api/me/library/"+com.example.steambeta.backend.demo.DemoDataset.id("offering:diseno")).statusCode());
            assertEquals(404,browser.get("/api/me/library/"+UUID.randomUUID()).statusCode());
            browser.csrf();assertEquals(204,browser.post("/api/auth/logout","",true).statusCode());
            assertEquals(401,browser.get("/api/me/library").statusCode());
        }
        try(var c=login("fixture_runtime")) {
            assertEquals("42501",assertThrows(SQLException.class,()->scalar(c,"select count(*) from replica.access_account")).getSQLState());
            assertNull(scalar(c,"select nullif(current_setting('replica.student_id',true),'')"));
        }
        try(var c=login("fixture_academic")) {
            assertEquals("42501",assertThrows(SQLException.class,()->scalar(c,"select account_id from replica.lookup_access_account('demo.alba')")).getSQLState());
        }
    }
    @Test void independentRegistrationLoginAndProfileNeedNoAcademicData() throws Exception {
        String identifier = "member." + UUID.randomUUID();
        String secret = UUID.randomUUID().toString();
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        String payload = json.writeValueAsString(Map.of("login", identifier, "password", secret,
                "alias", "New member", "declaredUniversity", "User declared university",
                "declaredCareer", "Systems engineering", "studentId", upgradedStudent.toString(),
                "universityId", upgradedUniversity.toString(), "role", "ADMIN",
                "institutionalIdentityVerified", true));
        var browser = new Browser();
        assertEquals(401, browser.get("/api/account").statusCode());
        assertEquals(403, browser.register(payload, false).statusCode());
        browser.csrf();
        long studentsBefore = count("select count(*) from replica.student");
        long walletsBefore = count("select count(*) from replica.wallet");
        long enrollmentsBefore = count("select count(*) from replica.enrollment");
        var created = browser.register(payload, true);
        assertEquals(201, created.statusCode());
        UUID accountId = UUID.fromString(json.readTree(created.body()).get("accountId").asText());
        assertEquals(1, json.readTree(created.body()).size());
        assertEquals(409, browser.register(payload, true).statusCode());
        assertEquals(studentsBefore, count("select count(*) from replica.student"));
        assertEquals(walletsBefore, count("select count(*) from replica.wallet"));
        assertEquals(enrollmentsBefore, count("select count(*) from replica.enrollment"));
        assertEquals(1, count("""
                select count(*) from replica.access_account
                where id=? and student_id is null and university_id is null
                """, accountId));
        var accepted = browser.login(identifier, secret);
        assertEquals(200, accepted.statusCode());
        assertTrue(json.readTree(accepted.body()).get("studentId").isNull());
        var profile = browser.get("/api/account");
        assertEquals(200, profile.statusCode());
        var data = json.readTree(profile.body());
        assertEquals(5, data.size());
        assertEquals(accountId.toString(), data.get("accountId").asText());
        assertEquals("New member", data.get("alias").asText());
        assertFalse(data.get("institutionalIdentityVerified").asBoolean());
        assertFalse(profile.body().contains(identifier));
        assertFalse(profile.body().contains(secret));
        assertFalse(profile.body().contains("password"));
        assertEquals(403, browser.get("/api/me/library").statusCode());
        assertEquals(200, browser.get("/api/me").statusCode());
        browser.csrf();
        assertEquals(204, browser.post("/api/auth/logout", "", true).statusCode());
        assertEquals(401, browser.get("/api/account").statusCode());
    }

    @Test void registrationIsAtomicUniqueAndCannotSetAnAcademicIdentity() throws Exception {
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        String identifier = "unique." + UUID.randomUUID();
        var request = new HashMap<String, Object>();
        request.put("login", identifier);
        request.put("password", UUID.randomUUID().toString());
        request.put("alias", "Independent member");
        var first = new Browser();
        var second = new Browser();
        first.csrf();
        second.csrf();
        String payload = json.writeValueAsString(request);
        var outcomes = simultaneous(() -> first.register(payload, true).statusCode(),
                () -> second.register(payload, true).statusCode());
        assertEquals(Set.of(201, 409), new HashSet<>(outcomes));
        assertEquals(1, count("select count(*) from replica.access_account where login_identifier=?", identifier));
        String invalidLogin = "invalid." + UUID.randomUUID();
        request.put("login", invalidLogin);
        request.put("alias", "x");
        assertEquals(400, first.register(json.writeValueAsString(request), true).statusCode());
        assertEquals(0, count("select count(*) from replica.access_account where login_identifier=?", invalidLogin));
        request.put("alias", "Valid alias");
        request.put("password", "short");
        assertEquals(400, first.register(json.writeValueAsString(request), true).statusCode());
        request.put("password", "a".repeat(73));
        assertEquals(400, first.register(json.writeValueAsString(request), true).statusCode());
        assertEquals(0, count("select count(*) from replica.access_account where login_identifier=?", invalidLogin));
        // Even a privileged function failure after the account INSERT rolls it back.
        try (var c = login("fixture_runtime")) {
            assertEquals("23514", assertThrows(SQLException.class, () -> scalar(c,
                    "select replica.register_account(?, ?, ?, null, null)",
                    invalidLogin, "fixture-unused-hash", "x")).getSQLState());
            assertEquals("42501", assertThrows(SQLException.class, () -> scalar(c,
                    "select count(*) from replica.account_profile")).getSQLState());
        }
        assertEquals(0, count("select count(*) from replica.access_account where login_identifier=?", invalidLogin));
        try (var c = admin()) {
            assertEquals("23514", assertThrows(SQLException.class, () -> execute(c, """
                    insert into replica.access_account(login_identifier,password_hash,student_id)
                    values (?, 'unused', ?)
                    """, invalidLogin, upgradedStudent)).getSQLState());
        }
    }

    @Test void migrationPreservesExistingAccountsAndBackfillsTheirPublicAlias() throws Exception {
        assertEquals(1, count("select count(*) from replica_meta.flyway_schema_history where success and version='3'"));
        assertEquals(1, count("""
                select count(*) from replica.access_account a
                join replica.account_profile p on p.account_id=a.id
                where a.id=? and a.university_id=? and a.student_id=?
                  and a.password_hash='fixture-unused-hash' and p.alias='Existing alias'
                  and p.declared_university is null and p.declared_career is null
                """, upgradedAccount, upgradedUniversity, upgradedStudent));
    }

    private record CommunityAccount(String login, String password, UUID id, Browser browser) {}

    private CommunityAccount communityAccount(String alias) throws Exception {
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        String identifier = "pilot." + UUID.randomUUID();
        String secret = UUID.randomUUID().toString();
        var browser = new Browser();
        browser.csrf();
        var created = browser.register(json.writeValueAsString(Map.of(
                "login", identifier, "password", secret, "alias", alias)), true);
        assertEquals(201, created.statusCode());
        UUID accountId = UUID.fromString(json.readTree(created.body()).get("accountId").asText());
        assertEquals(200, browser.login(identifier, secret).statusCode());
        browser.csrf();
        return new CommunityAccount(identifier, secret, accountId, browser);
    }

    private UUID community() throws Exception {
        UUID communityId = UUID.randomUUID();
        try (var c = admin()) {
            execute(c, """
                    insert into replica.community(id,slug,name,description,rules)
                    values (?,?,'Pilot community','Test group','Respect other members')
                    """, communityId, "pilot-" + communityId);
        }
        return communityId;
    }

    private void join(UUID communityId, CommunityAccount account, String role) throws Exception {
        try (var c = admin()) {
            execute(c, "insert into replica.community_membership(community_id,account_id,role) values (?,?,?)",
                    communityId, account.id(), role);
        }
    }

    private String postPayload(UUID requestKey, String title, String body) throws Exception {
        return bean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(Map.of(
                "requestKey", requestKey, "title", title, "body", body,
                "authorAccountId", upgradedAccount, "role", "ADMIN"));
    }

    @Test void communityMembersSharePostsAndOtherAccountsCannotAccessDirectRoutes() throws Exception {
        long studentsBefore = count("select count(*) from replica.student");
        var first = communityAccount("First pilot member");
        var second = communityAccount("Second pilot member");
        var outside = communityAccount("Other community administrator");
        UUID pilot = community();
        UUID other = community();
        join(pilot, first, "MEMBER");
        join(pilot, second, "MEMBER");
        join(other, outside, "ADMIN");
        String path = "/api/communities/" + pilot + "/posts";
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        assertEquals(401, new Browser().get("/api/me/communities").statusCode());
        assertEquals(401, new Browser().get(path).statusCode());
        var mine = first.browser().get("/api/me/communities?accountId=" + outside.id());
        assertEquals(200, mine.statusCode());
        assertEquals(pilot.toString(), json.readTree(mine.body()).get(0).get("id").asText());
        assertEquals(1, json.readTree(mine.body()).size());
        var empty = communityAccount("Member without membership");
        assertTrue(json.readTree(empty.browser().get("/api/me/communities").body()).isEmpty());
        assertEquals(404, empty.browser().get(path).statusCode());
        assertEquals(404, outside.browser().get(path).statusCode());
        String payload = postPayload(UUID.randomUUID(), "How do sessions work?", "A question shared with the pilot.");
        assertEquals(404, outside.browser().jsonPost(path, payload, true).statusCode());
        assertEquals(404, empty.browser().jsonPost(path, payload, true).statusCode());
        assertEquals(403, first.browser().jsonPost(path, payload, false).statusCode());
        var created = first.browser().jsonPost(path, payload, true);
        assertEquals(201, created.statusCode());
        assertEquals(first.id().toString(), json.readTree(created.body()).get("authorAccountId").asText());
        var read = second.browser().get(path);
        assertEquals(200, read.statusCode());
        var posts = json.readTree(read.body()).get("posts");
        assertEquals(1, posts.size());
        assertEquals("A question shared with the pilot.", posts.get(0).get("body").asText());
        assertEquals("First pilot member", posts.get(0).get("authorAlias").asText());
        assertEquals(9, posts.get(0).size());
        assertFalse(read.body().contains("password"));
        assertFalse(read.body().contains(first.login()));
        assertFalse(read.body().contains("studentId"));
        assertFalse(read.body().contains("balance"));
        assertEquals(studentsBefore, count("select count(*) from replica.student"));
        assertEquals(204, first.browser().post("/api/auth/logout", "", true).statusCode());
        assertEquals(401, first.browser().get(path).statusCode());
        first.browser().csrf();
        assertEquals(200, first.browser().login(outside.login(), outside.password()).statusCode());
        assertEquals(404, first.browser().get(path).statusCode());
        var changed = json.readTree(first.browser().get("/api/me/communities").body());
        assertEquals(1, changed.size());
        assertEquals(other.toString(), changed.get(0).get("id").asText());
        try (var c = login("fixture_runtime")) {
            assertEquals("42501", assertThrows(SQLException.class, () -> scalar(c,
                    "select count(*) from replica.community_post")).getSQLState());
        }
    }

    @Test void communityPagesAreChronologicalStableAndMembershipRevocationTakesEffect() throws Exception {
        var member = communityAccount("Pagination member");
        UUID pilot = community();
        join(pilot, member, "MEMBER");
        String path = "/api/communities/" + pilot + "/posts";
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        for (int i = 0; i < 3; i++) {
            assertEquals(201, member.browser().jsonPost(path,
                    postPayload(UUID.randomUUID(), "Question " + i, "Content"), true).statusCode());
        }
        // Equal timestamps exercise the UUID tie-breaker as well as the cursor.
        try (var c = admin()) {
            execute(c, "update replica.community_post set created_at='2026-01-01T00:00:00Z' where community_id=?", pilot);
        }
        var first = json.readTree(member.browser().get(path + "?limit=2").body());
        assertEquals(2, first.get("posts").size());
        String cursor = "?limit=2&beforeTime=" + java.net.URLEncoder.encode(first.get("nextBeforeTime").asText(),
                java.nio.charset.StandardCharsets.UTF_8) + "&beforeId=" + first.get("nextBeforeId").asText();
        var fresh = member.browser().jsonPost(path,
                postPayload(UUID.randomUUID(), "Newest question", "New content"), true);
        assertEquals(201, fresh.statusCode());
        var older = json.readTree(member.browser().get(path + cursor).body());
        assertEquals(1, older.get("posts").size());
        assertTrue(older.get("nextBeforeTime").isNull());
        var seen = new HashSet<String>();
        first.get("posts").forEach(post -> seen.add(post.get("id").asText()));
        assertTrue(seen.add(older.get("posts").get(0).get("id").asText()));
        var newest = json.readTree(member.browser().get(path + "?limit=2").body());
        assertEquals("Newest question", newest.get("posts").get(0).get("title").asText());
        assertEquals(400, member.browser().get(path + "?limit=0").statusCode());
        assertEquals(400, member.browser().get(path + "?limit=51").statusCode());
        assertEquals(400, member.browser().get(path + "?beforeId=" + UUID.randomUUID()).statusCode());
        try (var c = admin()) {
            execute(c, "update replica.community_membership set active=false where community_id=? and account_id=?",
                    pilot, member.id());
        }
        assertEquals(404, member.browser().get(path).statusCode());
        assertEquals(404, member.browser().jsonPost(path,
                postPayload(UUID.randomUUID(), "Denied post", "Content"), true).statusCode());
    }

    @Test void communityPostRetriesAreIdempotentAndInvalidContentIsRejected() throws Exception {
        var member = communityAccount("Retry member");
        UUID pilot = community();
        join(pilot, member, "MEMBER");
        String path = "/api/communities/" + pilot + "/posts";
        var json = bean(tools.jackson.databind.ObjectMapper.class);
        UUID key = UUID.randomUUID();
        String payload = postPayload(key, "One question", "Once only");
        var replies = simultaneous(() -> member.browser().jsonPost(path, payload, true),
                () -> member.browser().jsonPost(path, payload, true));
        assertEquals(201, replies.getFirst().statusCode());
        assertEquals(201, replies.getLast().statusCode());
        assertEquals(json.readTree(replies.getFirst().body()).get("id"),
                json.readTree(replies.getLast().body()).get("id"));
        assertEquals(1, count("select count(*) from replica.community_post where community_id=?", pilot));
        assertEquals(409, member.browser().jsonPost(path, postPayload(key, "Changed question", "Once only"), true).statusCode());
        assertEquals(400, member.browser().jsonPost(path, postPayload(UUID.randomUUID(), "x", "Body"), true).statusCode());
        assertEquals(400, member.browser().jsonPost(path, postPayload(UUID.randomUUID(), "Valid title", " "), true).statusCode());
        assertEquals(400, member.browser().jsonPost(path, postPayload(UUID.randomUUID(), "Valid title", "a".repeat(10001)), true).statusCode());
        assertEquals(1, count("select count(*) from replica.community_post where community_id=?", pilot));
        try (var c = admin()) {
            execute(c, "update replica.access_account set enabled=false where id=?", member.id());
        }
        assertEquals(401, member.browser().get("/api/me/communities").statusCode());
    }

    @Test void communityConversationOwnershipReportsModerationAndTombstonesAreEnforced() throws Exception {
        var author=communityAccount("Conversation author");
        var member=communityAccount("Conversation member");
        var moderator=communityAccount("Community moderator");
        var outsider=communityAccount("Unrelated moderator");
        UUID pilot=community(), other=community();
        join(pilot,author,"MEMBER");join(pilot,member,"MEMBER");join(pilot,moderator,"MODERATOR");join(other,outsider,"MODERATOR");
        var json=bean(tools.jackson.databind.ObjectMapper.class);
        String postsPath="/api/communities/"+pilot+"/posts";
        var created=author.browser().jsonPost(postsPath,postPayload(UUID.randomUUID(),"Question title","Question body"),true);
        assertEquals(201,created.statusCode());
        UUID postId=UUID.fromString(json.readTree(created.body()).get("id").asText());
        String commentsPath=postsPath+"/"+postId+"/comments";
        var comment=member.browser().jsonPost(commentsPath,"{\"body\":\"Helpful reply\",\"authorAccountId\":\""+outsider.id()+"\"}",true);
        assertEquals(201,comment.statusCode());
        UUID commentId=UUID.fromString(json.readTree(comment.body()).get("id").asText());
        assertEquals(member.id().toString(),json.readTree(comment.body()).get("authorAccountId").asText());

        String commentRoute="/api/communities/"+pilot+"/comments/"+commentId;
        assertEquals(404,author.browser().jsonMethod("PATCH",commentRoute,"{\"body\":\"forged edit\"}",true).statusCode());
        assertEquals(404,author.browser().jsonMethod("DELETE",commentRoute,null,true).statusCode());
        assertEquals(200,member.browser().jsonMethod("PATCH",commentRoute,"{\"body\":\"Edited reply\"}",true).statusCode());
        assertEquals(204,member.browser().jsonMethod("DELETE",commentRoute,null,true).statusCode());
        var deletedComment=json.readTree(member.browser().get(commentsPath).body()).get(0);
        assertEquals("DELETED",deletedComment.get("status").asText());assertTrue(deletedComment.get("body").isNull());

        String reportPath="/api/communities/"+pilot+"/reports";
        assertEquals(404,author.browser().jsonPost(reportPath,"{\"targetType\":\"POST\",\"targetId\":\""+postId+"\",\"reason\":\"Self report\"}",true).statusCode());
        var reported=member.browser().jsonPost(reportPath,"{\"targetType\":\"POST\",\"targetId\":\""+postId+"\",\"reason\":\"Contains private information\"}",true);
        assertEquals(201,reported.statusCode());
        assertEquals(409,member.browser().jsonPost(reportPath,"{\"targetType\":\"POST\",\"targetId\":\""+postId+"\",\"reason\":\"Duplicate report\"}",true).statusCode());
        String queue="/api/communities/"+pilot+"/moderation/reports";
        assertEquals(404,member.browser().get(queue).statusCode());assertEquals(404,outsider.browser().get(queue).statusCode());
        var reports=json.readTree(moderator.browser().get(queue).body());assertEquals(1,reports.size());
        UUID reportId=UUID.fromString(reports.get(0).get("id").asText());
        String hidePath="/api/communities/"+pilot+"/moderation/hide";
        assertEquals(404,member.browser().jsonPost(hidePath,"{\"targetType\":\"POST\",\"targetId\":\""+postId+"\",\"reason\":\"Hide it\"}",true).statusCode());
        assertEquals(204,moderator.browser().jsonPost("/api/communities/"+pilot+"/moderation/reports/"+reportId+"/hide",
                "{\"decision\":\"RESOLVED\",\"note\":\"Content hidden after review\"}",true).statusCode());
        assertEquals(404,author.browser().jsonMethod("PATCH",postsPath+"/"+postId,"{\"title\":\"Edited after hide\",\"body\":\"Must not unhide\"}",true).statusCode());
        assertEquals(0,json.readTree(member.browser().get(postsPath).body()).get("posts").size());
        assertTrue(json.readTree(moderator.browser().get(queue).body()).isEmpty());

        var commentTarget=author.browser().jsonPost(postsPath,postPayload(UUID.randomUUID(),"Comment report","Parent for comment"),true);
        UUID commentPost=UUID.fromString(json.readTree(commentTarget.body()).get("id").asText());
        String commentTargetPath=postsPath+"/"+commentPost+"/comments";
        var activeComment=member.browser().jsonPost(commentTargetPath,"{\"body\":\"Comment to moderate\"}",true);
        UUID activeCommentId=UUID.fromString(json.readTree(activeComment.body()).get("id").asText());
        assertEquals(201,author.browser().jsonPost(reportPath,"{\"targetType\":\"COMMENT\",\"targetId\":\""+activeCommentId+"\",\"reason\":\"Please review this reply\"}",true).statusCode());
        var commentReports=json.readTree(moderator.browser().get(queue).body());
        UUID commentReportId=UUID.fromString(commentReports.get(0).get("id").asText());
        assertEquals(204,moderator.browser().jsonPost("/api/communities/"+pilot+"/moderation/reports/"+commentReportId+"/hide",
                "{\"decision\":\"RESOLVED\",\"note\":\"Reply hidden after review\"}",true).statusCode());
        var hiddenComment=json.readTree(member.browser().get(commentTargetPath).body()).get(0);
        assertEquals("HIDDEN",hiddenComment.get("status").asText());assertTrue(hiddenComment.get("body").isNull());
        assertEquals(404,member.browser().jsonMethod("PATCH","/api/communities/"+pilot+"/comments/"+activeCommentId,
                "{\"body\":\"Trying to unhide by editing\"}",true).statusCode());

        var conversation=author.browser().jsonPost(postsPath,postPayload(UUID.randomUUID(),"Conversation remains","Parent text"),true);
        UUID parent=UUID.fromString(json.readTree(conversation.body()).get("id").asText());
        String parentComments=postsPath+"/"+parent+"/comments";
        assertEquals(201,member.browser().jsonPost(parentComments,"{\"body\":\"Preserved reply\"}",true).statusCode());
        String parentRoute=postsPath+"/"+parent;
        assertEquals(404,member.browser().jsonMethod("PATCH",parentRoute,"{\"title\":\"Not owner\",\"body\":\"Nope\"}",true).statusCode());
        assertEquals(204,author.browser().jsonMethod("DELETE",parentRoute,null,true).statusCode());
        var page=json.readTree(member.browser().get(postsPath).body()).get("posts");
        var tombstone=java.util.stream.StreamSupport.stream(page.spliterator(),false)
                .filter(node->parent.toString().equals(node.get("id").asText())).findFirst().orElseThrow();
        assertEquals("DELETED",tombstone.get("status").asText());assertTrue(tombstone.get("body").isNull());
        assertEquals(1,tombstone.get("commentCount").asInt());
        assertEquals("Preserved reply",json.readTree(member.browser().get(parentComments).body()).get(0).get("body").asText());
        assertEquals(404,member.browser().jsonPost(parentComments,"{\"body\":\"New response after delete\"}",true).statusCode());
        assertTrue(count("select count(*) from replica.community_moderation_event where community_id=?",pilot)>=2);
    }

    @Test void runtimeCannotWriteOrInvokeAcademicFunctions() throws Exception {
        var f=fixture();
        try(var c=login("fixture_runtime")) {
            c.setAutoCommit(false); context(c,f.university,f.student);
            assertEquals("42501",assertThrows(SQLException.class,()->scalar(c,"select replica.open_enrollment(?)",f.offering)).getSQLState());c.rollback();
            context(c,f.university,f.student);
            assertEquals("42501",assertThrows(SQLException.class,()->execute(c,"update replica.wallet set balance=999 where student_id=?",f.student)).getSQLState());c.rollback();
        }
        assertEquals(100,count("select balance from replica.wallet where student_id=?",f.student));
    }
}
