package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
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
import vn.edu.wspm.backend.repositories.PatternItemRepository;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.services.dataset.validation.SnapshotValidationService;
import vn.edu.wspm.backend.services.dataset.conversion.SnapshotConversionService;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.services.run.engine.MiningAlgorithm;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.*;

/** Actual CSV/storage/conversion/managed core/MySQL. SQL writes below are labeled negative controls. */
@SpringBootTest(properties = {"spring.config.import=", "wdm.result-publication.enabled=false", "wdm.execution.enabled=true", "wdm.execution.parallelism=1", "wdm.execution.queue-capacity=64",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunItemStatisticsPersistenceIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_item_statistics")
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService snapshots;
    @Autowired SnapshotValidationService validation;
    @Autowired SnapshotConversionService conversion;
    @Autowired RunCreationService creation;
    @Autowired ManagedRunExecutionAdmission provider;
    @Autowired PatternPersistenceService parentService;
    @Autowired PatternPersistenceRepository parents;
    @Autowired TemporalPatternPersistenceService temporalService;
    @Autowired RunItemStatisticsPersistenceService service;
    @Autowired RunItemStatisticsPersistenceRepository statistics;
    @Autowired TemporalPatternPersistenceRepository temporal;
    @Autowired PatternItemRepository itemRepository;
    @Autowired RunMetadataRepository metadata;
    @Autowired PlatformTransactionManager transactions;
    static String snapshot;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("wdm.execution.enabled", () -> true);
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load(); flyway.migrate(); flyway.validate();
        assertEquals("5", flyway.info().current().getVersion().getVersion());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("wdm.snapshot-storage.root", () -> storage.toString());
        AuthTestKeys.properties(p); p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
    }

    @BeforeEach void pipeline() throws Exception {
        if (snapshot != null) return;
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('temporal-cat','Synthetic','temporal-cat')");
        for (int i = 1; i <= 3; i++) {
            String id = "rt-p" + i;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'temporal-cat',999)", id, id, id, id);
        }
        snapshot = snapshots.acquire(new MockMultipartFile("file", "temporal.csv", "text/csv",
                new ClassPathResource("wdm/csv-v1/round-trip.csv").getContentAsByteArray())).snapshotId();
        assertEquals("VALID", validation.validate(snapshot).validationStatus()); conversion.convert(snapshot);
    }

    Outcome run(MiningAlgorithm algorithm, String mode, double threshold) throws Exception {
        String id = creation.create(new RunCreateRequest(snapshot, algorithm.name(), mode, threshold)).runId();
        var result = provider.completion(id).orElseThrow().toCompletableFuture().get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!result.lifecycleConfirmed() && System.nanoTime() < deadline) { Thread.sleep(20); result = provider.outcome(id).orElseThrow(); }
        assertTrue(result.lifecycleConfirmed()); assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, result.state());
        assertEquals(0, parentCount(id)); assertEquals(0, items(id).size()); return result;
    }
    Outcome run() throws Exception { return run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); }
    int parentCount(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id=?", Integer.class, id); }
    List<Map<String, Object>> items(String id) {
        return jdbc.queryForList("SELECT i.* FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id"
                + " WHERE p.run_id=? ORDER BY i.pattern_id,i.itemset_order,i.item_order", id);
    }
    List<Map<String, Object>> parentRows(String id) {
        return jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id", id);
    }
    Map<String, Long> expected(Outcome outcome) {
        var map = new TreeMap<String, Long>(); outcome.result().supports().forEach((p, s) -> map.put(p.serialize(), s.longValue())); return map;
    }
    void retained(Outcome outcome) {
        assertSame(outcome, provider.outcome(outcome.runId()).orElseThrow());
        var publication = metadata.read(outcome.runId()).orElseThrow().publication();
        assertEquals("NOT_PUBLISHED", publication.status()); assertNull(publication.patternCount()); assertNull(publication.elapsedMs());

    }

    List<Map<String, Object>> stats(String id) {
        return jdbc.queryForList("SELECT * FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id", id);
    }

    @Test void realPipelineBothAlgorithmsModesFullReadbackAndRunRegistryRemainUnchanged() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) for (String mode : List.of("ABSOLUTE", "PERCENT")) {
            var outcome = run(algorithm, mode, mode.equals("ABSOLUTE") ? .1 : 1);
            var before = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId());
            int capacity = provider.retainedCount(); var batch = service.prepare(outcome);
            assertEquals(RunItemStatisticsPersistenceRepository.Result.INSERTED, service.persist(outcome));
            var actual = service.verify(outcome);
            assertEquals(batch.rows(), actual.stream().map(RunItemStatisticsProjector.StoredRow::statistics).toList());
            assertEquals(3, actual.size());
            var byProduct = new HashMap<String, RunItemStatisticsProjector.Row>();
            actual.forEach(row -> byProduct.put(row.statistics().productId(), row.statistics()));
            // Independent CSV fixture: duplicate rows/quantities coalesce; p1 repeats across A's two itemsets.
            assertEquals(4, byProduct.get("rt-p1").occurrences()); assertEquals(3, byProduct.get("rt-p1").support());
            assertEquals(3, byProduct.get("rt-p2").occurrences()); assertEquals(3, byProduct.get("rt-p2").support());
            assertEquals(1, byProduct.get("rt-p3").occurrences()); assertEquals(1, byProduct.get("rt-p3").support());
            assertEquals(.5, byProduct.get("rt-p1").weight()); assertEquals(.375, byProduct.get("rt-p2").weight());
            assertEquals(.125, byProduct.get("rt-p3").weight());
            assertEquals(expected(outcome), temporalService.verify(outcome));
            var saved = stats(outcome.runId()); var oldParents = parentRows(outcome.runId()); var oldItems = items(outcome.runId());
            assertEquals(RunItemStatisticsPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
            assertEquals(saved, stats(outcome.runId())); assertEquals(oldParents, parentRows(outcome.runId())); assertEquals(oldItems, items(outcome.runId()));
            assertEquals(before, jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId()));
            assertEquals(capacity, provider.retainedCount()); retained(outcome);
        }
    }

    @Test void emptyEngineOutputsStillPersistAllInputItems() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(algorithm, "ABSOLUTE", 100);
            assertTrue(outcome.result().supports().isEmpty());
            assertEquals(RunItemStatisticsPersistenceRepository.Result.INSERTED, service.persist(outcome));
            assertEquals(3, service.verify(outcome).size()); assertEquals(0, parentCount(outcome.runId()));
            assertTrue(items(outcome.runId()).isEmpty()); retained(outcome);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"none", "parents", "temporal"})
    void existingStagesComposeWithoutReplacingRows(String stage) throws Exception {
        var outcome = run();
        if (stage.equals("parents")) parentService.persist(outcome);
        if (stage.equals("temporal")) temporalService.persist(outcome);
        var oldParents = parentRows(outcome.runId()); var oldItems = items(outcome.runId());
        assertThrows(ApplicationException.class, () -> service.verify(outcome));
        service.persist(outcome);
        if (!oldParents.isEmpty()) assertEquals(oldParents, parentRows(outcome.runId()));
        if (!oldItems.isEmpty()) assertEquals(oldItems, items(outcome.runId()));
        assertEquals(expected(outcome), temporalService.verify(outcome)); assertEquals(3, service.verify(outcome).size()); retained(outcome);
    }

    @Test void firstSuccessfulCaptureKeepsUnicodeAndSurvivesRenameInactiveCatalogAndRetry() throws Exception {
        var outcome = run();
        var original = jdbc.queryForMap("SELECT name,sku,status FROM PRODUCT WHERE product_id='rt-p1'");
        String name = " 😀".repeat(100), sku = "😀".repeat(80);
        try {
            jdbc.update("UPDATE PRODUCT SET name=?,sku=? WHERE product_id='rt-p1'", name, sku);
            service.persist(outcome); var saved = service.verify(outcome);
            var row = saved.stream().filter(r -> r.statistics().productId().equals("rt-p1")).findFirst().orElseThrow();
            assertEquals(name, row.name()); assertEquals(sku, row.sku()); assertNotEquals("OLD-SKU", row.sku());
            jdbc.update("UPDATE PRODUCT SET name='renamed',sku='new-sku',status='INACTIVE' WHERE product_id='rt-p1'");
            assertEquals(RunItemStatisticsPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
            assertEquals(saved, service.verify(outcome));
        } finally { jdbc.update("UPDATE PRODUCT SET name=?,sku=?,status=? WHERE product_id='rt-p1'",
                original.get("name"), original.get("sku"), original.get("status")); }
    }

    @ParameterizedTest @ValueSource(strings = {"name", "sku"})
    void blankCatalogTextRejectsWholeTransactionWithoutFakeFallback(String field) throws Exception {
        var outcome = run(); String original = jdbc.queryForObject("SELECT " + field + " FROM PRODUCT WHERE product_id='rt-p1'", String.class);
        try {
            jdbc.update("UPDATE PRODUCT SET " + field + "=' ' WHERE product_id='rt-p1'");
            assertThrows(ApplicationException.class, () -> service.persist(outcome));
            assertTrue(stats(outcome.runId()).isEmpty()); assertTrue(items(outcome.runId()).isEmpty()); assertEquals(0, parentCount(outcome.runId()));
        } finally { jdbc.update("UPDATE PRODUCT SET " + field + "=? WHERE product_id='rt-p1'", original); }
    }

    @ParameterizedTest @ValueSource(strings = {"none", "parents", "temporal"})
    void midStatisticsFailureRollsBackNewRowsAndRetryCapturesCurrentCatalog(String stage) throws Exception {
        var outcome = run();
        if (stage.equals("parents")) parentService.persist(outcome);
        if (stage.equals("temporal")) temporalService.persist(outcome);
        var oldParents = parentRows(outcome.runId()); var oldItems = items(outcome.runId());
        jdbc.execute("CREATE TRIGGER reject_statistics BEFORE INSERT ON RUN_ITEM_STATISTIC FOR EACH ROW BEGIN IF"
                + " (SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id='" + outcome.runId()
                + "')>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled statistics failure'; END IF; END");
        try { assertThrows(RuntimeException.class, () -> service.persist(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_statistics"); }
        assertEquals(oldParents, parentRows(outcome.runId())); assertEquals(oldItems, items(outcome.runId()));
        assertTrue(stats(outcome.runId()).isEmpty());
        String original = jdbc.queryForObject("SELECT name FROM PRODUCT WHERE product_id='rt-p1'", String.class);
        try {
            jdbc.update("UPDATE PRODUCT SET name='captured after rollback' WHERE product_id='rt-p1'");
            service.persist(outcome);
            assertEquals("captured after rollback", service.verify(outcome).stream()
                    .filter(row -> row.statistics().productId().equals("rt-p1")).findFirst().orElseThrow().name());
        } finally { jdbc.update("UPDATE PRODUCT SET name=? WHERE product_id='rt-p1'", original); }
        assertEquals(expected(outcome), temporalService.verify(outcome)); retained(outcome);
    }

    @ParameterizedTest @ValueSource(strings = {"partial", "extra", "mapping", "occurrence", "support", "weight", "null-weight",
            "unsigned-item", "unsigned-occurrence", "unsigned-support", "blank-name", "blank-sku"})
    void partialOrConflictingStatisticsAreNeverRepaired(String kind) throws Exception {
        var outcome = run(); service.persist(outcome);
        int item = ((Number) stats(outcome.runId()).getFirst().get("item_id")).intValue();
        jdbc.update("INSERT IGNORE INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('extra-stat-product','extra-stat-sku','Extra','extra-stat-slug','temporal-cat',999)");
        String predicate = " WHERE run_id=? AND item_id=?";
        switch (kind) {
            case "partial" -> jdbc.update("DELETE FROM RUN_ITEM_STATISTIC" + predicate, outcome.runId(), item);
            case "extra" -> jdbc.update("INSERT INTO RUN_ITEM_STATISTIC VALUES(?,99999,'extra-stat-product',1,1,0.1,'Extra','extra-stat-sku')", outcome.runId());
            case "mapping" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET product_id='extra-stat-product'" + predicate, outcome.runId(), item);
            case "occurrence" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET occurrence_count=occurrence_count+1" + predicate, outcome.runId(), item);
            case "support" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET sequence_support=0" + predicate, outcome.runId(), item);
            case "weight" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_weight=0.7" + predicate, outcome.runId(), item);
            case "null-weight" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_weight=NULL" + predicate, outcome.runId(), item);
            case "unsigned-item" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_id=4294967295" + predicate, outcome.runId(), item);
            case "unsigned-occurrence" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET occurrence_count=18446744073709551615" + predicate, outcome.runId(), item);
            case "unsigned-support" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET sequence_support=18446744073709551615" + predicate, outcome.runId(), item);
            case "blank-name" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot=' '" + predicate, outcome.runId(), item);
            case "blank-sku" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET sku_snapshot=' '" + predicate, outcome.runId(), item);
        }
        var before = stats(outcome.runId()); var oldParents = parentRows(outcome.runId()); var oldItems = items(outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome)); assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(before, stats(outcome.runId())); assertEquals(oldParents, parentRows(outcome.runId())); assertEquals(oldItems, items(outcome.runId())); retained(outcome);
    }

    @Test void concurrentDeliveryAndLostPostCommitSignalReconcileAllThreeTables() throws Exception {
        var outcome = run();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.persist(outcome)); var b = pool.submit(() -> service.persist(outcome));
            assertEquals(Set.of(RunItemStatisticsPersistenceRepository.Result.INSERTED, RunItemStatisticsPersistenceRepository.Result.VERIFIED_EXISTING),
                    Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
        }
        var other = run(); var batch = service.prepare(other);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            parents.persist(batch.temporal().parents()); temporal.persist(batch.temporal()); statistics.persist(batch);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { throw new IllegalStateException("controlled signal loss"); }
                    });
        }));
        var before = stats(other.runId()); var oldParents = parentRows(other.runId()); var oldItems = items(other.runId());
        assertEquals(3, service.verify(other).size());
        assertEquals(RunItemStatisticsPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(other));
        assertEquals(before, stats(other.runId())); assertEquals(oldParents, parentRows(other.runId())); assertEquals(oldItems, items(other.runId())); retained(other);
    }

    @Test void mandatoryTransactionOuterRollbackAndStaleProvenanceAreEnforced() throws Exception {
        var outcome = run(); var batch = service.prepare(outcome);
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> statistics.persist(batch));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> statistics.verify(batch));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThrows(IllegalStateException.class, () -> service.prepare(outcome));
            parents.persist(batch.temporal().parents()); temporal.persist(batch.temporal()); statistics.persist(batch); tx.setRollbackOnly();
        });
        assertEquals(0, parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); assertTrue(stats(outcome.runId()).isEmpty());
        temporalService.persist(outcome);
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?", "e".repeat(64), outcome.runId());
        assertThrows(ApplicationException.class, () -> new TransactionTemplate(transactions).execute(tx -> statistics.persist(batch)));
        assertTrue(stats(outcome.runId()).isEmpty());
    }

    @Test void actualForeignKeyRejectsUnknownProductAndArtifactTamperPreventsAnyWrite() throws Exception {
        var outcome = run(); service.persist(outcome);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE RUN_ITEM_STATISTIC SET product_id='missing-product' WHERE run_id=?", outcome.runId()));
        var fresh = run(); Path file = storage.resolve(fresh.persistenceContext().provenance().binding().publication().mapping().reference());
        byte[] original = Files.readAllBytes(file);
        try {
            Files.writeString(file, "{}"); assertThrows(ApplicationException.class, () -> service.persist(fresh));
            assertEquals(0, parentCount(fresh.runId())); assertTrue(items(fresh.runId()).isEmpty()); assertTrue(stats(fresh.runId()).isEmpty());
        } finally { Files.write(file, original); }
    }
}
