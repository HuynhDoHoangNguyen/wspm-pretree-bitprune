package vn.edu.wspm.backend.services.pattern;

import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
import vn.edu.wspm.backend.repositories.JdbcPatternDetailReader;
import static org.junit.jupiter.api.Assertions.*;

/** Minimal projection schema, including deliberate missing dictionary rows; not migration/constraint acceptance. */
class PatternDetailMySqlTest {
    @Test void realQueryScopesDictionaryAndKeepsMissingRows() throws Exception {
        try (var mysql = new OwnedMySql()) {
            var ds = mysql.dataSource();
            try(var fixture=ds.getConnection();var s=fixture.createStatement()) {
                s.execute("CREATE TABLE DATASET_SNAPSHOT(snapshot_id VARCHAR(40) PRIMARY KEY,sequence_count BIGINT UNSIGNED) ENGINE=InnoDB");
                s.execute("CREATE TABLE MINING_RUN(run_id VARCHAR(40) PRIMARY KEY,snapshot_id VARCHAR(40),status VARCHAR(32),pattern_count BIGINT UNSIGNED) ENGINE=InnoDB");
                s.execute("""
                        CREATE TABLE PATTERN(pattern_id BIGINT UNSIGNED PRIMARY KEY,run_id VARCHAR(40),
                        canonical_pattern TEXT,support_abs BIGINT UNSIGNED,support_ratio DOUBLE,
                        weighted_support DOUBLE,pattern_weight DOUBLE,pattern_weight_policy VARCHAR(80),
                        pattern_item_count INT,itemset_count INT) ENGINE=InnoDB
                        """);
                s.execute("CREATE TABLE PATTERN_ITEM(pattern_id BIGINT UNSIGNED,itemset_order INT,item_order INT,item_id INT UNSIGNED,PRIMARY KEY(pattern_id,itemset_order,item_order)) ENGINE=InnoDB");
                s.execute("CREATE TABLE RUN_ITEM_STATISTIC(run_id VARCHAR(40),item_id INT UNSIGNED,product_id VARCHAR(40),product_name_snapshot VARCHAR(200),sku_snapshot VARCHAR(80),item_weight DOUBLE,PRIMARY KEY(run_id,item_id)) ENGINE=InnoDB");
                s.execute("INSERT INTO DATASET_SNAPSHOT VALUES ('S',10)");
                s.execute("INSERT INTO MINING_RUN VALUES ('A','S','SUCCEEDED',1),('B','S','SUCCEEDED',1)");
                s.execute("INSERT INTO PATTERN VALUES (18446744073709551615,'A','<(1)(2)>',2,.2,1,.3,'P',2,2),(2,'B','<(1)>',1,.1,.5,.2,'P',1,1)");
                s.execute("INSERT INTO PATTERN_ITEM VALUES (18446744073709551615,2,1,2),(18446744073709551615,1,1,1),(2,1,1,1)");
                s.execute("INSERT INTO RUN_ITEM_STATISTIC VALUES ('A',1,'PA','Name-A','SKU-A',NULL),('B',1,'PB','Name-B','SKU-B',.5),('B',2,'WRONG','Wrong fallback','WRONG',.5)");
            }
            var service=new PatternDetailService(new JdbcPatternDetailReader(ds));
            var a=service.detail("A","18446744073709551615");
            assertEquals("18446744073709551615",a.patternId());
            assertEquals("10",a.sequenceCount()); assertEquals(2,a.itemsets().size());
            assertEquals("Name-A",a.itemsets().getFirst().items().getFirst().nameSnapshot());
            assertEquals("MAPPED",a.itemsets().getFirst().items().getFirst().mappingStatus().name());
            assertEquals("MISSING",a.itemsets().get(1).items().getFirst().mappingStatus().name());
            assertNull(a.itemsets().get(1).items().getFirst().productId());
            assertEquals("Name-B",service.detail("B","2").itemsets().getFirst().items().getFirst().nameSnapshot());
            assertEquals(PatternReadException.Code.PATTERN_NOT_FOUND,assertThrows(PatternReadException.class,
                    ()->service.detail("B","18446744073709551615")).getCode());
        }
    }
}
