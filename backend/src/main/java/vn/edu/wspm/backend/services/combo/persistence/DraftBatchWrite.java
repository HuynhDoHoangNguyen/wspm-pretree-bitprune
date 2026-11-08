package vn.edu.wspm.backend.services.combo.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview;

/** Proposed row mapping, not a database receipt or approved persistence contract. */
public final class DraftBatchWrite {
    private DraftBatchWrite() { }
    public record Position(long itemsetOrder,long itemOrder) { }
    public record ItemValues(String productId,long quantity,BigDecimal unitPriceSnapshot,
            String priceEvidenceRef,boolean discountEligible) { }
    public record Fields(String comboId,String name,String explanation,String currency,
            BigDecimal referenceOriginalPrice,BigDecimal referenceSalePrice,String priceEvidenceRef,
            String discountType,Map<Position,ItemValues> items) {
        public Fields { items=Map.copyOf(items); }
    }
    public record Request(Result grouping,Context context,ComboPreview.Result preview,String writeIdentity,
            String batchId,String createdBy,Instant createdAt,Map<StructureKey,Fields> fields,Bounds bounds) {
        public Request(Result grouping,Context context,ComboPreview.Result preview,String identity,String batchId,String actor,Instant at,Map<StructureKey,Fields> fields){this(grouping,context,preview,identity,batchId,actor,at,fields,null);}
        public Request { fields=Map.copyOf(fields); }
    }
    public record Bounds(int minimum,int maximum) {}
    public record BatchRow(String batchId,String runId,String rankingPolicy,String patternWeightPolicy,
            BigInteger topK,boolean categoryFilterEnabled,int minProductCount,int maxProductCount,
            String status,String createdBy,Instant createdAt) { }
    public record ComboRow(String comboId,String batchId,String name,String comboKind,String status,
            String discountType,String stackingPolicy,String currencyCode,BigDecimal referenceOriginalPrice,
            BigDecimal referenceSalePrice,String supportAbs,Double supportRatio,Double patternWeight,
            String explanation,Instant createdAt) { }
    public record ItemRow(String comboId,String productId,long itemsetOrder,long itemOrder,long quantity,
            BigDecimal unitPriceSnapshot,boolean anchorEligible,boolean discountEligible) { }
    public record SourceRow(String comboId,String patternId,boolean primary) { }
    public record Plan(String writeIdentity,Context context,ComboPreview.Configuration configuration,
            BatchRow batch,List<ComboRow> combos,List<ItemRow> items,List<SourceRow> sources,
            Map<StructureKey,String> comboIds,Map<StructureKey,Fields> suppliedFields) {
        public Plan {
            combos=List.copyOf(combos);items=List.copyOf(items);sources=List.copyOf(sources);
            comboIds=Map.copyOf(comboIds);suppliedFields=Map.copyOf(suppliedFields);
        }
    }
    public record Validation(boolean valid,List<String> errors,Plan plan) {
        public Validation { errors=List.copyOf(errors); }
    }
    public record Receipt(String batchId,Map<StructureKey,String> comboIds,Instant createdAt,
                          String contentDigest,String evidenceDigest) {
        public Receipt {comboIds=Map.copyOf(comboIds);}
    }
    /** Receipt only accompanies a verified INSERTED/NO_OP outcome. Other outcomes never imply commit. */
    public record WriteResult(String status,List<String> errors,Plan proposedPlan,Receipt receipt) {
        public WriteResult { errors=List.copyOf(errors); }
        public WriteResult(String status,List<String> errors,Plan proposedPlan){this(status,errors,proposedPlan,null);}
    }
}
