package vn.edu.wspm.backend.services.combo.evaluation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;

/** Pure business evaluation: no HTTP, database, mining or implicit production policy. */
public final class PatternCandidateEvaluator {
    private static final Logger log=LoggerFactory.getLogger(PatternCandidateEvaluator.class);
    public SessionResult evaluate(Input input) {
        var errors=gate(input);
        if(!errors.isEmpty()) {
            log.warn("Candidate evaluation blocked: {}",errors);
            return new SessionResult(false,errors,0,0,0,List.of());
        }
        var results=new ArrayList<PatternResult>();
        // Deterministic numeric source order, independent of caller iteration.
        var sources=input.patterns().stream().sorted(java.util.Comparator.comparing(p->new BigInteger(p.patternId()))).toList();
        for(var pattern:sources) results.add(evaluatePattern(input,pattern));
        int accepted=(int)results.stream().filter(r->r.decision()==Decision.ACCEPT).count();
        log.info("Candidate evaluation completed: examined={}, accepted={}, rejected={}",results.size(),accepted,results.size()-accepted);
        return new SessionResult(true,List.of(),results.size(),accepted,results.size()-accepted,results);
    }

    private List<String> gate(Input input) {
        var errors=new ArrayList<String>();var e=input.evidence();var policy=input.policy();
        if(e==null) return List.of("RUN_NOT_FOUND");
        if(!"SUCCEEDED".equals(e.runStatus())) errors.add("FAILED".equals(e.runStatus())?"RUN_FAILED":
                "LEGACY_UNVERIFIED".equals(e.runStatus())?"RUN_UNVERIFIED":"RUN_NOT_READY");
        if(blank(e.runId()) || blank(e.snapshotId()) || !"VALID".equals(e.snapshotValidation())) errors.add("SNAPSHOT_UNVERIFIED");
        if(e.purpose()==Purpose.BENCHMARK) errors.add("BENCHMARK_NOT_ELIGIBLE");
        else if(e.purpose()!=Purpose.DEMO_CATALOG || blank(e.purposeConfirmationRef())) errors.add("RUN_PURPOSE_UNCONFIRMED");
        if(!e.fullRunConfirmed() || blank(e.publishedEvidenceRef()) || e.publishedCount()==null
                || !e.publishedCount().equals(BigInteger.valueOf(input.patterns().size()))) errors.add("RESULTS_INCOMPLETE");
        if(!e.stableInputs() || blank(e.catalogSnapshotRef()) || e.evaluatedAt()==null) errors.add("EVALUATION_ABORTED");
        if(policy==null || blank(policy.version()) || blank(policy.confirmationRef()) || policy.eligibilityMode()==null
                || policy.selection()==null && (policy.laptopCategoryIds().isEmpty() || policy.accessoryCategoryIds().isEmpty()
                || policy.laptopCategoryIds().stream().anyMatch(policy.accessoryCategoryIds()::contains)
                || policy.laptopCategoryIds().stream().anyMatch(PatternCandidateEvaluator::blank)
                || policy.accessoryCategoryIds().stream().anyMatch(PatternCandidateEvaluator::blank))) errors.add("CANDIDATE_POLICY_UNCONFIRMED");
        if(policy!=null&&policy.selection()!=null&&(!vn.edu.wspm.backend.services.combo.generation.ProductSelection.flexiblePolicy(policy.version())
                ||policy.eligibilityMode()!=EligibilityMode.CATALOG_ONLY
                ||(vn.edu.wspm.backend.services.combo.generation.ProductSelection.VERSION.equals(policy.version())
                    ?policy.selection().requiredProductIds().isEmpty()||!policy.selection().requiredCategoryIds().isEmpty()
                    :policy.selection().requiredCategoryIds().isEmpty()||!policy.selection().requiredProductIds().isEmpty())
                ||policy.selection().minimum()<2||policy.selection().maximum()<policy.selection().minimum()
                ||policy.selection().maximum()>vn.edu.wspm.backend.services.combo.generation.ProductSelection.MAX_PRODUCTS
                ||policy.selection().requiredProductIds().size()+policy.selection().requiredCategoryIds().size()>policy.selection().maximum()))errors.add("CANDIDATE_POLICY_UNCONFIRMED");
        var ids=new java.util.HashSet<String>();
        for(var p:input.patterns()) {
            if(!validId(p.patternId()) || !ids.add(p.patternId())) {
                if(!errors.contains("RESULTS_INCOMPLETE")) errors.add("RESULTS_INCOMPLETE");
            }
        }
        return errors;
    }

