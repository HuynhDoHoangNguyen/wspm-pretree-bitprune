package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.backend.services.run.execution.*;
import vn.edu.wspm.backend.services.run.persistence.RunProvenance;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.*;

/** Real isolated MySQL/JPA transactions and descriptors; no owner DB or fabricated mined patterns. */
@SpringBootTest(properties = {"spring.config.import=", "wdm.result-publication.enabled=false", "wdm.execution.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunMetadataIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_run_metadata")
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RunMetadataRepository metadata;
    @Autowired RunExecutionVerificationRepository verifier;
    @Autowired RunLifecycleRepository lifecycle;
    @Autowired RunStatusRepository statuses;
    @PersistenceContext EntityManager em;
    static final Instant CREATED = Instant.parse("2026-11-07T05:00:00.123456Z");
    PreparedRun input;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load();
        flyway.migrate(); flyway.validate();
        assertEquals("5", flyway.info().current().getVersion().getVersion());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("wdm.snapshot-storage.root", () -> storage.toString());
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER); AuthTestKeys.properties(p);
    }

    @BeforeEach void fixture() throws Exception {
        jdbc.update("DELETE FROM RUN_RESULT_PUBLICATION"); jdbc.update("DELETE FROM RUN_PROVENANCE");
        jdbc.update("DELETE FROM MINING_RUN");
        jdbc.update("INSERT IGNORE INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count)"
                + " VALUES('snapshot','CSV','metadata test','PENDING','{}',3)");
        input = input(MiningAlgorithm.WSPM_PRETREE, MinimumSupport.absolute(.1));
    }

    static PreparedRun input(MiningAlgorithm algorithm, MinimumSupport config) throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 1 -1 -2\n1 -1 2 -1 -2\n1 2 -1 -2\n"));
        var publication = new ConversionArtifactResult("snapshot", "attempt", "a".repeat(64),
                new ConversionArtifactResult.ArtifactFile("private-sequence", "b".repeat(64), 10),
                new ConversionArtifactResult.ArtifactFile("private-mapping", "c".repeat(64), 20),
                new ConversionArtifactResult.Statistics(3, 1, 2, 5, 2, 7, 2, 3, 7.0 / 3));
        return new PreparedRun(new MiningEngineRequest(algorithm, db, config), new MinimumSupportResolver().resolve(config, db.size()),
                new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(db)),
                new PreparedRun.Binding(publication, "CSV", "metadata test", "private-raw", "d".repeat(64)));
    }

    void create(String id, PreparedRun prepared, boolean withMetadata) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var r = prepared.minimumSupport(); var w = prepared.weights();
            em.persist(RunEntity.queued(id, "snapshot", prepared.engineRequest().algorithm().name(), "metadata test", r.mode().name(),
                    r.configuredValue(), r.relativeMinimumSupport().isPresent() ? r.relativeMinimumSupport().getAsDouble() : null,
                    r.effectiveThreshold(), w.maxWeight().orElseThrow(), w.minWeight().orElseThrow(), w.meanWeight().orElseThrow(), CREATED));
            em.flush();
            if (withMetadata) metadata.insert(id, RunProvenance.from(prepared));
        });
    }

    @Test void bothAlgorithmsAndEveryModeRoundTripWithProvenanceAndUnpublishedState() throws Exception {
        int index = 0;
        for (var algorithm : MiningAlgorithm.values()) for (var config : List.of(MinimumSupport.absolute(.1), MinimumSupport.percentage(14))) {
            var prepared = input(algorithm, config); String id = "run-" + index++;
            create(id, prepared, true);
            var actual = metadata.read(id).orElseThrow(); var status = statuses.read(id);
            assertEquals(RunProvenance.from(prepared), actual.provenance()); assertEquals(CREATED, actual.capturedAt());
            assertEquals("NOT_PUBLISHED", actual.publication().status()); assertNull(actual.publication().patternCount());
            assertNull(actual.publication().committedAt()); assertNull(actual.publication().elapsedMs());
            assertEquals(algorithm.name(), status.algorithm()); assertEquals(config.configuredValue(), status.configuredValue());
            assertEquals(prepared.minimumSupport().effectiveThreshold(), status.effectiveThreshold());
            assertEquals(prepared.weights().meanWeight().orElseThrow(), status.meanW());
            assertEquals(prepared.minimumSupport().relativeMinimumSupport().isPresent()
                    ? prepared.minimumSupport().relativeMinimumSupport().getAsDouble() : null, status.relativeFraction());
            verifier.requireCommitted(id, prepared);
            assertFalse(actual.toString().contains("private"));
        }
    }

    @Test void insertRequiresAnExistingTransactionAndCannotCreateAnOrphan() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> metadata.insert("orphan", RunProvenance.from(input)));
        assertEquals(0, count("RUN_PROVENANCE", "orphan"));
    }

    @Test void failureAtEachMetadataInsertRollsBackAllThreeRows() {
        for (String table : List.of("RUN_PROVENANCE", "RUN_RESULT_PUBLICATION")) {
            jdbc.execute("CREATE TRIGGER reject_metadata BEFORE INSERT ON " + table
                    + " FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled metadata failure'");
            try {
                assertThrows(RuntimeException.class, () -> create("rollback", input, true));
                for (String checked : List.of("MINING_RUN", "RUN_PROVENANCE", "RUN_RESULT_PUBLICATION")) assertEquals(0, count(checked, "rollback"));
            } finally { jdbc.execute("DROP TRIGGER reject_metadata"); }
        }
    }

    @Test void duplicateMetadataNeverOverwritesAnExistingBinding() {
        create("run", input, true); var before = metadata.read("run");
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(
                tx -> metadata.insert("run", RunProvenance.from(input))));
        assertEquals(before, metadata.read("run"));
    }

    @Test void changedRawSequenceMappingValidationAttemptCountsOrVersionsAreRejected() {
        create("run", input, true);
        for (var change : Map.of(
                "$.binding.rawSha256", "e".repeat(64),
                "$.binding.publication.sequence.sha256", "e".repeat(64),
                "$.binding.publication.mapping.sha256", "e".repeat(64),
                "$.binding.publication.validationSha256", "e".repeat(64),
                "$.binding.publication.attemptId", "changed-attempt",
                "$.binding.publication.mapping.reference", "changed-reference",
                "$.conversionFormatVersion", "unknown-format",
                "$.policies.mapping", "unknown-policy").entrySet()) {
            String before = jdbc.queryForObject("SELECT CAST(provenance_json AS CHAR) FROM RUN_PROVENANCE WHERE run_id='run'", String.class);
            try {
                jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,?,?) WHERE run_id='run'", change.getKey(), change.getValue());
                assertThrows(ApplicationException.class, () -> verifier.requireCommitted("run", input), change.getKey());
            } finally { jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=? WHERE run_id='run'", before); }
        }
        jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.publication.statistics.totalItemOccurrences',8) WHERE run_id='run'");
        assertThrows(ApplicationException.class, () -> verifier.requireCommitted("run", input));
    }

    @Test void missingMetadataIsLegacyForReadButNeverAllowsExecution() {
        for (String state : List.of("QUEUED", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")) {
            create(state, input, false); jdbc.update("UPDATE MINING_RUN SET status=? WHERE run_id=?", state, state);
            var legacy = metadata.read(state).orElseThrow();
            assertNull(legacy.provenance()); assertNull(legacy.capturedAt()); assertEquals("UNVERIFIED", legacy.publication().status());
        }
        assertThrows(ApplicationException.class, () -> verifier.requireCommitted("QUEUED", input));
        assertTrue(metadata.read("missing").isEmpty());
    }

    @Test void partialMetadataPairIsCorruptionAndDoesNotMasqueradeAsLegacy() {
        create("run", input, true); jdbc.update("DELETE FROM RUN_RESULT_PUBLICATION WHERE run_id='run'");
        assertThrows(ApplicationException.class, () -> metadata.read("run"));
        assertThrows(ApplicationException.class, () -> verifier.requireCommitted("run", input));
    }

    @Test void succeededAndFailedRunsRemainUnpublishedIncludingZeroPatterns() {
        for (String state : List.of("SUCCEEDED", "FAILED")) {
            create(state, input, true); jdbc.update("UPDATE MINING_RUN SET status=?,pattern_count=0 WHERE run_id=?", state, state);
            assertEquals("NOT_PUBLISHED", metadata.read(state).orElseThrow().publication().status());
        }
        for (String table : List.of("PATTERN", "PATTERN_ITEM", "RUN_ITEM_STATISTIC")) assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
    }

    @Test void staleOwnershipStillFailsAndMetricsRemainInTheirOriginalScopes() {
        create("run", input, true);
        var owner = new ExecutionOwnership(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 180000);
        jdbc.update("UPDATE MINING_RUN SET execution_owner_id=?,execution_attempt_id=?,execution_lease_until=TIMESTAMPADD(MINUTE,5,UTC_TIMESTAMP(6)) WHERE run_id='run'", owner.ownerId(), owner.attemptId());
        verifier.requireOwned("run", input, "QUEUED", owner);
        assertThrows(ApplicationException.class, () -> verifier.requireOwned("run", input, "QUEUED", new ExecutionOwnership(UUID.randomUUID().toString(), owner.attemptId(), 180000)));
        assertEquals(1, lifecycle.claim("run", owner));
        var metrics = new RunMetricsCapture(1_000_000, 2_000_000, 3_000_000, 6_000_000, 10_000_000, 99, 0, 5);
        assertEquals(1, lifecycle.succeed("run", owner, metrics));
        var status = statuses.read("run");
        assertEquals("SUCCEEDED", status.status()); assertEquals("0", status.patternCount()); assertEquals("5", status.candidateCount());
        assertEquals("1", status.datasetIoMs()); assertEquals("2", status.preprocessingMs()); assertEquals("3", status.dfsMiningMs());
        assertEquals("6", status.completeAlgorithmMs()); assertEquals("10", status.totalExecutionMs()); assertEquals("99", status.peakSampledJvmHeapBytes());
        assertNull(status.outputIoMs()); assertNotNull(status.startedAt()); assertNotNull(status.finishedAt());
        assertEquals("NOT_PUBLISHED", metadata.read("run").orElseThrow().publication().status());
        assertEquals(RunProvenance.from(input), metadata.read("run").orElseThrow().provenance());
    }

    @Test void publicationSchemaRequiresACompleteManifestAndAllowsZeroPatternCounts() {
        create("run", input, true);
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED' WHERE run_id='run'"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET pattern_count=0 WHERE run_id='run'"));
        // Schema fixture only: production has no COMMITTED writer in WDM-06.2.
        jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED',manifest_version='SCHEMA_PROBE',result_sha256=?,"
                + "pattern_count=0,pattern_item_count=0,run_item_count=2,pattern_weight_policy='SCHEMA_PROBE',publication_elapsed_ms=0,committed_at=UTC_TIMESTAMP(6) WHERE run_id='run'", "a".repeat(64));
        assertEquals("0", metadata.read("run").orElseThrow().publication().patternCount());
        assertThrows(ApplicationException.class, () -> verifier.requireCommitted("run", input));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET result_sha256=? WHERE run_id='run'", "A".repeat(64)));
        jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET pattern_count=18446744073709551615 WHERE run_id='run'");
        assertEquals("18446744073709551615", metadata.read("run").orElseThrow().publication().patternCount());
    }

    @Test void foreignKeysAndDocumentVersionChecksRejectInvalidRows() {
        create("run", input, true);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("DELETE FROM MINING_RUN WHERE run_id='run'"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("INSERT INTO RUN_RESULT_PUBLICATION(run_id) VALUES('missing')"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json='{}' WHERE run_id='run'"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE RUN_PROVENANCE SET provenance_version='other' WHERE run_id='run'"));
    }

    int count(String table, String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE run_id=?", Integer.class, id); }
}
