package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.entities.PatternEntity;
import vn.edu.wspm.backend.entities.RunEntity;

/** Schema probes only: synthetic SQL rows are not mined results or a production persistence path. */
@SpringBootTest(properties = {"wdm.execution.enabled=false", "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersistenceSchemaAuditIntegrationTest {
    @TempDir static Path storage;
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_persistence_audit")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory emf;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").target("3").load();
        flyway.migrate();
        flyway.validate();
        assertEquals(List.of("1", "2", "3"), Arrays.stream(flyway.info().applied())
                .map(m -> m.getVersion().getVersion()).toList());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.flyway.target", () -> "3"); // Preserve the accepted V2/V3 audit scope.
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
        AuthTestKeys.properties(p);
        p.add("wdm.snapshot-storage.root", () -> storage.toString());
    }

    @BeforeEach void fixture() {
        // Only rows owned by this class in its newly allocated Testcontainers database.
        jdbc.update("DELETE FROM PATTERN_ITEM");
        jdbc.update("DELETE FROM PATTERN");
        jdbc.update("DELETE FROM RUN_ITEM_STATISTIC");
        jdbc.update("DELETE FROM MINING_RUN");
        jdbc.update("INSERT IGNORE INTO CATEGORY(category_id,name,slug) VALUES('audit-category','Audit','audit-category')");
        jdbc.update("INSERT IGNORE INTO PRODUCT(product_id,sku,name,slug,category_id,current_price)"
                + " VALUES('audit-product','AUDIT-SKU','Audit product','audit-product','audit-category',1)");
        jdbc.update("INSERT IGNORE INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count)"
                + " VALUES('audit-snapshot','CSV','schema probe','PENDING','{}',3)");
        run("audit-run");
        run("other-run");
    }

    @Test void allColumnsMatchRuntimeMigrationsIncludingDefaultsPrecisionAndUnsignedTypes() {
        columns("MINING_RUN", """
                run_id:varchar(40):N
                snapshot_id:varchar(40):N
                algorithm:varchar(32):N
                status:varchar(32):N
                dataset_source:varchar(255):N
                minsup_mode:varchar(32):N
                minsup_configured:double:N
                minsup_relative_fraction:double:Y
                effective_threshold:double:N
                max_w:double:Y
                min_w:double:Y
                mean_w:double:N
                pattern_count:bigint unsigned:Y
                candidate_count:bigint unsigned:Y
                dataset_io_ms:bigint unsigned:Y
                preprocessing_ms:bigint unsigned:Y
                dfs_mining_ms:bigint unsigned:Y
                complete_algorithm_ms:bigint unsigned:Y
                output_io_ms:bigint unsigned:Y
                total_execution_ms:bigint unsigned:Y
                peak_sampled_jvm_heap_bytes:bigint unsigned:Y
                legacy_metadata_json:json:Y
                error_message:varchar(2000):Y
                created_at:datetime(6):N
                started_at:datetime(6):Y
                finished_at:datetime(6):Y
                execution_owner_id:varchar(40):Y
                execution_attempt_id:varchar(40):Y
                execution_lease_until:datetime(6):Y
                """);
        columns("PATTERN", """
                pattern_id:bigint unsigned:N
                run_id:varchar(40):N
                canonical_pattern:text:N
                canonical_pattern_hash:char(64):N
                support_abs:bigint unsigned:N
                support_ratio:double:N
                weighted_support:double:N
                pattern_item_count:int unsigned:N
                itemset_count:int unsigned:N
                pattern_weight:double:N
                pattern_weight_policy:varchar(80):N
                """);
        columns("PATTERN_ITEM", """
                pattern_id:bigint unsigned:N
                itemset_order:int unsigned:N
                item_order:int unsigned:N
                item_id:int unsigned:N
                """);
        columns("RUN_ITEM_STATISTIC", """
                run_id:varchar(40):N
                item_id:int unsigned:N
                product_id:varchar(40):N
                occurrence_count:bigint unsigned:N
                sequence_support:bigint unsigned:N
                item_weight:double:Y
                product_name_snapshot:varchar(200):N
                sku_snapshot:varchar(80):N
                """);
    }

    @Test void keysIndexesAndForeignKeysMatchTheAuditedBoundary() {
        indexes("MINING_RUN", Map.of("PRIMARY", "0:run_id", "idx_v2_run_snapshot_algorithm", "1:snapshot_id,algorithm",
                "idx_v2_run_status_time", "1:status,created_at", "idx_run_execution_lease", "1:status,execution_lease_until"));
        indexes("PATTERN", Map.of("PRIMARY", "0:pattern_id", "uk_v2_pattern_run_hash", "0:run_id,canonical_pattern_hash",
                "idx_v2_pattern_run_rank", "1:run_id,support_abs,pattern_weight"));
        indexes("PATTERN_ITEM", Map.of("PRIMARY", "0:pattern_id,itemset_order,item_order"));
        indexes("RUN_ITEM_STATISTIC", Map.of("PRIMARY", "0:run_id,item_id", "uk_v2_run_stat_product", "0:run_id,product_id",
                "idx_v2_run_stat_product", "1:product_id"));
        assertEquals(Set.of("MINING_RUN.snapshot_id->DATASET_SNAPSHOT.snapshot_id:RESTRICT",
                "PATTERN.run_id->MINING_RUN.run_id:RESTRICT", "PATTERN_ITEM.pattern_id->PATTERN.pattern_id:RESTRICT",
                "RUN_ITEM_STATISTIC.run_id->MINING_RUN.run_id:RESTRICT",
                "RUN_ITEM_STATISTIC.product_id->PRODUCT.product_id:RESTRICT"), new HashSet<>(jdbc.queryForList("""
                SELECT CONCAT(k.TABLE_NAME,'.',k.COLUMN_NAME,'->',k.REFERENCED_TABLE_NAME,'.',k.REFERENCED_COLUMN_NAME,':',r.DELETE_RULE)
                FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
                  ON r.CONSTRAINT_SCHEMA=k.CONSTRAINT_SCHEMA AND r.CONSTRAINT_NAME=k.CONSTRAINT_NAME AND r.TABLE_NAME=k.TABLE_NAME
                WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME IN ('MINING_RUN','PATTERN','PATTERN_ITEM','RUN_ITEM_STATISTIC')
                  AND k.REFERENCED_TABLE_NAME IS NOT NULL
                """, String.class)));
        assertEquals(Set.of("chk_v2_run_algorithm", "chk_v2_run_status", "chk_v2_run_minsup", "chk_run_execution_ownership",
                "chk_v2_pattern_counts", "chk_v2_pattern_support", "chk_v2_pattern_item_order"),
                new HashSet<>(jdbc.queryForList("SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS"
                        + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('MINING_RUN','PATTERN','PATTERN_ITEM','RUN_ITEM_STATISTIC')"
                        + " AND CONSTRAINT_TYPE='CHECK' AND ENFORCED='YES'", String.class)));
    }

    @Test void completePatternMappingNowInsertsAndReadsEveryRequiredField() {
        assertTrue(emf.isOpen()); // Spring context initialized with ddl-auto=validate.
        var em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            var run = em.getReference(RunEntity.class, "audit-run");
            // Mapping probe only, not a mined result or policy fixture.
            var pattern = new PatternEntity(run, "<(1)>", "a".repeat(64), 1L, 1.0 / 3, .5, 1, 1, .5, "SCHEMA_PROBE_ONLY");
            em.persist(pattern); em.flush(); Long id = pattern.getPatternId(); em.clear();
            var actual = em.find(PatternEntity.class, id);
            assertEquals("<(1)>", actual.getCanonicalPattern()); assertEquals("a".repeat(64), actual.getCanonicalPatternHash());
            assertEquals(1L, actual.getSupport()); assertEquals(1.0 / 3, actual.getSupportRatio());
            assertEquals(.5, actual.getWeightedSupport()); assertEquals(1, actual.getPatternLength());
            assertEquals(1, actual.getItemsetCount()); assertEquals(.5, actual.getWeight());
            assertEquals("SCHEMA_PROBE_ONLY", actual.getPatternWeightPolicy());
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN", Integer.class));
    }

    @Test void hashIsUniquePerRunButDatabaseDoesNotVerifyHashAgainstCanonicalText() {
        pattern("audit-run", "<(1)>", "a".repeat(64));
        rejected(() -> pattern("audit-run", "<(2)>", "a".repeat(64)));
        pattern("other-run", "<(2)>", "a".repeat(64));
        // Same text with another hash is accepted: producer must validate hash and canonical content.
        pattern("audit-run", "<(1)>", "b".repeat(64));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN", Integer.class));
    }

    @Test void temporalPositionsArePositiveAndUniqueButGapsAndDuplicateItemsAreNotRejected() {
        long id = pattern("audit-run", "<(1)(2)>", "a".repeat(64));
        rejected(() -> item(id, 0, 1, 1));
        rejected(() -> item(id, 1, 0, 1));
        rejected(() -> item(id, 1, 1, 0));
        item(id, 1, 1, 1);
        rejected(() -> item(id, 1, 1, 2));
        item(id, 3, 2, 1); // Position gap, no matching run dictionary, same item: accepted by SQL.
        item(id, 3, 3, 1); // Duplicate within an itemset is also not checked.
        assertEquals(List.of("1:1:1", "3:2:1", "3:3:1"), jdbc.queryForList(
                "SELECT CONCAT(itemset_order,':',item_order,':',item_id) FROM PATTERN_ITEM WHERE pattern_id=? ORDER BY itemset_order,item_order",
                String.class, id));
    }

    @Test void itemStatisticsEnforceProductAndRunKeysButNotMappingOrCountSemantics() {
        statistic("audit-run", 1, "audit-product");
        rejected(() -> statistic("audit-run", 1, "audit-product"));
        rejected(() -> statistic("audit-run", 2, "audit-product"));
        rejected(() -> statistic("audit-run", 2, "missing-product"));
        rejected(() -> statistic("missing-run", 2, "audit-product"));
        statistic("other-run", 0, "audit-product"); // No >0 CHECK on this table, unlike ITEM_MAPPING.
        jdbc.update("UPDATE RUN_ITEM_STATISTIC SET occurrence_count=0,sequence_support=99,item_weight=NULL WHERE run_id='audit-run'");
        assertEquals(99L, jdbc.queryForObject("SELECT sequence_support FROM RUN_ITEM_STATISTIC WHERE run_id='audit-run'", Long.class));
    }

    @Test void patternChecksRejectInvalidRatiosAndCountsButNotSemanticInconsistency() {
        long id = pattern("audit-run", "<(1)>", "a".repeat(64));
        rejected(() -> jdbc.update("UPDATE PATTERN SET support_ratio=-.1 WHERE pattern_id=?", id));
        rejected(() -> jdbc.update("UPDATE PATTERN SET support_ratio=1.1 WHERE pattern_id=?", id));
        rejected(() -> jdbc.update("UPDATE PATTERN SET pattern_item_count=0 WHERE pattern_id=?", id));
        rejected(() -> jdbc.update("UPDATE PATTERN SET itemset_count=0 WHERE pattern_id=?", id));
        rejected(() -> jdbc.update("UPDATE PATTERN SET pattern_weight_policy=NULL WHERE pattern_id=?", id));
        jdbc.update("UPDATE PATTERN SET support_abs=99,support_ratio=.5,weighted_support=-1,pattern_weight=-1 WHERE pattern_id=?", id);
        assertEquals(99L, jdbc.queryForObject("SELECT support_abs FROM PATTERN WHERE pattern_id=?", Long.class, id));
    }

    @Test void runChecksAndMicrosecondTimestampsDoNotEstablishDurablePublication() {
        rejected(() -> jdbc.update("UPDATE MINING_RUN SET algorithm='OTHER' WHERE run_id='audit-run'"));
        rejected(() -> jdbc.update("UPDATE MINING_RUN SET status='PERSISTED' WHERE run_id='audit-run'"));
        rejected(() -> jdbc.update("UPDATE MINING_RUN SET effective_threshold=-1 WHERE run_id='audit-run'"));
        rejected(() -> jdbc.update("UPDATE MINING_RUN SET minsup_configured=-1 WHERE run_id='audit-run'"));
        rejected(() -> jdbc.update("UPDATE MINING_RUN SET execution_owner_id='partial' WHERE run_id='audit-run'"));
        jdbc.update("UPDATE MINING_RUN SET status='SUCCEEDED',effective_threshold=0,pattern_count=0,"
                + "created_at='2026-01-02 03:04:05.123456' WHERE run_id='audit-run'");
        assertEquals("2026-01-02 03:04:05.123456", jdbc.queryForObject(
                "SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') FROM MINING_RUN WHERE run_id='audit-run'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id='audit-run'", Integer.class));
        assertNull(jdbc.queryForObject("SELECT output_io_ms FROM MINING_RUN WHERE run_id='audit-run'", Long.class));
        // SQL permits zero; the accepted application gate still excludes R6. No zero-threshold mining is executed.
    }

    @Test void foreignKeysRestrictOrphansAndDeletionWithoutAnImplicitCascade() {
        rejected(() -> pattern("missing-run", "<(1)>", "a".repeat(64)));
        rejected(() -> item(Long.MAX_VALUE, 1, 1, 1));
        long id = pattern("audit-run", "<(1)>", "a".repeat(64));
        item(id, 1, 1, 1);
        statistic("audit-run", 1, "audit-product");
        rejected(() -> jdbc.update("DELETE FROM PATTERN WHERE pattern_id=?", id));
        rejected(() -> jdbc.update("DELETE FROM MINING_RUN WHERE run_id='audit-run'"));
        rejected(() -> jdbc.update("DELETE FROM PRODUCT WHERE product_id='audit-product'"));
    }

    @Test void sqlUnsignedRangeExceedsJavaSignedMappings() {
        long id = pattern("audit-run", "<(1)>", "a".repeat(64));
        jdbc.update("UPDATE PATTERN SET support_abs=18446744073709551615 WHERE pattern_id=?", id);
        assertEquals("18446744073709551615", jdbc.queryForObject("SELECT CAST(support_abs AS CHAR) FROM PATTERN WHERE pattern_id=?", String.class, id));
        jdbc.update("UPDATE PATTERN SET pattern_item_count=4294967295 WHERE pattern_id=?", id);
        assertEquals("4294967295", jdbc.queryForObject("SELECT CAST(pattern_item_count AS CHAR) FROM PATTERN WHERE pattern_id=?", String.class, id));
    }

    private void columns(String table, String specification) {
        var expected = specification.lines().filter(s -> !s.isBlank()).map(String::trim).toList();
        assertEquals(expected, jdbc.queryForList("SELECT CONCAT(COLUMN_NAME,':',COLUMN_TYPE,':',LEFT(IS_NULLABLE,1))"
                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", String.class, table));
        var defaults = jdbc.queryForList("SELECT COLUMN_NAME,COLUMN_DEFAULT,EXTRA FROM information_schema.COLUMNS"
                + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION", table);
        for (var column : defaults) {
            boolean created = table.equals("MINING_RUN") && column.get("COLUMN_NAME").equals("created_at");
            if (created) assertEquals("current_timestamp(6)", column.get("COLUMN_DEFAULT").toString().toLowerCase(Locale.ROOT));
            else assertNull(column.get("COLUMN_DEFAULT"), table + "." + column.get("COLUMN_NAME"));
            String extra = column.get("EXTRA").toString().toLowerCase(Locale.ROOT);
            boolean identity = table.equals("PATTERN") && column.get("COLUMN_NAME").equals("pattern_id");
            assertEquals(identity, extra.contains("auto_increment"));
            assertFalse(extra.contains("on update"));
        }
    }

    private void indexes(String table, Map<String, String> expected) {
        var actual = new TreeMap<String, String>();
        jdbc.query("SELECT INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS cols"
                + " FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?"
                + " GROUP BY INDEX_NAME,NON_UNIQUE", rs -> {
                    actual.put(rs.getString("INDEX_NAME"), rs.getInt("NON_UNIQUE") + ":" + rs.getString("cols"));
                }, table);
        assertEquals(expected, actual);
    }

    private void run(String id) {
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w)"
                + " VALUES(?,'audit-snapshot','WSPM_PRETREE','SUCCEEDED','schema probe','ABSOLUTE',.1,.1,.5)", id);
    }

    private long pattern(String run, String canonical, String hash) {
        jdbc.update("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,"
                + "pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(?,?,?,1,0.3333333333333333,.5,1,1,.5,'SCHEMA_PROBE_ONLY')",
                run, canonical, hash);
        return jdbc.queryForObject("SELECT pattern_id FROM PATTERN WHERE run_id=? AND canonical_pattern_hash=?", Long.class, run, hash);
    }

    private void item(long pattern, int itemset, int order, int item) {
        jdbc.update("INSERT INTO PATTERN_ITEM(pattern_id,itemset_order,item_order,item_id) VALUES(?,?,?,?)", pattern, itemset, order, item);
    }

    private void statistic(String run, int item, String product) {
        jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot)"
                + " VALUES(?,?,?,1,1,.5,'Audit product','AUDIT-SKU')", run, item, product);
    }

    private void rejected(Runnable write) { assertThrows(DataAccessException.class, write::run); }
}
