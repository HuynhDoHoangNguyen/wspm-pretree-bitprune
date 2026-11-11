package vn.edu.wspm.backend.repositories.recommendation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** Parameterized V2 projections: no entity mutation, lazy loading or per-combo item query. */
@Repository
public class ComboReadRepository {
    public record BatchRow(String batchId, String runId, String snapshotId, String algorithm,
            String rankingPolicy, String patternWeightPolicy, long topK, BigInteger sequenceCount) {}
    public record ComboRow(String comboId, String name, String kind, String status,
            String explanation, Instant validFrom, Instant validTo, Instant publishedAt,
            String primaryPatternId, String canonicalPattern, BigInteger supportAbs,
            double supportRatio, double patternWeight, String currencyCode,
            BigDecimal originalPrice, BigDecimal salePrice, String discountType,
            BigDecimal discountValue, BigDecimal maxDiscountAmount) {}
    public record ItemRow(String comboId, long itemsetOrder, long itemOrder, long quantity,
            boolean anchorEligible, boolean discountEligible, BigDecimal unitPriceSnapshot,
            String productId, String name, String sku, String categoryId, String imageUrl,
            BigDecimal currentPrice, String availableQuantity) {
        public ItemRow(String comboId,long itemsetOrder,long itemOrder,long quantity,boolean anchorEligible,boolean discountEligible,BigDecimal unitPriceSnapshot,
                String productId,String name,String sku,String categoryId,String imageUrl,BigDecimal currentPrice){
            this(comboId,itemsetOrder,itemOrder,quantity,anchorEligible,discountEligible,unitPriceSnapshot,productId,name,sku,categoryId,imageUrl,currentPrice,null);
        }
    }

    static final String ACTIVE_BATCH_SQL = """
            SELECT b.batch_id, b.run_id, r.snapshot_id, r.algorithm, b.ranking_policy,
                   b.pattern_weight_policy, b.top_k, d.sequence_count
            FROM COMBO_BATCH b
            JOIN MINING_RUN r ON r.run_id = b.run_id
            JOIN DATASET_SNAPSHOT d ON d.snapshot_id = r.snapshot_id
            WHERE CAST(b.status AS BINARY) = CAST('ACTIVE' AS BINARY)
            ORDER BY CAST(b.batch_id AS BINARY) LIMIT 2
            """;

