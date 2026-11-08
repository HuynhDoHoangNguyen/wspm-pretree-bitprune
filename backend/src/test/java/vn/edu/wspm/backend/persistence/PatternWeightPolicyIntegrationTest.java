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
class PatternWeightPolicyIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_weight_policy")
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
    @Autowired vn.edu.wspm.backend.services.dataset.conversion.ConversionReadService reads;
    @Autowired tools.jackson.databind.ObjectMapper json;
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

    }

    List<Map<String, Object>> stats(String id) {
        return jdbc.queryForList("SELECT * FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id", id);
    }

    record Checked(Map<String, Long> supports, Map<String, Double> weights, Map<Integer, String> products) {}
    static void bits(double expected, Object actual) {
        assertInstanceOf(Number.class, actual);
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(((Number) actual).doubleValue()));
    }
    static int positiveInt(Object value) {
        int result = Integer.parseInt(value.toString()); assertTrue(result > 0); return result;
    }

    /** Test-only checker: raw SQL + verified artifact/core weights; NEVER calls the production weight policy/projector. */
    Checked check(Outcome outcome) throws Exception {
        var storedProvenance = json.readValue(jdbc.queryForObject(
                "SELECT CAST(provenance_json AS CHAR) FROM RUN_PROVENANCE WHERE run_id=?", String.class, outcome.runId()), RunProvenance.class);
        assertEquals(outcome.persistenceContext().provenance(), storedProvenance);
        var binding = outcome.persistenceContext().provenance().binding();
        var bundle = reads.verified(binding.publication().snapshotId());
        assertEquals(binding.publication(), bundle.result());
        var database = new vn.edu.wspm.io.SpmfSequenceParser().parse(new java.io.StringReader(
                new String(bundle.sequenceBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        var coreStats = new vn.edu.wspm.statistics.ItemStatisticsCalculator().calculate(database);
        var coreWeights = new vn.edu.wspm.statistics.ItemWeightCalculator().calculate(coreStats);
        bits(coreWeights.meanWeight().orElseThrow(), outcome.result().globalMeanWeight());
        var dictionary = json.readValue(bundle.mappingBytes(), vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.DictionaryDocument.class);
        var products = new TreeMap<Integer, String>(); dictionary.items().forEach(e -> assertNull(products.put(e.itemId(), e.productId())));
        assertEquals(coreStats.itemStatistics().keySet(), products.keySet());
        var weights = new TreeMap<Integer, Double>();
        for (var row : stats(outcome.runId())) {
            int id = positiveInt(row.get("item_id"));
            assertTrue(products.containsKey(id)); assertEquals(products.get(id), row.get("product_id"));
            var item = coreStats.itemStatistics().get(id);
            assertEquals(item.occurrenceCount(), Long.parseLong(row.get("occurrence_count").toString()));
            assertEquals(item.sequenceSupport(), Long.parseLong(row.get("sequence_support").toString()));
            bits(coreWeights.itemWeights().get(id), row.get("item_weight"));
            assertNull(weights.put(id, ((Number) row.get("item_weight")).doubleValue()));
        }
        assertEquals(products.keySet(), weights.keySet());
        String policy = PatternWeightPolicyTest.document().policyVersion();
        var supports = new TreeMap<String, Long>(); var patternWeights = new TreeMap<String, Double>();
        for (var parent : parentRows(outcome.runId())) {
            assertEquals(policy, parent.get("pattern_weight_policy"));
            var rows = jdbc.queryForList("SELECT itemset_order,item_order,item_id FROM PATTERN_ITEM WHERE pattern_id=? ORDER BY itemset_order,item_order",
                    parent.get("pattern_id"));
            assertFalse(rows.isEmpty());
            var sets = new ArrayList<List<Integer>>();
            int set = 1, order = 1, previous = 0; var current = new ArrayList<Integer>();
            for (var row : rows) {
                int s = positiveInt(row.get("itemset_order")), o = positiveInt(row.get("item_order")), id = positiveInt(row.get("item_id"));
                if (s != set) {
                    assertEquals(set + 1, s); assertFalse(current.isEmpty()); sets.add(List.copyOf(current)); current.clear();
                    set = s; order = 1; previous = 0;
                }
                assertEquals(order++, o); assertTrue(id > previous); assertTrue(weights.containsKey(id));
                current.add(id); previous = id;
            }
            assertFalse(current.isEmpty()); sets.add(List.copyOf(current));
            assertEquals(positiveInt(parent.get("itemset_count")), sets.size());
            assertEquals(positiveInt(parent.get("pattern_item_count")), rows.size());
            var canonical = new StringBuilder("<"); double total = 0;
            for (var members : sets) {
                canonical.append('('); double sum = 0;
                for (int i = 0; i < members.size(); i++) {
                    if (i > 0) canonical.append(' ');
                    canonical.append(members.get(i)); sum += weights.get(members.get(i));
                }
                canonical.append(')'); total += sum / members.size();
            }
            String text = canonical.append('>').toString(); double value = total / sets.size();
            assertEquals(text, parent.get("canonical_pattern"));
            assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8))), parent.get("canonical_pattern_hash"));
            bits(value, parent.get("pattern_weight"));
            long support = Long.parseLong(parent.get("support_abs").toString());
            assertTrue(support > 0 && support <= database.size());
            bits((double) support / database.size(), parent.get("support_ratio"));
            bits(support * outcome.result().globalMeanWeight(), parent.get("weighted_support"));
            assertTrue(outcome.result().minimumSupport().meetsThreshold((int) support, outcome.result().globalMeanWeight()));
            assertNull(supports.put(text, support)); assertNull(patternWeights.put(text, value));
        }
        assertEquals(expected(outcome), supports);
        return new Checked(Map.copyOf(supports), Map.copyOf(patternWeights), Map.copyOf(products));
    }

    String manualTarget(Checked checked) {
        var ids = new HashMap<String, Integer>(); checked.products().forEach((id, p) -> ids.put(p, id));
        int a = ids.get("rt-p1"), b = ids.get("rt-p2");
        return "<(" + Math.min(a,b) + " " + Math.max(a,b) + ")(" + a + ")>";
    }

    @Test void independentLineageForEveryPatternBothAlgorithmsAndModesAndManualFixture() throws Exception {
        Map<String, Long> reference = null;
        for (var algorithm : MiningAlgorithm.values()) for (String mode : List.of("ABSOLUTE", "PERCENT")) {
            var outcome = run(algorithm, mode, mode.equals("ABSOLUTE") ? .1 : 1);
            var runBefore = jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId()); int retained = provider.retainedCount();
            assertEquals(RunItemStatisticsPersistenceRepository.Result.INSERTED, service.persist(outcome));
            var result = check(outcome);
            assertTrue(result.weights().containsKey(manualTarget(result)));
            bits(.46875, result.weights().get(manualTarget(result))); bits(.3125, outcome.result().globalMeanWeight());
            assertNotEquals(Double.doubleToRawLongBits(.46875), Double.doubleToRawLongBits(.3125));
            if (reference == null) reference = result.supports(); else assertEquals(reference, result.supports());
            assertEquals(runBefore, jdbc.queryForMap("SELECT * FROM MINING_RUN WHERE run_id=?", outcome.runId()));
            assertEquals(retained, provider.retainedCount()); retained(outcome);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"weight", "meanw-as-weight", "unknown-policy", "case-policy", "legacy-policy", "item-weight",
            "coherent-corruption", "unknown-item", "cross-run", "missing-stat"})
    void independentCheckerAndProductionVerifyRejectCorruptionWithoutRepair(String kind) throws Exception {
        var outcome = run(); service.persist(outcome); var original = check(outcome);
        Object parentId = parentRows(outcome.runId()).getFirst().get("pattern_id");
        switch (kind) {
            case "weight" -> jdbc.update("UPDATE PATTERN SET pattern_weight=.987 WHERE pattern_id=?", parentId);
            case "meanw-as-weight" -> assertEquals(1, jdbc.update("UPDATE PATTERN SET pattern_weight=.3125 WHERE run_id=? AND canonical_pattern=?", outcome.runId(), manualTarget(original)));
            case "unknown-policy" -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy='UNKNOWN' WHERE pattern_id=?", parentId);
            case "case-policy" -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy='eq2_eq3_arithmetic_mean_v1' WHERE pattern_id=?", parentId);
            case "legacy-policy" -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy='LEGACY_V1_WEIGHT' WHERE pattern_id=?", parentId);
            case "item-weight" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_weight=.25 WHERE run_id=?", outcome.runId());
            case "coherent-corruption" -> {
                jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_weight=.25 WHERE run_id=?", outcome.runId());
                jdbc.update("UPDATE PATTERN SET pattern_weight=.25 WHERE run_id=?", outcome.runId());
            }
            case "unknown-item" -> jdbc.update("UPDATE PATTERN_ITEM SET item_id=2147483647 WHERE pattern_id=? AND itemset_order=1 AND item_order=1", parentId);
            case "cross-run" -> {
                var other = run(); service.persist(other); var row = items(outcome.runId()).getFirst();
                jdbc.update("UPDATE PATTERN_ITEM SET pattern_id=?,itemset_order=1000 WHERE pattern_id=? AND itemset_order=? AND item_order=?",
                        parentRows(other.runId()).getFirst().get("pattern_id"), row.get("pattern_id"), row.get("itemset_order"), row.get("item_order"));
            }
            case "missing-stat" -> jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id=? AND item_id=?", outcome.runId(), original.products().keySet().iterator().next());
        }
        var beforeParents = parentRows(outcome.runId()); var beforeItems = items(outcome.runId()); var beforeStats = stats(outcome.runId());
        assertThrows(AssertionError.class, () -> check(outcome));
        assertThrows(ApplicationException.class, () -> service.verify(outcome)); assertThrows(ApplicationException.class, () -> service.persist(outcome));
        assertEquals(beforeParents, parentRows(outcome.runId())); assertEquals(beforeItems, items(outcome.runId())); assertEquals(beforeStats, stats(outcome.runId())); retained(outcome);
    }

    @Test void malformedRawPositionsCannotBeHiddenByCanonicalizationInChecker() throws Exception {
        var outcome = run(); service.persist(outcome); check(outcome);
        var row = items(outcome.runId()).getFirst();
        jdbc.update("UPDATE PATTERN_ITEM SET item_order=1000 WHERE pattern_id=? AND itemset_order=? AND item_order=?",
                row.get("pattern_id"), row.get("itemset_order"), row.get("item_order"));
        assertThrows(AssertionError.class, () -> check(outcome));
        assertThrows(ApplicationException.class, () -> service.verify(outcome));
    }

    @Test void legacyNullWeightsNeverBecomeCurrentPolicyWeights() throws Exception {
        var outcome = run(); service.persist(outcome);
        jdbc.update("UPDATE PATTERN SET pattern_weight_policy='LEGACY_V1_WEIGHT' WHERE run_id=?", outcome.runId());
        jdbc.update("UPDATE RUN_ITEM_STATISTIC SET item_weight=NULL WHERE run_id=?", outcome.runId());
        var before = stats(outcome.runId());
        assertThrows(AssertionError.class, () -> check(outcome));
        assertThrows(ApplicationException.class, () -> service.persist(outcome)); assertThrows(ApplicationException.class, () -> service.verify(outcome));
        assertEquals(before, stats(outcome.runId()));
    }

    @Test void staleProvenancePreventsPolicyStagingWithoutAnyWrites() throws Exception {
        var outcome = run(); var batch = service.prepare(outcome);
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?", "e".repeat(64), outcome.runId());
        assertThrows(ApplicationException.class, () -> new TransactionTemplate(transactions).execute(tx -> {
            parents.persist(batch.temporal().parents()); temporal.persist(batch.temporal()); return statistics.persist(batch);
        }));
        assertEquals(0, parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); assertTrue(stats(outcome.runId()).isEmpty()); retained(outcome);
    }

    @Test void retryAfterCatalogRenameKeepsIdsWeightsVersionAndSnapshots() throws Exception {
        var outcome = run(); service.persist(outcome); var checked = check(outcome);
        var oldParents = parentRows(outcome.runId()); var oldItems = items(outcome.runId()); var oldStats = stats(outcome.runId());
        var catalog = jdbc.queryForMap("SELECT name,sku FROM PRODUCT WHERE product_id='rt-p1'");
        try {
            jdbc.update("UPDATE PRODUCT SET name='policy renamed',sku='policy-renamed' WHERE product_id='rt-p1'");
            assertEquals(RunItemStatisticsPersistenceRepository.Result.VERIFIED_EXISTING, service.persist(outcome));
            assertEquals(checked, check(outcome)); assertEquals(oldParents, parentRows(outcome.runId()));
            assertEquals(oldItems, items(outcome.runId())); assertEquals(oldStats, stats(outcome.runId())); retained(outcome);
        } finally { jdbc.update("UPDATE PRODUCT SET name=?,sku=? WHERE product_id='rt-p1'", catalog.get("name"), catalog.get("sku")); }
    }

    @Test void changedProvenanceInvalidatesIndependentCheckerAndExistingPolicyStage() throws Exception {
        var outcome = run(); service.persist(outcome); check(outcome);
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?", "e".repeat(64), outcome.runId());
        var beforeParents = parentRows(outcome.runId()); var beforeStats = stats(outcome.runId());
        assertThrows(AssertionError.class, () -> check(outcome));
        assertThrows(ApplicationException.class, () -> service.verify(outcome)); assertThrows(ApplicationException.class, () -> service.persist(outcome));
        assertEquals(beforeParents, parentRows(outcome.runId())); assertEquals(beforeStats, stats(outcome.runId()));
    }

    @Test void controlledFailureRollsBackWeightsVersionAndBothDependentTables() throws Exception {
        var outcome = run();
        jdbc.execute("CREATE TRIGGER reject_policy_statistics BEFORE INSERT ON RUN_ITEM_STATISTIC FOR EACH ROW BEGIN IF"
                + " (SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id='" + outcome.runId()
                + "')>0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled policy gate failure'; END IF; END");
        try { assertThrows(RuntimeException.class, () -> service.persist(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_policy_statistics"); }
        assertEquals(0, parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty()); assertTrue(stats(outcome.runId()).isEmpty());
        service.persist(outcome); check(outcome); retained(outcome);
    }

    @Test void emptyPatternResultsHaveNoInventedPolicyRowsAndKeepItemWeights() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(algorithm, "ABSOLUTE", 100); service.persist(outcome);
            var checked = check(outcome); assertTrue(checked.supports().isEmpty()); assertTrue(checked.weights().isEmpty());
            assertEquals(3, checked.products().size()); assertEquals(3, stats(outcome.runId()).size()); retained(outcome);
        }
    }
}
