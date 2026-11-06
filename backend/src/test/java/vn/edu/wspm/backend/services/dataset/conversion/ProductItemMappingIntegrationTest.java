package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.ProductItemMappingStore;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.wspm.backend.services.dataset.conversion.ProductItemMappingServiceTest.proof;

/** Real V2 DDL and Spring transaction proxies on disposable MySQL. No development datasource. */
@SpringJUnitConfig(ProductItemMappingIntegrationTest.Config.class)
@Testcontainers
class ProductItemMappingIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("mapping_test");
    @Autowired RacingJdbc jdbc;
    @Autowired ProductItemMappingService service;
    @Autowired DataSourceTransactionManager transactions;

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()); }
        @Bean RacingJdbc jdbc(DataSource source) { return new RacingJdbc(source); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ProductItemMappingStore store(RacingJdbc jdbc) { return new ProductItemMappingStore(jdbc); }
        @Bean ProductItemMappingService service(ProductItemMappingStore store) { return new ProductItemMappingService(store); }
    }

    /** Forces both workers to observe the same MAX once; retries use ordinary JDBC. */
    static class RacingJdbc extends JdbcTemplate {
        volatile CyclicBarrier barrier;
        final AtomicInteger rendezvous = new AtomicInteger();
        RacingJdbc(DataSource source) { super(source); }
        @Override public <T> T queryForObject(String sql, Class<T> type) {
            T value = super.queryForObject(sql, type);
            if (sql.startsWith("SELECT COALESCE(MAX(item_id)") && rendezvous.getAndDecrement() > 0) {
                try { barrier.await(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException("Test workers failed to rendezvous", failure); }
            }
            return value;
        }
    }

    @BeforeAll static void schema(@Autowired RacingJdbc jdbc) throws Exception {
        var sql = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(sql);
        int count = 0;
        while (definitions.find()) { jdbc.execute(definitions.group()); count++; }
        assertEquals(47, count);
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES ('cat','Test','test')");
        for (String id : List.of("a", "b", "c", "d", "é", " exact ")) {
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES (?,?,?,?,'cat',1,'DISCONTINUED')",
                    id, id, id, id);
        }
    }

    @BeforeEach void resetOwnFixture() { jdbc.barrier = null; jdbc.rendezvous.set(0); jdbc.update("DELETE FROM ITEM_MAPPING"); }

    @Test void reuseAcrossSnapshotsAndInputPermutations() {
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('a',12),('b',27)");
        assertEquals(Map.of("a", 12, "b", 27), service.resolve(proof("b", "a", "a")).productToItem());
        assertEquals(Map.of("b", 27, "c", 28), service.resolve(proof("c", "b")).productToItem());
        assertEquals(Map.of("c", 28, "a", 12), service.resolve(proof("a", "c")).productToItem());
        assertEquals(3, count());
    }

    @Test void newBatchUsesSortedExactProductIdentity() {
        assertEquals(Map.of("a", 2, "b", 3, " exact ", 1), service.resolve(proof("b", "a", " exact ")).productToItem());
        assertEquals(Map.of("a", 2, "b", 3), service.resolve(proof("b", "a")).productToItem());
        assertEquals(3, count());
    }

    @Test void collationAliasesAndMissingProductsRollbackWholeBatch() {
        for (String invalid : List.of("B", "e\u0301", "b ", "missing")) {
            assertEquals(ErrorCode.INVALID_RESOURCE_STATE,
                    assertThrows(ApplicationException.class, () -> service.resolve(proof("a", invalid))).errorCode());
            assertEquals(0, count());
        }
    }

    @Test void exhaustionRollsBackEarlierInsertAndExistingMaxCanBeReused() {
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('d',2147483646)");
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(ApplicationException.class, () -> service.resolve(proof("a", "b"))).errorCode());
        assertEquals(1, count());
        assertEquals(Map.of("a", Integer.MAX_VALUE), service.resolve(proof("a")).productToItem());
        assertEquals(Map.of("a", Integer.MAX_VALUE), service.resolve(proof("a")).productToItem());
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,
                assertThrows(ApplicationException.class, () -> service.resolve(proof("b"))).errorCode());
        assertEquals(2, count());
    }

    @Test void existingMappingWithCollationOnlyProductAliasFailsClosed() {
        // V2's FK follows DB collation; application identity must remain exact.
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('A',12)");
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE,
                assertThrows(ApplicationException.class, () -> service.resolve(proof("a"))).errorCode());
        assertEquals(1, count());
        assertEquals("A", jdbc.queryForObject("SELECT product_id FROM ITEM_MAPPING", String.class));
    }

    @Test void unsignedOutOfCoreRangeFailsBeforeNarrowingOrWriting() {
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('d',4294967295)");
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE,
                assertThrows(ApplicationException.class, () -> service.resolve(proof("a"))).errorCode());
        assertEquals(1, count());
        assertEquals(4294967295L, jdbc.queryForObject("SELECT item_id FROM ITEM_MAPPING WHERE product_id='d'", Long.class));
    }

    @Test void v2ConstraintsRejectZeroNegativeCollisionAndMissingProduct() {
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('a',0)"));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('a',-1)"));
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('a',1)");
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('b',1)"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES ('missing',2)"));
        assertEquals(1, count());
    }

    @Test void mappingTransactionCommitsIndependentlyOfCallerRollback() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertEquals(Map.of("a", 1), service.resolve(proof("a")).productToItem());
            status.setRollbackOnly();
        });
        assertEquals(Map.of("a", 1), service.resolve(proof("a")).productToItem());
    }

    @Test void concurrentSameProductOnEmptyTableIsReusedAfterRetry() throws Exception { race(new String[]{"a"}, new String[]{"a"}, 1); }
    @Test void concurrentDifferentProductsOnEmptyTableCannotCollide() throws Exception { race(new String[]{"a"}, new String[]{"b"}, 2); }
    @Test void concurrentOverlappingBatchesAreAtomicAndConsistent() throws Exception { race(new String[]{"a", "b"}, new String[]{"b", "c"}, 3); }

    private void race(String[] left, String[] right, int expected) throws Exception {
        jdbc.barrier = new CyclicBarrier(2); jdbc.rendezvous.set(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.resolve(proof(left)));
            var second = workers.submit(() -> service.resolve(proof(right)));
            for (var dictionary : List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS))) {
                dictionary.productToItem().forEach((product, item) -> assertEquals(item,
                        jdbc.queryForObject("SELECT item_id FROM ITEM_MAPPING WHERE product_id=?", Integer.class, product)));
            }
        } finally { jdbc.rendezvous.set(0); jdbc.barrier = null; }
        assertEquals(expected, count());
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(DISTINCT item_id) FROM ITEM_MAPPING", Integer.class));
    }

    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM ITEM_MAPPING", Integer.class); }
}
