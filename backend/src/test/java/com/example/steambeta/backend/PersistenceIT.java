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