    // HISTORY_PREFIX_V1: product discovery is not proof of a customer's history.
    // For temporal kinds, verify the full ordered product projection, not anchor flags.
    static final String ELIGIBLE_SQL = """
            SELECT c.combo_id, c.name, c.combo_kind, c.status, c.explanation,
                   c.valid_from, c.valid_to, c.published_at, p.pattern_id,
                   p.canonical_pattern, p.support_abs, p.support_ratio, p.pattern_weight,
                   c.currency_code, c.reference_original_price, c.reference_sale_price,
                   c.discount_type, c.discount_value, c.max_discount_amount
            FROM COMBO c
            JOIN COMBO_BATCH b ON b.batch_id = c.batch_id
            JOIN COMBO_SOURCE src ON src.combo_id = c.combo_id AND src.is_primary = TRUE
            JOIN PATTERN p ON p.pattern_id = src.pattern_id AND p.run_id = b.run_id
              AND CAST(p.pattern_weight_policy AS BINARY) = CAST(b.pattern_weight_policy AS BINARY)
            WHERE c.batch_id = ?
              AND CAST(b.status AS BINARY) = CAST('ACTIVE' AS BINARY)
              AND CAST(c.status AS BINARY) = CAST('PUBLISHED' AS BINARY)
              AND CAST(c.combo_kind AS BINARY) IN
                  (CAST('SAME_ORDER_BUNDLE' AS BINARY),CAST('SEQUENTIAL_CROSS_SELL' AS BINARY),CAST('MIXED_SEQUENCE' AS BINARY))
              AND NOT EXISTS (SELECT 1 FROM COMBO_ITEM ci
                  JOIN PRODUCT cp ON cp.product_id=ci.product_id
                  JOIN CATEGORY cc ON cc.category_id=cp.category_id
                  WHERE ci.combo_id=c.combo_id AND
                    (CAST(cp.status AS BINARY)<>CAST('ACTIVE' AS BINARY)
                     OR CAST(cc.status AS BINARY)<>CAST('ACTIVE' AS BINARY) OR cp.current_price<0
                     OR CAST(cp.currency_code AS BINARY)<>CAST(c.currency_code AS BINARY)))
              AND (c.combo_kind='SAME_ORDER_BUNDLE' OR (
                  (SELECT COUNT(*) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id)=
                  (SELECT COUNT(*) FROM COMBO_ITEM ci WHERE ci.combo_id=c.combo_id)
                  AND (SELECT COUNT(DISTINCT pi.itemset_order) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id)>1
                  AND ((c.combo_kind='SEQUENTIAL_CROSS_SELL' AND
                    (SELECT COUNT(*) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id)=
                    (SELECT COUNT(DISTINCT pi.itemset_order) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id))
                    OR (c.combo_kind='MIXED_SEQUENCE' AND
                    (SELECT COUNT(*) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id)>
                    (SELECT COUNT(DISTINCT pi.itemset_order) FROM PATTERN_ITEM pi WHERE pi.pattern_id=p.pattern_id)))
                  AND NOT EXISTS (SELECT 1 FROM COMBO_ITEM ci WHERE ci.combo_id=c.combo_id
                    AND NOT EXISTS (SELECT 1 FROM PATTERN_ITEM pi
                      JOIN RUN_ITEM_STATISTIC rs ON rs.run_id=b.run_id AND rs.item_id=pi.item_id
                      WHERE pi.pattern_id=p.pattern_id AND pi.itemset_order=ci.itemset_order
                        AND pi.item_order=ci.item_order AND CAST(rs.product_id AS BINARY)=CAST(ci.product_id AS BINARY)))))
              AND (c.valid_from IS NULL OR c.valid_from <= ?)
              AND (c.valid_to IS NULL OR c.valid_to > ?)
              AND (SELECT COUNT(*) FROM COMBO_SOURCE proof
                   WHERE proof.combo_id = c.combo_id AND proof.is_primary = TRUE) = 1
              AND EXISTS (SELECT 1 FROM COMBO_ITEM present WHERE present.combo_id = c.combo_id)
            """;
    static final String TOP_K_SQL = ELIGIBLE_SQL + """
              AND CAST(c.combo_kind AS BINARY) = CAST('SAME_ORDER_BUNDLE' AS BINARY)
              AND EXISTS (SELECT 1 FROM COMBO_ITEM anchor
                  WHERE anchor.combo_id = c.combo_id
                    AND CAST(anchor.product_id AS BINARY) = CAST(? AS BINARY)
                    AND (anchor.is_anchor_eligible = TRUE OR c.combo_kind<>'SAME_ORDER_BUNDLE'))
            ORDER BY p.support_abs DESC, p.pattern_weight DESC,
                     CAST(p.canonical_pattern AS BINARY) ASC, CAST(c.combo_id AS BINARY) ASC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    public ComboReadRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean productExists(String productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM PRODUCT WHERE CAST(product_id AS BINARY) = CAST(? AS BINARY))",
                Boolean.class, productId));
    }

    public List<BatchRow> activeBatches() {
        return jdbc.query(ACTIVE_BATCH_SQL, (r, n) -> new BatchRow(r.getString("batch_id"),
                r.getString("run_id"), r.getString("snapshot_id"), r.getString("algorithm"),
                r.getString("ranking_policy"), r.getString("pattern_weight_policy"),
                r.getLong("top_k"), integer(r, "sequence_count")));
    }

    public List<ComboRow> topK(String batchId, String productId, int k, Instant at) {
        if (k < 1 || k > 20) throw new IllegalArgumentException("Invalid read limit");
        return jdbc.query(TOP_K_SQL, statement -> {
            statement.setString(1, batchId);
            statement.setObject(2, utc(at));
            statement.setObject(3, utc(at));
            statement.setString(4, productId);
            statement.setInt(5, k);
        }, COMBO_MAPPER);
    }

    public List<ComboRow> candidates(String batchId, Instant at, int offset, boolean latest) {
        return jdbc.query(ELIGIBLE_SQL + (latest
                ? " AND CAST(c.combo_kind AS BINARY) = CAST('SAME_ORDER_BUNDLE' AS BINARY) ORDER BY c.published_at DESC, CAST(c.combo_id AS BINARY) ASC"
                : " ORDER BY p.support_abs DESC,p.pattern_weight DESC,CAST(p.canonical_pattern AS BINARY),CAST(c.combo_id AS BINARY)")
                + " LIMIT 20 OFFSET ?", statement -> {
                    statement.setString(1, batchId); statement.setObject(2, utc(at));
                    statement.setObject(3, utc(at)); statement.setInt(4, offset);
                }, COMBO_MAPPER);
    }

    public record HistoryRow(String orderId, Instant orderedAt, String productId) {}
    public List<HistoryRow> history(String authenticatedUserId) {
        return jdbc.query("""
                SELECT o.order_id,o.ordered_at,i.product_id FROM CUSTOMER c
                JOIN ORDERS o ON o.customer_id=c.customer_id
                JOIN ORDER_ITEM i ON i.order_id=o.order_id
                WHERE CAST(c.user_id AS BINARY)=CAST(? AS BINARY)
                  AND CAST(o.status AS BINARY)=CAST('COMPLETED' AS BINARY)
                ORDER BY o.ordered_at,CAST(o.order_id AS BINARY),i.order_item_id
                """, (r,n) -> new HistoryRow(r.getString("order_id"),instant(r,"ordered_at"),r.getString("product_id")), authenticatedUserId);
    }

    public List<ComboRow> detail(String batchId, String comboId, Instant at) {
        return jdbc.query(ELIGIBLE_SQL + " AND CAST(c.combo_id AS BINARY) = CAST(? AS BINARY)",
                statement -> {
                    statement.setString(1, batchId);
                    statement.setObject(2, utc(at));
                    statement.setObject(3, utc(at));
                    statement.setString(4, comboId);
                }, COMBO_MAPPER);
    }

    public List<ItemRow> items(List<String> comboIds) {
        if (comboIds.isEmpty()) return List.of();
        if (comboIds.size() > 20) throw new IllegalArgumentException("Too many combos");
        String placeholders = String.join(",", Collections.nCopies(comboIds.size(), "?"));
        String sql = """
                SELECT i.combo_id, i.itemset_order, i.item_order, i.quantity,
                       i.is_anchor_eligible, i.is_discount_eligible, i.unit_price_snapshot,
                       p.product_id, p.name, p.sku, p.category_id, p.current_price, stock.available_quantity,
                       (SELECT image.image_url FROM PRODUCT_IMAGE image
                        WHERE image.product_id = p.product_id
                        ORDER BY image.display_order, CAST(image.image_id AS BINARY) LIMIT 1) AS image_url
                FROM COMBO_ITEM i JOIN PRODUCT p ON p.product_id = i.product_id
                LEFT JOIN (
                    SELECT balance.product_id,
                           CAST(SUM(CAST(balance.on_hand_quantity AS SIGNED) - CAST(balance.reserved_quantity AS SIGNED)) AS CHAR) AS available_quantity
                    FROM INVENTORY_BALANCE balance
                    JOIN WAREHOUSE warehouse ON warehouse.warehouse_id = balance.warehouse_id
                    WHERE CAST(warehouse.status AS BINARY) = CAST('ACTIVE' AS BINARY)
                    GROUP BY balance.product_id
                ) stock ON stock.product_id = p.product_id
                WHERE i.combo_id IN (
                """ + placeholders + ") ORDER BY CAST(i.combo_id AS BINARY), i.itemset_order, i.item_order";
        return jdbc.query(sql, statement -> {
            for (int i = 0; i < comboIds.size(); i++) statement.setString(i + 1, comboIds.get(i));
        }, (r, n) -> new ItemRow(r.getString("combo_id"), r.getLong("itemset_order"),
                r.getLong("item_order"), r.getLong("quantity"), r.getBoolean("is_anchor_eligible"),
                r.getBoolean("is_discount_eligible"), r.getBigDecimal("unit_price_snapshot"),
                r.getString("product_id"), r.getString("name"), r.getString("sku"),
                r.getString("category_id"), r.getString("image_url"), r.getBigDecimal("current_price"), r.getString("available_quantity")));
    }

    private static final RowMapper<ComboRow> COMBO_MAPPER = (r, n) -> new ComboRow(
            r.getString("combo_id"), r.getString("name"), r.getString("combo_kind"),
            r.getString("status"), r.getString("explanation"), instant(r, "valid_from"),
            instant(r, "valid_to"), instant(r, "published_at"), r.getString("pattern_id"),
            r.getString("canonical_pattern"), integer(r, "support_abs"), r.getDouble("support_ratio"),
            r.getDouble("pattern_weight"), r.getString("currency_code"),
            r.getBigDecimal("reference_original_price"), r.getBigDecimal("reference_sale_price"),
            r.getString("discount_type"), r.getBigDecimal("discount_value"), r.getBigDecimal("max_discount_amount"));

    private static BigInteger integer(ResultSet r, String field) throws SQLException {
        return new BigInteger(r.getString(field));
    }
    private static Instant instant(ResultSet r, String field) throws SQLException {
        LocalDateTime value = r.getObject(field, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
