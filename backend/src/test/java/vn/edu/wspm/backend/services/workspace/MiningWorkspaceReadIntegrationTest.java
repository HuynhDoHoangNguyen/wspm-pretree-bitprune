package vn.edu.wspm.backend.services.workspace;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.exception.ApplicationException;

/** Real MySQL with actual V1-V3 migrations. Only this test-owned database is seeded. */
@Testcontainers
class MiningWorkspaceReadIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("workspace_read_test")
        .withCommand("--sort_buffer_size=32768"); // Test-owned server only; production settings remain untouched.
    static JdbcTemplate jdbc;
    static MiningWorkspaceReadService service;
    static final String A = "10000000-0000-4000-8000-000000000001", B = "10000000-0000-4000-8000-000000000002";
    @BeforeAll static void setup() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(ds);
        service = new MiningWorkspaceReadService(new NamedParameterJdbcTemplate(ds), Path.of(System.getProperty("java.io.tmpdir"), "workspace-metadata-only").toString());
        for (String id : List.of(A, B)) {
            jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count,created_at,raw_file_path,raw_sha256) VALUES(?,'CSV',?,'PENDING',?,0,'2026-11-07 00:00:00',?,?)",
                id, id.equals(A) ? "orders_100%.csv" : "other.csv", "{\"conversionStatus\":\"NOT_STARTED\",\"statisticsStatus\":\"NOT_COMPUTED\"}", id + "/raw.csv", "a".repeat(64));
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,created_at) VALUES(?,?,'WSPM_PRETREE','SUCCEEDED','fixture','ABSOLUTE',0.1,0.1,0.5,'2026-11-07 00:00:00')", id, id);
        }
    }
    @Test void stableNewestOrderPaginationCountsAndLiteralSearch() {
        var first = service.list(false, Map.of("size", "1")); assertEquals(2, first.total()); assertEquals(B, first.items().getFirst().get("snapshotId"));
        var second = service.list(false, Map.of("size", "1", "page", "2")); assertEquals(A, second.items().getFirst().get("snapshotId"));
        assertNull(second.items().getFirst().get("sequenceCount"));
        assertEquals(1, service.list(false, Map.of("search", "100%" )).total());
        assertEquals(0, service.list(false, Map.of("search", "' OR 1=1 --")).total());
        assertEquals(0, service.list(false, Map.of("validationStatus", "VALID")).total());
        assertEquals(0, service.list(false, Map.of("conversionStatus", "SUCCEEDED")).total());
    }
    @Test void largeValidationReportsDoNotEnterThePaginationSortBuffer() {
        var originals = jdbc.queryForList("SELECT snapshot_id,CAST(conversion_report_json AS CHAR) AS report FROM DATASET_SNAPSHOT WHERE snapshot_id IN (?,?)", A, B);
        String report = "{\"conversionStatus\":\"NOT_STARTED\",\"statisticsStatus\":\"NOT_COMPUTED\",\"diagnostics\":\"" + "x".repeat(1_000_000) + "\"}";
        jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=? WHERE snapshot_id IN (?,?)", report, A, B);
        try {
            var first = service.list(false, Map.of("size", "1"));
            assertEquals(2, first.total()); assertEquals(B, first.items().getFirst().get("snapshotId"));
            var second = service.list(false, Map.of("size", "1", "page", "2", "conversionStatus", "NOT_STARTED"));
            assertEquals(A, second.items().getFirst().get("snapshotId"));
            assertEquals(2, service.list(true, Map.of()).total());
        } finally {
            for (var row : originals) jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=? WHERE snapshot_id=?", row.get("report"), row.get("snapshot_id"));
        }
    }
    @Test void runHistoryFiltersAndDoesNotWriteOrReleaseAnything() {
        var before = jdbc.queryForList("SELECT * FROM MINING_RUN ORDER BY run_id");
        var page = service.list(true, Map.of("snapshotId", A, "status", "SUCCEEDED", "algorithm", "WSPM_PRETREE"));
        assertEquals(1, page.total()); assertEquals(A, page.items().getFirst().get("runId"));
        assertEquals(0, service.list(true, Map.of("algorithm", "WSPM_BITPRUNE")).total());
        assertEquals(B, service.list(true, Map.of()).items().getFirst().get("runId"));
        assertEquals(before, jdbc.queryForList("SELECT * FROM MINING_RUN ORDER BY run_id"));
    }
    @Test void metadataPathsAreSnapshotBoundAndNeverReadFiles() {
        var result = service.storage(A); assertEquals("CURRENT_SNAPSHOT_PUBLICATION", result.binding());
        assertEquals(1, result.files().size()); assertEquals(A + "/raw.csv", result.files().getFirst().reference());
        assertTrue(Path.of(result.files().getFirst().absolutePath()).startsWith(Path.of(result.root())));
        jdbc.update("UPDATE DATASET_SNAPSHOT SET raw_file_path='../outside.csv' WHERE snapshot_id=?", B);
        try { assertTrue(service.storage(B).files().isEmpty()); }
        finally { jdbc.update("UPDATE DATASET_SNAPSHOT SET raw_file_path=? WHERE snapshot_id=?", B + "/raw.csv", B); }
        assertThrows(ApplicationException.class, () -> service.storage("../outside"));
        assertThrows(ApplicationException.class, () -> service.storage("90000000-0000-4000-8000-000000000001"));
    }
    @Test void rejectsInvalidPaginationEnumsAndIdentity() {
        for (var query : List.of(Map.of("size", "101"), Map.of("page", "0"), Map.of("status", "oops"), Map.of("snapshotId", "bad")))
            assertThrows(ApplicationException.class, () -> service.list(true, query));
    }
    @Test void legacyMetadataRemainsListedWithoutFabricatedPublishedStatistics() {
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count,created_at) VALUES('legacy-record','LEGACY_PATH','legacy-source.csv','LEGACY_UNVERIFIED','{\"migration\":\"V2\"}',99,'2020-01-01 00:00:00')");
        try {
            var result = service.list(false, Map.of("search", "legacy-source"));
            assertEquals(1, result.total()); assertEquals("LEGACY_PATH", result.items().getFirst().get("sourceType"));
            assertNull(result.items().getFirst().get("sequenceCount")); assertNull(result.items().getFirst().get("conversionStatus"));
            assertThrows(ApplicationException.class, () -> service.storage("legacy-record"));
        } finally { jdbc.update("DELETE FROM DATASET_SNAPSHOT WHERE snapshot_id='legacy-record'"); }
    }
}
