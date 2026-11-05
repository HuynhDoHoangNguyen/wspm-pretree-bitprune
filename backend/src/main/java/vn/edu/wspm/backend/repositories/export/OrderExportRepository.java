package vn.edu.wspm.backend.repositories.export;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** One consistent, parameterized historical read; no catalog value substitution. */
@Repository
public class OrderExportRepository {
    private static final Logger log = LoggerFactory.getLogger(OrderExportRepository.class);
    private static final Instant MIN_TIME = LocalDateTime.of(1000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    private static final Instant MAX_TIME_EXCLUSIVE = LocalDateTime.of(10000, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);

    private static final String SQL = """
            SELECT o.order_id, i.order_item_id, c.anonymous_code AS customer_code,
                   o.ordered_at, o.status, i.product_id, i.sku_snapshot,
                   i.product_name_snapshot, i.quantity, i.list_unit_price,
                   i.allocated_discount_amount, i.line_total, o.currency_code
            FROM ORDERS o
            JOIN CUSTOMER c ON c.customer_id = o.customer_id
            LEFT JOIN ORDER_ITEM i ON i.order_id = o.order_id
            WHERE o.status = 'COMPLETED'
              AND CAST(o.status AS BINARY) = CAST('COMPLETED' AS BINARY)
              AND o.ordered_at >= ? AND o.ordered_at < ?
            ORDER BY CAST(c.anonymous_code AS BINARY), o.ordered_at,
                     CAST(o.order_id AS BINARY), i.order_item_id
            """;

    private final JdbcTemplate jdbc;

    public OrderExportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bounds have explicit offsets and microsecond precision; storage is UTC DATETIME(6).
     * The returned list is materialized inside the transaction and cannot be modified.
     * No rows are silently omitted for eligible orders without historical lines.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OrderExportRow> findCompletedInPeriod(OffsetDateTime fromTime, OffsetDateTime toTime) {
        return readCompletedInPeriod(fromTime, toTime, null);
    }

    /** Bounded endpoint read: one extra row detects overflow; never returns truncated success. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<OrderExportRow> findCompletedInPeriod(OffsetDateTime fromTime, OffsetDateTime toTime, int maxRows) {
        if (maxRows < 1 || maxRows == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Row limit must be positive and allow a sentinel row");
        }
        return readCompletedInPeriod(fromTime, toTime, maxRows);
    }

    private List<OrderExportRow> readCompletedInPeriod(OffsetDateTime fromTime, OffsetDateTime toTime, Integer maxRows) {
        LocalDateTime fromUtc = utcBound(fromTime);
        LocalDateTime toUtc = utcBound(toTime);
        if (!fromUtc.isBefore(toUtc)) {
            throw new IllegalArgumentException("fromTime must precede toTime");
        }

        List<OrderExportRow> rows = jdbc.query(maxRows == null ? SQL : SQL + " LIMIT ?", statement -> {
            // LocalDateTime is intentional: do not introduce JVM/session timezone conversion
            // by binding java.sql.Timestamp or reading a DATETIME as a JDBC Instant.
            statement.setObject(1, fromUtc);
            statement.setObject(2, toUtc);
            if (maxRows != null) {
                statement.setInt(3, maxRows + 1);
            }
        }, (result, rowNumber) -> {
            String lineId = result.getString("order_item_id");
            if (lineId == null) {
                throw new IllegalStateException("Eligible order has no historical order lines");
            }
            return new OrderExportRow(
                    result.getString("order_id"), new BigInteger(lineId),
                    result.getString("customer_code"),
                    result.getObject("ordered_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    result.getString("status"), result.getString("product_id"),
                    result.getString("sku_snapshot"), result.getString("product_name_snapshot"),
                    result.getLong("quantity"), result.getBigDecimal("list_unit_price"),
                    result.getBigDecimal("allocated_discount_amount"),
                    result.getBigDecimal("line_total"), result.getString("currency_code"));
        });
        if (maxRows != null && rows.size() > maxRows) {
            throw new ResultLimitExceededException();
        }
        log.debug("Order period export read completed: rowCount={}", rows.size());
        return List.copyOf(rows);
    }

    public static class ResultLimitExceededException extends RuntimeException { }

    private static LocalDateTime utcBound(OffsetDateTime value) {
        if (value == null || value.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("Period bounds require timestamps with microsecond precision or less");
        }
        Instant instant = value.toInstant();
        if (instant.isBefore(MIN_TIME) || !instant.isBefore(MAX_TIME_EXCLUSIVE)) {
            throw new IllegalArgumentException("Period bounds are outside the MySQL DATETIME range");
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
