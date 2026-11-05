package vn.edu.wspm.backend.services.combo.admin;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.wspm.backend.repositories.JdbcComboAdminReader;
import static org.junit.jupiter.api.Assertions.*;

/** Query fixture only. Opt-in disposable server, random owned schema; never application datasource. */
@EnabledIfEnvironmentVariable(named="COMBO_TEST_DISPOSABLE",matches="YES_CREATE_DROP_SCHEMA")
class ComboAdminMySqlTest {
    @Test void persistedQueryFiltersCountsBinaryOrderPagingAndTrace() throws Exception {
        String server=System.getenv("COMBO_TEST_SERVER");
        if(server==null || !server.matches("jdbc:mysql://[A-Za-z0-9.\\-]+:[0-9]+/")) throw new IllegalArgumentException("Server-only test URL required");
        String user=System.getenv("COMBO_TEST_USER"),password=System.getenv("COMBO_TEST_PASSWORD");
        String schema="combo_reader_test_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(server,user,password);var ddl=admin.createStatement()) {
            ddl.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                var ds=new DriverManagerDataSource(server+schema,user,password);
                try(var c=ds.getConnection();var q=c.createStatement()) {
                    // Minimal V2 column projections, deliberately not a migration/complete schema acceptance test.
                    q.execute("CREATE TABLE MINING_RUN(run_id VARCHAR(40) PRIMARY KEY,status VARCHAR(24)) ENGINE=InnoDB");
                    q.execute("CREATE TABLE COMBO_BATCH(batch_id VARCHAR(40) PRIMARY KEY,run_id VARCHAR(40),status VARCHAR(24),ranking_policy VARCHAR(80),pattern_weight_policy VARCHAR(80),top_k INT UNSIGNED) ENGINE=InnoDB");
                    q.execute("""
                        CREATE TABLE COMBO(combo_id VARCHAR(40) PRIMARY KEY,batch_id VARCHAR(40),name VARCHAR(255),
                        combo_kind VARCHAR(40),status VARCHAR(24),created_at DATETIME(6),reviewed_by VARCHAR(40),reviewed_at DATETIME(6),
                        review_note VARCHAR(1000),support_abs BIGINT UNSIGNED,support_ratio DOUBLE,pattern_weight DOUBLE,
                        explanation VARCHAR(1000),currency_code CHAR(3),reference_original_price DECIMAL(19,2),reference_sale_price DECIMAL(19,2)) ENGINE=InnoDB
                        """);
                    q.execute("""
                        CREATE TABLE COMBO_ITEM(combo_id VARCHAR(40),itemset_order INT UNSIGNED,item_order INT UNSIGNED,product_id VARCHAR(40),
                        quantity INT UNSIGNED,unit_price_snapshot DECIMAL(19,2),is_anchor_eligible BOOLEAN,is_discount_eligible BOOLEAN,
                        PRIMARY KEY(combo_id,itemset_order,item_order),UNIQUE(combo_id,product_id)) ENGINE=InnoDB
                        """);
                    q.execute("CREATE TABLE RUN_ITEM_STATISTIC(run_id VARCHAR(40),product_id VARCHAR(40),product_name_snapshot VARCHAR(200),sku_snapshot VARCHAR(80),PRIMARY KEY(run_id,product_id)) ENGINE=InnoDB");
                    q.execute("CREATE TABLE PATTERN(pattern_id BIGINT UNSIGNED PRIMARY KEY,run_id VARCHAR(40),canonical_pattern TEXT,support_abs BIGINT UNSIGNED,support_ratio DOUBLE,pattern_weight DOUBLE,weighted_support DOUBLE,pattern_weight_policy VARCHAR(80)) ENGINE=InnoDB");
                    q.execute("CREATE TABLE COMBO_SOURCE(combo_id VARCHAR(40),pattern_id BIGINT UNSIGNED,is_primary BOOLEAN,PRIMARY KEY(combo_id,pattern_id)) ENGINE=InnoDB");
                    q.execute("INSERT INTO MINING_RUN VALUES ('R','SUCCEEDED'),('OTHER','SUCCEEDED')");
                    q.execute("INSERT INTO COMBO_BATCH VALUES ('B','R','DRAFT','RANK','W',1),('EMPTY','R','DRAFT','RANK','W',1),('B2','R','ACTIVE','RANK','W',1)");
                    try(var insert=c.prepareStatement("INSERT INTO COMBO VALUES (?,?,? ,?,?,'2026-01-01 00:00:00.123456',NULL,NULL,NULL,9007199254740993,.5,.4,'Explanation','VND',2,2)")) {
                        for(var row:new String[][]{{"a","B","DRAFT","SAME_ORDER_BUNDLE"},{"Z","B","DRAFT","SAME_ORDER_BUNDLE"},
                                {"x","B","APPROVED","SEQUENTIAL_CROSS_SELL"},{"other","B2","PUBLISHED","SAME_ORDER_BUNDLE"}}) {
                            insert.setString(1,row[0]);insert.setString(2,row[1]);insert.setString(3,"Fixture");insert.setString(4,row[3]);insert.setString(5,row[2]);insert.executeUpdate();
                        }
                    }
                    q.execute("INSERT INTO RUN_ITEM_STATISTIC VALUES ('R','L','Laptop snapshot','L'),('R','M','Mouse snapshot','M'),('OTHER','L','Wrong label','WRONG')");
                    q.execute("INSERT INTO COMBO_ITEM VALUES ('Z',2,1,'M',1,1,false,false),('Z',1,1,'L',1,1,true,false)");
                    q.execute("INSERT INTO PATTERN VALUES (18446744073709551615,'R','<(1)(2)>',9007199254740993,.5,.4,4,'W'),(2,'R','<(1)(2)>',8,.2,.8,4,'W')");
                    q.execute("INSERT INTO COMBO_SOURCE VALUES ('Z',18446744073709551615,true),('Z',2,false)");
                }
                var service=new ComboAdminService(new JdbcComboAdminReader(ds));
                var page=service.list("B","DRAFT","SAME_ORDER_BUNDLE",0,1);
                assertEquals("3",page.scopeTotal());assertEquals("2",page.filteredTotal());assertEquals("Z",page.items().getFirst().data().comboId());
                assertEquals("a",service.list("B","DRAFT","SAME_ORDER_BUNDLE",1,1).items().getFirst().data().comboId());
                assertTrue(service.list("B","DRAFT","SAME_ORDER_BUNDLE",99,1).items().isEmpty());
                assertEquals("0",service.list("EMPTY",null,null,0,20).scopeTotal());
                assertEquals("0",service.list("B","PUBLISHED",null,0,20).filteredTotal());
                assertEquals(ComboReadException.Code.BATCH_NOT_FOUND,assertThrows(ComboReadException.class,()->service.list("absent",null,null,0,20)).code());
                var detail=service.detail("Z");assertEquals(List.of(1L,2L),detail.itemsets().stream().map(i->i.itemsetOrder()).toList());
                assertEquals("Laptop snapshot",detail.itemsets().getFirst().items().getFirst().nameSnapshot());
                assertEquals("18446744073709551615",detail.assessment().primaryPatternId());assertEquals("2",detail.assessment().sourceCount());
                assertEquals("2026-01-01T00:00:00.123456Z",detail.data().createdAt());assertTrue(detail.assessment().allowedActions().isEmpty());
                try(var c=ds.getConnection();var q=c.createStatement()) { q.executeUpdate("UPDATE PATTERN SET run_id='OTHER',support_abs=1 WHERE pattern_id=18446744073709551615"); }
                var codes=service.detail("Z").assessment().blockingIssues().stream().map(i->i.code()).toList();
                assertTrue(codes.containsAll(List.of("SOURCE_RUN_MISMATCH","PRIMARY_METRICS_MISMATCH")));
            } finally { ddl.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
}
