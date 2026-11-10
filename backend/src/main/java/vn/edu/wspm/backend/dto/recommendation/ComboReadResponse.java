package vn.edu.wspm.backend.dto.recommendation;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/** Storefront read models. Monetary strings are exact publish snapshots, never quotes. */
public final class ComboReadResponse {
    private ComboReadResponse() {}

    public record BatchProvenance(String batchId, String runId, String snapshotId,
            String algorithm, String rankingPolicy, String patternWeightPolicy) {}
    public record Provenance(BatchProvenance batch, String primaryPatternId,
            String canonicalPattern) {}
    public record Metrics(BigInteger supportAbs, double supportRatio,
            BigInteger sequenceCount, double patternWeight) {}
    public record Pricing(String basis, String currencyCode, String referenceOriginalPrice,
            String referenceSalePrice, String discountType, String discountValue,
            String maxDiscountAmount, boolean isCheckoutQuote) {}
    public record Product(String productId, String name, String sku, String categoryId,
            String imageUrl, String currentPrice, String availableQuantity) {
        public Product(String productId,String name,String sku,String categoryId,String imageUrl,String currentPrice){
            this(productId,name,sku,categoryId,imageUrl,currentPrice,null);
        }
    }
    public record Item(long itemsetOrder, long itemOrder, long quantity,
            boolean anchorEligible, boolean discountEligible,
            String unitPriceSnapshot, Product product) {}
    public record Detail(String comboId, String name, String kind, String status,
            String explanation, Instant validFrom, Instant validTo, Instant publishedAt,
            Instant evaluatedAt, Provenance provenance, Metrics metrics, Pricing pricing,
            List<Item> items) {
        public Detail { items = List.copyOf(items); }
    }
    public record Recommendation(int rank, Detail combo) {}
    public record PersonalRecommendation(int rank, int matchedItemsets, long nextItemsetOrder,
            List<String> suggestedProductIds, Detail combo) {
        public PersonalRecommendation { suggestedProductIds = List.copyOf(suggestedProductIds); }
    }
    public record Personalized(String policy, Instant evaluatedAt, List<PersonalRecommendation> items) {
        public Personalized { items = List.copyOf(items); }
    }
    public record TopK(String productId, int requestedK, int effectiveK, Instant evaluatedAt,
            BatchProvenance provenance, List<Recommendation> items) {
        public TopK { items = List.copyOf(items); }
    }
}
