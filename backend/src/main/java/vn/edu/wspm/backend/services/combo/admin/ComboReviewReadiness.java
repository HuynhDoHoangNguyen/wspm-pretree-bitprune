package vn.edu.wspm.backend.services.combo.admin;

import java.math.BigInteger;
import java.util.*;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;

/** Shared read-side diagnostics for IMP-12. Never authorizes or substitutes for its transactional validator. */
public final class ComboReviewReadiness {
    public Assessment assess(Bundle bundle) {
        return new vn.edu.wspm.backend.services.combo.review.ComboReviewRules().assess(bundle,
                vn.edu.wspm.backend.services.combo.review.ComboReview.Policy.unconfirmed(),
                vn.edu.wspm.backend.services.combo.review.ComboReview.Evidence.unverified());
    }
    /** Persisted diagnostics only. Shared rules add policy/evidence/lock gates for read and submit. */
    public Assessment diagnostics(Bundle bundle) {
        var row=bundle.row();var issues=new LinkedHashSet<String>();var warnings=new LinkedHashSet<String>();
        if(!Set.of("DRAFT","APPROVED","REJECTED","PUBLISHED","ARCHIVED").contains(Objects.toString(row.comboStatus(),""))) issues.add("UNKNOWN_COMBO_STATUS");
        if(!Set.of("DRAFT","ACTIVE","ARCHIVED").contains(Objects.toString(row.batchStatus(),""))) issues.add("UNKNOWN_BATCH_STATUS");
        if(!Set.of("SAME_ORDER_BUNDLE","SEQUENTIAL_CROSS_SELL","MIXED_SEQUENCE").contains(Objects.toString(row.comboKind(),""))) issues.add("UNKNOWN_COMBO_KIND");
        if(row.runId()==null || row.runStatus()==null) issues.add("RUN_CONTEXT_MISSING");
        var primary=bundle.sources().stream().filter(Source::primary).toList();
        String primaryId=primary.size()==1 ? primary.getFirst().patternId():null;
        if(primary.size()!=1) issues.add("PRIMARY_SOURCE_INVALID");
        if(bundle.sources().isEmpty()) issues.add("SOURCES_MISSING");
        for(var source:bundle.sources()) {
            if(source.sourceRunId()==null || source.canonicalPattern()==null || !usableMetrics(source.metrics())) issues.add("SOURCE_DATA_MISSING");
            if(!Objects.equals(row.runId(),source.sourceRunId())) issues.add("SOURCE_RUN_MISMATCH");
        }
        if(primary.size()==1 && !sameMetrics(row.metrics(),primary.getFirst().metrics())) issues.add("PRIMARY_METRICS_MISMATCH");
        if(primary.size()==1 && !Objects.equals(row.patternWeightPolicy(),primary.getFirst().patternWeightPolicy())) issues.add("WEIGHT_POLICY_MISMATCH");
        if(bundle.items().isEmpty()) issues.add("ITEMS_MISSING");
        var products=new HashSet<String>();var positions=new HashSet<String>();
        for(var item:bundle.items()) {
            if(item.itemsetOrder()<=0 || item.itemOrder()<=0 || !positions.add(item.itemsetOrder()+":"+item.itemOrder()) || !products.add(item.productId())) issues.add("ITEM_STRUCTURE_INVALID");
            if(item.nameSnapshot()==null || item.skuSnapshot()==null) issues.add("DICTIONARY_SNAPSHOT_MISSING");
        }
        var audit=row.review();
        if("DRAFT".equals(row.comboStatus())) {
            if(audit.reviewedBy()!=null || audit.reviewedAt()!=null || audit.reviewNote()!=null) issues.add("REVIEW_AUDIT_INCONSISTENT");
            if(!"DRAFT".equals(row.batchStatus())) issues.add("BATCH_NOT_REVIEWABLE");
        } else if(Set.of("APPROVED","REJECTED","PUBLISHED").contains(Objects.toString(row.comboStatus(),""))) {
            if(audit.reviewedBy()==null || audit.reviewedBy().isBlank() || audit.reviewedAt()==null
                    || "REJECTED".equals(row.comboStatus()) && (audit.reviewNote()==null || audit.reviewNote().isBlank())) issues.add("REVIEW_AUDIT_INCONSISTENT");
        } else if("ARCHIVED".equals(row.comboStatus()) && (audit.reviewedBy()==null || audit.reviewedAt()==null)) warnings.add("REVIEW_HISTORY_UNAVAILABLE");
        return new Assessment(primaryId,Integer.toString(bundle.sources().size()),List.of(),
                issues.stream().map(Issue::new).toList(),warnings.stream().map(Issue::new).toList());
    }
    private boolean sameMetrics(Metrics a,Metrics b) {
        if(a==null || b==null) return false;
        try {
            return new BigInteger(a.supportAbs()).equals(new BigInteger(b.supportAbs()))
                    && finiteEqual(a.supportRatio(),b.supportRatio()) && finiteEqual(a.patternWeight(),b.patternWeight());
        } catch(NumberFormatException | NullPointerException ex) { return false; }
    }
    private boolean finiteEqual(Double a,Double b) { return a!=null && b!=null && Double.isFinite(a) && Double.isFinite(b) && a.doubleValue()==b.doubleValue(); }
    private boolean usableMetrics(Metrics m) {
        if(m==null || m.supportRatio()==null || m.patternWeight()==null) return false;
        try {
            return new BigInteger(m.supportAbs()).signum()>=0 && Double.isFinite(m.supportRatio())
                    && m.supportRatio()>=0 && m.supportRatio()<=1 && Double.isFinite(m.patternWeight());
        } catch(NumberFormatException | NullPointerException ex) { return false; }
    }
}
