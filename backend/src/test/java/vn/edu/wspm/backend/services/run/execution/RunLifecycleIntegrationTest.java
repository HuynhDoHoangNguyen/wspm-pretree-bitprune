package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.repositories.run.RunLifecycleRepository;

/** Actual MySQL CAS/UTC/leases/recovery. Rows are synthetic protocol fixtures, not fabricated mined patterns. */
@SpringBootTest(properties = "wdm.execution.enabled=false")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunLifecycleIntegrationTest {
    @TempDir static java.nio.file.Path root;
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_lifecycle_test")
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @Autowired JdbcTemplate jdbc;
    @Autowired RunLifecycleRepository repository;
    final RunMetricsCapture capture = new RunMetricsCapture(1, 2, 3, 6, 10, 99, 4, 5);
    final ExecutionOwnership owner = new ExecutionOwnership(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 180000);
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        var ddl = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(ddl);
        while (definitions.find()) jdbc.execute(definitions.group());
        vn.edu.wspm.backend.testsupport.ExecutionSchema.apply(jdbc);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never"); p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER); AuthTestKeys.properties(p);
        p.add("wdm.snapshot-storage.root", () -> root.toString());
    }
    @BeforeEach void ownedFixture() {
        jdbc.update("DELETE FROM MINING_RUN"); // Only this class's fresh disposable database, never owner data.
        jdbc.update("INSERT IGNORE INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count)"
                + " VALUES(?,'CSV','synthetic','PENDING','{}',0)", ExecutionTestInputs.SNAPSHOT);
    }
    String row(ExecutionOwnership ownership) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,"
                + "max_w,min_w,mean_w,execution_owner_id,execution_attempt_id,execution_lease_until)"
                + " VALUES(?,?,'WSPM_PRETREE','QUEUED','synthetic','ABSOLUTE',0.1,0.1,?,?,0.5,?,?,"
                + (ownership == null ? "NULL" : "TIMESTAMPADD(MICROSECOND," + ownership.leaseMillis() * 1000 + ",UTC_TIMESTAMP(6))") + ")",
                id, ExecutionTestInputs.SNAPSHOT, 4.0 / 7, 3.0 / 7,
                ownership == null ? null : ownership.ownerId(), ownership == null ? null : ownership.attemptId());
        return id;
    }
    void expire(String id) { jdbc.update("UPDATE MINING_RUN SET execution_lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6)) WHERE run_id=?", id); }
    String status(String id) { return jdbc.queryForObject("SELECT status FROM MINING_RUN WHERE run_id=?", String.class, id); }

    @Test void competingClaimsAllowOnlyOneAndTerminalTimestampsAreImmutable() throws Exception {
        String id = row(owner); var start = new CountDownLatch(1);
        try (var callers = Executors.newFixedThreadPool(2)) {
            var a = callers.submit(() -> { start.await(); return repository.claim(id, owner); });
            var b = callers.submit(() -> { start.await(); return repository.claim(id, owner); }); start.countDown();
            assertEquals(1, a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS));
        }
        assertEquals("RUNNING", status(id)); assertNotNull(jdbc.queryForMap("SELECT started_at FROM MINING_RUN WHERE run_id=?", id).get("started_at"));
        assertEquals(1, repository.succeed(id, owner, capture));
        assertEquals(capture.persisted(), repository.read(id).metrics());
        var terminal = jdbc.queryForMap("SELECT started_at,finished_at,error_message FROM MINING_RUN WHERE run_id=?", id);
        assertEquals(0, repository.succeed(id, owner, capture)); assertEquals(0, repository.fail(id, owner, "RUNNING", RunFailureCode.ENGINE_EXECUTION_FAILED));
        assertEquals(terminal, jdbc.queryForMap("SELECT started_at,finished_at,error_message FROM MINING_RUN WHERE run_id=?", id));
    }
    @Test void wrongOwnerAttemptAndExpiredLeaseCannotClaimRenewOrPublish() {
        String id = row(owner); var foreign = new ExecutionOwnership(UUID.randomUUID().toString(), owner.attemptId(), 180000);
        assertEquals(0, repository.claim(id, foreign)); assertEquals(1, repository.claim(id, owner));
        var wrongAttempt = new ExecutionOwnership(owner.ownerId(), UUID.randomUUID().toString(), 180000);
        assertEquals(0, repository.succeed(id, wrongAttempt, capture)); assertEquals(0, repository.fail(id, foreign, "RUNNING", RunFailureCode.ENGINE_EXECUTION_FAILED));
        expire(id); assertEquals(0, repository.renew(id, owner)); assertEquals(0, repository.succeed(id, owner, capture));
        assertEquals(1, repository.recover(100)); assertEquals("FAILED", status(id));
        assertEquals("EXECUTION_LEASE_EXPIRED", repository.read(id).failureCode());
        assertNull(repository.read(id).metrics().patternCount());
    }
    @Test void recoveryIsBatchedAndLeavesUnownedLegacyLiveAndTerminalRowsUntouched() {
        String unowned = row(null), legacy = row(null), live = row(owner), done = row(owner);
        jdbc.update("UPDATE MINING_RUN SET status='LEGACY_UNVERIFIED' WHERE run_id=?", legacy);
        repository.claim(done, owner); repository.succeed(done, owner, capture); expire(done);
        var expired = List.of(row(owner), row(owner), row(owner)); expired.forEach(this::expire);
        assertEquals(2, repository.recover(2)); assertEquals(1, repository.recover(2)); assertEquals(0, repository.recover(2));
        expired.forEach(id -> assertEquals("FAILED", status(id)));
        assertEquals("QUEUED", status(unowned)); assertEquals("LEGACY_UNVERIFIED", status(legacy));
        assertEquals("QUEUED", status(live)); assertEquals("SUCCEEDED", status(done));
    }
    @Test void failureBeforeAndAfterClaimHasCorrectTimesAndFixedSafeCodes() {
        String queued = row(owner), running = row(owner);
        assertEquals(1, repository.fail(queued, owner, "QUEUED", RunFailureCode.ADMISSION_FAILED));
        var q = jdbc.queryForMap("SELECT started_at,finished_at,error_message FROM MINING_RUN WHERE run_id=?", queued);
        assertNull(q.get("started_at")); assertNotNull(q.get("finished_at")); assertEquals("ADMISSION_FAILED", q.get("error_message"));
        repository.claim(running, owner); repository.fail(running, owner, "RUNNING", RunFailureCode.ENGINE_EXECUTION_FAILED);
        var r = jdbc.queryForMap("SELECT started_at,finished_at FROM MINING_RUN WHERE run_id=?", running);
        assertNotNull(r.get("started_at")); assertNotNull(r.get("finished_at"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN WHERE finished_at<COALESCE(started_at,created_at)", Integer.class));
    }
    @Test void heartbeatExtendsLiveLeaseButCannotReviveExpiredRun() {
        String id = row(owner);
        jdbc.update("UPDATE MINING_RUN SET execution_lease_until=TIMESTAMPADD(SECOND,60,UTC_TIMESTAMP(6)) WHERE run_id=?", id);
        var before = jdbc.queryForObject("SELECT execution_lease_until FROM MINING_RUN WHERE run_id=?", java.sql.Timestamp.class, id);
        assertEquals(1, repository.renew(id, owner));
        var after = jdbc.queryForObject("SELECT execution_lease_until FROM MINING_RUN WHERE run_id=?", java.sql.Timestamp.class, id);
        assertTrue(after.after(before)); expire(id); assertEquals(0, repository.renew(id, owner));
    }
    @Test void expiredWorkerFinishingAfterAnotherProcessRecoveryCannotPublishOrLoseCompleteMap() throws Exception {
        var service = new RunLifecycleService(repository, new LifecycleLimits(180000, 60000, 100));
        var verifier = mock(RunExecutionVerifier.class); when(verifier.refreshMeasuredOwned(anyString(), any(), any(), any())).thenAnswer(call -> new vn.edu.wspm.backend.services.run.MeasuredRunPreparation(call.getArgument(1), 0));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var input = ExecutionTestInputs.input();
        var expected = ExecutionTestInputs.result(input);
        var ticks = new java.util.concurrent.atomic.AtomicLong();
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), request -> {
            entered.countDown(); ManagedRunExecutionAdmissionTest.await(release); return expected;
        }, verifier, executor, service, () -> ticks.getAndAdd(expected.metrics().completeAlgorithmNanos() + 1000))) {
            String id = UUID.randomUUID().toString(); var reservation = provider.reserve(id, input);
            var ticket = reservation.ownership().orElseThrow();
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,"
                    + "execution_owner_id,execution_attempt_id,execution_lease_until) VALUES(?,?,'WSPM_PRETREE','QUEUED','synthetic','ABSOLUTE',.1,.1,.5,?,?,TIMESTAMPADD(SECOND,180,UTC_TIMESTAMP(6)))",
                    id, ExecutionTestInputs.SNAPSHOT, ticket.ownerId(), ticket.attemptId());
            try {
                reservation.activate(); assertTrue(entered.await(10, TimeUnit.SECONDS)); assertEquals("RUNNING", status(id)); expire(id);
                new RunLifecycleService(repository, new LifecycleLimits(180000, 60000, 100)).recover();
                release.countDown(); var outcome = provider.completion(id).orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertSame(expected, outcome.result()); assertFalse(outcome.lifecycleConfirmed()); assertEquals("FAILED", status(id));
                assertNotNull(outcome.metrics()); assertEquals(expected.patternCount(), outcome.metrics().patternCount());
                assertFalse(provider.acknowledge(id, outcome.token())); assertEquals(1, provider.retainedCount());
            } finally { release.countDown(); }
        }
    }
    @Test void rejectedTerminalStatementRollsBackStatusAndEveryMetricTogether() {
        String id = row(owner); assertEquals(1, repository.claim(id, owner));
        jdbc.execute("CREATE TRIGGER reject_metrics_terminal BEFORE UPDATE ON MINING_RUN FOR EACH ROW "
                + "BEGIN IF NEW.status='SUCCEEDED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled test rejection'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> repository.succeed(id, owner, capture));
            var observed = repository.read(id); assertEquals("RUNNING", observed.status());
            assertEquals(new RunMetricsCapture.Persisted(null, null, null, null, null, null, null, null, null), observed.metrics());
            assertNull(jdbc.queryForMap("SELECT finished_at FROM MINING_RUN WHERE run_id=?", id).get("finished_at"));
        } finally { jdbc.execute("DROP TRIGGER reject_metrics_terminal"); }
        assertEquals(1, repository.succeed(id, owner, capture));
        assertEquals(capture.persisted(), repository.read(id).metrics());
    }
    @Test void fullMetricReadbackIsExactForLargeCountsAndCannotRewriteTerminalSnapshot() {
        String id = row(owner); repository.claim(id, owner);
        var large = new RunMetricsCapture(999999, 2000000, 3000000, 7000000, 10000000, Long.MAX_VALUE, 0, Long.MAX_VALUE);
        assertEquals(1, repository.succeed(id, owner, large));
        assertEquals(large.persisted(), repository.read(id).metrics());
        assertEquals(0, repository.succeed(id, owner, capture));
        assertEquals(large.persisted(), repository.read(id).metrics());
    }
    @Test void ownershipGroupConstraintRejectsPartialOwnership() {
        String id = row(null);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE MINING_RUN SET execution_owner_id=? WHERE run_id=?", owner.ownerId(), id));
        assertNull(repository.read(id).ownerId());
    }
}
