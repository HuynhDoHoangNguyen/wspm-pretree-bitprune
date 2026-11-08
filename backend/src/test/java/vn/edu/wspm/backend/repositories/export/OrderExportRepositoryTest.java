package vn.edu.wspm.backend.repositories.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Real Spring JDBC/transaction wiring on disposable MySQL, never the development DB. */
@SpringJUnitConfig(OrderExportRepositoryTest.Config.class)
@Testcontainers
@Transactional
class OrderExportRepositoryTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_export_test")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");

    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-11-01T00:00:00Z");
    private static final OffsetDateTime TO = FROM.plusDays(1);

    @Autowired JdbcTemplate jdbc;
    @Autowired OrderExportRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(OrderExportRepository.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @BeforeAll
    static void createDisposableSchemaFromActualV2Definitions(@Autowired JdbcTemplate jdbc) throws IOException {
        String v2 = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        // Only exact CREATE/ALTER definitions. Never execute V1 rename/copy/drop or seed.
        var statements = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int definitions = 0;
        while (statements.find()) {
            jdbc.execute(statements.group());
            definitions++;
        }
        assertEquals(47, definitions); // 45 CREATEs + 2 final FK ALTERs
        vn.edu.wspm.backend.testsupport.ExecutionSchema.applyOwnershipOnly(jdbc);
        assertEquals(45, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
                """, Integer.class));
        jdbc.update("INSERT INTO CATEGORY(category_id, name, slug) VALUES ('category', 'Test', 'test')");
        jdbc.update("""
                INSERT INTO PRODUCT(product_id, sku, name, slug, category_id, current_price)
                VALUES ('product-laptop', 'CURRENT-SKU', 'Current catalog name', 'laptop', 'category', 999.99)
                """);
        jdbc.update("""
                INSERT INTO CUSTOMER(customer_id, anonymous_code)
                VALUES ('customer-A', 'anon-A'), ('customer-B', 'anon-B')
                """);
    }

    @Test
    void selectsHalfOpenPeriodAndExactCompletedStatusUsingOrderTime() {
        order("before", "customer-A", FROM.minusNanos(1_000), "COMPLETED", FROM);
        order("start", "customer-A", FROM, "COMPLETED", TO.plusDays(2));
        order("last", "customer-A", TO.minusNanos(1_000), "COMPLETED", TO.plusDays(2));
        order("end", "customer-A", TO, "COMPLETED", TO.plusHours(1));
        long id = 1;
        for (String order : List.of("before", "start", "last", "end")) {
            line(order, Long.toString(id++));
        }
        for (String status : List.of("PENDING_PAYMENT", "PAID", "PROCESSING", "CANCELLED",
                "EXPIRED", "RETURNED", "REFUNDED", "completed")) {
            String order = "excluded-" + status;
            order(order, "customer-A", FROM.plusHours(1), status, null);
            line(order, Long.toString(id++));
        }
        assertEquals(List.of("start", "last"), repository.findCompletedInPeriod(FROM, TO)
                .stream().map(OrderExportRow::orderId).toList());
    }

    @Test
    void projectsEveryHistoricalFieldWithoutCurrentCatalogSubstitution() {
        order("order-a", "customer-A", FROM, "COMPLETED", TO);
        String historicalName = "Laptop Việt, \"bản cũ\"\r\nDòng thứ hai";
        jdbc.update("""
                INSERT INTO ORDER_ITEM(order_item_id, order_id, product_id, quantity,
                    list_unit_price, allocated_discount_amount, line_total, sku_snapshot, product_name_snapshot)
                VALUES (9007199254740993, 'order-a', 'product-laptop', 2,
                    15000000.00, 1000000.00, 29000000.00, '=OLD-SKU', ?)
                """, historicalName);
        OrderExportRow expected = new OrderExportRow("order-a", new BigInteger("9007199254740993"),
                "anon-A", FROM.toInstant(), "COMPLETED", "product-laptop", "=OLD-SKU",
                historicalName, 2, new BigDecimal("15000000.00"), new BigDecimal("1000000.00"),
                new BigDecimal("29000000.00"), "VND");
        var rows = repository.findCompletedInPeriod(FROM, TO);
        assertEquals(List.of(expected), rows);
        assertThrows(UnsupportedOperationException.class, () -> rows.add(expected));
    }

    @Test
    void preservesFullUnsignedRangesAndExactDecimals() {
        order("unsigned", "customer-A", FROM, "COMPLETED", null);
        jdbc.update("""
                INSERT INTO ORDER_ITEM(order_item_id, order_id, product_id, quantity,
                    list_unit_price, allocated_discount_amount, line_total, sku_snapshot, product_name_snapshot)
                VALUES (18446744073709551615, 'unsigned', 'product-laptop', 4294967295,
                    0.01, 0.00, 42949672.95, 'HISTORICAL', 'History')
                """);
        var row = repository.findCompletedInPeriod(FROM, TO).getFirst();
        assertEquals(new BigInteger("18446744073709551615"), row.orderItemId());
        assertEquals(4294967295L, row.quantity());
        assertEquals(new BigDecimal("42949672.95"), row.lineTotal());
    }

    @Test
    void ordersByCodePointsAndOrderAndNumericLineTieBreaks() {
        // Default accent/case-insensitive MySQL collation would place 'a' before 'Z'.
        customer("sort-customer-upper", "Z-customer");
        customer("sort-customer-lower", "a-customer");
        order("Z-order", "sort-customer-upper", FROM, "COMPLETED", null);
        order("a-order", "sort-customer-upper", FROM, "COMPLETED", null);
        order("other-customer", "sort-customer-lower", FROM.plusNanos(1_000), "COMPLETED", null);
        line("Z-order", "10");
        line("other-customer", "1");
        line("Z-order", "9");
        line("a-order", "2");
        var rows = repository.findCompletedInPeriod(FROM, TO);
        assertEquals(List.of("9", "10", "2", "1"), rows.stream()
                .map(row -> row.orderItemId().toString()).toList());
        assertEquals(rows, repository.findCompletedInPeriod(FROM, TO));
        assertEquals("product-laptop", rows.get(0).productId());
        assertEquals(rows.get(0).productId(), rows.get(1).productId());
    }

    @Test
    void sortsSupplementaryUnicodeByCodePointRatherThanJavaUtf16Order() {
        String bmpOrder = "\uE000-order";
        String supplementaryOrder = "\uD800\uDC00-order";
        order(supplementaryOrder, "customer-A", FROM, "COMPLETED", null);
        order(bmpOrder, "customer-A", FROM, "COMPLETED", null);
        line(supplementaryOrder, "1");
        line(bmpOrder, "2");
        assertEquals(List.of(bmpOrder, supplementaryOrder), repository.findCompletedInPeriod(FROM, TO)
                .stream().map(OrderExportRow::orderId).toList());
    }

    @Test
    @ResourceLock("java.util.TimeZone.default")
    void normalizesOffsetsAndRoundTripsMicrosecondsWithNonUtcJvmAndSession() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            jdbc.execute("SET time_zone = '+09:00'");
            assertEquals("+09:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
            OffsetDateTime timestamp = FROM.plusHours(3).plusNanos(123_456_000);
            order("microseconds", "customer-A", timestamp, "COMPLETED", null);
            line("microseconds", "1");
            var rows = repository.findCompletedInPeriod(
                    FROM.withOffsetSameInstant(ZoneOffset.ofHours(7)),
                    TO.withOffsetSameInstant(ZoneOffset.ofHours(-5)));
            assertEquals(1, rows.size());
            assertEquals(Instant.parse("2026-11-01T03:00:00.123456Z"), rows.getFirst().orderedAt());
            assertEquals(List.of(), repository.findCompletedInPeriod(FROM, timestamp));
            assertEquals(rows, repository.findCompletedInPeriod(timestamp, timestamp.plusNanos(1_000)));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void boundedReadRejectsOverflowInsteadOfReturningTruncatedRows() {
        order("bounded", "customer-A", FROM, "COMPLETED", null);
        line("bounded", "1");
        line("bounded", "2");
        assertThrows(OrderExportRepository.ResultLimitExceededException.class,
                () -> repository.findCompletedInPeriod(FROM, TO, 1));
        assertEquals(repository.findCompletedInPeriod(FROM, TO),
                repository.findCompletedInPeriod(FROM, TO, 2));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findCompletedInPeriod(FROM, TO, 0));
        assertThrows(IllegalArgumentException.class,
                () -> repository.findCompletedInPeriod(FROM, TO, Integer.MAX_VALUE));
    }

    @Test
    void returnsAnEmptyListForAValidPeriodWithoutRows() {
        assertEquals(List.of(), repository.findCompletedInPeriod(FROM, TO));
    }

    @Test
    void rejectsInvalidBoundsWithoutRoundingOrClamping() {
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(null, TO));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(FROM, null));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(FROM, FROM));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(TO, FROM));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(FROM.plusNanos(1), TO));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(FROM, TO.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(
                OffsetDateTime.parse("0999-12-31T23:59:59Z"), TO));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(
                FROM, OffsetDateTime.parse("+10000-01-01T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(
                OffsetDateTime.MIN, TO));
        assertThrows(IllegalArgumentException.class, () -> repository.findCompletedInPeriod(
                FROM, OffsetDateTime.MAX));
    }

    @Test
    void refusesToSilentlyDropAnEligibleOrderWithoutHistoryLines() {
        order("no-lines", "customer-A", FROM, "COMPLETED", null);
        assertThrows(IllegalStateException.class, () -> repository.findCompletedInPeriod(FROM, TO));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void materializesImmutableRowsInsideARepeatableReadDespiteConcurrentCommit() {
        OffsetDateTime start = FROM.plusYears(1);
        order("concurrent", "customer-A", start, "COMPLETED", null);
        line("concurrent", "800");
        TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setReadOnly(true);
        TransactionTemplate writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<OrderExportRow> captured = new ArrayList<>();
        snapshot.executeWithoutResult(ignored -> {
            captured.addAll(repository.findCompletedInPeriod(start, start.plusDays(1)));
            assertEquals(1, captured.size());
            writer.executeWithoutResult(other -> jdbc.update(
                    "UPDATE ORDERS SET status = 'REFUNDED' WHERE order_id = 'concurrent'"));
            assertEquals(captured, repository.findCompletedInPeriod(start, start.plusDays(1)));
        });
        assertEquals("COMPLETED", captured.getFirst().status());
        assertEquals(List.of(), repository.findCompletedInPeriod(start, start.plusDays(1)));
    }

    private void customer(String id, String code) {
        jdbc.update("INSERT INTO CUSTOMER(customer_id, anonymous_code) VALUES (?, ?)", id, code);
    }

    private void order(String id, String customer, OffsetDateTime orderedAt,
            String status, OffsetDateTime completedAt) {
        jdbc.update("""
                INSERT INTO ORDERS(order_id, order_number, customer_id, ordered_at,
                                   completed_at, status, source, currency_code)
                VALUES (?, ?, ?, ?, ?, ?, 'IMPORT', 'VND')
                """, id, id, customer, LocalDateTime.ofInstant(orderedAt.toInstant(), ZoneOffset.UTC),
                completedAt == null ? null : LocalDateTime.ofInstant(completedAt.toInstant(), ZoneOffset.UTC), status);
    }

    private void line(String orderId, String lineId) {
        jdbc.update("""
                INSERT INTO ORDER_ITEM(order_item_id, order_id, product_id, quantity, list_unit_price,
                                       allocated_discount_amount, line_total, sku_snapshot, product_name_snapshot)
                VALUES (?, ?, 'product-laptop', 1, 100.00, 0.00, 100.00, 'OLD-SKU', 'Old product name')
                """, new BigInteger(lineId), orderId);
    }
}
