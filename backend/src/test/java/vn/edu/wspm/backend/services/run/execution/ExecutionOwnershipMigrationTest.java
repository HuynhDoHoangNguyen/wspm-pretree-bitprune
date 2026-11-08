package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Actual Flyway V1->V2 then V3 upgrade on owned MySQL. No history/checksum is fabricated. */
@Testcontainers
class ExecutionOwnershipMigrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_ownership_migration_test")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");

    @Test void actualFlywayUpgradePreservesV2DataChecksumsAndLeavesLegacyUnowned() {
        var old = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("2").load(); old.migrate(); old.validate();
        int checksum = old.info().current().getChecksum();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count)"
                + " VALUES('old-snapshot','CSV','legacy fixture','PENDING','{}',0)");
        for (String state : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")) {
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,created_at)"
                    + " VALUES(?,'old-snapshot','WSPM_PRETREE',?,'legacy fixture','ABSOLUTE',.25,.25,.5,'2020-01-02 03:04:05.123456')", state, state);
        }
        var before = jdbc.queryForList("SELECT * FROM MINING_RUN ORDER BY run_id");
        var current = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("3").load(); current.migrate(); current.validate();
        assertEquals("3", current.info().current().getVersion().getVersion());
        assertEquals(checksum, Arrays.stream(current.info().all()).filter(m -> m.getVersion().getVersion().equals("2")).findFirst().orElseThrow().getChecksum());
        var after = jdbc.queryForList("SELECT * FROM MINING_RUN ORDER BY run_id"); assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            for (var field : before.get(i).entrySet()) assertEquals(field.getValue(), after.get(i).get(field.getKey()), field.getKey());
            assertNull(after.get(i).get("execution_owner_id")); assertNull(after.get(i).get("execution_attempt_id")); assertNull(after.get(i).get("execution_lease_until"));
        }
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE MINING_RUN SET execution_owner_id='partial' WHERE run_id='QUEUED'"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()"
                + " AND table_name='MINING_RUN' AND index_name='idx_run_execution_lease'", Integer.class) > 0);
    }
}
