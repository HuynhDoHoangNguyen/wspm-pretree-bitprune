package vn.edu.wspm.backend.dto.combo;

import java.math.BigDecimal;
import java.util.List;

/** Persisted read projection. IDs/counts never pass through floating-point numbers. */
public final class ComboAdminRead {
    private ComboAdminRead() { }
    public record Filters(String batchId,String status,String comboKind) { }
    public record Issue(String code) { }
    public record Audit(String reviewedBy,String reviewerDisplayName,String reviewedAt,String reviewNote) { }
    public record Metrics(String supportAbs,Double supportRatio,Double patternWeight) { }
    public record Row(String comboId,String name,String comboKind,String comboStatus,String batchId,String batchStatus,
            String runId,String runStatus,String createdAt,Audit review,Metrics metrics,String explanation,
            String currencyCode,BigDecimal referenceOriginalPrice,BigDecimal referenceSalePrice,
            String rankingPolicy,String patternWeightPolicy,String topK) { }
    public record Item(long itemsetOrder,long itemOrder,String productId,String quantity,BigDecimal unitPriceSnapshot,
            boolean anchorEligible,boolean discountEligible,String nameSnapshot,String skuSnapshot) { }
    public record Itemset(long itemsetOrder,List<Item> items) {
        public Itemset { items=List.copyOf(items); }
    }
    public record Source(String patternId,boolean primary,String sourceRunId,String canonicalPattern,
            Metrics metrics,Double weightedSupport,String patternWeightPolicy) { }
    public record Assessment(String primaryPatternId,String sourceCount,List<String> allowedActions,
            List<Issue> blockingIssues,List<Issue> warnings) {
        public Assessment { allowedActions=List.copyOf(allowedActions);blockingIssues=List.copyOf(blockingIssues);warnings=List.copyOf(warnings); }
    }
    public record Summary(Row data,Assessment assessment) { }
    public record Page(int page,int size,String scopeTotal,String filteredTotal,Filters appliedFilters,List<Summary> items) {
        public Page { items=List.copyOf(items); }
    }
    public record Detail(Row data,List<Itemset> itemsets,List<Source> sources,Assessment assessment) {
        public Detail { itemsets=List.copyOf(itemsets);sources=List.copyOf(sources); }
    }
}
