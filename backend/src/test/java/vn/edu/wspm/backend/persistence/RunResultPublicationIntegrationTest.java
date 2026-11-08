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
        "spring.config.import=", "wdm.result-publication.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunResultPublicationIntegrationTest {
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
    @Autowired RunResultPublicationService publication;
    @Autowired vn.edu.wspm.backend.services.run.query.RunResultQueryService queries;
    @Autowired vn.edu.wspm.backend.services.combo.PublishedMiningResultProvider combos;
    @Autowired RunResultPublicationRepository manifest;
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
        return run(snapshot, algorithm, mode, threshold);
    }
    Outcome run(String snapshotId, MiningAlgorithm algorithm, String mode, double threshold) throws Exception {
        String id = creation.create(new RunCreateRequest(snapshotId, algorithm.name(), mode, threshold)).runId();
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

    @Test void bothEnginesPublishExactMapsAndReadAfterAcknowledgement() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome=run(algorithm,"ABSOLUTE",.1);
            assertThrows(ApplicationException.class,() -> combos.requireEligible(outcome.runId()));
            String hash=publication.publish(outcome);
            assertEquals(hash,publication.publish(outcome)); // lost post-commit signal reconciliation
            var bundle=combos.requireEligible(outcome.runId());
            var actual=new TreeMap<String,Long>();
            bundle.patterns().forEach(p -> actual.put(p.pattern().canonicalPattern(),Long.parseLong(p.pattern().supportAbs())));
            assertEquals(expected(outcome),actual);
            assertEquals(3,bundle.runItems().size());
            assertTrue(bundle.result().publishedReadable()); assertTrue(bundle.result().eligibleForCombo());
            assertEquals(hash,bundle.result().resultSha256());
            assertTrue(provider.acknowledge(outcome.runId(),outcome.token()));
            assertTrue(provider.outcome(outcome.runId()).isEmpty());
            jdbc.update("UPDATE PRODUCT SET name='Current catalog renamed' WHERE product_id='rt-p1'");
            assertEquals(bundle,combos.requireEligible(outcome.runId()));
            var doc=new tools.jackson.databind.ObjectMapper().readTree(queries.export(outcome.runId(),"published"));
            assertEquals("WDM_RESULT_EXPORT_V2",doc.get("formatVersion").asString());
            assertEquals(actual.size(),doc.get("patterns").size()); assertEquals(3,doc.get("runItems").size());
            assertThrows(ApplicationException.class,() -> queries.page(outcome.runId(),"staging",null,null));
        }
    }
    @Test void multiDigitItemIdsPublishAndExportInNumericOrderForBothEngines() throws Exception {
        // Ten independent one-item sequences: exactly ten singleton patterns, each support=1.
        // The existing snapshot already allocated IDs 1..3, so this crosses the decimal 9/10 boundary.
        for (int n = 1; n <= 10; n++) {
            String product = "numeric-p" + n;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'temporal-cat',999)", product, product, product, product);
        }
        String numericSnapshot = snapshots.acquire(new MockMultipartFile("file", "numeric-item-order.csv", "text/csv",
                new ClassPathResource("wdm/csv-v1/numeric-item-order.csv").getContentAsByteArray())).snapshotId();
        assertEquals("VALID", validation.validate(numericSnapshot).validationStatus()); conversion.convert(numericSnapshot);
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(numericSnapshot, algorithm, "ABSOLUTE", .1);
            assertEquals(10, outcome.result().patternCount());
            outcome.result().supports().forEach((p, s) -> { assertEquals(1, p.size()); assertEquals(1, p.length()); assertEquals(1, s); });
            var expectedIds = service.prepare(outcome).rows().stream().map(RunItemStatisticsProjector.Row::itemId).sorted().toList();
            assertEquals(10, expectedIds.size()); assertTrue(expectedIds.stream().anyMatch(id -> id >= 10));
            String digest = publication.publish(outcome);
            assertEquals(digest, publication.publish(outcome));
            assertTrue(provider.acknowledge(outcome.runId(), outcome.token()));
            var bundle = combos.requireEligible(outcome.runId());
            assertEquals(expectedIds, bundle.runItems().stream().map(i -> i.itemId()).toList());
            var actual = new TreeMap<String, Long>();
            bundle.patterns().forEach(p -> actual.put(p.pattern().canonicalPattern(), Long.parseLong(p.pattern().supportAbs())));
            assertEquals(expected(outcome), actual);
            var doc = new tools.jackson.databind.ObjectMapper().readTree(queries.export(outcome.runId(), "published"));
            var exportedIds = new ArrayList<Integer>(); doc.get("runItems").forEach(i -> exportedIds.add(i.get("itemId").asInt()));
            assertEquals(expectedIds, exportedIds); assertEquals(10, doc.get("patterns").size());
        }
    }
    @Test void zeroPatternsAreCompleteButNotComboEligible() throws Exception {
        var outcome=run(MiningAlgorithm.WSPM_PRETREE,"ABSOLUTE",100000);
        assertEquals(0,outcome.result().patternCount()); publication.publish(outcome);
        var data=queries.publishedBundle(outcome.runId());
        assertTrue(data.result().publishedReadable()); assertFalse(data.result().eligibleForCombo());
        assertTrue(data.patterns().isEmpty()); assertEquals(3,data.runItems().size());
        assertThrows(ApplicationException.class,() -> combos.requireEligible(outcome.runId()));
        jdbc.update("UPDATE RUN_ITEM_STATISTIC SET sku_snapshot='Changed unused item' WHERE run_id=?",outcome.runId());
        assertThrows(ApplicationException.class,() -> queries.metadata(outcome.runId()));
    }
    @Test void publishedExportEnforcesWholeFileBudgetBeforeReturningBytes() throws Exception {
        var o=run();publication.publish(o);
        var jdbcReader=new vn.edu.wspm.backend.repositories.run.RunResultQueryRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var limited=new vn.edu.wspm.backend.services.run.query.RunResultQueryService(jdbcReader,new tools.jackson.databind.ObjectMapper(),4096);
        limited.publications(manifest);
        var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var failure=assertThrows(ApplicationException.class,() -> tx.execute(s -> limited.export(o.runId(),"published")));
        assertEquals("PAYLOAD_TOO_LARGE",failure.code());
        assertTrue(queries.export(o.runId(),"published").length>4096);
    }
    @Test void concurrentDeliveriesCommitOnePublication() throws Exception {
        var outcome=run();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> publication.publish(outcome)); var b=pool.submit(() -> publication.publish(outcome));
            assertEquals(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS));
        }
        assertEquals(expected(outcome).size(),parentCount(outcome.runId()));
    }
    @Test void publicationMarkerFailureRollsBackAllRows() throws Exception {
        var outcome=run();
        // Force final publication SQL failure after all three writers have run.
        jdbc.execute("CREATE TRIGGER reject_publication BEFORE UPDATE ON RUN_RESULT_PUBLICATION FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled failure'");
        try { assertThrows(RuntimeException.class,() -> publication.publish(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_publication"); }
        assertEquals(0,parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id=?",Integer.class,outcome.runId()));
        assertEquals("NOT_PUBLISHED",metadata.read(outcome.runId()).orElseThrow().publication().status());
        assertSame(outcome,provider.outcome(outcome.runId()).orElseThrow());
        publication.publish(outcome); assertTrue(queries.metadata(outcome.runId()).publishedReadable());
    }
    @ParameterizedTest @ValueSource(strings={"support","temporal","name","delete","version","provenance","extra-stat"})
    void corruptPublishedDataRejectsReadsAndConsumer(String kind) throws Exception {
        var o=run();publication.publish(o);String id=o.runId();
        switch(kind) {
            case "support" -> jdbc.update("UPDATE PATTERN SET support_abs=support_abs+1 WHERE run_id=?",id);
            case "temporal" -> jdbc.update("UPDATE PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id SET i.item_id=99999 WHERE p.run_id=?",id);
            case "name" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='Changed' WHERE run_id=?",id);
            case "delete" -> jdbc.update("DELETE FROM PATTERN_ITEM WHERE pattern_id IN (SELECT pattern_id FROM PATTERN WHERE run_id=?)",id);
            case "version" -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET manifest_version='unknown' WHERE run_id=?",id);
            case "provenance" -> jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?","e".repeat(64),id);
            case "extra-stat" -> jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id DESC LIMIT 1",id);
        }
        assertThrows(ApplicationException.class,() -> queries.publishedBundle(id));
        assertThrows(ApplicationException.class,() -> queries.export(id,"published"));
        assertThrows(ApplicationException.class,() -> combos.requireEligible(id));
        assertThrows(RuntimeException.class,() -> publication.publish(o));
        assertSame(o,provider.outcome(id).orElseThrow());
    }
    @Test void automaticConsumerPublishesThenReleasesCapacity() throws Exception {
        var o=run();int before=provider.retainedCount();
        try(var consumer=new vn.edu.wspm.backend.services.run.execution.RunResultPublicationConsumer(provider,publication)) {
            consumer.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(provider.outcome(o.runId()).isPresent() && System.nanoTime()<deadline) Thread.sleep(50);
            assertTrue(provider.outcome(o.runId()).isEmpty());
            assertTrue(provider.retainedCount()<before);
            assertTrue(queries.metadata(o.runId()).publishedReadable());
        }
    }
}
