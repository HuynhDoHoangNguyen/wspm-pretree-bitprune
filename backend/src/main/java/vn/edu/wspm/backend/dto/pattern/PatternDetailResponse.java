package vn.edu.wspm.backend.dto.pattern;

import java.util.List;

public record PatternDetailResponse(String runId, String patternId, String canonicalPattern,
        String supportAbs, Double supportRatio, Double weightedSupport, Double patternWeight,
        String patternWeightPolicy, long patternItemCount, long itemsetCount,
        String sequenceCount, IntegrityStatus integrityStatus, List<Issue> issues,
        List<Itemset> itemsets) {
    public PatternDetailResponse { issues = List.copyOf(issues); itemsets = List.copyOf(itemsets); }
    public enum IntegrityStatus { COMPLETE, INCOMPLETE }
    public enum MappingStatus { MAPPED, MISSING }
    public record Issue(String code, Long itemsetOrder, Long itemOrder, Long itemId) { }
    public record Itemset(long itemsetOrder, List<Item> items) {
        public Itemset { items = List.copyOf(items); }
    }
    public record Item(long itemOrder, long itemId, MappingStatus mappingStatus,
            String productId, String nameSnapshot, String skuSnapshot, Double itemWeight) { }
}
