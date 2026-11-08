package vn.edu.wspm.backend.services.combo.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview.*;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreviewRanker;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Complete proposed mapping only. Storage/evidence/idempotency decisions are deliberately not invented. */
public final class DraftBatchPlanner {
    private static final BigInteger UINT_MAX=new BigInteger("4294967295");
    private static final BigDecimal MONEY_MAX=new BigDecimal("99999999999999999.99");

    public Validation plan(Request request) {
        var errors=new TreeSet<String>();
        if(request==null) return invalid(List.of("INPUT_INVALID"));
        if(request.grouping()==null || !request.grouping().completed() || !request.grouping().errors().isEmpty())
            errors.add("GROUPING_NOT_COMPLETED");
        if(request.preview()==null || !request.preview().completed() || !request.preview().errors().isEmpty()) errors.add("PREVIEW_NOT_COMPLETED");
        if(!text(request.writeIdentity(),200)) errors.add("WRITE_IDENTITY_REQUIRED");
        if(!id(request.batchId()) || request.createdBy()!=null && !id(request.createdBy())) errors.add("INVALID_STORAGE_ID");
        if(request.createdAt()==null || request.createdAt().getNano()%1000!=0) errors.add("INVALID_CREATED_AT");
        else {
            try {
                int year=java.time.LocalDateTime.ofInstant(request.createdAt(),java.time.ZoneOffset.UTC).getYear();
                if(year<1000 || year>9999) errors.add("INVALID_CREATED_AT");
            } catch(java.time.DateTimeException ex) { errors.add("INVALID_CREATED_AT"); }
        }
        if(!errors.isEmpty()) return invalid(errors);
        boolean selected=request.context()!=null&&vn.edu.wspm.backend.services.combo.generation.ProductSelection.flexiblePolicy(request.context().evaluationPolicyVersion());
        var bounds=request.bounds();
        if(selected&&(bounds==null||bounds.minimum()<2||bounds.maximum()<bounds.minimum()
                ||bounds.maximum()>vn.edu.wspm.backend.services.combo.generation.ProductSelection.MAX_PRODUCTS))return invalid(List.of("INVALID_PRODUCT_BOUNDS"));
        if(!selected&&bounds!=null)return invalid(List.of("UNSUPPORTED_PRODUCT_BOUNDS"));
        var cfg=request.preview().configuration();
        var verified=new ComboPreviewRanker().preview(request.grouping(),request.context(),request.preview().anchorProductId(),cfg);
        if(!verified.completed()) return invalid(verified.errors());
        if(cfg.k().compareTo(UINT_MAX)>0) errors.add("TOP_K_OUT_OF_COLUMN_RANGE");
        if(!id(request.context().runId()) || !text(cfg.rankingPolicy(),80) || !text(cfg.weightPolicy().policy(),80))
            errors.add("STORAGE_FIELD_OUT_OF_RANGE");
        if(!request.context().equals(request.preview().context())) errors.add("EVALUATION_CONTEXT_MISMATCH");
        // Zero-group persistence has not been approved. Do not create an empty batch implicitly.
        if(request.grouping().groups().isEmpty()) errors.add("EMPTY_BATCH_POLICY_UNCONFIRMED");
        var expectedFlags=new HashMap<StructureKey,GroupEligibility>();
        verified.structuralEligibility().forEach(e->expectedFlags.put(e.group().structureKey(),e));
        var suppliedFlags=new HashMap<StructureKey,GroupEligibility>();
        for(var e:request.preview().structuralEligibility()) {
            if(suppliedFlags.put(e.group().structureKey(),e)!=null) errors.add("ANCHOR_FLAGS_INVALID");
        }
        if(!expectedFlags.keySet().equals(suppliedFlags.keySet())) errors.add("ANCHOR_FLAGS_INCOMPLETE");
        var flags=new HashMap<StructureKey,java.util.Map<Position,AnchorFlag>>();
        for(var key:expectedFlags.keySet()) {
            var supplied=suppliedFlags.get(key);if(supplied==null) continue;
            var wanted=expectedFlags.get(key);
            if(!wanted.group().equals(supplied.group())) errors.add("ANCHOR_FLAGS_INVALID");
            var byPosition=new HashMap<Position,AnchorFlag>();
            for(var f:supplied.items()) if(byPosition.put(new Position(f.itemsetOrder(),f.itemOrder()),f)!=null) errors.add("ANCHOR_FLAGS_INVALID");
            var expected=new HashMap<Position,AnchorFlag>();
            wanted.items().forEach(f->expected.put(new Position(f.itemsetOrder(),f.itemOrder()),f));
            if(!expected.equals(byPosition)) errors.add("ANCHOR_FLAGS_INVALID");
            flags.put(key,byPosition);
        }
        if(!request.fields().keySet().equals(expectedFlags.keySet())) errors.add("COMBO_FIELDS_INCOMPLETE");
        var usedIds=new HashSet<String>();
        for(var e:expectedFlags.values()) {
            var group=e.group();var f=request.fields().get(group.structureKey());if(f==null) continue;
            if(!id(f.comboId()) || !usedIds.add(f.comboId())) errors.add("INVALID_STORAGE_ID");
            if(!text(f.name(),255) || !text(f.explanation(),1000) || !text(f.priceEvidenceRef(),200)
                    || f.currency()==null || !f.currency().matches("[A-Z]{3}")
                    || !money(f.referenceOriginalPrice()) || !money(f.referenceSalePrice())) errors.add("COMBO_FIELDS_INCOMPLETE");
            if(!"NONE".equals(f.discountType())) errors.add("DISCOUNT_OUT_OF_SCOPE");
            if(f.referenceOriginalPrice()!=null && f.referenceSalePrice()!=null
                    && f.referenceOriginalPrice().compareTo(f.referenceSalePrice())!=0) errors.add("PRICE_TERMS_INCONSISTENT");
            var positions=new HashSet<Position>();
            BigDecimal total=BigDecimal.ZERO;
            for(var set:group.itemsets()) for(var item:set.items()) {
                if(set.itemsetOrder()<=0 || set.itemsetOrder()>4294967295L || item.itemOrder()<=0 || item.itemOrder()>4294967295L
                        || !id(item.productId())) errors.add("STORAGE_FIELD_OUT_OF_RANGE");
                var position=new Position(set.itemsetOrder(),item.itemOrder());positions.add(position);
                var value=f.items().get(position);
                if(value==null || !item.productId().equals(value.productId()) || value.quantity()<=0 || value.quantity()>4294967295L
                        || !money(value.unitPriceSnapshot()) || !text(value.priceEvidenceRef(),200)) errors.add("ITEM_FIELDS_INCOMPLETE");
                else if(item.catalogEvidence()==null || !java.util.Objects.equals(f.currency(),item.catalogEvidence().currency())
                        || item.catalogEvidence().price()==null || value.unitPriceSnapshot().compareTo(item.catalogEvidence().price())!=0)
                    errors.add("PRICE_SNAPSHOT_MISMATCH");
                if(value!=null && value.discountEligible()) errors.add("DISCOUNT_OUT_OF_SCOPE");
                if(value!=null&&value.unitPriceSnapshot()!=null)total=total.add(value.unitPriceSnapshot().multiply(BigDecimal.valueOf(value.quantity())));
            }
            if(selected&&(positions.size()<bounds.minimum()||positions.size()>bounds.maximum()))errors.add("PRODUCT_COUNT_OUT_OF_RANGE");
            if(selected&&(!money(total)||f.referenceOriginalPrice()==null||total.compareTo(f.referenceOriginalPrice())!=0))errors.add("PRICE_TERMS_INCONSISTENT");
            if(!positions.equals(f.items().keySet())) errors.add("ITEM_FIELDS_INCOMPLETE");
        }
        if(!errors.isEmpty()) return invalid(errors);

        var combos=new ArrayList<ComboRow>();var items=new ArrayList<ItemRow>();var sources=new ArrayList<SourceRow>();
        var ids=new HashMap<StructureKey,String>();
        // Entire validated structural enumeration, never preview.items/eligibleCount.
        for(var e:verified.structuralEligibility()) {
            var g=e.group();var f=request.fields().get(g.structureKey());ids.put(g.structureKey(),f.comboId());
            combos.add(new ComboRow(f.comboId(),request.batchId(),f.name(),g.comboKind().name(),"DRAFT","NONE","NON_STACKABLE",
                    f.currency(),f.referenceOriginalPrice(),f.referenceSalePrice(),g.supportAbs(),g.supportRatio(),g.patternWeight(),f.explanation(),request.createdAt()));
            for(var set:g.itemsets()) for(var item:set.items()) {
                var pos=new Position(set.itemsetOrder(),item.itemOrder());var value=f.items().get(pos);
                items.add(new ItemRow(f.comboId(),item.productId(),set.itemsetOrder(),item.itemOrder(),value.quantity(),
                        value.unitPriceSnapshot(),flags.get(g.structureKey()).get(pos).anchorEligible(),false));
            }
            g.sources().forEach(s->sources.add(new SourceRow(f.comboId(),s.candidate().patternId(),s.primary())));
        }
        var batch=new BatchRow(request.batchId(),request.context().runId(),cfg.rankingPolicy(),cfg.weightPolicy().policy(),cfg.k(),
                !selected||vn.edu.wspm.backend.services.combo.generation.CategorySelection.VERSION.equals(request.context().evaluationPolicyVersion()),selected?bounds.minimum():2,selected?bounds.maximum():4,"DRAFT",request.createdBy(),request.createdAt());
        return new Validation(true,List.of(),new Plan(request.writeIdentity(),request.context(),cfg,batch,combos,items,sources,ids,request.fields()));
    }
    private static boolean money(BigDecimal value) { return value!=null && value.signum()>=0 && value.scale()<=2 && value.compareTo(MONEY_MAX)<=0; }
    private static boolean id(String value) { return text(value,40); }
    private static boolean text(String value,int length) {
        return value!=null && !value.isBlank() && value.codePointCount(0,value.length())<=length
                && StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.indexOf('\0')<0;
    }
    private static Validation invalid(java.util.Collection<String> errors) { return new Validation(false,List.copyOf(errors),null); }
}
