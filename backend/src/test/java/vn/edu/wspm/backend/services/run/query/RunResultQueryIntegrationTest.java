package vn.edu.wspm.backend.services.run.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.controllers.api.v1.admin.RunResultController;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.services.dataset.validation.SnapshotValidationService;
import vn.edu.wspm.backend.services.dataset.conversion.SnapshotConversionService;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.RunItemStatisticsPersistenceService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

/** Real CSV/core/staging/MySQL and proxied read transactions. HTTP uses an explicit test ADMIN seam. */
@SpringBootTest(properties = {"wdm.execution.enabled=true", "wdm.execution.parallelism=1", "wdm.execution.queue-capacity=64",
        "spring.config.import=", "wdm.result-publication.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunResultQueryIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_result_query")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService snapshots;
    @Autowired SnapshotValidationService validation;
    @Autowired SnapshotConversionService conversion;
    @Autowired RunCreationService creation;
    @Autowired ManagedRunExecutionAdmission provider;
    @Autowired RunItemStatisticsPersistenceService staging;
    @Autowired RunResultQueryService service;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    MockMvc mvc;
    static String snapshot;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load(); flyway.migrate(); flyway.validate();
        assertEquals("5", flyway.info().current().getVersion().getVersion());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("wdm.snapshot-storage.root", () -> storage.toString());
        AuthTestKeys.properties(p); p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
    }
    @BeforeEach void setup() throws Exception {
        var beans = new StaticListableBeanFactory();
        beans.addBean("guard", (WdmAdminAuthorizer) request -> {
            if (request.getHeader("X-Test-Identity") == null) throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
            if (!"ADMIN".equals(request.getHeader("X-Test-Identity"))) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        });
        mvc = MockMvcBuilders.standaloneSetup(new RunResultController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter(), new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), json)).build();
        if (snapshot != null) return;
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('result-cat','Synthetic','result-cat')");
        for (int i = 1; i <= 3; i++) {
            String id = "rt-p" + i;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'result-cat',999)", id, id, id, id);
        }
        snapshot = snapshots.acquire(new MockMultipartFile("file", "result.csv", "text/csv",
                new ClassPathResource("wdm/csv-v1/round-trip.csv").getContentAsByteArray())).snapshotId();
        assertEquals("VALID", validation.validate(snapshot).validationStatus()); conversion.convert(snapshot);
    }
    Outcome run(String algorithm, String mode, double value) throws Exception {
        String id = creation.create(new RunCreateRequest(snapshot, algorithm, mode, value)).runId();
        var result = provider.completion(id).orElseThrow().toCompletableFuture().get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!result.lifecycleConfirmed() && System.nanoTime() < deadline) { Thread.sleep(20); result = provider.outcome(id).orElseThrow(); }
        assertTrue(result.lifecycleConfirmed()); return result;
    }
    Outcome staged() throws Exception {
        var outcome = run("WSPM_PRETREE", "ABSOLUTE", .1); staging.persist(outcome); return outcome;
    }
    String path(String id) { return "/api/v1/admin/mining-runs/" + id; }
    JsonNode read(String uri) throws Exception {
        var response = mvc.perform(get(uri).header("X-Test-Identity", "ADMIN")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
    List<List<Map<String, Object>>> state(String id) {
        return List.of(jdbc.queryForList("SELECT * FROM MINING_RUN WHERE run_id=?", id),
                jdbc.queryForList("SELECT * FROM RUN_PROVENANCE WHERE run_id=?", id),
                jdbc.queryForList("SELECT * FROM RUN_RESULT_PUBLICATION WHERE run_id=?", id),
                jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id", id),
                jdbc.queryForList("SELECT i.* FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=? ORDER BY i.pattern_id,i.itemset_order,i.item_order", id),
                jdbc.queryForList("SELECT * FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id", id));
    }
    @Test void bothAlgorithmsAndModesRoundTripCompleteMapsThroughPagesDetailsAndExport() throws Exception {
        for (String algorithm : List.of("WSPM_PRETREE", "WSPM_BITPRUNE")) for (String mode : List.of("ABSOLUTE", "PERCENT")) {
            var outcome = run(algorithm, mode, mode.equals("ABSOLUTE") ? .1 : 10);
            String id = outcome.runId();
            var beforeStage = read(path(id) + "/results").get("data");
            assertEquals("0", beforeStage.get("storedPatternCount").asString());
            assertEquals("UNVERIFIED", beforeStage.get("completeness").asString());
            staging.persist(outcome); var before = state(id); int retained = provider.retainedCount();
            var expected = new TreeMap<String, String>(); outcome.result().supports().forEach((p, s) -> expected.put(p.serialize(), s.toString()));
            var actual = new TreeMap<String, String>();
            for (int page = 1; ; page++) {
                var data = read(path(id) + "/patterns?view=staging&size=1&page=" + page).get("data");
                assertEquals(Integer.toString(expected.size()), data.get("total").asString());
                if (data.get("items").isEmpty()) break;
                var parent = data.get("items").get(0);
                assertNull(actual.put(parent.get("canonicalPattern").asString(), parent.get("supportAbs").asString()));
                var detail = read(path(id) + "/patterns/" + parent.get("patternId").asString() + "?view=staging").get("data");
                assertEquals(parent, detail.get("pattern"));
                var canonical = new StringBuilder("<");
                for (var set : detail.get("itemsets")) {
                    canonical.append('('); int index = 0;
                    for (var item : set.get("items")) { if (index++ > 0) canonical.append(' '); canonical.append(item.get("itemId").asInt()); }
                    canonical.append(')');
                }
                assertEquals(parent.get("canonicalPattern").asString(), canonical.append('>').toString());
            }
            assertEquals(expected, actual);
            var exported = read(path(id) + "/patterns/export?view=staging");
            var exportMap = new TreeMap<String, String>();
            for (var detail : exported.get("patterns")) assertNull(exportMap.put(detail.get("pattern").get("canonicalPattern").asString(), detail.get("pattern").get("supportAbs").asString()));
            assertEquals(expected, exportMap); assertFalse(exported.get("result").get("eligibleForCombo").asBoolean());
            assertFalse(exported.toString().contains("rawReference")); assertFalse(exported.toString().contains("sequenceBytes"));
            mvc.perform(get(path(id) + "/patterns").header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict());
            assertEquals(before, state(id)); assertEquals(retained, provider.retainedCount()); assertSame(outcome, provider.outcome(id).orElseThrow());
        }
    }
    @Test void emptyEngineOutputRemainsUnpublishedAndKeepsItemStatistics() throws Exception {
        var outcome = run("WSPM_BITPRUNE", "ABSOLUTE", 100); staging.persist(outcome);
        assertTrue(outcome.result().supports().isEmpty());
        var metadata = read(path(outcome.runId()) + "/results").get("data");
        assertEquals("0", metadata.get("storedPatternCount").asString()); assertEquals("3", metadata.get("storedRunItemCount").asString());
        assertEquals("UNVERIFIED", metadata.get("completeness").asString());
        assertTrue(read(path(outcome.runId()) + "/patterns/export?view=staging").get("patterns").isEmpty());
    }
    @Test void rankTiesUseNumericIdAndSupportEntireUnsignedIdRange() throws Exception {
        var outcome = staged(); String run = outcome.runId();
        var ids = jdbc.queryForList("SELECT CAST(pattern_id AS CHAR) FROM PATTERN WHERE run_id=? ORDER BY pattern_id", String.class, run);
        assertTrue(ids.size() >= 2);
        // Move one parent and its children to an ID beyond signed long. Test-owned corruption fixture only.
        String old = ids.getFirst(), high = "18446744073709551614";
        movePatternId(old, high);
        try {
            var detail = read(path(run) + "/patterns/" + high + "?view=staging").get("data");
            assertEquals(high, detail.get("pattern").get("patternId").asString());
            var all = read(path(run) + "/patterns?view=staging&size=100").get("data").get("items");
            var expected = jdbc.queryForList("SELECT CAST(pattern_id AS CHAR) FROM PATTERN WHERE run_id=? ORDER BY support_abs DESC,pattern_weight DESC,pattern_id DESC", String.class, run);
            for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), all.get(i).get("patternId").asString());
        } finally {
            movePatternId(high, old);
            jdbc.execute("ALTER TABLE PATTERN AUTO_INCREMENT=1"); // Reset only this test-owned boundary fixture.
        }
    }
    void movePatternId(String from, String to) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) c -> {
            var one = new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
            one.execute("SET FOREIGN_KEY_CHECKS=0");
            try {
                one.update("UPDATE PATTERN_ITEM SET pattern_id=CAST(? AS UNSIGNED) WHERE pattern_id=CAST(? AS UNSIGNED)", to, from);
                one.update("UPDATE PATTERN SET pattern_id=CAST(? AS UNSIGNED) WHERE pattern_id=CAST(? AS UNSIGNED)", to, from);
            } finally { one.execute("SET FOREIGN_KEY_CHECKS=1"); }
            return null;
        });
    }
    @ParameterizedTest @ValueSource(strings = {"canonical", "hash", "policy", "ratio", "weight", "position", "missing-stat", "uint-item"})
    void corruptStoredResultsRejectWithoutRepair(String kind) throws Exception {
        var outcome = staged(); String run = outcome.runId();
        String id = jdbc.queryForObject("SELECT CAST(MIN(pattern_id) AS CHAR) FROM PATTERN WHERE run_id=?", String.class, run);
        switch (kind) {
            case "canonical" -> jdbc.update("UPDATE PATTERN SET canonical_pattern='<(999)>' WHERE pattern_id=CAST(? AS UNSIGNED)", id);
            case "hash" -> jdbc.update("UPDATE PATTERN SET canonical_pattern_hash=? WHERE pattern_id=CAST(? AS UNSIGNED)", "f".repeat(64), id);
            case "policy" -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy='UNKNOWN' WHERE pattern_id=CAST(? AS UNSIGNED)", id);
            case "ratio" -> jdbc.update("UPDATE PATTERN SET support_ratio=0 WHERE pattern_id=CAST(? AS UNSIGNED)", id);
            case "weight" -> jdbc.update("UPDATE PATTERN SET pattern_weight=.999 WHERE pattern_id=CAST(? AS UNSIGNED)", id);
            case "position" -> jdbc.update("UPDATE PATTERN_ITEM SET itemset_order=itemset_order+100 WHERE pattern_id=CAST(? AS UNSIGNED)", id);
            case "missing-stat" -> jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id=?", run);
            case "uint-item" -> jdbc.update("UPDATE PATTERN_ITEM SET item_id=4294967295 WHERE pattern_id=CAST(? AS UNSIGNED)", id);
        }
        var before = state(run);
        mvc.perform(get(path(run) + "/patterns/" + id + "?view=staging").header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict());
        mvc.perform(get(path(run) + "/patterns/export?view=staging").header("X-Test-Identity", "ADMIN"))
                .andExpect(status().isConflict()).andExpect(header().doesNotExist("Content-Disposition"));
        assertEquals(before, state(run));
    }
    @Test void legacyMetadataAndPartialPairHaveDifferentSemantics() throws Exception {
        var outcome = staged(); String run = outcome.runId();
        jdbc.update("DELETE FROM RUN_PROVENANCE WHERE run_id=?", run);
        mvc.perform(get(path(run) + "/results").header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict());
        jdbc.update("DELETE FROM RUN_RESULT_PUBLICATION WHERE run_id=?", run);
        var metadata = read(path(run) + "/results").get("data");
        assertEquals("UNVERIFIED", metadata.get("publicationStatus").asString()); assertFalse(metadata.get("stagingReadable").asBoolean());
        mvc.perform(get(path(run) + "/patterns?view=staging").header("X-Test-Identity", "ADMIN")).andExpect(status().isConflict());
    }
    @Test void detailIsScopedToRunAndCatalogRenameDoesNotRewriteHistoricalNames() throws Exception {
        var first = staged(); var second = staged();
        String id = jdbc.queryForObject("SELECT CAST(MIN(pattern_id) AS CHAR) FROM PATTERN WHERE run_id=?", String.class, first.runId());
        mvc.perform(get(path(second.runId()) + "/patterns/" + id + "?view=staging").header("X-Test-Identity", "ADMIN")).andExpect(status().isNotFound());
        var before = read(path(first.runId()) + "/patterns/" + id + "?view=staging");
        jdbc.update("UPDATE PRODUCT SET name=CONCAT('renamed-',product_id),sku=CONCAT('new-',product_id) WHERE category_id='result-cat'");
        assertEquals(before.get("data"), read(path(first.runId()) + "/patterns/" + id + "?view=staging").get("data"));
    }
    @Test void repeatableReadKeepsMetadataCountsAndPagesInOneDatabaseSnapshot() throws Exception {
        var outcome = staged(); String run = outcome.runId();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        tx.setReadOnly(true); tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> {
            var before = service.page(run, "staging", "1", "100");
            assertFalse(before.items().isEmpty());
            String id = before.items().getFirst().patternId();
            var writer = java.util.concurrent.CompletableFuture.runAsync(() -> jdbc.update(
                    "UPDATE PATTERN SET pattern_weight=.999 WHERE pattern_id=CAST(? AS UNSIGNED)", id));
            assertDoesNotThrow(() -> writer.get(5, TimeUnit.SECONDS));
            assertEquals(before, service.page(run, "staging", "1", "100"));
        });
    }
}
