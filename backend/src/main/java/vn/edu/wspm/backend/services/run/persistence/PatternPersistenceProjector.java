package vn.edu.wspm.backend.services.run.persistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;

/** Bounded application projection. No candidate generation, pruning or temporal verification. */
public final class PatternPersistenceProjector {
    public static final int MAX_PATTERNS = 10_000;
    public static final long MAX_PROJECTED_BYTES = 8L * 1024 * 1024;
    public static final int MAX_CANONICAL_BYTES = 65_535;
    private PatternPersistenceProjector() {}

    public record Row(String canonical, String hash, long support, double ratio, double weightedSupport,
                      int itemCount, int itemsetCount, double weight, String policy) {
        @Override public String toString() { return "PatternPersistenceRow[items=" + itemCount + "]"; }
    }

    /** Only this projector constructs a batch; callers cannot replace validated rows. */
    public static final class Batch {
        private final PatternPersistenceContext context;
        private final List<Row> rows;
        private Batch(PatternPersistenceContext context, List<Row> rows) {
            this.context = context; this.rows = List.copyOf(rows);
        }
        public PatternPersistenceContext context() { return context; }
        public List<Row> rows() { return rows; }
        @Override public String toString() { return "PatternPersistenceBatch[count=" + rows.size() + "]"; }
    }

    public static Batch project(PatternPersistenceContext context) {
        var result = context.result();
        if (result.patternCount() > MAX_PATTERNS) throw invalid();
        var rows = new ArrayList<Row>(); var hashes = new HashSet<String>();
        long bytes = 0;
        for (var entry : result.supports().entrySet()) {
            var pattern = entry.getKey(); int support = entry.getValue();
            long length = 0;
            for (var itemset : pattern.itemsets()) for (var item : itemset.items()) {
                length = Math.addExact(length, 1);
                if (item.id() <= 0 || !context.products().containsKey(item.id())) throw invalid();
            }
            int itemCount = Math.toIntExact(length);
            if (itemCount <= 0 || pattern.length() != itemCount || pattern.size() <= 0
                    || !result.minimumSupport().meetsThreshold(support, result.globalMeanWeight())) throw invalid();
            byte[] canonical = pattern.serialize().getBytes(StandardCharsets.UTF_8);
            bytes = Math.addExact(bytes, Math.addExact(canonical.length, 256L));
            if (canonical.length > MAX_CANONICAL_BYTES || bytes > MAX_PROJECTED_BYTES) throw invalid();
            String hash = ConversionArtifactCodec.sha256(canonical);
            if (!hashes.add(hash)) throw invalid();
            double ratio = (double) support / result.minimumSupport().numberOfSequences();
            double weighted = support * result.globalMeanWeight();
            if (!Double.isFinite(ratio) || ratio <= 0 || ratio > 1 || !Double.isFinite(weighted)) throw invalid();
            rows.add(new Row(new String(canonical, StandardCharsets.UTF_8), hash, support, ratio, weighted,
                    itemCount, pattern.size(), PatternWeightPolicy.calculate(pattern, context.weights()), PatternWeightPolicy.VERSION));
        }
        rows.sort(Comparator.comparing(Row::hash));
        return new Batch(context, rows);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Pattern projection exceeds supported bounds or is inconsistent"); }
}
