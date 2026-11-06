package vn.edu.wspm.backend.services.combo.evaluation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;

/** Internal handoff only; approval/evidence must come from a trusted integration, never client claims. */
public final class CandidateEvaluation {
    private CandidateEvaluation() { }
    public enum Purpose { DEMO_CATALOG, BENCHMARK, UNCONFIRMED }
    public enum EligibilityMode { CATALOG_ONLY, INVENTORY_AWARE }
    public enum Inventory { ELIGIBLE, INELIGIBLE, UNKNOWN }
    public enum Check { STRUCTURE, MAPPING, CATALOG, PRODUCT_COUNT, REPETITION, CATEGORIES, ELIGIBILITY, METRICS }
    public enum CheckStatus { PASS, FAIL, NOT_EVALUATED }
    public enum Decision { ACCEPT, REJECT }
    public record Policy(String version,String confirmationRef,Set<String> laptopCategoryIds,
            Set<String> accessoryCategoryIds,EligibilityMode eligibilityMode) {
        public Policy {
            laptopCategoryIds=Set.copyOf(laptopCategoryIds);
            accessoryCategoryIds=Set.copyOf(accessoryCategoryIds);
        }
    }
    public record Evidence(String runId,String runStatus,String snapshotId,String snapshotValidation,
            Purpose purpose,String purposeConfirmationRef,BigInteger publishedCount,
            boolean fullRunConfirmed,String publishedEvidenceRef,boolean stableInputs,
            String catalogSnapshotRef,Instant evaluatedAt) { }
    public record DictionaryItem(String runId,long itemId,String productId,String nameSnapshot,String skuSnapshot) { }
    public record CatalogProduct(String productId,String categoryId,String productStatus,String categoryStatus,
            BigDecimal price,String currency,String version,Inventory inventory,String inventoryEvidenceRef) { }
    public record Input(Evidence evidence,Policy policy,List<PatternDetailResponse> patterns,
            List<DictionaryItem> dictionary,Map<String,CatalogProduct> catalog) {
        public Input { patterns=List.copyOf(patterns);dictionary=List.copyOf(dictionary);catalog=Map.copyOf(catalog); }
    }
    public record Reason(String code,Long itemsetOrder,Long itemOrder,Long itemId,String productId) { }
    public record Item(long itemOrder,long sourceItemId,String productId,String nameSnapshot,String skuSnapshot,
            CatalogProduct catalogEvidence,String role,String stockCheck) { }
    public record Itemset(long itemsetOrder,List<Item> items) {
        public Itemset { items=List.copyOf(items); }
    }
    /** Source object is retained unchanged, including canonical, metrics, policy and original positions. */
    public record Candidate(String status,String runId,String snapshotId,String patternId,
            PatternDetailResponse source,String evaluationPolicyVersion,String policyConfirmationRef,
            EligibilityMode eligibilityMode,String publishedEvidenceRef,String catalogSnapshotRef,Instant evaluatedAt,List<Itemset> itemsets) {
        public Candidate { itemsets=List.copyOf(itemsets); }
    }
    public record PatternResult(String patternId,Decision decision,List<Reason> reasons,
            Map<Check,CheckStatus> checks,Candidate candidate) {
        public PatternResult {
            reasons=List.copyOf(reasons);
            var ordered=new java.util.EnumMap<Check,CheckStatus>(Check.class);ordered.putAll(checks);
            checks=java.util.Collections.unmodifiableMap(ordered);
        }
    }
    public record SessionResult(boolean completed,List<String> sessionErrors,int examined,int accepted,
            int rejected,List<PatternResult> patterns) {
        public SessionResult { sessionErrors=List.copyOf(sessionErrors);patterns=List.copyOf(patterns); }
    }
}
