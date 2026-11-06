package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.repositories.auth.AuthSessionRepository;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Full HTTP/JPA lifecycle on a disposable V2 MySQL schema, with a controlled clock. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import({AuthSessionIntegrationTest.TimeConfig.class, AuthSessionIntegrationTest.AdminProbeConfig.class})
@ExtendWith(OutputCaptureExtension.class)
class AuthSessionIntegrationTest {
    static final String USER = "session.customer",
            PASSWORD = "Auth1234",
            ORIGIN = AuthTestKeys.ISSUER;
    static final Instant NOW = Instant.parse("2026-11-04T07:08:09Z");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("auth_session_test")
                    .withUrlParam("connectionTimeZone", "UTC")
                    .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) throws Exception {
        var setup =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String v2 =
                new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int count = 0;
        while (definitions.find()) {
            setup.execute(definitions.group());
            count++;
        }
        assertEquals(47, count);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> ORIGIN);
        p.add("auth.cookie-secure", () -> true);
        AuthTestKeys.properties(p);
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(NOW);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return time.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority controlledComboAuthority() {
            return b -> "combo-http-run".equals(b.row().runId())
                    ? new vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority.Check(
                        new vn.edu.wspm.backend.services.combo.review.ComboReview.Policy("CONTROLLED_HTTP_REVIEW_V1"),
                        new vn.edu.wspm.backend.services.combo.review.ComboReview.Evidence(true,true),"CONTROLLED_HTTP_FIXTURE")
                    : vn.edu.wspm.backend.services.combo.review.ComboReviewAuthority.Check.unavailable();
        }
        @Bean
        @Primary
        MutableClock controlledClock() {
            return new MutableClock();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AdminProbeConfig {
        @Bean AdminProbe adminProbe(vn.edu.wspm.backend.web.auth.AdminPrincipalResolver resolver) { return new AdminProbe(resolver); }
    }

    /** Only in this test context, never registered as a production business endpoint. */
    @org.springframework.web.bind.annotation.RestController
    static class AdminProbe {
        private final vn.edu.wspm.backend.web.auth.AdminPrincipalResolver resolver;
        AdminProbe(vn.edu.wspm.backend.web.auth.AdminPrincipalResolver resolver) { this.resolver = resolver; }
        @org.springframework.web.bind.annotation.RequestMapping(path = "/api/v1/admin/test-identity",
                method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
        vn.edu.wspm.backend.common.api.ApiResponse<Map<String, Object>> identity(jakarta.servlet.http.HttpServletRequest request) {
            return vn.edu.wspm.backend.common.api.ApiResponse.success(Map.of("reviewerId", resolver.reviewerId(request),
                    "principalName", request.getUserPrincipal().getName(), "admin", request.isUserInRole("ADMIN")),
                    vn.edu.wspm.backend.web.common.RequestIdFilter.requestId(request));
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @MockitoSpyBean AuthSessionRepository repository;
    @MockitoSpyBean JwtTokenService jwt;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void customer() throws Exception {
        clock.time.set(NOW);
        for (String table :
                List.of("USER_ROLE", "CUSTOMER", "USER_SESSION", "USER_ACCOUNT", "ROLE"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO ROLE(role_id,code,name)"
                    + " VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        var response =
                post(
                        "register",
                        JSON.writeValueAsString(
                                Map.of(
                                        "username",
                                        USER,
                                        "password",
                                        PASSWORD,
                                        "email",
                                        "session@example.test",
                                        "displayName",
                                        "Session customer")),
                        null);
        assertEquals(201, response.statusCode(), response.body());
    }

    @Test
    void adminNamespaceRejectsMissingMalformedAndExpiredCredentials() throws Exception {
        for (String access : new String[] {null, "not-a-jwt", "abc.def.ghi"}) {
            var denied = get("/api/v1/admin/test-identity", access);
            error(denied, 401);
            assertEquals("AUTHENTICATION_REQUIRED", JSON.readTree(denied.body()).get("code").asString());
            assertEquals("Bearer", denied.headers().firstValue("WWW-Authenticate").orElseThrow());
        }
        var access = token(login(USER, PASSWORD));
        error(get("/api/v1/%61dmin/test-identity", access), 403);
        clock.time.set(NOW.plusSeconds(900));
        error(get("/api/v1/admin/test-identity", access), 401);
    }

    @Test void comboHttpAdminReadAndReviewUsesServerPrincipalAndCommonContract() throws Exception {
        String root="/api/v1/admin/combos";
        error(get(root,null),401);error(comboPost("missing","{}",null),401);
        var access=token(login(USER,PASSWORD));error(get(root,access),403);error(comboPost("missing","{}",access),403);
        String userId=jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?",String.class,USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')",userId);
        var empty=get(root,access);assertEquals(200,empty.statusCode(),empty.body());metadata(empty);
        assertEquals("0",JSON.readTree(empty.body()).get("data").get("scopeTotal").asString());
        for(String q:List.of("?page=0&page=1","?role=ADMIN","?size=0","?status=PENDING","?comboKind=wrong"))error(get(root+q,access),400);
        error(get(root+"/missing",access),404);error(get(root+"?batchId=missing",access),404);
        try {
            jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('combo-http-s','CSV','fixture','VALID','{}',10)");
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES('combo-http-run','combo-http-s','WSPM_PRETREE','SUCCEEDED','fixture','ABSOLUTE',.1,.1,.5,1)");
            jdbc.update("INSERT INTO CATEGORY(category_id,name,slug,status) VALUES('combo-http-cat','Fixture','combo-http-cat','ACTIVE')");
            for(String p:List.of("combo-http-p1","combo-http-p2"))jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES(?,?,?,?,'combo-http-cat',1,'ACTIVE')",p,p,p,p);
            for(int i=1;i<=2;i++)jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES('combo-http-run',?,?,2,1,.5,?,?)",i,"combo-http-p"+i,"Historic "+i,"SKU"+i);
            jdbc.update("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES('combo-http-run','<(1)(2)>',SHA2('<(1)(2)>',256),9007199254740993,.5,.5,2,2,.4,'CONTROLLED_HTTP_WEIGHT')");
            String pattern=jdbc.queryForObject("SELECT CAST(pattern_id AS CHAR) FROM PATTERN WHERE run_id='combo-http-run'",String.class);
            jdbc.update("INSERT INTO PATTERN_ITEM VALUES(?,1,1,1),(?,2,1,2)",pattern,pattern);
            jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k) VALUES('combo-http-b','combo-http-run','CONTROLLED_HTTP_RANK','CONTROLLED_HTTP_WEIGHT',1)");
            for(String id:List.of("combo-http-Z","combo-http-a")) {
                jdbc.update("INSERT INTO COMBO(combo_id,batch_id,name,combo_kind,status,currency_code,reference_original_price,reference_sale_price,support_abs,support_ratio,pattern_weight,explanation,created_at) VALUES(?,'combo-http-b',?,'SEQUENTIAL_CROSS_SELL','DRAFT','VND',9007199254740993.01,9007199254740993.01,9007199254740993,.5,.4,'fixture','2026-01-01 00:00:00.123456')",id,id);
                jdbc.update("INSERT INTO COMBO_ITEM(combo_id,itemset_order,item_order,product_id,quantity,unit_price_snapshot,is_anchor_eligible,is_discount_eligible) VALUES(?,1,1,'combo-http-p1',1,1,true,false),(?,2,1,'combo-http-p2',1,1,false,false)",id,id);
                jdbc.update("INSERT INTO COMBO_SOURCE VALUES(?,?,true)",id,pattern);
            }
            var list=JSON.readTree(get(root+"?batchId=combo-http-b&status=DRAFT&comboKind=SEQUENTIAL_CROSS_SELL&size=1",access).body()).get("data");
            assertEquals("2",list.get("scopeTotal").asString());assertEquals("2",list.get("filteredTotal").asString());assertEquals("combo-http-Z",list.get("items").get(0).get("data").get("comboId").asString());
            var detail=get(root+"/combo-http-Z",access);assertEquals(200,detail.statusCode(),detail.body());metadata(detail);
            var data=JSON.readTree(detail.body()).get("data");assertEquals("9007199254740993.01",data.get("data").get("referenceOriginalPrice").asString());assertEquals(2,data.get("itemsets").size());assertEquals("9007199254740993",data.get("data").get("metrics").get("supportAbs").asString());
            assertEquals(2,data.get("assessment").get("allowedActions").size());String reviewToken=data.get("reviewToken").asString();
            error(comboPost("combo-http-Z","{\"decision\":\"APPROVE\",\"reviewedBy\":\"forged\",\"expectedReviewToken\":\"x\"}",access),400);
            error(comboPost("combo-http-Z","{\"decision\":\"APPROVE\",\"decision\":\"REJECT\",\"expectedReviewToken\":\"x\"}",access),400);
            error(comboPost("combo-http-Z",JSON.writeValueAsString(Map.of("decision","REJECT","reviewNote"," ","expectedReviewToken",reviewToken)),access),400);
            error(comboPost("combo-http-Z",JSON.writeValueAsString(Map.of("decision","APPROVE","expectedReviewToken","stale")),access),409);
            var applied=comboPost("combo-http-Z",JSON.writeValueAsString(Map.of("decision","APPROVE","expectedReviewToken",reviewToken)),access);
            assertEquals(200,applied.statusCode(),applied.body());metadata(applied);var result=JSON.readTree(applied.body()).get("data");assertEquals("APPLIED",result.get("outcome").asString());assertEquals(userId,result.get("storedAudit").get("reviewedBy").asString());
            var retry=comboPost("combo-http-Z",JSON.writeValueAsString(Map.of("decision","APPROVE","expectedReviewToken","old")),access);assertEquals(200,retry.statusCode());assertEquals(result.get("storedAudit"),JSON.readTree(retry.body()).get("data").get("storedAudit"));
            error(comboPost("combo-http-Z",JSON.writeValueAsString(Map.of("decision","REJECT","reviewNote","reason","expectedReviewToken",reviewToken)),access),409);
            assertEquals("APPROVED",JSON.readTree(get(root+"/combo-http-Z",access).body()).get("data").get("data").get("comboStatus").asString());
            assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM COMBO_BATCH WHERE batch_id='combo-http-b'",String.class));
        }finally {
            for(String table:List.of("COMBO_SOURCE","COMBO_ITEM"))jdbc.update("DELETE FROM "+table+" WHERE combo_id IN ('combo-http-Z','combo-http-a')");
            jdbc.update("DELETE FROM COMBO WHERE batch_id='combo-http-b'");jdbc.update("DELETE FROM COMBO_BATCH WHERE batch_id='combo-http-b'");
            jdbc.update("DELETE pi FROM PATTERN_ITEM pi JOIN PATTERN p ON p.pattern_id=pi.pattern_id WHERE p.run_id='combo-http-run'");jdbc.update("DELETE FROM PATTERN WHERE run_id='combo-http-run'");jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id='combo-http-run'");jdbc.update("DELETE FROM MINING_RUN WHERE run_id='combo-http-run'");jdbc.update("DELETE FROM DATASET_SNAPSHOT WHERE snapshot_id='combo-http-s'");jdbc.update("DELETE FROM PRODUCT WHERE category_id='combo-http-cat'");jdbc.update("DELETE FROM CATEGORY WHERE category_id='combo-http-cat'");
        }
    }
    private HttpResponse<String> comboPost(String id,String body,String access)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/admin/combos/"+id+"/review"));
        if(access!=null)request.header("Authorization","Bearer "+access);
        return http.send(request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void adminUsesCurrentDatabaseRolesAndStableUserIdNotClaimedReviewer() throws Exception {
        var access = token(login(USER, PASSWORD));
        var customer = get("/api/v1/admin/test-identity", access);
        error(customer, 403);
        assertEquals("ACCESS_DENIED", JSON.readTree(customer.body()).get("code").asString());
        var forgedBody = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/admin/test-identity"))
                .header("Authorization", "Bearer " + access).header("X-Role", "ADMIN").header("X-User-Id", "forged")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"reviewedBy\":\"forged\",\"roles\":[\"ADMIN\"]}")).build();
        error(http.send(forgedBody, HttpResponse.BodyHandlers.ofString()), 403);
        String userId = jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?", String.class, USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", userId);
        // Token was issued before the role change: permissions must come from DB, not stale token claims.
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/admin/test-identity?reviewedBy=forged&role=CUSTOMER"))
                .header("Authorization", "Bearer " + access).header("X-User-Id", "forged").header("X-Role", "ADMIN").GET().build();
        var mixed = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, mixed.statusCode());metadata(mixed);
        var data = JSON.readTree(mixed.body()).get("data");
        assertEquals(userId, data.get("reviewerId").asString());
        assertEquals(userId, data.get("principalName").asString());
        assertNotEquals(USER, data.get("reviewerId").asString());
        assertTrue(data.get("admin").asBoolean());
        var bodyReply = http.send(forgedBody, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bodyReply.statusCode());
        assertEquals(userId, JSON.readTree(bodyReply.body()).get("data").get("reviewerId").asString());
        jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='customer-role'", userId);
        assertEquals(200, get("/api/v1/admin/test-identity", access).statusCode());
        jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='admin-role'", userId);
        error(get("/api/v1/admin/test-identity", access), 403);
    }

    @Test
    void adminCannotReuseRevokedSessionOrInactiveAccount() throws Exception {
        var access = token(login(USER, PASSWORD));
        String userId = jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?", String.class, USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", userId);
        assertEquals(200, get("/api/v1/admin/test-identity", access).statusCode());
        jdbc.update("UPDATE USER_ACCOUNT SET status='DISABLED' WHERE user_id=?", userId);
        error(get("/api/v1/admin/test-identity", access), 401);
        jdbc.update("UPDATE USER_ACCOUNT SET status='ACTIVE' WHERE user_id=?", userId);
        jdbc.update("UPDATE USER_SESSION SET revoked_at=? WHERE user_id=?", java.sql.Timestamp.from(NOW), userId);
        error(get("/api/v1/admin/test-identity", access), 401);
    }

    @Test
    void roleAdminPrefixOrClaimedHeaderDoesNotGrantAdmin() throws Exception {
        String userId = jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?", String.class, USER);
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('prefixed-admin','ROLE_ADMIN','Not the agreed role')");
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'prefixed-admin')", userId);
        var access = token(login(USER, PASSWORD));
        error(get("/api/v1/admin/test-identity?reviewedBy=forged&role=ADMIN", access), 403);
    }

    @Test
    void runListHttpRequiresAdminAndReturnsEmptyEnvelope() throws Exception {
        error(get("/api/v1/admin/mining-runs", null), 401);
        var access = token(login(USER, PASSWORD));
        error(get("/api/v1/admin/mining-runs", access), 403);
        String userId = jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?", String.class, USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", userId);
        var reply = get("/api/v1/admin/mining-runs", access);
        assertEquals(200, reply.statusCode());metadata(reply);
        var data = JSON.readTree(reply.body()).get("data");
        assertEquals("0", data.get("total").asString());assertTrue(data.get("total").isString());
        assertEquals(0, data.get("page").asInt());assertEquals(20, data.get("size").asInt());assertEquals(0,data.get("items").size());
        jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='customer-role'", userId);
        assertEquals(200,get("/api/v1/admin/mining-runs",access).statusCode());
    }

    @Test
    void runListHttpUsesPersistedSnapshotAndBinaryDbPagination() throws Exception {
        var access = token(login(USER, PASSWORD));
        String userId = jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?", String.class, USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", userId);
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,raw_file_path,validation_status,conversion_report_json,sequence_count)"
                + " VALUES('run-list-snapshot','DATABASE','private-source','/private/internal/input','VALID','{}',9007199254740993)");
        try {
            var states = List.of("QUEUED","RUNNING","SUCCEEDED","FAILED","LEGACY_UNVERIFIED");
            var ids = List.of("run-list-Z","run-list-a","run-list-b","run-list-c","run-list-d");
            for (int i=0;i<ids.size();i++) jdbc.update("""
                    INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,
                    minsup_configured,effective_threshold,mean_w,created_at)
                    VALUES(?,'run-list-snapshot','WSPM_PRETREE',?,'/private/internal/dataset','TEST',.5,1,.4,'2026-01-01 00:00:00.123456')
                    """,ids.get(i),states.get(i));
            for(int page=0;page<3;page++) {
                var reply=get("/api/v1/admin/mining-runs?page="+page+"&size=2",access);assertEquals(200,reply.statusCode());metadata(reply);
                assertFalse(reply.body().contains("/private/"));
                var data=JSON.readTree(reply.body()).get("data");assertEquals("5",data.get("total").asString());
                var items=data.get("items");assertEquals(page==2?1:2,items.size());
                for(int j=0;j<items.size();j++) {
                    assertEquals(ids.get(page*2+j),items.get(j).get("runId").asString());
                    assertEquals(states.get(page*2+j),items.get(j).get("status").asString());
                    assertEquals("9007199254740993",items.get(j).get("sequenceCount").asString());
                    assertTrue(items.get(j).get("sequenceCount").isString());
                    assertEquals("2026-01-01T00:00:00.123456Z",items.get(j).get("createdAt").asString());
                }
            }
            var beyond=JSON.readTree(get("/api/v1/admin/mining-runs?page=99&size=2",access).body()).get("data");
            assertEquals("5",beyond.get("total").asString());assertEquals(0,beyond.get("items").size());
            var actual=jdbc.queryForList("SELECT status FROM MINING_RUN ORDER BY CAST(run_id AS BINARY)",String.class);
            assertEquals(states,actual); // Read did not alter lifecycle.
        } finally {
            jdbc.update("DELETE FROM MINING_RUN WHERE snapshot_id='run-list-snapshot'");
            jdbc.update("DELETE FROM DATASET_SNAPSHOT WHERE snapshot_id='run-list-snapshot'");
        }
    }

    @Test
    void runListHttpRejectsInvalidScalarQueriesWithoutLeakingInput() throws Exception {
        var access=token(login(USER,PASSWORD));
        String userId=jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?",String.class,USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')",userId);
        for(String query:List.of("page=-1","size=0","size=101","page=2147483648","page=1.5","page=","size=20&size=30","unknown=private-value")) {
            var reply=get("/api/v1/admin/mining-runs?"+query,access);error(reply,400);
            assertEquals("VALIDATION_FAILED",JSON.readTree(reply.body()).get("code").asString());
            assertFalse(reply.body().contains("private-value"));
        }
    }

    @Test
    void patternHttpSecurityAndRawValidation() throws Exception {
        var access=token(login(USER,PASSWORD));
        var paths=List.of("/api/v1/admin/mining-runs/absent/patterns",
                "/api/v1/admin/mining-runs/absent/patterns/18446744073709551615",
                "/api/v1/admin/mining-runs/absent/dictionary");
        for(String path:paths) { error(get(path,null),401); error(get(path,"invalid"),401); error(get(path,access),403); }
        String userId=jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?",String.class,USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')",userId);
        for(String path:paths) { var reply=get(path,access);error(reply,404);assertEquals("RUN_NOT_FOUND",JSON.readTree(reply.body()).get("code").asString()); }
        for(String query:List.of("page=","page=-1","page=2147483648","size=101","size=2&size=3",
                "unknown=private-input","itemId=1,2","itemId%5B%5D=1","itemId=","supportMin=",
                "supportMin=1&supportMin=2","weightMin=NaN","weightMin=1&weightMax=0",
                "itemId=1&".repeat(100)+"itemId=1")) error(get(paths.getFirst()+"?"+query,access),400);
        error(get(paths.get(2)+"?q=a&q=b",access),400);
        error(get(paths.get(1)+"?page=0",access),400);
        error(get("/api/v1/admin/mining-runs/absent/patterns/18446744073709551616",access),400);
        error(get(paths.getFirst()+"?itemId=1&itemId=1&supportMin=9007199254740993",access),404);
        jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='customer-role'",userId);
        error(get(paths.getFirst(),access),404);
        clock.time.set(NOW.plusSeconds(901));
        for(String path:paths) error(get(path,access),401);
    }

    @Test
    void patternHttpReadsPersistedResultsAndPreservesOwnership() throws Exception {
        var access=token(login(USER,PASSWORD));
        String userId=jdbc.queryForObject("SELECT user_id FROM USER_ACCOUNT WHERE username=?",String.class,USER);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')",userId);
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,raw_file_path,validation_status,conversion_report_json,sequence_count) VALUES('pattern-http-s','DATABASE','fixture','test','VALID','{}',9007199254740993)");
        try {
            for(String state:List.of("QUEUED","RUNNING","FAILED","LEGACY_UNVERIFIED","SUCCEEDED")) {
                jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES(?,'pattern-http-s','WSPM_PRETREE',?,'test','TEST',.5,1,.4,0)","pattern-http-"+state,state);
            }
            String base="/api/v1/admin/mining-runs/pattern-http-";
            for(String state:List.of("QUEUED","RUNNING","FAILED","LEGACY_UNVERIFIED")) {
                var reply=get(base+state+"/patterns",access);assertEquals(200,reply.statusCode());metadata(reply);
                var data=JSON.readTree(reply.body()).get("data");assertTrue(data.get("runPatternTotal").isNull());assertTrue(data.get("filteredTotal").isNull());
                assertEquals(state.equals("FAILED")?"FAILED":state.equals("LEGACY_UNVERIFIED")?"UNVERIFIED":"NOT_READY",data.get("resultState").asString());
                var unavailable=get(base+state+"/patterns/1",access);error(unavailable,409);assertEquals("RESULTS_NOT_AVAILABLE",JSON.readTree(unavailable.body()).get("code").asString());
            }
            String ready=base+"SUCCEEDED";
            assertEquals("0",JSON.readTree(get(ready+"/patterns",access).body()).get("data").get("runPatternTotal").asString());
            jdbc.update("INSERT INTO PATTERN(pattern_id,run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(18446744073709551615,'pattern-http-SUCCEEDED','<(1)(2)>',REPEAT('a',64),9007199254740993,.5,1,2,2,.5,'TEST_ONLY')");
            jdbc.update("INSERT INTO PATTERN_ITEM VALUES(18446744073709551615,1,1,1),(18446744073709551615,2,1,2)");
            jdbc.update("UPDATE MINING_RUN SET pattern_count=1 WHERE run_id='pattern-http-SUCCEEDED'");
            var list=get(ready+"/patterns?supportMin=9007199254740993&supportMax=9007199254740993",access);assertEquals(200,list.statusCode());metadata(list);
            var data=JSON.readTree(list.body()).get("data");assertEquals("1",data.get("filteredTotal").asString());
            assertEquals("9007199254740993",data.get("items").get(0).get("supportAbs").asString());assertFalse(data.get("weightFilter").get("enabled").asBoolean());
            var detail=get(ready+"/patterns/18446744073709551615",access);assertEquals(200,detail.statusCode());metadata(detail);
            var d=JSON.readTree(detail.body()).get("data");assertEquals("18446744073709551615",d.get("patternId").asString());assertEquals(2,d.get("itemsets").size());assertEquals("INCOMPLETE",d.get("integrityStatus").asString());
            assertEquals("MISSING",d.get("itemsets").get(0).get("items").get(0).get("mappingStatus").asString());
            error(get(ready+"/patterns?weightMin=0",access),409);
            var membership=get(ready+"/patterns?itemId=1&itemId=1",access);error(membership,400);
            assertEquals("offendingItemIds",JSON.readTree(membership.body()).get("details").get(0).get("field").asString());
            assertEquals(200,get(ready+"/dictionary?q=",access).statusCode());
            jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('pattern-http-cat','Fixture','pattern-http-cat')");
            for(int item=1;item<=2;item++) {
                jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?, 'Current catalog label',?,'pattern-http-cat',1)","pattern-http-p"+item,"pattern-http-sku"+item,"pattern-http-p"+item);
                jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES('pattern-http-SUCCEEDED',?,?,1,1,.5,?,?)",item,"pattern-http-p"+item,item==1?"Đèn snapshot":"Mouse snapshot","SNAP-"+item);
            }
            var all=get(ready+"/patterns?itemId=1&itemId=2&itemId=1&supportMin=9007199254740993",access);
            assertEquals(200,all.statusCode());assertEquals("1",JSON.readTree(all.body()).get("data").get("filteredTotal").asString());
            var dictionary=get(ready+"/dictionary?q=den",access);assertEquals(200,dictionary.statusCode());metadata(dictionary);
            var dict=JSON.readTree(dictionary.body()).get("data");assertEquals("2",dict.get("dictionaryTotal").asString());assertEquals("1",dict.get("matchedTotal").asString());
            assertEquals("Đèn snapshot",dict.get("items").get(0).get("nameSnapshot").asString());
            assertEquals("0",JSON.readTree(get(ready+"/dictionary?q=%25",access).body()).get("data").get("matchedTotal").asString());
            jdbc.update("UPDATE MINING_RUN SET status='SUCCEEDED' WHERE run_id='pattern-http-QUEUED'");
            var foreign=get(base+"QUEUED/patterns/18446744073709551615",access);
            var missing=get(base+"QUEUED/patterns/1",access);error(foreign,404);error(missing,404);
            assertEquals(JSON.readTree(missing.body()).get("code"),JSON.readTree(foreign.body()).get("code"));
            jdbc.update("UPDATE MINING_RUN SET pattern_count=2 WHERE run_id='pattern-http-SUCCEEDED'");
            error(get(ready+"/patterns",access),409);
        } finally {
            jdbc.update("DELETE FROM PATTERN_ITEM WHERE pattern_id=18446744073709551615");
            jdbc.update("DELETE FROM PATTERN WHERE run_id LIKE 'pattern-http-%'");
            jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id='pattern-http-SUCCEEDED'");
            jdbc.update("DELETE FROM PRODUCT WHERE category_id='pattern-http-cat'");
            jdbc.update("DELETE FROM CATEGORY WHERE category_id='pattern-http-cat'");
            jdbc.update("DELETE FROM MINING_RUN WHERE snapshot_id='pattern-http-s'");
            jdbc.update("DELETE FROM DATASET_SNAPSHOT WHERE snapshot_id='pattern-http-s'");
        }
    }

    @Test
    void loginRefreshLogoutAndIdentityUseRealSessionHashAndFixedExpiry() throws Exception {
        var login = login(USER, PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        metadata(login);
        JsonNode first = JSON.readTree(login.body()).get("data");
        String access = first.get("accessToken").asString(), cookie = refreshCookie(login);
        assertEquals(900, first.get("expiresIn").asInt());
        assertEquals("Bearer", first.get("tokenType").asString());
        assertEquals(NOW.plusSeconds(900).toString(), first.get("expiresAt").asString());
        var claims = jwt.verify(access);
        String sid = claims.getClaimAsString("sid");
        var session = jdbc.queryForMap("SELECT * FROM USER_SESSION");
        String hash = (String) session.get("refresh_token_hash");
        assertEquals(sid, session.get("session_id"));
        assertTrue(hash.matches("[a-f0-9]{64}"));
        assertEquals(
                hash,
                vn.edu.wspm.backend.services.auth.AuthSessionService.refreshHash(raw(cookie)));
        assertFalse(login.body().contains(raw(cookie)));
        assertNull(session.get("revoked_at"));
        assertNull(session.get("device_info"));
        assertNull(session.get("ip_hash"));
        assertEquals(
                NOW,
                jdbc.queryForObject("SELECT last_login_at FROM USER_ACCOUNT", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC));
        Instant expiry =
                jdbc.queryForObject("SELECT expires_at FROM USER_SESSION", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC);
        assertEquals(NOW.plusSeconds(604800), expiry);
        assertCookie(login, "Max-Age=604800");
        assertTrue(
                login.headers().allValues("Set-Cookie").stream()
                        .anyMatch(
                                c ->
                                        c.startsWith("XSRF-TOKEN=;")
                                                && java.net.HttpCookie.parse(c)
                                                                .getFirst()
                                                                .getMaxAge()
                                                        == 0),
                login.headers().allValues("Set-Cookie").stream()
                        .map(c -> c.substring(c.indexOf(';')))
                        .toList()
                        .toString());
        assertEquals(200, me(access).statusCode());
        clock.time.set(NOW.plusSeconds(120));
        var refreshed = post("refresh", "", cookie);
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        String next = refreshCookie(refreshed);
        assertNotEquals(cookie, next);
        assertCookie(refreshed, "Max-Age=604680");
        assertEquals(sid, jwt.verify(token(refreshed)).getClaimAsString("sid"));
        assertEquals(
                expiry,
                jdbc.queryForObject("SELECT expires_at FROM USER_SESSION", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC));
        assertNotEquals(
                hash,
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class));
        error(post("refresh", "", cookie), 401);
        assertEquals(200, me(access).statusCode());
        assertEquals(204, post("logout", "", next).statusCode());
        assertNotNull(
                jdbc.queryForObject("SELECT revoked_at FROM USER_SESSION", LocalDateTime.class));
        error(me(access), 401);
        error(me(token(refreshed)), 401);
        error(post("refresh", "", next), 401);
        assertEquals(204, post("logout", "", next).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user", "PENDING", "LOCKED", "DISABLED"})
    void loginRejectsCredentialsAndIneligibleAccountsWithSame401(String reason) throws Exception {
        if (List.of("PENDING", "LOCKED", "DISABLED").contains(reason))
            jdbc.update("UPDATE USER_ACCOUNT SET status=?", reason);
        var response =
                login(
                        reason.equals("unknown-user") ? "unknown.user" : USER,
                        reason.equals("wrong-password") ? "Wrong123" : PASSWORD);
        error(response, 401);
        assertEquals(
                "AUTHENTICATION_REQUIRED", JSON.readTree(response.body()).get("code").asString());
        assertEquals(0, countSessions());
        assertTrue(
                response.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
    }

    @Test
    void expiryHasZeroSkewAndSessionCannotRefreshAtSevenDayBoundary() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        clock.time.set(NOW.plusSeconds(899));
        assertEquals(200, me(token).statusCode());
        clock.time.set(NOW.plusSeconds(900));
        error(me(token), 401);
        var refreshed = post("refresh", "", cookie);
        assertEquals(200, refreshed.statusCode());
        clock.time.set(NOW.plusSeconds(604800));
        var expired = post("refresh", "", refreshCookie(refreshed));
        error(expired, 401);
        assertCookie(expired, "Max-Age=0");
    }

    @Test
    void accountSessionAndCurrentRolesAreReadFromDatabase() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        jdbc.update(
                "INSERT INTO USER_ROLE(user_id,role_id) SELECT user_id,'admin-role' FROM"
                    + " USER_ACCOUNT");
        assertTrue(me(token).body().contains("ADMIN"));
        jdbc.update("DELETE FROM USER_ROLE WHERE role_id='admin-role'");
        assertFalse(me(token).body().contains("ADMIN"));
        jdbc.update("UPDATE USER_ACCOUNT SET status='LOCKED'");
        error(me(token), 401);
        error(post("refresh", "", cookie), 401);
        assertEquals(204, post("logout", "", cookie).statusCode());
    }

    @Test
    void revokedSessionWrongOwnerAndRefreshAsBearerCannotAuthenticate() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        error(me(raw(cookie)), 401);
        error(me(jwt.issue("wrong-user", jwt.verify(token).getClaimAsString("sid"), NOW)), 401);
        jdbc.update("UPDATE USER_SESSION SET revoked_at=UTC_TIMESTAMP(6)");
        error(me(token), 401);
        error(post("refresh", "", cookie), 401);
    }

    @Test
    void missingMalformedDuplicateAndQueryCredentialsAreRejectedBeforeIdentity() throws Exception {
        error(get("/api/v1/auth/me", null), 401);
        error(get("/api/v1/auth/me?access_token=untrusted", null), 401);
        error(me("invalid"), 401);
        var login = login(USER, PASSWORD);
        String access = token(login);
        var duplicate =
                base("me")
                        .header("Authorization", "Bearer " + access)
                        .header("Authorization", "Bearer " + access)
                        .GET()
                        .build();
        error(http.send(duplicate, HttpResponse.BodyHandlers.ofString()), 401);
        error(get("/api/v1/%61uth/me", null), 401);
        error(get("/api/v1/auth;matrix/me", null), 400);
    }

    @Test
    void missingMalformedDuplicateRefreshCookiesAndStaleLogoutAreSafe() throws Exception {
        error(post("refresh", "", null), 401);
        error(post("refresh", "", "wspm_refresh=invalid"), 401);
        var first = login(USER, PASSWORD);
        String original = refreshCookie(first);
        var rotated = post("refresh", "", original);
        String current = refreshCookie(rotated);
        assertEquals(204, post("logout", "", original).statusCode());
        assertEquals(200, me(token(rotated)).statusCode());
        error(post("refresh", "", current + "; " + current), 401);
        assertEquals(204, post("logout", "", null).statusCode());
        assertEquals(1, countSessions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "logout"})
    void emptyBodyAndCsrfOriginRemainRequired(String operation) throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        var csrf = csrf();
        error(post(operation, "{}", cookie), 400);
        var missing =
                base(operation)
                        .header("Origin", ORIGIN)
                        .header("Cookie", cookie)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        error(http.send(missing, HttpResponse.BodyHandlers.ofString()), 403);
        var wrong =
                base(operation)
                        .header("Origin", "https://attacker.example.test")
                        .header("Cookie", csrf.cookie() + "; " + cookie)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        error(http.send(wrong, HttpResponse.BodyHandlers.ofString()), 403);
        assertEquals(200, me(token(login)).statusCode());
    }

    @Test
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        barrier();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> post("refresh", "", cookie));
            var b = workers.submit(() -> post("refresh", "", cookie));
            var replies = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(
                    List.of(200, 401),
                    replies.stream().map(HttpResponse::statusCode).sorted().toList());
            var winner =
                    replies.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow();
            assertEquals(200, me(token(winner)).statusCode());
        }
        assertEquals(1, countSessions());
    }

    @Test
    void concurrentRefreshAndLogoutHonorCurrentHashWithoutDeadlock() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        barrier();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var r = workers.submit(() -> post("refresh", "", cookie));
            var l = workers.submit(() -> post("logout", "", cookie));
            var refreshed = r.get(30, TimeUnit.SECONDS);
            assertEquals(204, l.get(30, TimeUnit.SECONDS).statusCode());
            assertTrue(List.of(200, 401).contains(refreshed.statusCode()), refreshed.body());
            if (refreshed.statusCode() == 200)
                assertEquals(200, me(token(refreshed)).statusCode()); // Logout used a stale hash.
            else error(me(token(login)), 401); // Logout won and revoked the original session.
        }
    }

    @Test
    void signingFailureRollsBackLoginAndRefreshWithoutIssuingCookies() throws Exception {
        doThrow(new IllegalStateException("private signing detail"))
                .when(jwt)
                .issue(anyString(), anyString(), any());
        var failed = login(USER, PASSWORD);
        error(failed, 500);
        assertEquals(0, countSessions());
        assertTrue(
                failed.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
        reset(jwt);
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        String before =
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class);
        doThrow(new IllegalStateException("private signing detail"))
                .when(jwt)
                .issue(anyString(), anyString(), any());
        error(post("refresh", "", cookie), 500);
        assertEquals(
                before,
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class));
    }

    @Test
    void storageFailuresFailClosedAndLogoutDoesNotClaimSuccess() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login), token = token(login);
        doThrow(new DataAccessResourceFailureException("private DB detail"))
                .when(repository)
                .account(anyString(), anyBoolean());
        error(me(token), 503);
        error(post("refresh", "", cookie), 503);
        var logout = post("logout", "", cookie);
        error(logout, 503);
        assertTrue(
                logout.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
    }

    @Test
    void credentialsAndTokensAreAbsentFromDebugLogs(CapturedOutput output) throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        assertEquals(200, me(token).statusCode());
        var refresh = post("refresh", "", cookie);
        assertEquals(200, refresh.statusCode());
        assertEquals(204, post("logout", "", refreshCookie(refresh)).statusCode());
        for (String sensitive :
                List.of(
                        PASSWORD,
                        token,
                        raw(cookie),
                        token(refresh),
                        raw(refreshCookie(refresh)),
                        "session@example.test",
                        jdbc.queryForObject(
                                "SELECT refresh_token_hash FROM USER_SESSION", String.class))) {
            assertFalse(output.getAll().contains(sensitive));
        }
    }

    private void barrier() {
        CountDownLatch owners = new CountDownLatch(2);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            owners.countDown();
                            assertTrue(owners.await(10, TimeUnit.SECONDS));
                            return result;
                        })
                .when(repository)
                .ownerOfRefresh(anyString());
    }

    private int countSessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM USER_SESSION", Integer.class);
    }

    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/auth/" + path));
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return post(
                "login",
                JSON.writeValueAsString(Map.of("username", username, "password", password)),
                null);
    }

    private HttpResponse<String> me(String token) throws Exception {
        return get("/api/v1/auth/me", token);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Csrf csrf() throws Exception {
        var response = get("/api/v1/auth/csrf", null);
        assertEquals(200, response.statusCode(), response.body());
        return new Csrf(
                response.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("XSRF-TOKEN="))
                        .findFirst()
                        .orElseThrow()
                        .split(";", 2)[0],
                JSON.readTree(response.body()).get("data").get("token").asString());
    }

    private HttpResponse<String> post(String operation, String body, String refresh)
            throws Exception {
        var csrf = csrf();
        var request =
                base(operation)
                        .header("Origin", ORIGIN)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .header("Cookie", csrf.cookie() + (refresh == null ? "" : "; " + refresh));
        if (!body.isEmpty()) request.header("Content-Type", "application/json");
        return http.send(
                request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String token(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body()).get("data").get("accessToken").asString();
    }

    private String refreshCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("wspm_refresh="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
    }

    private String raw(String cookie) {
        return cookie.substring("wspm_refresh=".length());
    }

    private void assertCookie(HttpResponse<String> response, String attribute) {
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("wspm_refresh="))
                        .findFirst()
                        .orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
        assertTrue(cookie.contains(attribute), cookie);
        assertFalse(cookie.contains("Domain="));
    }

    private void error(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        metadata(response);
        if (status == 401
                && JSON.readTree(response.body()).get("path").asString().endsWith("/me")) {
            assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
        }
    }

    private void metadata(HttpResponse<String> response) throws Exception {
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        if ((response.statusCode() == 200 || response.statusCode() == 201)
                && response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .contains("application/json")) {
            JsonNode envelope = JSON.readTree(response.body());
            assertEquals(4, envelope.size());
            assertEquals("SUCCESS", envelope.get("code").asString());
            assertEquals("Request completed successfully.", envelope.get("message").asString());
            assertEquals(id, envelope.get("requestId").asString());
            assertNotNull(envelope.get("data"));
        }

        if (response.statusCode() >= 400) {
            JsonNode error = JSON.readTree(response.body());
            assertEquals(id, error.get("requestId").asString());
            assertEquals(response.statusCode(), error.get("status").asInt());
        }
    }

    record Csrf(String cookie, String token) {}
}
