package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import jakarta.servlet.MultipartConfigElement;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.conversion.OrderSnapshotConverter;
import vn.edu.wspm.backend.services.mining.provider.*;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.*;
import vn.edu.wspm.backend.testsupport.*;
import vn.edu.wspm.config.MinimumSupport;

/** Real MySQL 8.4 and full target V2 constraints; no application context/demo datasource fallback. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SnapshotInputMySqlTest {
    OwnedMySql mysql;HikariDataSource pool;JdbcTemplate jdbc;
    @TempDir Path root;
    RawSnapshotStorage storage;RawSnapshotStorage.StoredRaw raw;String product;
    @BeforeAll void database() throws Exception {
        mysql=new OwnedMySql();
        try {mysql.createV2Tables();pool=mysql.pool(4);jdbc=new JdbcTemplate(pool);
            jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('C','Fixture','snapshot-fixture')");
        } catch(Exception failure){mysql.close();throw failure;}
    }
    @AfterAll void close() {if(pool!=null)pool.close();if(mysql!=null)mysql.close();}
    @BeforeEach void seed() throws Exception {
        product="P"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'C',1)",product,product,"Current catalog label",product);
        storage=new RawSnapshotStorage(root.resolve(UUID.randomUUID().toString()).toString(),new MultipartConfigElement("",10000,11000,0));
        acquire(product);
    }
    void acquire(String identity) throws Exception {
        byte[] bytes=(OrderSnapshotConverter.HEADER+"\nO,1,c,2026-01-01T00:00:00.000000Z,COMPLETED,"+identity+",Historical SKU,Historical label,1,1.00,0.00,1.00,VND\n").getBytes(StandardCharsets.UTF_8);
        raw=storage.save(new ByteArrayInputStream(bytes));
        String report="{\"contractVersion\":\"wdm-order-csv-v1\",\"conversionStatus\":\"NOT_STARTED\",\"statisticsStatus\":\"NOT_COMPUTED\",\"provenance\":{\"acquisition\":\"CSV_UPLOAD\",\"sourceTimeZone\":null,\"timeField\":\"ordered_at\",\"statusPolicy\":\"COMPLETED_CURRENT_AT_ACQUISITION\",\"rawSizeBytes\":\""+bytes.length+"\"}}";
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,raw_file_path,raw_sha256,validation_status,conversion_report_json,sequence_count) VALUES(?,'CSV','upload.csv',?,?,'PENDING',?,0)",raw.snapshotId(),raw.reference(),raw.sha256(),report);
    }
    TrustedRawSnapshotProvider provider(SnapshotInputReader reader) {
        return new TrustedRawSnapshotProvider(reader,storage,new Configuration(new OrderSnapshotConverter.Limits(10000,20,300,20000),
                MinimumSupport.absolute(.4),OrderSnapshotConverter.POLICY),m->Optional.empty(),Optional::empty);
    }
    @Test void binarySnapshotLookupAndRealCollationAliasAreDifferentContracts() throws Exception {
        var reader=new JdbcSnapshotInputReader(pool);
        var row=reader.read(raw.snapshotId(),Set.of(product,product.toLowerCase(Locale.ROOT),"missing-product"));
        var metadata=row.metadata().orElseThrow();
        assertEquals(raw.sha256(),metadata.rawHash());assertEquals(Long.toString(raw.sizeBytes()),metadata.rawSize());
        assertNull(metadata.sourceTimeZone());assertEquals("CSV_UPLOAD",metadata.acquisition());
        assertEquals(List.of(product),row.productMatches().get(product));
        assertEquals(List.of(product),row.productMatches().get(product.toLowerCase(Locale.ROOT)));
        assertEquals(List.of(),row.productMatches().get("missing-product"));
        assertTrue(reader.read(raw.snapshotId().toUpperCase(Locale.ROOT),Set.of()).metadata().isEmpty());
        assertTrue(reader.read(raw.snapshotId()+" ",Set.of()).metadata().isEmpty());
        String collation=jdbc.queryForObject("SELECT COLLATION(product_id) FROM PRODUCT WHERE product_id=?",String.class,product);
        assertTrue(collation.endsWith("_ai_ci"),"Review actual V2/container collation before changing alias assertions");
    }
    @Test void providerRejectsActualAliasAndMissingProductWithoutChangingHistoricalLabels() throws Exception {
        var reader=new JdbcSnapshotInputReader(pool);
        var original=provider(reader).prepare(raw.snapshotId());
        assertEquals(State.BLOCKED,original.state());
        assertEquals("Historical label",original.converted().items().getFirst().nameSnapshot());
        acquire(product.toLowerCase(Locale.ROOT));
        assertEquals("PRODUCT_IDENTITY_COLLISION",assertThrows(ProviderException.class,()->provider(reader).prepare(raw.snapshotId())).code());
        acquire("absent-product");
        assertEquals("PRODUCT_NOT_FOUND",assertThrows(ProviderException.class,()->provider(reader).prepare(raw.snapshotId())).code());
    }
    @Test void metadataAndCatalogShareReadViewThenFreshReadSeesCommit() throws Exception {
        var reached=new CountDownLatch(1);var committed=new CountDownLatch(1);var used=new AtomicBoolean();
        var readerConnection=new AtomicLong();
        var source=new ObservedDataSource(pool,new ObservedDataSource.Observer(){
            @Override public void closedResult(String sql,Connection c) throws SQLException {
                if(!sql.contains("FROM DATASET_SNAPSHOT") || !used.compareAndSet(false,true))return;
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());assertTrue(c.isReadOnly());assertFalse(c.getAutoCommit());
                readerConnection.set(id(c));reached.countDown();
                try{if(!committed.await(15,TimeUnit.SECONDS))throw new SQLException("Commit barrier timeout");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new SQLException("Interrupted");}
            }
        });
        var executor=Executors.newSingleThreadExecutor();
        var future=executor.submit(()->new JdbcSnapshotInputReader(source).read(raw.snapshotId(),Set.of(product)));
        try {
            assertTrue(reached.await(15,TimeUnit.SECONDS));
            try(var writer=pool.getConnection()) {
                assertNotEquals(readerConnection.get(),id(writer));writer.setAutoCommit(false);
                try(var q=writer.prepareStatement("UPDATE DATASET_SNAPSHOT SET source_name='committed.csv' WHERE snapshot_id=?")) {q.setString(1,raw.snapshotId());q.executeUpdate();}
                try(var q=writer.prepareStatement("UPDATE PRODUCT SET product_id=? WHERE product_id=?")) {q.setString(1,product+"X");q.setString(2,product);q.executeUpdate();}
                writer.commit();
            }
            committed.countDown();var old=future.get(15,TimeUnit.SECONDS);
            assertEquals("upload.csv",old.metadata().orElseThrow().sourceName());assertEquals(List.of(product),old.productMatches().get(product));
            var fresh=new JdbcSnapshotInputReader(pool).read(raw.snapshotId(),Set.of(product));
            assertEquals("committed.csv",fresh.metadata().orElseThrow().sourceName());assertTrue(fresh.productMatches().get(product).isEmpty());
        } finally {committed.countDown();future.cancel(true);executor.shutdownNow();assertTrue(executor.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void providerDetectsCommittedMetadataMutationBetweenPhases() {
        var changed=new AtomicBoolean();
        var observed=new ObservedDataSource(pool,new ObservedDataSource.Observer(){
            @Override public void closedResult(String sql,Connection c) throws SQLException {
                if(!sql.contains("FROM DATASET_SNAPSHOT") || !changed.compareAndSet(false,true))return;
                try(var writer=pool.getConnection()) {
                    assertNotEquals(id(c),id(writer));
                    try(var q=writer.prepareStatement("UPDATE DATASET_SNAPSHOT SET source_name='new-name.csv' WHERE snapshot_id=?")){q.setString(1,raw.snapshotId());q.executeUpdate();}
                }
            }
        });
        assertEquals("METADATA_CHANGED",assertThrows(ProviderException.class,()->provider(new JdbcSnapshotInputReader(observed)).prepare(raw.snapshotId())).code());
    }
    @Test void requiresNewDoesNotInheritOuterReadCommittedAndPoolRestoresStateAfterFailure() throws Exception {
        var outer=new TransactionTemplate(new DataSourceTransactionManager(pool));outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var innerId=new AtomicLong();
        var observed=new ObservedDataSource(pool,new ObservedDataSource.Observer(){
            @Override public void queried(String sql,Connection c) throws SQLException {
                assertEquals(Connection.TRANSACTION_REPEATABLE_READ,c.getTransactionIsolation());assertTrue(c.isReadOnly());innerId.set(id(c));
            }
        });
        outer.execute(tx->{
            var c=DataSourceUtils.getConnection(pool);
            try {
                long outerId=id(c);assertTrue(new JdbcSnapshotInputReader(observed).read(raw.snapshotId(),Set.of(product)).metadata().isPresent());
                assertNotEquals(outerId,innerId.get());assertEquals(Connection.TRANSACTION_READ_COMMITTED,c.getTransactionIsolation());
            } catch(SQLException e){throw new IllegalStateException(e);}finally{DataSourceUtils.releaseConnection(c,pool);}return null;
        });
        try(var single=mysql.pool(1)) {
            long physical;try(var c=single.getConnection()){physical=id(c);}
            new JdbcSnapshotInputReader(single).read(raw.snapshotId(),Set.of(product));assertRestored(single,physical);
            var failure=new ObservedDataSource(single,new ObservedDataSource.Observer(){
                @Override public void queried(String sql,Connection c)throws SQLException{throw new SQLException("Owned test failure after real SELECT");}
            });
            assertThrows(org.springframework.dao.DataAccessException.class,()->new JdbcSnapshotInputReader(failure).read(raw.snapshotId(),Set.of()));
            assertRestored(single,physical);
        }
    }
    static long id(Connection c)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SELECT CONNECTION_ID()")){r.next();return r.getLong(1);}}
    static void assertRestored(HikariDataSource ds,long physical)throws SQLException{
        try(var c=ds.getConnection()){assertEquals(physical,id(c));assertTrue(c.getAutoCommit());assertFalse(c.isReadOnly());assertEquals(Connection.TRANSACTION_READ_COMMITTED,c.getTransactionIsolation());}
    }
}
