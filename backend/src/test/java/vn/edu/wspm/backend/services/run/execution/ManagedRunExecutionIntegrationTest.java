package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.run.RunCreationRepository;
import vn.edu.wspm.backend.services.run.*;
import vn.edu.wspm.backend.services.run.engine.CoreMiningEngineAdapter;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Enabled production provider/actual core + real Auth/HTTP/V2 MySQL/artifacts. Spy adds latches only, then calls real engine. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ManagedRunExecutionIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_managed_execution_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path root;
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String PATH = "/api/v1/admin/mining-runs", DATASETS = "/api/v1/admin/dataset-snapshots";
    static String admin, snapshot;
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManagedRunExecutionAdmission provider;
    @Autowired RunPreparationService preparation;
    @Autowired RunCreationRepository creation;
    @Autowired org.springframework.context.ApplicationContext context;
    @MockitoSpyBean CoreMiningEngineAdapter engine;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        var setup = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        var ddl = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(ddl);
        int count = 0; while (definitions.find()) { setup.execute(definitions.group()); count++; } assertEquals(47, count);
        vn.edu.wspm.backend.testsupport.ExecutionSchema.apply(setup);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never"); p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER); p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> root.toString()); AuthTestKeys.properties(p);
        p.add("wdm.execution.enabled", () -> true); p.add("wdm.execution.parallelism", () -> 1);
        p.add("wdm.execution.queue-capacity", () -> 1); p.add("wdm.execution.shutdown-grace-millis", () -> 1000);
    }
    byte[] fixture(String name) throws Exception { return new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray(); }
    HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)); }
    JsonNode envelope(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        var body = JSON.readTree(response.body()); assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(), body.get("requestId").asString());
        if (status < 300) { assertEquals("SUCCESS", body.get("code").asString()); return body.get("data"); }
        assertTrue(response.headers().firstValue("Location").isEmpty()); return body;
    }
    JsonNode auth(String operation, Map<String, String> input, int status) throws Exception {
        var csrf = http.send(request("/api/v1/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        var token = envelope(csrf, 200).get("token").asString();
        String cookie = csrf.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        return envelope(http.send(request("/api/v1/auth/" + operation).header("Origin", AuthTestKeys.ISSUER)
                .header("X-XSRF-TOKEN", token).header("Cookie", cookie).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(input))).build(), HttpResponse.BodyHandlers.ofByteArray()), status);
    }
    JsonNode operation(String path) throws Exception {
        return envelope(http.send(request(path).header("Authorization", "Bearer " + admin)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }
    HttpResponse<byte[]> submit(String algorithm, String mode, double value) throws Exception {
        return http.send(request(PATH).header("Authorization", "Bearer " + admin).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(new RunCreateRequest(snapshot, algorithm, mode, value)))).build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }
    String accepted(String algorithm) throws Exception {
        var response = submit(algorithm, "ABSOLUTE", .1); var data = envelope(response, 202);
        String id = data.get("runId").asString(); assertEquals(PATH + "/" + id, response.headers().firstValue("Location").orElseThrow());
        return id;
    }
    ManagedRunExecutionAdmission.Outcome outcome(String id) throws Exception {
        return provider.completion(id).orElseThrow().toCompletableFuture().get(20, TimeUnit.SECONDS);
    }
    JsonNode poll(String id) throws Exception {
        var response = http.send(request(PATH + "/" + id).header("Authorization", "Bearer " + admin).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        return envelope(response, 200);
    }
    void assertStatusPayload(ManagedRunExecutionAdmission.Outcome outcome) throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId());
        int retained = provider.retainedCount();
        var data = poll(outcome.runId()); var metrics = data.get("metrics"); var expected = outcome.metrics().persisted();
        assertEquals("SUCCEEDED", data.get("status").asString());
        assertEquals(outcome.result().algorithm().name(), data.get("algorithm").asString());
        assertEquals(expected.patternCount().toString(), data.get("patternCount").asString());
        assertEquals(expected.candidateCount().toString(), data.get("candidateCount").asString());
        assertEquals(expected.datasetIoMs().toString(), metrics.get("datasetIoMs").asString());
        assertEquals(expected.preprocessingMs().toString(), metrics.get("preprocessingMs").asString());
        assertEquals(expected.dfsMiningMs().toString(), metrics.get("dfsMiningMs").asString());
        assertEquals(expected.completeAlgorithmMs().toString(), metrics.get("completeAlgorithmMs").asString());
        assertEquals(expected.totalExecutionMs().toString(), metrics.get("totalExecutionMs").asString());
        assertEquals(expected.peakSampledJvmHeapBytes().toString(), metrics.get("peakSampledJvmHeapBytes").asString());
        assertTrue(metrics.get("outputIoMs").isNull()); assertEquals("WDM-05.6", data.get("measurement").get("contractVersion").asString());
        assertTrue(data.get("failure").isNull()); assertEquals(retained, provider.retainedCount());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId()));
    }
    void assertCaptured(ManagedRunExecutionAdmission.Outcome outcome) {
        assertTrue(outcome.lifecycleConfirmed()); assertNotNull(outcome.metrics());
        var raw = outcome.result().metrics(); var captured = outcome.metrics();
        assertEquals(raw.preprocessing().elapsedNanos(), captured.preprocessingNanos());
        assertEquals(raw.dfsMining().elapsedNanos(), captured.dfsMiningNanos());
        assertEquals(raw.completeAlgorithmNanos(), captured.completeAlgorithmNanos());
        assertEquals(raw.observedPeakHeapAlgorithmBytes(), captured.peakSampledJvmHeapBytes());
        assertEquals(outcome.result().statistics().candidateCount(), captured.candidateCount());
        var db = jdbc.queryForMap("SELECT pattern_count,candidate_count,dataset_io_ms,preprocessing_ms,dfs_mining_ms,"
                + "complete_algorithm_ms,output_io_ms,total_execution_ms,peak_sampled_jvm_heap_bytes FROM MINING_RUN WHERE run_id=?", outcome.runId());
        var expected = captured.persisted();
        assertEquals(expected.patternCount(), ((Number) db.get("pattern_count")).longValue());
        assertEquals(expected.candidateCount(), ((Number) db.get("candidate_count")).longValue());
        assertEquals(expected.datasetIoMs(), ((Number) db.get("dataset_io_ms")).longValue());
        assertEquals(expected.preprocessingMs(), ((Number) db.get("preprocessing_ms")).longValue());
        assertEquals(expected.dfsMiningMs(), ((Number) db.get("dfs_mining_ms")).longValue());
        assertEquals(expected.completeAlgorithmMs(), ((Number) db.get("complete_algorithm_ms")).longValue());
        assertEquals(expected.totalExecutionMs(), ((Number) db.get("total_execution_ms")).longValue());
        assertEquals(expected.peakSampledJvmHeapBytes(), ((Number) db.get("peak_sampled_jvm_heap_bytes")).longValue());
        assertNull(db.get("output_io_ms"));
    }
    void blockEngine(CountDownLatch entered, CountDownLatch release) {
        doAnswer(call -> {
            assertTrue(Thread.currentThread().getName().startsWith("wspm-mining-"));
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown(); assertTrue(release.await(20, TimeUnit.SECONDS));
            return call.callRealMethod();
        }).when(engine).execute(any());
    }
    @BeforeEach void setupThroughActualDatasetPipelineOnce() throws Exception {
        if (snapshot != null) return;
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('execution-category','Synthetic','execution-category')");
        for (var product : JSON.readTree(fixture("round-trip-expected.json")).get("catalogProducts")) {
            String id = product.asString(); jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'execution-category',999)", id, id, id, id);
        }
        String password = "Execution12345";
        String user = auth("register", Map.of("username", "execution.admin", "password", password,
                "email", "execution@example.test", "displayName", "Execution synthetic"), 201).get("userId").asString();
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", user);
        admin = auth("login", Map.of("username", "execution.admin", "password", password), 200).get("accessToken").asString();
        String boundary = "execution-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"round-trip.csv\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        String uploaded = envelope(http.send(request(DATASETS).header("Authorization", "Bearer " + admin)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, fixture("round-trip.csv"), suffix))).build(),
                HttpResponse.BodyHandlers.ofByteArray()), 201).get("snapshotId").asString();
        operation(DATASETS + "/" + uploaded + "/validation"); operation(DATASETS + "/" + uploaded + "/conversion"); snapshot = uploaded;
    }

    @Test @Order(1) void actualProviderReturns202BeforeMiningCompletesAndPreservesBothCompleteMapsWithBackpressure() throws Exception {
        assertEquals(1, context.getBeansOfType(RunExecutionAdmission.class).size());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); blockEngine(entered, release);
        try {
            String first = accepted("WSPM_PRETREE"); assertTrue(entered.await(10, TimeUnit.SECONDS));
            assertTrue(provider.outcome(first).isEmpty());
            assertEquals("RUNNING", poll(first).get("status").asString());
            String second = accepted("WSPM_BITPRUNE");
            assertEquals(ManagedRunExecutionAdmission.State.SUBMITTED, provider.state(second).orElseThrow());
            assertEquals("QUEUED", poll(second).get("status").asString());
            int before = jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Integer.class);
            envelope(submit("WSPM_PRETREE", "ABSOLUTE", .1), 503);
            assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Integer.class));
            release.countDown(); var a = outcome(first); var b = outcome(second);
            assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, a.state());
            assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, b.state());
            var prepared = preparation.prepare(new RunCreateRequest(snapshot, "WSPM_PRETREE", "ABSOLUTE", .1));
            for (var outcome : List.of(a, b)) {
                assertEquals(outcome.runId(), outcome.persistenceContext().runId());
                assertEquals(prepared.binding(), outcome.persistenceContext().provenance().binding());
                var stored = context.getBean(vn.edu.wspm.backend.repositories.run.RunMetadataRepository.class).read(outcome.runId()).orElseThrow();
                assertEquals(outcome.persistenceContext().provenance(), stored.provenance());
                assertEquals("NOT_PUBLISHED", stored.publication().status());
            }
            var directPre = new WspmPreTreeMiner().mine(prepared.engineRequest().database(), MinimumSupport.absolute(.1));
            var directBit = new BitPruneMiner().mine(prepared.engineRequest().database(), MinimumSupport.absolute(.1));
            assertEquals(directPre.supports(), a.result().supports()); assertEquals(directBit.supports(), b.result().supports());
            assertEquals(a.result().supports(), b.result().supports()); assertEquals(a.result().patternCount(), b.result().patternCount());
            assertCaptured(a); assertCaptured(b);
            assertStatusPayload(a); assertStatusPayload(b);
            assertEquals(2, provider.retainedCount()); envelope(submit("WSPM_PRETREE", "ABSOLUTE", .1), 503);
            assertFalse(provider.acknowledge(first, UUID.randomUUID())); assertTrue(provider.acknowledge(first, a.token()));
            assertTrue(provider.acknowledge(second, b.token()));
            assertEquals("SUCCEEDED", poll(first).get("status").asString()); // durable status outlives registry outcome
            var emptyResponse = envelope(submit("WSPM_PRETREE", "PERCENT", 100), 202); String emptyId = emptyResponse.get("runId").asString();
            var empty = outcome(emptyId); assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, empty.state());
            assertTrue(empty.result().supports().isEmpty()); assertCaptured(empty);
            assertNotNull(empty.persistenceContext());
            assertEquals("NOT_PUBLISHED", jdbc.queryForObject("SELECT publication_status FROM RUN_RESULT_PUBLICATION WHERE run_id=?", String.class, emptyId));
            assertStatusPayload(empty);
            assertEquals(0, empty.metrics().patternCount()); assertTrue(provider.acknowledge(emptyId, empty.token()));
            assertEquals(0, provider.retainedCount());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN WHERE status='SUCCEEDED' AND started_at IS NOT NULL AND finished_at IS NOT NULL", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN_ITEM", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RUN_ITEM_STATISTIC", Integer.class));
        } finally { release.countDown(); }
    }

    @Test @Order(2) void queuedJobReverifiesTamperedArtifactsBeforeEngineCall() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); blockEngine(entered, release);
        String reference = jdbc.queryForObject("SELECT sequence_file_path FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, snapshot);
        Path file = root.resolve(reference); byte[] bytes = Files.readAllBytes(file);
        try {
            String first = accepted("WSPM_PRETREE"); assertTrue(entered.await(10, TimeUnit.SECONDS));
            String second = accepted("WSPM_BITPRUNE");
            Files.write(file, "controlled-artifact-tamper".getBytes(StandardCharsets.UTF_8)); release.countDown();
            var a = outcome(first); var b = outcome(second);
            assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, a.state());
            assertEquals(ManagedRunExecutionAdmission.State.FAILED, b.state()); assertNull(b.result());
            assertEquals("INPUT_REVERIFICATION_FAILED", b.failureCode()); verify(engine).execute(any());
            var failedStatus = poll(second); assertEquals("FAILED", failedStatus.get("status").asString());
            assertEquals("INPUT_REVERIFICATION_FAILED", failedStatus.get("failure").get("code").asString());
            assertTrue(provider.acknowledge(first, a.token())); assertTrue(provider.acknowledge(second, b.token()));
            assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM MINING_RUN WHERE run_id=?", String.class, second));
            assertNull(b.metrics());
            var failed = jdbc.queryForMap("SELECT pattern_count,candidate_count,dataset_io_ms,preprocessing_ms,dfs_mining_ms,complete_algorithm_ms,"
                    + "output_io_ms,total_execution_ms,peak_sampled_jvm_heap_bytes FROM MINING_RUN WHERE run_id=?", second);
            assertTrue(failed.values().stream().allMatch(java.util.Objects::isNull));
        } finally { Files.write(file, bytes); release.countDown(); }
    }

    @Test @Order(3) void activationRequiresVisibleMatchingCommittedRunAndNeverStartsAnUncommittedTicket() throws Exception {
        var prepared = preparation.prepare(new RunCreateRequest(snapshot, "WSPM_PRETREE", "ABSOLUTE", .1));
        var reservation = provider.reserve(UUID.randomUUID().toString(), prepared);
        assertThrows(ApplicationException.class, reservation::activate); reservation.close();
        String id = UUID.randomUUID().toString(); creation.create(id, prepared);
        jdbc.update("UPDATE MINING_RUN SET minsup_configured=? WHERE run_id=?", Math.nextUp(.1), id);
        try {
            var mismatched = provider.reserve(id, prepared); assertThrows(ApplicationException.class, mismatched::activate); mismatched.close();
            assertEquals(0, provider.retainedCount()); verifyNoInteractions(engine);
        } finally { jdbc.update("UPDATE MINING_RUN SET minsup_configured=? WHERE run_id=?", .1, id); }
    }
}
