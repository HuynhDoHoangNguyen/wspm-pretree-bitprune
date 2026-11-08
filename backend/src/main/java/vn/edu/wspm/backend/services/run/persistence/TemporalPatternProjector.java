package vn.edu.wspm.backend.services.run.persistence;

import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.backend.services.run.persistence.PatternPersistenceProjector.Row;

/** Structural projection only: no mining, canonical parsing or temporal-support verification. */
public final class TemporalPatternProjector {
    public static final int MAX_TEMPORAL_ROWS = 100_000;
    public static final long TEMPORAL_ROW_BUDGET = 32;
    private TemporalPatternProjector() {}

    public record ItemRow(int itemsetOrder, int itemOrder, int itemId) {}
    public record PatternRows(Row parent, List<ItemRow> items) {
        public PatternRows { Objects.requireNonNull(parent); items = List.copyOf(items); }
        @Override public String toString() { return "TemporalPatternRows[count=" + items.size() + "]"; }
    }
    public static final class Batch {
        private final PatternPersistenceProjector.Batch parents;
        private final List<PatternRows> patterns;
        private final int rowCount;
        private Batch(PatternPersistenceProjector.Batch parents, List<PatternRows> patterns, int rowCount) {
            this.parents = parents; this.patterns = List.copyOf(patterns); this.rowCount = rowCount;
        }
        public PatternPersistenceProjector.Batch parents() { return parents; }
        public List<PatternRows> patterns() { return patterns; }
        public int rowCount() { return rowCount; }
        @Override public String toString() { return "TemporalPatternBatch[rows=" + rowCount + "]"; }
    }

    public static Batch project(PatternPersistenceProjector.Batch parents) {
        long rows = 0, bytes = 0;
        for (var parent : parents.rows()) {
            rows = Math.addExact(rows, parent.itemCount());
            bytes = Math.addExact(bytes, parent.canonical().getBytes(StandardCharsets.UTF_8).length + 256L);
        }
        bytes = Math.addExact(bytes, Math.multiplyExact(rows, TEMPORAL_ROW_BUDGET));
        if (rows > MAX_TEMPORAL_ROWS || bytes > PatternPersistenceProjector.MAX_PROJECTED_BYTES) throw invalid();
        var originals = new HashMap<String, Pattern>();
        parents.context().result().supports().keySet().forEach(p -> originals.put(p.serialize(), p));
        var patterns = new ArrayList<PatternRows>();
        for (var parent : parents.rows()) {
            var original = Objects.requireNonNull(originals.get(parent.canonical()));
            var items = new ArrayList<ItemRow>();
            for (int set = 0; set < original.size(); set++) {
                var values = original.itemsets().get(set).items();
                for (int item = 0; item < values.size(); item++) items.add(new ItemRow(set + 1, item + 1, values.get(item).id()));
            }
            if (!original.equals(reconstruct(parent, items, parents.context().products()))) throw invalid();
            patterns.add(new PatternRows(parent, items));
        }
        return new Batch(parents, patterns, Math.toIntExact(rows));
    }

    /** Validate raw order BEFORE Itemset's canonicalization can conceal a malformed stored structure. */
    public static Pattern reconstruct(Row parent, List<ItemRow> rows, Map<Integer, String> products) {
        if (parent == null || rows == null || products == null || rows.isEmpty()
                || rows.size() > MAX_TEMPORAL_ROWS || rows.size() != parent.itemCount()) throw invalid();
        var sets = new ArrayList<Itemset>(); var items = new ArrayList<Item>();
        int currentSet = 1, nextItem = 1, lastId = 0;
        for (var row : rows) {
            if (row == null || row.itemsetOrder() <= 0 || row.itemOrder() <= 0 || row.itemId() <= 0
                    || !products.containsKey(row.itemId())) throw invalid();
            if (row.itemsetOrder() != currentSet) {
                if (row.itemsetOrder() != currentSet + 1 || items.isEmpty()) throw invalid();
                sets.add(new Itemset(items)); items = new ArrayList<>(); currentSet++; nextItem = 1; lastId = 0;
            }
            if (row.itemOrder() != nextItem || row.itemId() <= lastId) throw invalid();
            items.add(new Item(row.itemId())); lastId = row.itemId(); nextItem++;
        }
        sets.add(new Itemset(items));
        var pattern = new Pattern(sets);
        String canonical = pattern.serialize();
        if (pattern.size() != parent.itemsetCount() || !canonical.equals(parent.canonical())
                || !ConversionArtifactCodec.sha256(canonical.getBytes(StandardCharsets.UTF_8)).equals(parent.hash())) throw invalid();
        return pattern;
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Temporal projection is inconsistent or exceeds supported bounds"); }
}
