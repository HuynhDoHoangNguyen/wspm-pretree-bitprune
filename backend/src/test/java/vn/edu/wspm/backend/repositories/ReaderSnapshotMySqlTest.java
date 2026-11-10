package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput;
import vn.edu.wspm.backend.services.pattern.*;
import vn.edu.wspm.backend.services.combo.admin.ComboAdminService;
import vn.edu.wspm.backend.testsupport.ObservedDataSource;
import vn.edu.wspm.backend.testsupport.OwnedMySql;

/** Full V2 target-table declarations/constraints. Fixtures are not mining/publisher output. */
class ReaderSnapshotMySqlTest {
    @FunctionalInterface interface Mutation { void write(JdbcTemplate jdbc) throws Exception; }

    static final class Gate implements ObservedDataSource.Observer {
        final String prefix;
        final CountDownLatch reached=new CountDownLatch(1),committed=new CountDownLatch(1);
        final AtomicBoolean used=new AtomicBoolean();
        final AtomicLong readerId=new AtomicLong();
        Gate(String prefix) { this.prefix=prefix; }
        @Override public void closedResult(String sql,Connection connection) throws SQLException {
            if(!sql.strip().replaceAll("\\s+"," ").startsWith(prefix) || !used.compareAndSet(false,true)) return;
            assertFalse(connection.getAutoCommit());
            assertTrue(connection.isReadOnly());
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,connection.getTransactionIsolation());
            readerId.set(connectionId(connection));
            reached.countDown();
            try { if(!committed.await(15,TimeUnit.SECONDS)) throw new SQLException("Writer commit barrier timed out."); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new SQLException("Reader barrier interrupted."); }
        }
    }

    /** Barrier releases only after a different physical connection commits. No sleep-based ordering. */
    static <T> T concurrentRead(DataSource pool,Gate gate,Supplier<T> reader,Mutation mutation) throws Exception {
        ExecutorService worker=Executors.newSingleThreadExecutor();
        Future<T> result=worker.submit(reader::get);
        try {
            if(!gate.reached.await(15,TimeUnit.SECONDS)) {
                if(result.isDone()) result.get(1,TimeUnit.SECONDS);
                fail("Reader did not establish the gated read view.");
            }
            try(var writer=pool.getConnection()) {
                assertNotEquals(gate.readerId.get(),connectionId(writer));
                writer.setAutoCommit(false);
                try {
                    mutation.write(new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(writer,true)));
                    writer.commit();
                } catch(Exception | AssertionError failure) { writer.rollback();throw failure; }
            }
            gate.committed.countDown();
            return result.get(15,TimeUnit.SECONDS);
        } finally {
            gate.committed.countDown();result.cancel(true);worker.shutdownNow();
            assertTrue(worker.awaitTermination(15,TimeUnit.SECONDS),"Reader worker did not stop.");
        }
    }

    @Test void patternListKeepsOldStatusCountAndPageAcrossWriterCommit() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(3)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);
            pattern(jdbc,2,"<(1)>",5,.4,"TEST_ONLY",1,1);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=1 WHERE run_id='A'");
            var gate=new Gate("SELECT status, pattern_count FROM MINING_RUN");
            var service=new PatternListService(new JdbcPatternListReader(new ObservedDataSource(pool,gate)));
            var old=concurrentRead(pool,gate,()->service.list("A",0,20),writer->{
                pattern(writer,3,"<(2)>",3,.2,"TEST_ONLY",1,1);
                writer.update("UPDATE MINING_RUN SET status='FAILED',pattern_count=2 WHERE run_id='A'");
            });
            assertEquals("READY",old.resultState().name());assertEquals("SUCCEEDED",old.runStatus());
            assertEquals("1",old.runPatternTotal());assertEquals("1",old.filteredTotal());
            assertEquals(List.of("2"),old.items().stream().map(i->i.patternId()).toList());
            var fresh=service.list("A",0,20);
            assertEquals("FAILED",fresh.resultState().name());assertNull(fresh.runPatternTotal());assertTrue(fresh.items().isEmpty());
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id='A'",Integer.class));
            assertEquals(2L,jdbc.queryForObject("SELECT pattern_count FROM MINING_RUN WHERE run_id='A'",Long.class));
        }
    }

    @Test void detailKeepsHeaderItemsetsDictionaryAndSequenceCountFromFirstReadView() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(3)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);
            pattern(jdbc,2,"<(1)(2)>",5,.4,"TEST_ONLY",2,2);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=1 WHERE run_id='A'");
            jdbc.update("INSERT INTO PATTERN_ITEM VALUES(2,1,1,1),(2,2,1,2)");
            var gate=new Gate("SELECT r.status,r.pattern_count,s.sequence_count");
            var service=new PatternDetailService(new JdbcPatternDetailReader(new ObservedDataSource(pool,gate)));
            var old=concurrentRead(pool,gate,()->service.detail("A","2"),writer->{
                writer.update("UPDATE DATASET_SNAPSHOT SET sequence_count=20 WHERE snapshot_id='S'");
                writer.update("UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='New snapshot' WHERE run_id='A' AND item_id=1");
                writer.update("UPDATE PATTERN SET canonical_pattern='<(1 2)>',canonical_pattern_hash=SHA2('<(1 2)>',256),itemset_count=1 WHERE pattern_id=2");
                writer.update("DELETE FROM PATTERN_ITEM WHERE pattern_id=2");
                writer.update("INSERT INTO PATTERN_ITEM VALUES(2,1,1,1),(2,1,2,2)");
            });
            assertEquals("10",old.sequenceCount());assertEquals("<(1)(2)>",old.canonicalPattern());
            assertEquals(2,old.itemsets().size());assertEquals("Old snapshot",old.itemsets().getFirst().items().getFirst().nameSnapshot());
            assertEquals("COMPLETE",old.integrityStatus().name());
            var fresh=service.detail("A","2");
            assertEquals("20",fresh.sequenceCount());assertEquals("<(1 2)>",fresh.canonicalPattern());
            assertEquals(1,fresh.itemsets().size());assertEquals(2,fresh.itemsets().getFirst().items().size());
            assertEquals("New snapshot",fresh.itemsets().getFirst().items().getFirst().nameSnapshot());
            assertEquals("COMPLETE",fresh.integrityStatus().name());
        }
    }

    @Test void membershipCapabilityFilteredCountAndPageCannotMixVersions() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(3)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);
            pattern(jdbc,2,"<(1 2)>",4,.5,"TEST_ONLY",2,1);
            pattern(jdbc,3,"<(1)(2)>",3,.4,"TEST_ONLY",2,2);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=2 WHERE run_id='A'");
            jdbc.update("INSERT INTO PATTERN_ITEM VALUES(2,1,1,1),(2,1,2,2),(3,1,1,1),(3,2,1,2)");
            var gate=new Gate("SELECT pattern_weight_policy,pattern_weight FROM PATTERN");
            var service=new PatternListService(new JdbcPatternListReader(new ObservedDataSource(pool,gate),Set.of("TEST_ONLY")));
            var filter=new PatternFilterInput(List.of("1","2"),"3",null,"0.4",null);
            var old=concurrentRead(pool,gate,()->service.list("A",0,20,filter),writer->{
                writer.update("UPDATE PATTERN SET pattern_weight_policy='OTHER_TEST',pattern_weight=.1,support_abs=1,canonical_pattern='<(1)>',canonical_pattern_hash=SHA2('<(1)>',256),pattern_item_count=1,itemset_count=1 WHERE pattern_id=3");
                writer.update("DELETE FROM PATTERN_ITEM WHERE pattern_id=3");writer.update("INSERT INTO PATTERN_ITEM VALUES(3,1,1,1)");
            });
            assertTrue(old.weightFilter().enabled());assertEquals("TEST_ONLY",old.weightFilter().policy());
            assertEquals("2",old.runPatternTotal());assertEquals("2",old.filteredTotal());
            assertEquals(List.of("2","3"),old.items().stream().map(i->i.patternId()).toList());
            assertEquals("3",old.items().get(1).supportAbs());assertEquals(.4,old.items().get(1).patternWeight());
            var fresh=service.list("A",0,20);
            assertFalse(fresh.weightFilter().enabled());assertEquals("MIXED_OR_MISSING_POLICY",fresh.weightFilter().reason());
            assertEquals("1",service.list("A",0,20,new PatternFilterInput(List.of("1","2"),null,null,null,null)).filteredTotal());
            assertEquals(PatternReadException.Code.WEIGHT_FILTER_UNAVAILABLE,assertThrows(PatternReadException.class,()->service.list("A",0,20,filter)).getCode());
        }
    }

    @Test void comboCountsPageAuditItemsAndSourcesStayOnTheSameReadView() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(3)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);
            pattern(jdbc,2,"<(1)(2)>",5,.4,"TEST_ONLY",2,2);
            pattern(jdbc,3,"<(1 2)>",2,.2,"TEST_ONLY",2,1);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=2 WHERE run_id='A'");
            jdbc.update("INSERT INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES('reviewer','fixture-reviewer','unusable-test-only','Fixture reviewer','ACTIVE')");
            jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k) VALUES('B','A','TEST_RANK','TEST_ONLY',1)");
            combo(jdbc,"Z");
            jdbc.update("INSERT INTO COMBO_ITEM(combo_id,product_id,itemset_order,item_order,unit_price_snapshot,is_anchor_eligible) VALUES('Z','P1',1,1,1,true),('Z','P2',2,1,1,false)");
            jdbc.update("INSERT INTO COMBO_SOURCE VALUES('Z',2,true),('Z',3,false)");
            var gate=new Gate("SELECT COUNT(*) FROM COMBO c");
            var reader=new JdbcComboAdminReader(new ObservedDataSource(pool,gate));
            var service=new ComboAdminService(reader);
            var old=concurrentRead(pool,gate,()->reader.list(new vn.edu.wspm.backend.dto.combo.ComboAdminRead.Filters("B","DRAFT",null),0,20),writer->{
                combo(writer,"a");
                writer.update("UPDATE COMBO SET status='APPROVED',reviewed_by='reviewer',reviewed_at='2026-11-05 00:00:00.123456',review_note='Fixture review' WHERE combo_id='Z'");
                writer.update("UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='New snapshot' WHERE run_id='A' AND item_id=1");
                writer.update("DELETE FROM COMBO_SOURCE WHERE combo_id='Z' AND pattern_id=3");
            });
            assertEquals("1",old.scopeTotal());assertEquals("1",old.filteredTotal());
            var bundle=old.bundles().getFirst();assertEquals("Z",bundle.row().comboId());assertEquals("DRAFT",bundle.row().comboStatus());
            assertNull(bundle.row().review().reviewedBy());assertEquals(2,bundle.sources().size());
            assertEquals("Old snapshot",bundle.items().getFirst().nameSnapshot());
            var fresh=service.list("B","DRAFT",null,0,20);
            assertEquals("2",fresh.scopeTotal());assertEquals("1",fresh.filteredTotal());assertEquals("a",fresh.items().getFirst().data().comboId());
            var detail=service.detail("Z");assertEquals("APPROVED",detail.data().comboStatus());
            assertEquals("reviewer",detail.data().review().reviewedBy());assertEquals("1",detail.assessment().sourceCount());
            assertEquals("New snapshot",detail.itemsets().getFirst().items().getFirst().nameSnapshot());
        }
    }

    @Test void pooledSessionSortLengthAndTransactionStateRestoreAfterSuccessAndInjectedQueryFailure() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(1)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);pattern(jdbc,2,"<(1)>",5,.4,"TEST_ONLY",1,1);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=1 WHERE run_id='A'");
            jdbc.execute("SET SESSION max_sort_length=2048");
            long id=jdbc.queryForObject("SELECT CONNECTION_ID()",Long.class);
            AtomicBoolean inject=new AtomicBoolean();
            var source=new ObservedDataSource(pool,new ObservedDataSource.Observer() {
                @Override public void queried(String sql,Connection c) throws SQLException {
                    if(sql.contains("ORDER BY support_abs DESC")) {
                        assertEquals(Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());assertTrue(c.isReadOnly());
                        if(inject.getAndSet(false)) throw new SQLException("Injected test-only page transfer failure.");
                    }
                }
            });
            var service=new PatternListService(new JdbcPatternListReader(source));
            assertEquals("1",service.list("A",0,20).filteredTotal());assertRestored(pool,id);
            inject.set(true);assertThrows(DataAccessException.class,()->service.list("A",0,20));assertRestored(pool,id);
            assertEquals("1",service.list("A",0,20).filteredTotal());assertRestored(pool,id);
        }
    }

    @Test void outerReadCommittedTransactionDoesNotWeakenRequiresNewReader() throws Exception {
        try(var mysql=new OwnedMySql();var pool=mysql.pool(3)) {
            seed(mysql,pool);var jdbc=new JdbcTemplate(pool);pattern(jdbc,2,"<(1)>",5,.4,"TEST_ONLY",1,1);
            jdbc.update("UPDATE MINING_RUN SET pattern_count=1 WHERE run_id='A'");
            var gate=new Gate("SELECT status, pattern_count FROM MINING_RUN");var source=new ObservedDataSource(pool,gate);
            var service=new PatternListService(new JdbcPatternListReader(source));
            var outer=new TransactionTemplate(new DataSourceTransactionManager(source));outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            var old=concurrentRead(pool,gate,()->outer.execute(tx->{
                var value=service.list("A",0,20);
                var connection=DataSourceUtils.getConnection(source);
                try { assertEquals(Connection.TRANSACTION_READ_COMMITTED,connection.getTransactionIsolation()); }
                catch(SQLException failure) { throw new IllegalStateException(failure); }
                finally { DataSourceUtils.releaseConnection(connection,source); }
                return value;
            }),writer->{pattern(writer,3,"<(2)>",3,.2,"TEST_ONLY",1,1);writer.update("UPDATE MINING_RUN SET pattern_count=2 WHERE run_id='A'");});
            assertEquals("1",old.runPatternTotal());assertEquals(1,old.items().size());
            assertEquals("2",service.list("A",0,20).runPatternTotal());
        }
    }

    static void seed(OwnedMySql mysql,DataSource pool) throws Exception {
        mysql.createV2Tables();var jdbc=new JdbcTemplate(pool);
        assertTrue(jdbc.queryForObject("SELECT @@version",String.class).startsWith("8.4."));
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,raw_file_path,validation_status,conversion_report_json,sequence_count) VALUES('S','DATABASE','Test-only','fixture','VALID','{}',10)");
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES('A','S','WSPM_PRETREE','SUCCEEDED','fixture','ABSOLUTE',1,1,.5,0)");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('C','Fixture','fixture')");
        for(int item=1;item<=2;item++) {
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'C',1)","P"+item,"SKU"+item,"Product "+item,"product-"+item);
            jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES('A',?,?,1,1,.5,?,?)",item,"P"+item,item==1?"Old snapshot":"Other snapshot","SKU"+item);
        }
    }
    static void pattern(JdbcTemplate jdbc,long id,String canonical,int support,double weight,String policy,int items,int groups) {
        jdbc.update("INSERT INTO PATTERN(pattern_id,run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_weight,pattern_weight_policy,pattern_item_count,itemset_count) VALUES(?,'A',?,SHA2(?,256),?,.5,1,?,?,?,?)",id,canonical,canonical,support,weight,policy,items,groups);
    }
    static void combo(JdbcTemplate jdbc,String id) {
        jdbc.update("INSERT INTO COMBO(combo_id,batch_id,name,combo_kind,support_abs,support_ratio,pattern_weight,explanation,currency_code,reference_original_price,reference_sale_price) VALUES(?,'B','Fixture','SEQUENTIAL_CROSS_SELL',5,.5,.4,'Fixture explanation','VND',2,2)",id);
    }
    static long connectionId(Connection c) throws SQLException { try(var s=c.createStatement();var rs=s.executeQuery("SELECT CONNECTION_ID()")) { rs.next();return rs.getLong(1); } }
    static void assertRestored(DataSource pool,long id) throws SQLException {
        try(var c=pool.getConnection();var s=c.createStatement();var rs=s.executeQuery("SELECT @@SESSION.max_sort_length")) {
            rs.next();assertEquals(2048,rs.getInt(1));assertEquals(id,connectionId(c));
            assertTrue(c.getAutoCommit());assertFalse(c.isReadOnly());assertEquals(Connection.TRANSACTION_READ_COMMITTED,c.getTransactionIsolation());
        }
    }
}
