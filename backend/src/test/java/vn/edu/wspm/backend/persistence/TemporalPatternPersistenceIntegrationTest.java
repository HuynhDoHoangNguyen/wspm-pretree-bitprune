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
@SpringBootTest(properties = {"wdm.execution.enabled=true", "wdm.execution.parallelism=1", "wdm.execution.queue-capacity=64",
        "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TemporalPatternPersistenceIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_temporal_persistence")
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
    @Autowired TemporalPatternPersistenceService service;
    @Autowired TemporalPatternPersistenceRepository temporal;
    @Autowired PatternItemRepository itemRepository;
    @Autowired RunMetadataRepository metadata;
    @Autowired PlatformTransactionManager transactions;
    static String snapshot;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("wdm.execution.enabled", () -> true);
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load(); flyway.migrate(); flyway.validate();
        assertEquals("4", flyway.info().current().getVersion().getVersion());
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
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id=?", Integer.class, outcome.runId()));
    }

    @Test void bothAlgorithmsAndModesRoundTripFullMapsEveryPositionAndJpaReadOrder() throws Exception {
        Map<String, Long> reference = null;
        for (var algorithm : MiningAlgorithm.values()) for (String mode : List.of("ABSOLUTE", "PERCENT")) {
            var outcome = run(algorithm, mode, mode.equals("ABSOLUTE") ? .1 : 1);
            var metrics = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId());
            var batch = service.prepare(outcome); int used = provider.retainedCount();
            assertEquals(TemporalPatternPersistenceRepository.Result.INSERTED, service.persist(outcome));
            var actual = service.verify(outcome); assertEquals(expected(outcome), actual);
            if (reference == null) reference = actual; else assertEquals(reference, actual);
            assertEquals(batch.rowCount(), items(outcome.runId()).size());
            var ids = new TransactionTemplate(transactions).execute(tx -> parents.verifyWithIds(batch.parents()));
            for (var projected : batch.patterns()) {
                var entities = itemRepository.findByPatternIdOrderByItemsetOrderAscItemOrderAsc(ids.get(projected.parent().hash()));
                assertEquals(projected.items().size(), entities.size());
                for (int i = 0; i < entities.size(); i++) {
                    var entity = entities.get(i); var row = projected.items().get(i);
                    assertEquals(row.itemsetOrder(), entity.getItemsetOrder()); assertEquals(row.itemOrder(), entity.getItemOrder());
                    assertEquals(row.itemId(), entity.getItemId()); assertEquals(ids.get(projected.parent().hash()), entity.getPatternId());
                }
            }
            var before = parentRows(outcome.runId()); var children = items(outcome.runId());
            assertEquals(TemporalPatternPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
            assertEquals(before, parentRows(outcome.runId())); assertEquals(children, items(outcome.runId()));
            assertEquals(metrics, jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId()));
            assertEquals(used, provider.retainedCount()); retained(outcome);
        }
    }

    @Test void acceptedParentOnlyStageCanGainAllTemporalRowsWithoutReplacingParentIds() throws Exception {
        var outcome = run(); parentService.persist(outcome); var before = parentRows(outcome.runId());
        assertTrue(items(outcome.runId()).isEmpty()); assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(TemporalPatternPersistenceRepository.Result.INSERTED, service.persist(outcome));
        assertEquals(before, parentRows(outcome.runId())); assertEquals(expected(outcome), service.verify(outcome)); retained(outcome);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void midChildInsertFailureRollsBackNewParentsAndPreservesExistingParents(boolean staged) throws Exception {
        var outcome = run(); if (staged) parentService.persist(outcome); var before = parentRows(outcome.runId());
        // Test-owned trigger fails only after one temporal row of this actual run was inserted.
        jdbc.execute("CREATE TRIGGER reject_temporal BEFORE INSERT ON PATTERN_ITEM FOR EACH ROW BEGIN IF"
                + " (SELECT COUNT(*) FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id='"
                + outcome.runId() + "')>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled temporal failure'; END IF; END");
        try { assertThrows(RuntimeException.class, () -> service.persist(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_temporal"); }
        assertEquals(before, parentRows(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); retained(outcome);
        assertEquals(TemporalPatternPersistenceRepository.Result.INSERTED, service.persist(outcome));
        assertEquals(expected(outcome), service.verify(outcome));
    }

    @ParameterizedTest @ValueSource(strings = {"partial", "set-gap", "item-gap", "unknown-item", "unsigned-item", "unsigned-set", "unsigned-order"})
    void malformedOrPartialExistingTemporalRowsAreNeverRepaired(String kind) throws Exception {
        var outcome = run(); service.persist(outcome);
        var first = items(outcome.runId()).getFirst();
        Object id = first.get("pattern_id"), set = first.get("itemset_order"), order = first.get("item_order");
        String predicate = " WHERE pattern_id=? AND itemset_order=? AND item_order=?";
        switch (kind) {
            case "partial" -> jdbc.update("DELETE FROM PATTERN_ITEM" + predicate, id, set, order);
            case "set-gap" -> jdbc.update("UPDATE PATTERN_ITEM SET itemset_order=1000" + predicate, id, set, order);
            case "item-gap" -> jdbc.update("UPDATE PATTERN_ITEM SET item_order=1000" + predicate, id, set, order);
            case "unknown-item" -> jdbc.update("UPDATE PATTERN_ITEM SET item_id=2147483647" + predicate, id, set, order);
            case "unsigned-item" -> jdbc.update("UPDATE PATTERN_ITEM SET item_id=4294967295" + predicate, id, set, order);
            case "unsigned-set" -> jdbc.update("UPDATE PATTERN_ITEM SET itemset_order=4294967295" + predicate, id, set, order);
            case "unsigned-order" -> jdbc.update("UPDATE PATTERN_ITEM SET item_order=4294967295" + predicate, id, set, order);
        }
        var before = items(outcome.runId()); var parentBefore = parentRows(outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome)); assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(before, items(outcome.runId())); assertEquals(parentBefore, parentRows(outcome.runId())); retained(outcome);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true}) void duplicateOrDescendingItemsWithinASetAreRejected(boolean descending) throws Exception {
        var outcome = run(); service.persist(outcome);
        var group = jdbc.queryForMap("SELECT i.pattern_id,i.itemset_order FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id"
                + " WHERE p.run_id=? GROUP BY i.pattern_id,i.itemset_order HAVING COUNT(*)>=2 LIMIT 1", outcome.runId());
        var values = jdbc.queryForList("SELECT item_id FROM PATTERN_ITEM WHERE pattern_id=? AND itemset_order=? ORDER BY item_order",
                Integer.class, group.get("pattern_id"), group.get("itemset_order"));
        jdbc.update("UPDATE PATTERN_ITEM SET item_id=CASE WHEN item_order=1 THEN ? ELSE ? END WHERE pattern_id=? AND itemset_order=? AND item_order IN (1,2)",
                descending ? values.get(1) : values.get(0), values.get(0), group.get("pattern_id"), group.get("itemset_order"));
        var before = items(outcome.runId()); assertThrows(ApplicationException.class, () -> service.persist(outcome));
        assertThrows(ApplicationException.class, () -> service.verify(outcome)); assertEquals(before, items(outcome.runId())); retained(outcome);
    }

    @Test void rowsMovedToAnotherRunsParentInvalidateBothStructuresWithoutCrossRunRepair() throws Exception {
        var a = run(); var b = run(); service.persist(a); service.persist(b);
        var row = items(a.runId()).getFirst();
        Object otherId = parentRows(b.runId()).getFirst().get("pattern_id");
        jdbc.update("UPDATE PATTERN_ITEM SET pattern_id=?,itemset_order=1000 WHERE pattern_id=? AND itemset_order=? AND item_order=?",
                otherId, row.get("pattern_id"), row.get("itemset_order"), row.get("item_order"));
        var beforeA = items(a.runId()); var beforeB = items(b.runId());
        assertThrows(ApplicationException.class, () -> service.persist(a)); assertThrows(ApplicationException.class, () -> service.persist(b));
        assertEquals(beforeA, items(a.runId())); assertEquals(beforeB, items(b.runId())); retained(a); retained(b);
    }

    @Test void emptyOutcomesKeepBothTablesEmptyAndRemainUnpublished() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(algorithm, "ABSOLUTE", 100);
            assertEquals(TemporalPatternPersistenceRepository.Result.EMPTY, service.persist(outcome));
            assertTrue(service.verify(outcome).isEmpty()); assertEquals(0, parentCount(outcome.runId())); retained(outcome);
        }
    }

    @Test void concurrentDeliverySerializesAndKeepsIdsAndRowsStable() throws Exception {
        var outcome = run();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> service.persist(outcome)); var b = pool.submit(() -> service.persist(outcome));
            assertEquals(Set.of(TemporalPatternPersistenceRepository.Result.INSERTED, TemporalPatternPersistenceRepository.Result.VERIFIED_EXISTING),
                    Set.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)));
        }
        assertEquals(expected(outcome), service.verify(outcome)); retained(outcome);
    }

    @Test void postCommitSignalLossReconcilesBothTablesWithoutReinsertion() throws Exception {
        var outcome = run(); var batch = service.prepare(outcome);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            parents.persist(batch.parents()); temporal.persist(batch);
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { throw new IllegalStateException("controlled signal loss"); }
                    });
        }));
        var before = parentRows(outcome.runId()); var childBefore = items(outcome.runId());
        assertEquals(expected(outcome), service.verify(outcome));
        assertEquals(TemporalPatternPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
        assertEquals(before, parentRows(outcome.runId())); assertEquals(childBefore, items(outcome.runId())); retained(outcome);
    }

    @Test void requiredTransactionAndOuterRollbackCoverBothTables() throws Exception {
        var outcome = run(); var batch = service.prepare(outcome);
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> temporal.persist(batch));
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class, () -> parents.verifyWithIds(batch.parents()));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThrows(IllegalStateException.class, () -> service.prepare(outcome));
            parents.persist(batch.parents()); temporal.persist(batch); tx.setRollbackOnly();
        });
        assertEquals(0, parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); retained(outcome);
    }

    @Test void staleParentAndProvenanceAfterPreparationCannotGainTemporalRows() throws Exception {
        var outcome = run(); parentService.persist(outcome); var batch = service.prepare(outcome);
        String original = jdbc.queryForObject("SELECT CAST(provenance_json AS CHAR) FROM RUN_PROVENANCE WHERE run_id=?", String.class, outcome.runId());
        try {
            jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?", "e".repeat(64), outcome.runId());
            assertThrows(ApplicationException.class, () -> new TransactionTemplate(transactions).execute(tx -> temporal.persist(batch)));
        } finally { jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=? WHERE run_id=?", original, outcome.runId()); }
        jdbc.update("UPDATE PATTERN SET support_abs=support_abs+1 WHERE run_id=?", outcome.runId());
        assertThrows(ApplicationException.class, () -> service.persist(outcome));
        assertTrue(items(outcome.runId()).isEmpty()); retained(outcome);
    }

    @Test void tamperedMappingBlocksPreparationWithoutCreatingParentsOrChildren() throws Exception {
        var outcome = run(); Path file = storage.resolve(outcome.persistenceContext().provenance().binding().publication().mapping().reference());
        byte[] original = Files.readAllBytes(file);
        try {
            Files.writeString(file, "{}"); assertThrows(ApplicationException.class, () -> service.persist(outcome));
            assertEquals(0, parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); retained(outcome);
        } finally { Files.write(file, original); }
    }
}
