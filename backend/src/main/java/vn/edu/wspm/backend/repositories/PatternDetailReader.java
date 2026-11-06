package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.util.List;

public interface PatternDetailReader {
    Snapshot read(String runId, String patternId);
    record Header(String patternId, String runId, String canonicalPattern, String supportAbs,
            Double supportRatio, Double weightedSupport, Double patternWeight,
            String patternWeightPolicy, long patternItemCount, long itemsetCount) { }
    record Row(long itemsetOrder, long itemOrder, long itemId, boolean mapped,
            String productId, String nameSnapshot, String skuSnapshot, Double itemWeight) { }
    record Snapshot(String status, BigInteger publishedCount, BigInteger actualCount,
            String sequenceCount, Header pattern, List<Row> rows) {
        public Snapshot { rows = List.copyOf(rows); }
    }
}
