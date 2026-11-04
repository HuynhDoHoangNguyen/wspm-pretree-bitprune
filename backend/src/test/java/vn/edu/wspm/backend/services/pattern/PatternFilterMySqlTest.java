package vn.edu.wspm.backend.services.pattern;

import java.sql.DriverManager;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput;
import vn.edu.wspm.backend.repositories.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="PATTERN_TEST_DISPOSABLE",matches="YES_CREATE_DROP_SCHEMA")
class PatternFilterMySqlTest {
    @Test void realSearchAllItemsBoundsCapabilityAndTotals() throws Exception {
        String server=System.getenv("PATTERN_TEST_SERVER");
        if(server==null || !server.matches("jdbc:mysql://[A-Za-z0-9.\\-]+:[0-9]+/"))
            throw new IllegalArgumentException("Disposable test requires server-only URL");
        String user=System.getenv("PATTERN_TEST_USER"),password=System.getenv("PATTERN_TEST_PASSWORD");
        String schema="pattern_filter_test_"+UUID.randomUUID().toString().replace("-","");
        try(var admin=DriverManager.getConnection(server,user,password);var ddl=admin.createStatement()) {
            ddl.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4");
            try {
                var ds=new DriverManagerDataSource(server+schema,user,password);
                try(var c=ds.getConnection();var q=c.createStatement()) {
                    q.execute("CREATE TABLE MINING_RUN(run_id VARCHAR(40) PRIMARY KEY,status VARCHAR(32),pattern_count BIGINT) ENGINE=InnoDB");
                    q.execute("""
                            CREATE TABLE PATTERN(pattern_id BIGINT UNSIGNED PRIMARY KEY,run_id VARCHAR(40),
                            canonical_pattern TEXT,support_abs BIGINT UNSIGNED,support_ratio DOUBLE,
                            weighted_support DOUBLE,pattern_weight DOUBLE NULL,pattern_weight_policy VARCHAR(80),
                            pattern_item_count INT,itemset_count INT) ENGINE=InnoDB
                            """);
                    q.execute("CREATE TABLE PATTERN_ITEM(pattern_id BIGINT UNSIGNED,itemset_order INT,item_order INT,item_id INT,PRIMARY KEY(pattern_id,itemset_order,item_order)) ENGINE=InnoDB");
                    q.execute("CREATE TABLE RUN_ITEM_STATISTIC(run_id VARCHAR(40),item_id INT,product_id VARCHAR(40),product_name_snapshot VARCHAR(200),sku_snapshot VARCHAR(80),PRIMARY KEY(run_id,item_id)) ENGINE=InnoDB");
                    q.execute("INSERT INTO MINING_RUN VALUES ('A','SUCCEEDED',5),('B','SUCCEEDED',0),('C','RUNNING',NULL)");
                    q.execute("INSERT INTO PATTERN VALUES (101,'A','<(1 2)>',4,.4,2.4,.5,'TEST',2,1),(102,'A','<(1)(2)>',3,.3,1.8,.4,'TEST',2,2),(103,'A','<(1)>',5,.5,3,.7,'TEST',1,1),(104,'A','<(1)(1 2)>',2,.2,1.2,NULL,'TEST',3,2),(105,'A','<(3)>',1,.1,.6,.2,'TEST',1,1)");
                    q.execute("INSERT INTO PATTERN_ITEM VALUES (101,1,1,1),(101,1,2,2),(102,1,1,1),(102,2,1,2),(103,1,1,1),(104,1,1,1),(104,2,1,1),(104,2,2,2),(105,1,1,3)");
                    q.execute("INSERT INTO RUN_ITEM_STATISTIC VALUES ('A',1,'PA','Chuột A','SKU-A'),('A',2,'PB','Bàn phím B','SKU-B'),('A',3,'PC','Literal%_','SKU-C'),('B',1,'OTHER','Other','OTHER')");
                }
                var list=new PatternListService(new JdbcPatternListReader(ds,Set.of("TEST")));
                var all=new PatternFilterInput(List.of("1","2","1"),null,null,null,null);
                var result=list.list("A",0,2,all);
                assertEquals("5",result.runPatternTotal()); assertEquals("3",result.filteredTotal());
                assertEquals(List.of("101","102"),result.items().stream().map(x->x.patternId()).toList());
                assertEquals("104",list.list("A",1,2,all).items().getFirst().patternId());
                var combined=list.list("A",0,20,new PatternFilterInput(List.of("1","2"),"3","4","0.4","0.5"));
                assertEquals("2",combined.filteredTotal());
                assertEquals("1",list.list("A",0,20,new PatternFilterInput(List.of(),"2",null,null,"0.4")).filteredTotal());
                assertEquals("0",list.list("A",0,20,new PatternFilterInput(List.of("2","3"),null,null,null,null)).filteredTotal());
                assertEquals("0",list.list("B",0,20).runPatternTotal());
                assertEquals(PatternReadException.Code.ITEM_NOT_IN_RUN_DICTIONARY,assertThrows(PatternReadException.class,
                        ()->list.list("B",0,20,all)).getCode());
                var denied=new PatternListService(new JdbcPatternListReader(ds));
                assertFalse(denied.list("A",0,20).weightFilter().enabled());
                assertEquals(PatternReadException.Code.WEIGHT_FILTER_UNAVAILABLE,assertThrows(PatternReadException.class,
                        ()->denied.list("A",0,20,new PatternFilterInput(List.of(),null,null,"0",null))).getCode());
                assertEquals("NOT_READY",denied.list("C",0,20,all).resultState().name());
                var dictionary=new DictionaryService(new JdbcDictionaryReader(ds));
                assertEquals("Chuột A",dictionary.search("A","CHUOT",0,20).items().getFirst().nameSnapshot());
                assertEquals("Other",dictionary.search("B","",0,20).items().getFirst().nameSnapshot());
                assertEquals("1",dictionary.search("A","%_",0,20).matchedTotal());
                assertEquals("1",dictionary.search("A","sku-b",0,20).matchedTotal());
                // Missing unselected dictionary must not remove a Pattern from the ALL predicate.
                try(var c=ds.getConnection();var q=c.createStatement()) { q.execute("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id='A' AND item_id=3"); }
                assertEquals("5",list.list("A",0,20).filteredTotal());
            } finally { ddl.execute("DROP DATABASE `"+schema+"`"); }
        }
    }
}