    private PatternResult evaluatePattern(Input in,PatternDetailResponse p) {
        var checks=new EnumMap<Check,CheckStatus>(Check.class);
        for(var check:Check.values()) checks.put(check,CheckStatus.NOT_EVALUATED);
        var reasons=new ArrayList<Reason>();
        if(!in.evidence().runId().equals(p.runId())) add(reasons,"PATTERN_RUN_MISMATCH");
        else if(!structure(p)) add(reasons,"PATTERN_STRUCTURE_INVALID");
        checks.put(Check.STRUCTURE,reasons.isEmpty()?CheckStatus.PASS:CheckStatus.FAIL);
        if(!reasons.isEmpty()) return result(p,reasons,checks,null);

        var mapped=new ArrayList<DictionaryItem>();
        for(var group:p.itemsets()) for(var item:group.items()) {
            var matches=in.dictionary().stream().filter(d->p.runId().equals(d.runId()) && item.itemId()==d.itemId()).toList();
            if(matches.isEmpty() || matches.size()>1 || blank(matches.getFirst().productId())) {
                reasons.add(new Reason(matches.size()>1?"ITEM_MAPPING_AMBIGUOUS":"ITEM_MAPPING_MISSING",
                        group.itemsetOrder(),item.itemOrder(),item.itemId(),null));
            } else mapped.add(matches.getFirst());
        }
        checks.put(Check.MAPPING,reasons.isEmpty()?CheckStatus.PASS:CheckStatus.FAIL);
        if(!reasons.isEmpty()) return result(p,reasons,checks,null);

        var products=new ArrayList<CatalogProduct>();
        for(var d:mapped) {
            var product=in.catalog().get(d.productId());
            if(product==null || !d.productId().equals(product.productId())) add(reasons,"PRODUCT_NOT_FOUND",d.productId());
            else if(blank(product.categoryId()) || product.categoryStatus()==null) add(reasons,"CATEGORY_NOT_FOUND",d.productId());
            else if(blank(d.nameSnapshot()) || blank(d.skuSnapshot()) || blank(product.productStatus())
                    || blank(product.categoryStatus()) || product.price()==null || product.price().signum()<0
                    || product.price().compareTo(new java.math.BigDecimal("99999999999999999.99"))>0
                    || product.price().scale()>2 || product.price().precision()>19 || product.currency()==null
                    || !product.currency().matches("[A-Z]{3}") || blank(product.version()))
                add(reasons,"CATALOG_DATA_INCOMPLETE",d.productId());
            else products.add(product);
        }
        if(products.stream().map(CatalogProduct::currency).distinct().count()>1) add(reasons,"CATALOG_DATA_INCOMPLETE");
        checks.put(Check.CATALOG,reasons.isEmpty()?CheckStatus.PASS:CheckStatus.FAIL);
        if(!reasons.isEmpty()) return result(p,reasons,checks,null);

        var occurrences=new HashMap<String,Integer>();
        mapped.forEach(d->occurrences.merge(d.productId(),1,Integer::sum));
        int before=reasons.size();
        var selection=in.policy().selection();
        if(occurrences.size()<(selection==null?2:selection.minimum()) || occurrences.size()>(selection==null?4:selection.maximum())) add(reasons,"PRODUCT_COUNT_OUT_OF_RANGE");
        if(selection!=null&&!occurrences.keySet().containsAll(selection.requiredProductIds()))add(reasons,"REQUIRED_PRODUCTS_MISSING");
        set(checks,Check.PRODUCT_COUNT,before,reasons);
        before=reasons.size();
        occurrences.keySet().stream().sorted().filter(id->occurrences.get(id)>1).forEach(id->add(reasons,"REPEATED_PRODUCT",id));
        set(checks,Check.REPETITION,before,reasons);
        before=reasons.size();
        var unique=products.stream().collect(java.util.stream.Collectors.toMap(CatalogProduct::productId,x->x,(a,b)->a)).values();
        long laptops=unique.stream().filter(x->in.policy().laptopCategoryIds().contains(x.categoryId())).count();
        long accessories=unique.stream().filter(x->in.policy().accessoryCategoryIds().contains(x.categoryId())).count();
        if(selection==null){
            if(accessories+laptops!=unique.size()) add(reasons,"CATEGORY_NOT_ALLOWED");
            if(laptops!=1) add(reasons,"LAPTOP_COUNT_INVALID");
            if(accessories==0) add(reasons,"ACCESSORY_REQUIRED");
        }else if(!unique.stream().map(CatalogProduct::categoryId).collect(java.util.stream.Collectors.toSet()).containsAll(selection.requiredCategoryIds())){
            add(reasons,"REQUIRED_CATEGORIES_MISSING");
        }
        set(checks,Check.CATEGORIES,before,reasons);
        before=reasons.size();
        unique.stream().sorted(java.util.Comparator.comparing(CatalogProduct::productId)).forEach(x->{
            if(!"ACTIVE".equals(x.productStatus())) add(reasons,"PRODUCT_NOT_ELIGIBLE",x.productId());
            if(!"ACTIVE".equals(x.categoryStatus())) add(reasons,"CATEGORY_NOT_ELIGIBLE",x.productId());
            if(in.policy().eligibilityMode()==EligibilityMode.INVENTORY_AWARE) {
                if(x.inventory()==null || x.inventory()==Inventory.UNKNOWN || blank(x.inventoryEvidenceRef())) add(reasons,"ELIGIBILITY_UNVERIFIED",x.productId());
                else if(x.inventory()==Inventory.INELIGIBLE) add(reasons,"PRODUCT_NOT_SALEABLE",x.productId());
            }
        });
        set(checks,Check.ELIGIBILITY,before,reasons);
        before=reasons.size();
        if(p.supportAbs()==null || !p.supportAbs().matches("[0-9]{1,20}")
                || new BigInteger(p.supportAbs()).compareTo(new BigInteger("18446744073709551615"))>0 || !finite(p.supportRatio())
                || p.supportRatio()<0 || p.supportRatio()>1 || !finite(p.weightedSupport())
                || p.weightedSupport()<0
                || !finite(p.patternWeight()) || blank(p.patternWeightPolicy())) add(reasons,"SOURCE_METRICS_UNAVAILABLE");
        set(checks,Check.METRICS,before,reasons);
        if(!reasons.isEmpty()) return result(p,reasons,checks,null);

        int index=0;var groups=new ArrayList<Itemset>();
        for(var group:p.itemsets()) {
            var items=new ArrayList<Item>();
            for(var item:group.items()) {
                var d=mapped.get(index++);var product=in.catalog().get(d.productId());
                items.add(new Item(item.itemOrder(),item.itemId(),d.productId(),d.nameSnapshot(),d.skuSnapshot(),product,
                        selection!=null?"CATALOG_PRODUCT":in.policy().laptopCategoryIds().contains(product.categoryId())?"LAPTOP":"ACCESSORY",
                        in.policy().eligibilityMode()==EligibilityMode.CATALOG_ONLY?"NOT_EVALUATED":"ELIGIBLE"));
            }
            groups.add(new Itemset(group.itemsetOrder(),items));
        }
        var e=in.evidence();var policy=in.policy();
        return result(p,reasons,checks,new Candidate("DRAFT",p.runId(),e.snapshotId(),p.patternId(),p,
                policy.version(),policy.confirmationRef(),policy.eligibilityMode(),e.publishedEvidenceRef(),e.catalogSnapshotRef(),e.evaluatedAt(),groups));
    }

