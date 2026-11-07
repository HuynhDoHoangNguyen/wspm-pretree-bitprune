package vn.edu.wspm.backend.services.run;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

/** Real shared Auth/HTTP/V2 MySQL/artifacts/preparation/commit. Admission provider is explicitly test-only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunCreationIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_run_creation_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path root;
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PATH = "/api/v1/admin/mining-runs", DATASETS = "/api/v1/admin/dataset-snapshots";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfigurableApplicationContext context;
    @Autowired RunPreparationService preparation;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static String customer, admin, snapshot;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        p.add("wdm.execution.enabled", () -> false); // Keep WDM-05.3 no-provider evidence independent of host settings.
        var setup = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String ddl = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(ddl);
        int count = 0; while (definitions.find()) { setup.execute(definitions.group()); count++; } assertEquals(47, count);
        vn.edu.wspm.backend.testsupport.ExecutionSchema.apply(setup);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never"); p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER); p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> root.toString()); AuthTestKeys.properties(p);
    }
    byte[] fixture(String name) throws Exception { return new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray(); }
    HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)); }
    JsonNode envelope(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow(); UUID.fromString(id);
        var body = JSON.readTree(response.body()); assertEquals(id, body.get("requestId").asString());
        assertFalse(body.toString().contains(root.toString()));
        if (status < 300) { assertEquals("SUCCESS", body.get("code").asString()); return body.get("data"); }
        assertTrue(response.headers().firstValue("Location").isEmpty()); return body;
    }
    JsonNode auth(String operation, Map<String, String> input, int status) throws Exception {
        var csrf = http.send(request("/api/v1/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String token = envelope(csrf, 200).get("token").asString();
        String cookie = csrf.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        return envelope(http.send(request("/api/v1/auth/" + operation).header("Origin", AuthTestKeys.ISSUER)
                .header("X-XSRF-TOKEN", token).header("Cookie", cookie).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(input))).build(), HttpResponse.BodyHandlers.ofByteArray()), status);
    }
    JsonNode operation(String path) throws Exception {
        return envelope(http.send(request(path).header("Authorization", "Bearer " + admin)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }
    HttpResponse<byte[]> create(String token, String path, String body, String media) throws Exception {
        var builder = request(path).header("Content-Type", media);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    String body(String id, String algorithm, String mode, String value) {
        return "{\"snapshotId\":\"" + id + "\",\"algorithm\":\"" + algorithm + "\",\"minsupMode\":\"" + mode + "\",\"minsupValue\":" + value + "}";
    }
    String valid() { return body(snapshot, "WSPM_PRETREE", "PERCENT", "14"); }
    int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Integer.class); }
    DefaultListableBeanFactory beans() { return (DefaultListableBeanFactory) context.getBeanFactory(); }

    @BeforeEach void setupDataThroughRealPipeline() throws Exception {
        if (snapshot != null) return;
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('run-category','Synthetic','run-category')");
        for (var item : JSON.readTree(fixture("round-trip-expected.json")).get("catalogProducts")) {
            String id = item.asString(); jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'run-category',999)", id, id, id, id);
        }
        String password = "RunCreation12345";
        String user = auth("register", Map.of("username", "run.admin", "password", password,
                "email", "run@example.test", "displayName", "Run synthetic"), 201).get("userId").asString();
        customer = auth("login", Map.of("username", "run.admin", "password", password), 200).get("accessToken").asString();
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", user);
        admin = auth("login", Map.of("username", "run.admin", "password", password), 200).get("accessToken").asString();
        // Role changes are checked dynamically, so remove ADMIN during the CUSTOMER check below and restore it.
        jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='admin-role'", user);
        envelope(create(customer, PATH, "{", "application/json"), 403);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", user);
        String boundary = "run-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"round-trip.csv\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        String uploaded = envelope(http.send(request(DATASETS).header("Authorization", "Bearer " + admin)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, fixture("round-trip.csv"), suffix))).build(),
                HttpResponse.BodyHandlers.ofByteArray()), 201).get("snapshotId").asString();
        operation(DATASETS + "/" + uploaded + "/validation"); operation(DATASETS + "/" + uploaded + "/conversion");
        snapshot = uploaded;
    }

    @Test @Order(1) void productionWithoutProviderFailsClosedAndPreservesErrorOrdering() throws Exception {
        assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty());
        envelope(create(null, PATH, "{", "text/plain"), 401);
        envelope(create(admin, PATH, "{", "application/json"), 400);
        envelope(create(admin, PATH + "?force=true", valid(), "application/json"), 400);
        envelope(create(admin, PATH, valid(), "text/plain"), 415);
        envelope(create(admin, PATH, body(UUID.randomUUID().toString(), "WSPM_PRETREE", "PERCENT", "0"), "application/json"), 404);
        for (String zero : List.of("0", "-0.0", "4.9e-324")) {
            assertEquals("UNSUPPORTED_MINING_CONFIGURATION", envelope(create(admin, PATH,
                    body(snapshot, "WSPM_PRETREE", "PERCENT", zero), "application/json"), 422).get("code").asString());
        }
        assertEquals("SERVICE_UNAVAILABLE", envelope(create(admin, PATH, valid(), "application/json"), 503).get("code").asString());
        String reference = jdbc.queryForObject("SELECT sequence_file_path FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, snapshot);
        Path file = root.resolve(reference); byte[] original = Files.readAllBytes(file);
        try {
            Files.write(file, "controlled-tamper".getBytes(StandardCharsets.UTF_8));
            envelope(create(admin, PATH, valid(), "application/json"), 503);
        } finally { Files.write(file, original); }
        assertEquals(0, count());
    }

    /** Simulates admission acknowledgement only. It NEVER calls the engine or claims actual execution. */
    final class TestAdmission implements RunExecutionAdmission {
        final List<String> events = new ArrayList<>();
        Runnable beforeReserve = () -> {};
        boolean failActivate;
        @Override public Reservation reserve(String id, PreparedRun input) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            events.add("reserve"); beforeReserve.run();
            return new Reservation() {
                boolean closed;
                public void activate() {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN WHERE run_id=?", Integer.class, id));
                    events.add("committed-visible"); events.add("activate");
                    if (failActivate) throw new IllegalStateException("controlled admission outcome unknown");
                }
                public void close() { if (!closed) { closed = true; events.add("close"); } }
            };
        }
    }
    void install(TestAdmission admission) { beans().registerSingleton("runCreationTestAdmission", admission); }
    void remove() { beans().destroySingleton("runCreationTestAdmission"); }

    @Test @Order(2) void controlledAdmissionProvesCommittedV2ReadbackAnd202Contract() throws Exception {
        var admission = new TestAdmission(); install(admission);
        var beforeSnapshot = jdbc.queryForMap("SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id=?", snapshot);
        try {
            var ids = new HashSet<String>();
            for (String algorithm : List.of("WSPM_PRETREE", "WSPM_BITPRUNE")) {
                for (String config : List.of("PERCENT:14", "PERCENT:100", "ABSOLUTE:0.25", "ABSOLUTE:4.9e-324", "ABSOLUTE:1.7976931348623157e308")) {
                    String[] fields = config.split(":");
                    var prepared = preparation.prepare(new RunCreateRequest(snapshot, algorithm, fields[0], Double.parseDouble(fields[1])));
                    var response = create(admin, PATH, body(snapshot, algorithm, fields[0], fields[1]), "application/json");
                    var data = envelope(response, 202); String id = data.get("runId").asString(); UUID.fromString(id);
                    assertTrue(ids.add(id)); assertNotEquals(snapshot, id); assertEquals(PATH + "/" + id, response.headers().firstValue("Location").orElseThrow());
                    var row = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", id);
                    assertEquals(snapshot, row.get("snapshot_id")); assertEquals(algorithm, row.get("algorithm"));
                    assertEquals("QUEUED", row.get("status")); assertEquals(fields[0], row.get("minsup_mode"));
                    assertEquals(beforeSnapshot.get("source_name"), row.get("dataset_source"));
                    assertEquals(Double.doubleToRawLongBits(prepared.minimumSupport().configuredValue()), Double.doubleToRawLongBits(((Number) row.get("minsup_configured")).doubleValue()));
                    assertEquals(Double.doubleToRawLongBits(prepared.minimumSupport().effectiveThreshold()), Double.doubleToRawLongBits(((Number) row.get("effective_threshold")).doubleValue()));
                    assertEquals(prepared.minimumSupport().relativeMinimumSupport().isPresent()
                            ? prepared.minimumSupport().relativeMinimumSupport().getAsDouble() : null, row.get("minsup_relative_fraction"));
                    assertEquals(prepared.weights().maxWeight().orElseThrow(), row.get("max_w"));
                    assertEquals(prepared.weights().minWeight().orElseThrow(), row.get("min_w"));
                    assertEquals(prepared.weights().meanWeight().orElseThrow(), row.get("mean_w"));
                    Instant time = jdbc.queryForObject("SELECT created_at FROM MINING_RUN WHERE run_id=?",
                            (rs, n) -> rs.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC), id);
                    assertEquals(time, Instant.parse(data.get("createdAt").asString())); assertEquals(0, time.getNano() % 1000);
                    for (String empty : List.of("legacy_metadata_json", "pattern_count", "candidate_count", "dataset_io_ms", "preprocessing_ms",
                            "dfs_mining_ms", "complete_algorithm_ms", "output_io_ms", "total_execution_ms", "peak_sampled_jvm_heap_bytes",
                            "started_at", "finished_at", "error_message")) assertNull(row.get(empty), empty);
                    assertEquals(List.of("reserve", "committed-visible", "activate", "close"), List.copyOf(admission.events)); admission.events.clear();
                }
            }
            assertEquals(10, ids.size()); assertEquals(10, count());
            assertEquals(beforeSnapshot, jdbc.queryForMap("SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id=?", snapshot));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN", Integer.class));
        } finally { remove(); }
    }

    @Test @Order(3) void changedBindingAndInsertFailureRollBackAndReleaseReservation() throws Exception {
        var admission = new TestAdmission(); install(admission); int before = count();
        String name = jdbc.queryForObject("SELECT source_name FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, snapshot);
        try {
            admission.beforeReserve = () -> jdbc.update("UPDATE DATASET_SNAPSHOT SET source_name='changed-binding' WHERE snapshot_id=?", snapshot);
            envelope(create(admin, PATH, valid(), "application/json"), 409);
            assertEquals(before, count()); assertEquals(List.of("reserve", "close"), List.copyOf(admission.events));
        } finally { jdbc.update("UPDATE DATASET_SNAPSHOT SET source_name=? WHERE snapshot_id=?", name, snapshot); remove(); }
        var rollbackAdmission = new TestAdmission(); install(rollbackAdmission);
        boolean constraintCreated = false;
        try {
            jdbc.execute("ALTER TABLE MINING_RUN ADD CONSTRAINT chk_run_creation_test_reject CHECK (minsup_configured <> 0.125)");
            constraintCreated = true;
            envelope(create(admin, PATH, body(snapshot, "WSPM_PRETREE", "ABSOLUTE", "0.125"), "application/json"), 503);
            assertEquals(before, count()); assertEquals(List.of("reserve", "close"), List.copyOf(rollbackAdmission.events));
        } finally {
            try { if (constraintCreated) jdbc.execute("ALTER TABLE MINING_RUN DROP CHECK chk_run_creation_test_reject"); }
            finally { remove(); }
        }
    }

    @Test @Order(4) void unknownActivationOutcomeRetainsCommittedRunAndNeverReturns202() throws Exception {
        var admission = new TestAdmission(); admission.failActivate = true; install(admission); int before = count();
        try {
            envelope(create(admin, PATH, valid(), "application/json"), 503);
            assertEquals(before + 1, count()); assertEquals(List.of("reserve", "committed-visible", "activate", "close"), List.copyOf(admission.events));
            assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN WHERE status='QUEUED' AND error_message IS NULL AND finished_at IS NULL", Integer.class));
        } finally { remove(); }
        assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty());
    }

    JsonNode statusRead(String token, String suffix, int expected) throws Exception {
        var builder = request(PATH + "/" + suffix);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return envelope(http.send(builder.GET().build(), HttpResponse.BodyHandlers.ofByteArray()), expected);
    }
    @Test @Order(5) void statusUsesActualSharedAdminGuardBeforeMalformedOrMissingResourceChecks() throws Exception {
        auth("register", Map.of("username", "status.customer", "password", "StatusCustomer12345", "email", "status.customer@example.test",
                "displayName", "Status customer synthetic"), 201);
        String statusCustomer = auth("login", Map.of("username", "status.customer", "password", "StatusCustomer12345"), 200).get("accessToken").asString();
        statusRead(null, "invalid?force=true", 401); statusRead(statusCustomer, "invalid?force=true", 403);
        statusRead(admin, "invalid", 400); statusRead(admin, UUID.randomUUID().toString(), 404);
        statusRead(admin, UUID.randomUUID() + "?force=true", 400);
        assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty());
    }
    @Test @Order(6) void disabledExecutionReadsAllPersistedStatesExactUnsignedMetricsAndSafeFailuresWithoutMutation() throws Exception {
        for (String state : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")) {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,"
                    + "effective_threshold,mean_w,created_at,error_message) VALUES(?,?,'WSPM_BITPRUNE',?,'status protocol fixture',"
                    + "'ABSOLUTE',0.1,0.1,0.5,'2026-11-07 05:00:00.123456','private SQL /path secret')", id, snapshot, state);
            try {
                if (state.equals("SUCCEEDED")) jdbc.update("UPDATE MINING_RUN SET pattern_count=0,candidate_count=9007199254740993,"
                        + "dataset_io_ms=1,preprocessing_ms=2,dfs_mining_ms=3,complete_algorithm_ms=6,total_execution_ms=10,"
                        + "peak_sampled_jvm_heap_bytes=18446744073709551615 WHERE run_id=?", id);
                var before = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", id);
                for (int poll = 0; poll < 3; poll++) {
                    var data = statusRead(admin, id, 200); assertEquals(state, data.get("status").asString());
                    assertEquals("2026-11-07T05:00:00.123456Z", data.get("createdAt").asString());
                    assertTrue(data.get("startedAt").isNull()); assertTrue(data.get("finishedAt").isNull());
                    assertTrue(data.get("measurement").get("contractVersion").isNull());
                    String json = data.toString(); assertFalse(json.contains("private")); assertFalse(json.contains("secret"));
                    assertFalse(json.contains("executionOwner")); assertFalse(json.contains("execution_lease"));
                    if (state.equals("SUCCEEDED")) {
                        assertEquals("0", data.get("patternCount").asString()); assertEquals("9007199254740993", data.get("candidateCount").asString());
                        assertEquals("18446744073709551615", data.get("metrics").get("peakSampledJvmHeapBytes").asString());
                    } else assertTrue(data.get("patternCount").isNull());
                    if (state.equals("FAILED")) assertEquals("RUN_FAILED", data.get("failure").get("code").asString());
                    else assertTrue(data.get("failure").isNull());
                }
                assertEquals(before, jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", id));
                assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty());
            } finally { jdbc.update("DELETE FROM MINING_RUN WHERE run_id=?", id); }
        }
    }
}
