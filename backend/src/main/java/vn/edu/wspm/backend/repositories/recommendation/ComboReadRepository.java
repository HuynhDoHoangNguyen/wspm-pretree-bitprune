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
            BigDecimal currentPrice) {}

    static final String ACTIVE_BATCH_SQL = """
            SELECT b.batch_id, b.run_id, r.snapshot_id, r.algorithm, b.ranking_policy,
                   b.pattern_weight_policy, b.top_k, d.sequence_count
            FROM COMBO_BATCH b
            JOIN MINING_RUN r ON r.run_id = b.run_id
            JOIN DATASET_SNAPSHOT d ON d.snapshot_id = r.snapshot_id
            WHERE CAST(b.status AS BINARY) = CAST('ACTIVE' AS BINARY)
            ORDER BY CAST(b.batch_id AS BINARY) LIMIT 2
            """;

    // V2 has no versioned prefix proof/policy. A persisted anchor boolean alone cannot
    // establish sequential prefix semantics. Exclude sequential kinds BEFORE LIMIT,
    // for both list and detail, until the mining owner supplies an approved contract.
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
              AND CAST(c.combo_kind AS BINARY) = CAST('SAME_ORDER_BUNDLE' AS BINARY)
              AND (c.valid_from IS NULL OR c.valid_from <= ?)
              AND (c.valid_to IS NULL OR c.valid_to > ?)
              AND (SELECT COUNT(*) FROM COMBO_SOURCE proof
                   WHERE proof.combo_id = c.combo_id AND proof.is_primary = TRUE) = 1
              AND EXISTS (SELECT 1 FROM COMBO_ITEM present WHERE present.combo_id = c.combo_id)
            """;
    static final String TOP_K_SQL = ELIGIBLE_SQL + """
              AND EXISTS (SELECT 1 FROM COMBO_ITEM anchor
                  WHERE anchor.combo_id = c.combo_id
                    AND CAST(anchor.product_id AS BINARY) = CAST(? AS BINARY)
                    AND anchor.is_anchor_eligible = TRUE)
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
                       p.product_id, p.name, p.sku, p.category_id, p.current_price,
                       (SELECT image.image_url FROM PRODUCT_IMAGE image
                        WHERE image.product_id = p.product_id
                        ORDER BY image.display_order, CAST(image.image_id AS BINARY) LIMIT 1) AS image_url
                FROM COMBO_ITEM i JOIN PRODUCT p ON p.product_id = i.product_id
                WHERE i.combo_id IN (
                """ + placeholders + ") ORDER BY CAST(i.combo_id AS BINARY), i.itemset_order, i.item_order";
        return jdbc.query(sql, statement -> {
            for (int i = 0; i < comboIds.size(); i++) statement.setString(i + 1, comboIds.get(i));
        }, (r, n) -> new ItemRow(r.getString("combo_id"), r.getLong("itemset_order"),
                r.getLong("item_order"), r.getLong("quantity"), r.getBoolean("is_anchor_eligible"),
                r.getBoolean("is_discount_eligible"), r.getBigDecimal("unit_price_snapshot"),
                r.getString("product_id"), r.getString("name"), r.getString("sku"),
                r.getString("category_id"), r.getString("image_url"), r.getBigDecimal("current_price")));
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
