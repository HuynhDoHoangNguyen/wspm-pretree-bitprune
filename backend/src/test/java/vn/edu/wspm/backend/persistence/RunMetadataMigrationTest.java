package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Actual V3->V4 upgrade with synthetic existing run/pattern/statistic data. Never connects to owner DB. */
@Testcontainers
class RunMetadataMigrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_metadata_upgrade");

    @Test void additiveUpgradePreservesAllExistingRowsChecksumsAndUnverifiedLegacyStatus() {
        var old = flyway("3"); old.migrate(); old.validate();
        var checksums = new TreeMap<String, Integer>();
        Arrays.stream(old.info().applied()).forEach(m -> checksums.put(m.getVersion().getVersion(), m.getChecksum()));
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('c','Synthetic','c')");
        jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('p','p','Synthetic','p','c',1)");
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count)"
                + " VALUES('old','CSV','legacy fixture','LEGACY_UNVERIFIED','{}',3)");
        for (String state : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED")) {
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,legacy_metadata_json,created_at)"
                    + " VALUES(?,'old','WSPM_PRETREE',?,'legacy fixture','ABSOLUTE',.1,.1,.5,'{\"legacy\":true}','2020-01-02 03:04:05.123456')", state, state);
        }
        jdbc.update("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy)"
                + " VALUES('LEGACY_UNVERIFIED','<(1)>',?,1,.3333333333333333,.5,1,1,.5,'LEGACY_V1_WEIGHT')", "a".repeat(64));
        long pattern = jdbc.queryForObject("SELECT pattern_id FROM PATTERN", Long.class);
        jdbc.update("INSERT INTO PATTERN_ITEM(pattern_id,itemset_order,item_order,item_id) VALUES(?,1,1,1)", pattern);
        jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot)"
                + " VALUES('LEGACY_UNVERIFIED',1,'p',0,0,NULL,'Historical','OLD-SKU')");
        var before = new TreeMap<String, List<Map<String, Object>>>();
        for (String table : List.of("DATASET_SNAPSHOT", "MINING_RUN", "PATTERN", "PATTERN_ITEM", "RUN_ITEM_STATISTIC"))
            before.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        var current = flyway("4"); assertEquals(1, current.migrate().migrationsExecuted); current.validate();
        assertEquals(0, current.migrate().migrationsExecuted); current.validate();
        assertEquals("4", current.info().current().getVersion().getVersion());
        for (var migration : current.info().applied()) if (!migration.getVersion().getVersion().equals("4"))
            assertEquals(checksums.get(migration.getVersion().getVersion()), migration.getChecksum());
        for (var table : before.entrySet()) assertEquals(table.getValue(), jdbc.queryForList("SELECT * FROM " + table.getKey() + " ORDER BY 1"), table.getKey());
        for (String table : List.of("RUN_PROVENANCE", "RUN_RESULT_PUBLICATION"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS"
                + " WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME IN ('RUN_PROVENANCE','RUN_RESULT_PUBLICATION') AND DELETE_RULE='RESTRICT'", Integer.class));
        assertEquals(47, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history'", Integer.class));
    }
    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }
}
