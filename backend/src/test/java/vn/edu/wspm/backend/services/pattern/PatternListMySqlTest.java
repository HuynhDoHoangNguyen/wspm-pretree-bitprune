package vn.edu.wspm.backend.services.pattern;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.wspm.backend.repositories.JdbcPatternListReader;
import static org.junit.jupiter.api.Assertions.*;

/** Optional real MySQL test. Never targets the application/demo schema. */
@EnabledIfEnvironmentVariable(named = "PATTERN_TEST_DISPOSABLE", matches = "YES_CREATE_DROP_SCHEMA")
class PatternListMySqlTest {
    @Test void databasePagingUsesFullBinaryCanonicalAndNumericIdsWithinRun() throws Exception {
        String server = System.getenv("PATTERN_TEST_SERVER");
        if (server == null || !server.matches("jdbc:mysql://[A-Za-z0-9.\\-]+:[0-9]+/"))
            throw new IllegalArgumentException("PATTERN_TEST_SERVER must be a server-only JDBC URL");
        String user = System.getenv("PATTERN_TEST_USER"), password = System.getenv("PATTERN_TEST_PASSWORD");
        String schema = "pattern_reader_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin = DriverManager.getConnection(server, user, password); var ddl = admin.createStatement()) {
            ddl.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4");
            try {
                var ds = new DriverManagerDataSource(server + schema, user, password);
                try (var c = ds.getConnection(); var q = c.createStatement()) {
                    q.execute("CREATE TABLE MINING_RUN (run_id VARCHAR(40) PRIMARY KEY,status VARCHAR(32),pattern_count BIGINT) ENGINE=InnoDB");
                    q.execute("""
                            CREATE TABLE PATTERN (pattern_id BIGINT UNSIGNED PRIMARY KEY,run_id VARCHAR(40),
                            canonical_pattern TEXT,support_abs BIGINT UNSIGNED,support_ratio DOUBLE,
                            weighted_support DOUBLE,pattern_weight DOUBLE,pattern_weight_policy VARCHAR(80),
                            pattern_item_count INT,itemset_count INT) ENGINE=InnoDB
                            """);
                    q.execute("INSERT INTO MINING_RUN VALUES ('A','SUCCEEDED',4),('B','SUCCEEDED',1)");
                    try (var insert = c.prepareStatement("INSERT INTO PATTERN VALUES (?,?,?,5,.5,3,.4,'P',1,1)")) {
                        // Differences after byte 1024 expose MySQL's default prefix sort.
                        String prefix = "1".repeat(2000);
                        for (var row : new String[][]{{"10","A",prefix+"b"},{"2","A",prefix+"a"},
                                {"3","A",prefix+"a"},{"4","A",prefix+"c"},{"1","B","0"}}) {
                            insert.setLong(1,Long.parseLong(row[0])); insert.setString(2,row[1]);
                            insert.setString(3,row[2]); insert.executeUpdate();
                        }
                    }
                }
                var service = new PatternListService(new JdbcPatternListReader(ds));
                assertEquals(java.util.List.of("2","3"), service.list("A",0,2).items().stream().map(x->x.patternId()).toList());
                assertEquals(java.util.List.of("10","4"), service.list("A",1,2).items().stream().map(x->x.patternId()).toList());
                assertEquals("4",service.list("A",9,2).filteredTotal());
                assertTrue(service.list("A",9,2).items().isEmpty());
                assertEquals("1",service.list("B",0,2).runPatternTotal());
                assertEquals("1",service.list("B",0,2).items().getFirst().patternId());
            } finally { ddl.execute("DROP DATABASE `" + schema + "`"); }
        }
    }
}
