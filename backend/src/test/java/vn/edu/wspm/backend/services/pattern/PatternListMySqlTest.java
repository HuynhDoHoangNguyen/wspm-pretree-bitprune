package vn.edu.wspm.backend.services.pattern;

import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
import vn.edu.wspm.backend.repositories.JdbcPatternListReader;
import static org.junit.jupiter.api.Assertions.*;

/** Minimal comparator fixture: duplicate canonicals/long defensive strings do not satisfy full V2 identity. */
class PatternListMySqlTest {
    @Test void databasePagingUsesFullBinaryCanonicalAndNumericIdsWithinRun() throws Exception {
        try (var mysql = new OwnedMySql()) {
            var ds = mysql.dataSource();
            try (var c = ds.getConnection(); var q = c.createStatement()) {
                q.execute("CREATE TABLE MINING_RUN (run_id VARCHAR(40) PRIMARY KEY,status VARCHAR(32),pattern_count BIGINT) ENGINE=InnoDB");
                q.execute("""
                        CREATE TABLE PATTERN (pattern_id BIGINT UNSIGNED PRIMARY KEY,run_id VARCHAR(40),
                        canonical_pattern TEXT,support_abs BIGINT UNSIGNED,support_ratio DOUBLE,
                        weighted_support DOUBLE,pattern_weight DOUBLE,pattern_weight_policy VARCHAR(80),
                        pattern_item_count INT,itemset_count INT) ENGINE=InnoDB
                        """);
                q.execute("INSERT INTO MINING_RUN VALUES ('A','SUCCEEDED',5),('B','SUCCEEDED',1)");
                try (var insert = c.prepareStatement("INSERT INTO PATTERN VALUES (?,?,?,5,.5,3,.4,'P',1,1)")) {
                    // Differences after byte 1024 expose MySQL's default prefix sort.
                    String prefix = "1".repeat(2000);
                    // 2/3/10 tie on all preceding keys, so ID ordering must be numeric, not lexical.
                    for (var row : new String[][]{{"10","A",prefix+"a"},{"2","A",prefix+"a"},
                            {"3","A",prefix+"a"},{"4","A",prefix+"b"},{"11","A",prefix+"c"},{"1","B","0"}}) {
                        insert.setLong(1,Long.parseLong(row[0])); insert.setString(2,row[1]);
                        insert.setString(3,row[2]); insert.executeUpdate();
                    }
                }
            }
            var service = new PatternListService(new JdbcPatternListReader(ds));
            assertEquals(java.util.List.of("2","3"), service.list("A",0,2).items().stream().map(x->x.patternId()).toList());
            assertEquals(java.util.List.of("10","4"), service.list("A",1,2).items().stream().map(x->x.patternId()).toList());
            assertEquals(java.util.List.of("11"),service.list("A",2,2).items().stream().map(x->x.patternId()).toList());
            assertEquals("5",service.list("A",9,2).filteredTotal());
            assertTrue(service.list("A",9,2).items().isEmpty());
            assertEquals("1",service.list("B",0,2).runPatternTotal());
            assertEquals("1",service.list("B",0,2).items().getFirst().patternId());
        }
    }
}
