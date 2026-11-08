package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.PatternRepository;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.services.dataset.validation.SnapshotValidationService;
import vn.edu.wspm.backend.services.dataset.conversion.SnapshotConversionService;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.services.run.engine.MiningAlgorithm;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.*;

/** Real CSV/storage/conversion/managed engine/Flyway/MySQL. No seeded mining results, no owner datasource. */
@SpringBootTest(properties = {"spring.config.import=", "wdm.result-publication.enabled=false", "wdm.execution.enabled=true", "wdm.execution.parallelism=1", "wdm.execution.queue-capacity=64",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PatternPersistenceIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_pattern_persistence")
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService snapshots;
    @Autowired SnapshotValidationService validation;
    @Autowired SnapshotConversionService conversion;
    @Autowired RunCreationService creation;
    @Autowired ManagedRunExecutionAdmission provider;
    @Autowired PatternPersistenceService service;
    @Autowired PatternPersistenceRepository repository;
    @Autowired PatternRepository patterns;
    @Autowired RunMetadataRepository metadata;
    @Autowired PlatformTransactionManager transactions;
    static String snapshot;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("wdm.execution.enabled", () -> true);
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load();
        flyway.migrate(); flyway.validate(); assertEquals("5", flyway.info().current().getVersion().getVersion());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("wdm.snapshot-storage.root", () -> storage.toString());
        AuthTestKeys.properties(p); p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
    }

    @BeforeEach void pipeline() throws Exception {
        if (snapshot != null) return;
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('pattern-cat','Synthetic','pattern-cat')");
        for (int i = 1; i <= 3; i++) {
            String id = "rt-p" + i;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'pattern-cat',999)", id, id, id, id);
        }
        byte[] csv = new ClassPathResource("wdm/csv-v1/round-trip.csv").getContentAsByteArray();
        snapshot = snapshots.acquire(new MockMultipartFile("file", "patterns.csv", "text/csv", csv)).snapshotId();
        assertEquals("VALID", validation.validate(snapshot).validationStatus());
        assertEquals(snapshot, conversion.convert(snapshot).snapshotId());
    }

    Outcome run(MiningAlgorithm algorithm, String mode, double threshold) throws Exception {
        String id = creation.create(new RunCreateRequest(snapshot, algorithm.name(), mode, threshold)).runId();
        var result = provider.completion(id).orElseThrow().toCompletableFuture().get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!result.lifecycleConfirmed() && System.nanoTime() < deadline) {
            Thread.sleep(20); result = provider.outcome(id).orElseThrow();
        }
        assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, result.state()); assertTrue(result.lifecycleConfirmed());
        assertEquals(0, count(id)); // No automatic writer connected to execution.
        return result;
    }

    int count(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id=?", Integer.class, id); }
    Map<String, String> metrics(String id) {
        return jdbc.queryForObject("SELECT status,CAST(pattern_count AS CHAR),CAST(candidate_count AS CHAR),CAST(dataset_io_ms AS CHAR),"
                + "CAST(preprocessing_ms AS CHAR),CAST(dfs_mining_ms AS CHAR),CAST(complete_algorithm_ms AS CHAR),"
                + "CAST(output_io_ms AS CHAR),CAST(total_execution_ms AS CHAR),CAST(peak_sampled_jvm_heap_bytes AS CHAR) FROM MINING_RUN WHERE run_id=?",
                (rs, row) -> { var values = new TreeMap<String, String>(); for (int i = 1; i <= 10; i++) values.put("c" + i, rs.getString(i)); return values; }, id);
    }

    void retained(Outcome outcome) {
        assertSame(outcome, provider.outcome(outcome.runId()).orElseThrow());
        var publication = metadata.read(outcome.runId()).orElseThrow().publication();
        assertEquals("NOT_PUBLISHED", publication.status()); assertNull(publication.patternCount()); assertNull(publication.elapsedMs());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=?", Integer.class, outcome.runId()));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id=?", Integer.class, outcome.runId()));
    }

    @Test void bothAlgorithmsAndModesRoundTripEveryFieldAndFullMapsWithoutAcknowledgement() throws Exception {
        Map<String, Long> reference = null;
        for (var algorithm : MiningAlgorithm.values()) for (var mode : List.of("ABSOLUTE", "PERCENT")) {
            var outcome = run(algorithm, mode, mode.equals("ABSOLUTE") ? .1 : 1);
            var before = metrics(outcome.runId()); int retained = provider.retainedCount();
            var batch = service.prepare(outcome); assertFalse(batch.rows().isEmpty());
            assertEquals(PatternPersistenceRepository.Result.INSERTED, service.persist(outcome));
            var actual = service.verify(outcome); assertEquals(batch.rows(), actual);
            var expectedMap = new TreeMap<String, Long>(); outcome.result().supports().forEach((p, s) -> expectedMap.put(p.serialize(), s.longValue()));
            var actualMap = new TreeMap<String, Long>(); actual.forEach(row -> actualMap.put(row.canonical(), row.support()));
            assertEquals(expectedMap, actualMap); assertEquals(outcome.result().patternCount(), count(outcome.runId()));
            if (reference == null) reference = actualMap; else assertEquals(reference, actualMap);
            // JPA mapping reads the actual production writer's complete rows.
            var entities = patterns.findByRun_RunIdOrderBySupportDescWeightDesc(outcome.runId());
            assertEquals(actual.size(), entities.size());
            for (var entity : entities) {
                var row = actual.stream().filter(r -> r.hash().equals(entity.getCanonicalPatternHash())).findFirst().orElseThrow();
                assertEquals(row.canonical(), entity.getCanonicalPattern()); assertEquals(row.support(), entity.getSupport());
                assertEquals(row.ratio(), entity.getSupportRatio()); assertEquals(row.weightedSupport(), entity.getWeightedSupport());
                assertEquals(row.itemCount(), entity.getPatternLength()); assertEquals(row.itemsetCount(), entity.getItemsetCount());
                assertEquals(row.weight(), entity.getWeight()); assertEquals(row.policy(), entity.getPatternWeightPolicy());
            }
            assertEquals(PatternPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
            assertEquals(before, metrics(outcome.runId())); assertEquals(retained, provider.retainedCount()); retained(outcome);
        }
    }

    @Test void emptyEngineResultRemainsUnpublishedAndCannotBeConfusedWithCompletePublication() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(algorithm, "ABSOLUTE", 100);
            assertTrue(outcome.result().supports().isEmpty());
            assertEquals(PatternPersistenceRepository.Result.EMPTY, service.persist(outcome));
            assertTrue(service.verify(outcome).isEmpty()); retained(outcome);
        }
    }

    @Test void controlledMidInsertFailureRollsBackWholeBatchAndAllowsExactRetry() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1);
        var batch = service.prepare(outcome); assertTrue(batch.rows().size() > 1);
        String rejected = batch.rows().get(1).hash(); // Hash is generated 64-hex, never untrusted SQL.
        jdbc.execute("CREATE TRIGGER reject_pattern BEFORE INSERT ON PATTERN FOR EACH ROW BEGIN IF NEW.canonical_pattern_hash='"
                + rejected + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled failure'; END IF; END");
        try { assertThrows(RuntimeException.class, () -> service.persist(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_pattern"); }
        assertEquals(0, count(outcome.runId())); retained(outcome);
        assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(PatternPersistenceRepository.Result.INSERTED, service.persist(outcome));
        assertEquals(batch.rows(), service.verify(outcome));
    }

    @ParameterizedTest @ValueSource(strings = {"canonical", "support", "weight", "policy", "partial", "unsigned"})
    void conflictingOrPartialExistingRowsNeverGetSilentlyReplaced(String kind) throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); service.persist(outcome);
        Long id = jdbc.queryForObject("SELECT MIN(pattern_id) FROM PATTERN WHERE run_id=?", Long.class, outcome.runId());
        switch (kind) {
            case "canonical" -> jdbc.update("UPDATE PATTERN SET canonical_pattern='<(999)>' WHERE pattern_id=?", id);
            case "support" -> jdbc.update("UPDATE PATTERN SET support_abs=support_abs+1 WHERE pattern_id=?", id);
            case "weight" -> jdbc.update("UPDATE PATTERN SET pattern_weight=.987 WHERE pattern_id=?", id);
            case "policy" -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy='UNKNOWN' WHERE pattern_id=?", id);
            case "partial" -> jdbc.update("DELETE FROM PATTERN WHERE pattern_id=?", id);
            case "unsigned" -> jdbc.update("UPDATE PATTERN SET support_abs=18446744073709551615 WHERE pattern_id=?", id);
        }
        var before = jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id", outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome));
        assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(before, jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id", outcome.runId()));
        retained(outcome);
    }

    @Test void staleProvenanceStatusAndConfigurationBlockWritesEvenAfterProjection() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); var batch = service.prepare(outcome);
        var tx = new TransactionTemplate(transactions);
        String provenance = jdbc.queryForObject("SELECT CAST(provenance_json AS CHAR) FROM RUN_PROVENANCE WHERE run_id=?", String.class, outcome.runId());
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?", "e".repeat(64), outcome.runId());
        assertThrows(ApplicationException.class, () -> tx.execute(s -> repository.persist(batch)));
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=? WHERE run_id=?", provenance, outcome.runId());
        for (String mutation : List.of("status='FAILED'", "mean_w=.123", "pattern_count=999", "algorithm='WSPM_BITPRUNE'")) {
            String original = mutation.startsWith("status") ? "status='SUCCEEDED'" : mutation.startsWith("mean_w")
                    ? "mean_w=" + outcome.result().globalMeanWeight() : mutation.startsWith("pattern_count")
                    ? "pattern_count=" + outcome.result().patternCount() : "algorithm='WSPM_PRETREE'";
            try {
                jdbc.update("UPDATE MINING_RUN SET " + mutation + " WHERE run_id=?", outcome.runId());
                assertThrows(ApplicationException.class, () -> tx.execute(s -> repository.persist(batch)));
            } finally { jdbc.update("UPDATE MINING_RUN SET " + original + " WHERE run_id=?", outcome.runId()); }
        }
        assertEquals(0, count(outcome.runId())); retained(outcome);
    }

    @Test void tamperedArtifactBlocksPreparationAndDatabaseRemainsUnchanged() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1);
        Path path = storage.resolve(outcome.persistenceContext().provenance().binding().publication().sequence().reference());
        byte[] original = Files.readAllBytes(path);
        try {
            Files.writeString(path, "1 -1 -2\n", StandardCharsets.UTF_8);
            assertThrows(ApplicationException.class, () -> service.persist(outcome));
            assertEquals(0, count(outcome.runId())); retained(outcome);
        } finally { Files.write(path, original); }
    }

    @Test void repositoryMustJoinTransactionAndOuterRollbackIncludesItsRows() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); var batch = service.prepare(outcome);
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> repository.persist(batch));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThrows(IllegalStateException.class, () -> service.prepare(outcome));
            repository.persist(batch); tx.setRollbackOnly();
        });
        assertEquals(0, count(outcome.runId())); retained(outcome);
    }

    @Test void concurrentDuplicateDeliverySerializesOnRunAndVerifiesSameRows() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.persist(outcome)); var b = pool.submit(() -> service.persist(outcome));
            assertEquals(Set.of(PatternPersistenceRepository.Result.INSERTED, PatternPersistenceRepository.Result.VERIFIED_EXISTING),
                    Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
        }
        assertEquals(outcome.result().patternCount(), count(outcome.runId())); retained(outcome);
    }

    @Test void committedRowsSurviveLostCompletionSignalAndReconcileWithoutReinsertion() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); var batch = service.prepare(outcome);
        // Controlled post-commit delivery failure, not a claim of a physical network fault.
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            repository.persist(batch);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { throw new IllegalStateException("controlled completion signal loss"); }
                    });
        }));
        assertEquals(batch.rows().size(), count(outcome.runId()));
        var ids = jdbc.queryForList("SELECT pattern_id FROM PATTERN WHERE run_id=? ORDER BY pattern_id", Long.class, outcome.runId());
        assertEquals(batch.rows(), service.verify(outcome));
        assertEquals(PatternPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
        assertEquals(ids, jdbc.queryForList("SELECT pattern_id FROM PATTERN WHERE run_id=? ORDER BY pattern_id", Long.class, outcome.runId()));
        retained(outcome);
    }

    @Test void missingMetadataAndAlreadyCommittedPublicationBlockWriter() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1);
        jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED',manifest_version='SCHEMA_PROBE_ONLY',result_sha256=?,"
                + "pattern_count=0,pattern_item_count=0,run_item_count=0,pattern_weight_policy='SCHEMA_PROBE_ONLY',publication_elapsed_ms=0,"
                + "committed_at=UTC_TIMESTAMP(6) WHERE run_id=?", "a".repeat(64), outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome)); assertEquals(0, count(outcome.runId()));
        jdbc.update("DELETE FROM RUN_RESULT_PUBLICATION WHERE run_id=?", outcome.runId());
        jdbc.update("DELETE FROM RUN_PROVENANCE WHERE run_id=?", outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome)); assertEquals(0, count(outcome.runId()));
        assertSame(outcome, provider.outcome(outcome.runId()).orElseThrow());
    }

    @Test void generatedUnsignedIdsOutsideSignedApplicationRangeRollBackWithoutNarrowing() throws Exception {
        var outcome = run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1);
        try {
            jdbc.execute("ALTER TABLE PATTERN AUTO_INCREMENT=9223372036854775808");
            assertThrows(ApplicationException.class, () -> service.persist(outcome));
            assertEquals(0, count(outcome.runId())); retained(outcome);
        } finally {
            long next = jdbc.queryForObject("SELECT COALESCE(MAX(pattern_id),0)+1 FROM PATTERN", Long.class);
            jdbc.execute("ALTER TABLE PATTERN AUTO_INCREMENT=" + next);
        }
    }
}
