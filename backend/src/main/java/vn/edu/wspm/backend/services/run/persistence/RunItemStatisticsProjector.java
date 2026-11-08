package vn.edu.wspm.backend.services.run.persistence;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Application projection of unchanged core statistics; no mining or weight formula implementation. */
public final class RunItemStatisticsProjector {
    public static final int MAX_ITEMS = 10_000;
    public static final long MAX_PROJECTED_BYTES = 8L * 1024 * 1024;
    public static final long ROW_BUDGET = 192;
    private RunItemStatisticsProjector() {}

    public record Row(int itemId, String productId, long occurrences, long support, double weight) {
        @Override public String toString() { return "RunItemRow[itemId=" + itemId + "]"; }
    }
    public record StoredRow(Row statistics, String name, String sku) {
        @Override public String toString() { return "StoredRunItemRow[itemId=" + statistics.itemId() + "]"; }
    }
    public static final class Batch {
        private final TemporalPatternProjector.Batch temporal;
        private final List<Row> rows;
        private Batch(TemporalPatternProjector.Batch temporal, List<Row> rows) {
            this.temporal = temporal; this.rows = List.copyOf(rows);
        }
        public TemporalPatternProjector.Batch temporal() { return temporal; }
        public List<Row> rows() { return rows; }
        public String runId() { return temporal.parents().context().identity().runId(); }
        @Override public String toString() { return "RunItemBatch[count=" + rows.size() + "]"; }
    }

    public static Batch project(TemporalPatternProjector.Batch temporal) {
        var context = Objects.requireNonNull(temporal).parents().context();
        var raw = context.statistics();
        if (raw.numberOfDistinctItems() > MAX_ITEMS
                || !raw.itemStatistics().keySet().equals(context.products().keySet())
                || !raw.itemStatistics().keySet().equals(context.weights().keySet())
                || raw.numberOfSequences() != context.result().minimumSupport().numberOfSequences()) throw invalid();
        long total = 0;
        var rows = new ArrayList<Row>(); var products = new HashSet<String>();
        for (var item : raw.itemStatistics().values()) {
            String product = context.products().get(item.itemId());
            double weight = context.weights().get(item.itemId());
            requireText(product, 40);
            if (item.itemId() <= 0 || !products.add(product) || item.occurrenceCount() <= 0
                    || item.sequenceSupport() <= 0 || item.sequenceSupport() > raw.numberOfSequences()
                    || item.sequenceSupport() > item.occurrenceCount()
                    || !Double.isFinite(weight) || weight <= 0 || weight > 1) throw invalid();
            total = Math.addExact(total, item.occurrenceCount());
            rows.add(new Row(item.itemId(), product, item.occurrenceCount(), item.sequenceSupport(), weight));
        }
        if (total != raw.totalDatabaseLength()) throw invalid();
        return new Batch(temporal, rows);
    }

    /** Actual UTF-8 strings + fixed accounting per row; not a JVM heap measurement. */
    public static List<StoredRow> validateStored(List<StoredRow> rows) {
        if (rows == null || rows.size() > MAX_ITEMS) throw invalid();
        long bytes = 0;
        for (var row : rows) {
            if (row == null || row.statistics() == null) throw invalid();
            requireText(row.statistics().productId(), 40); requireText(row.name(), 200); requireText(row.sku(), 80);
            bytes = Math.addExact(bytes, ROW_BUDGET);
            for (String text : List.of(row.statistics().productId(), row.name(), row.sku())) {
                bytes = Math.addExact(bytes, text.getBytes(StandardCharsets.UTF_8).length);
            }
            if (bytes > MAX_PROJECTED_BYTES) throw invalid();
        }
        return List.copyOf(rows);
    }

    private static void requireText(String text, int limit) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > limit) throw invalid();
        // Reject unpaired surrogates instead of letting UTF-8 encoding replace them silently.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw invalid();
            } else if (Character.isLowSurrogate(c)) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Run item statistics projection is inconsistent or exceeds bounds");
    }
}