    private static boolean structure(PatternDetailResponse p) {
        if(p.itemsets().isEmpty() || p.issues().stream().anyMatch(i->"STRUCTURE_MISMATCH".equals(i.code()))) return false;
        var canonical=new StringBuilder("<");long set=0,count=0;
        for(var group:p.itemsets()) {
            if(group.itemsetOrder()!=++set || group.items().isEmpty()) return false;
            canonical.append('(');long order=0,lastItem=0;
            for(var item:group.items()) {
                if(item.itemOrder()!=++order || item.itemId()<=lastItem || item.itemId()>Integer.MAX_VALUE) return false;
                if(order>1) canonical.append(' ');
                canonical.append(item.itemId());lastItem=item.itemId();count++;
            }
            canonical.append(')');
        }
        return canonical.append('>').toString().equals(p.canonicalPattern()) && count==p.patternItemCount() && set==p.itemsetCount();
    }
    private static boolean validId(String id) {
        return id!=null && id.matches("[1-9][0-9]{0,19}") && new BigInteger(id).compareTo(new BigInteger("18446744073709551615"))<=0;
    }
    private static void set(EnumMap<Check,CheckStatus> checks,Check key,int before,List<Reason> reasons) {
        checks.put(key,reasons.size()==before?CheckStatus.PASS:CheckStatus.FAIL);
    }
    private static boolean finite(Double value) { return value!=null && Double.isFinite(value); }
    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static void add(List<Reason> reasons,String code) { add(reasons,code,null); }
    private static void add(List<Reason> reasons,String code,String productId) { reasons.add(new Reason(code,null,null,null,productId)); }
    private static PatternResult result(PatternDetailResponse p,List<Reason> reasons,Map<Check,CheckStatus> checks,Candidate candidate) {
        return new PatternResult(p.patternId(),reasons.isEmpty()?Decision.ACCEPT:Decision.REJECT,reasons,checks,candidate);
    }
}
