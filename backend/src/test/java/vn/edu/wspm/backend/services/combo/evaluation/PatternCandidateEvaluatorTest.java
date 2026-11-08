package vn.edu.wspm.backend.services.combo.evaluation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static org.junit.jupiter.api.Assertions.*;

class PatternCandidateEvaluatorTest {
    @Test void categoryPolicySelectsAnyProductOfEachRequiredCategoryAcrossItemsets(){
        var patterns=List.of(pattern("R","1",new long[][]{{1},{2}}),pattern("R","2",new long[][]{{1},{3}}),pattern("R","3",new long[][]{{2,3}}));
        var base=input(patterns);
        var policy=new Policy(vn.edu.wspm.backend.services.combo.generation.CategorySelection.VERSION,"OWNER",Set.of(),Set.of(),EligibilityMode.CATALOG_ONLY,new SelectionRules(Set.of(),2,4,Set.of("laptops","accessories")));
        var result=evaluator.evaluate(new Input(base.evidence(),policy,patterns,base.dictionary(),base.catalog()));
        assertEquals(2,result.accepted());assertTrue(codes(result.patterns().get(2)).contains("REQUIRED_CATEGORIES_MISSING"));
        assertEquals(Set.of("1","2"),result.patterns().stream().filter(p->p.decision()==Decision.ACCEPT).map(PatternResult::patternId).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void selectedPolicyMatchesAllAcrossItemsetsWithoutLaptopOrRewritingMetrics(){
        var p=pattern("R","1",new long[][]{{2},{3,5}});var base=input(List.of(p));
        var policy=new Policy(vn.edu.wspm.backend.services.combo.generation.ProductSelection.VERSION,"OWNER",Set.of(),Set.of(),EligibilityMode.CATALOG_ONLY,new SelectionRules(Set.of("M","X"),2,5));
        var result=evaluator.evaluate(new Input(base.evidence(),policy,base.patterns(),base.dictionary(),base.catalog()));
        assertEquals(1,result.accepted());assertSame(p,result.patterns().getFirst().candidate().source());
        var missing=new Policy(policy.version(),"OWNER",Set.of(),Set.of(),EligibilityMode.CATALOG_ONLY,new SelectionRules(Set.of("M","L"),2,5));
        var rejected=evaluator.evaluate(new Input(base.evidence(),missing,base.patterns(),base.dictionary(),base.catalog()));
        assertTrue(codes(rejected.patterns().getFirst()).contains("REQUIRED_PRODUCTS_MISSING"));
    }
    @Test void selectedPolicyPermitsFiveDistinctProductsButKeepsRepetitionAndCatalogGates(){
        var p=pattern("R","1",new long[][]{{1,2,3,4,5}});var base=input(List.of(p));
        var policy=new Policy(vn.edu.wspm.backend.services.combo.generation.ProductSelection.VERSION,"OWNER",Set.of(),Set.of(),EligibilityMode.CATALOG_ONLY,new SelectionRules(Set.of("X"),5,5));
        assertEquals(1,evaluator.evaluate(new Input(base.evidence(),policy,base.patterns(),base.dictionary(),base.catalog())).accepted());
        assertEquals(0,evaluator.evaluate(base).accepted());
        var repeated=input(List.of(pattern("R","2",new long[][]{{2},{2,5}})));
        assertTrue(codes(evaluator.evaluate(new Input(repeated.evidence(),policy,repeated.patterns(),repeated.dictionary(),repeated.catalog())).patterns().getFirst()).contains("REPEATED_PRODUCT"));
    }
    private final PatternCandidateEvaluator evaluator=new PatternCandidateEvaluator();
    private Policy policy(EligibilityMode mode) { return new Policy("TEST-v1","TEST-only-approval",Set.of("laptops"),Set.of("accessories"),mode); }
    private Evidence evidence(String run,String status,Purpose purpose,int count,boolean full) {
        return new Evidence(run,status,"snapshot","VALID",purpose,"TEST-purpose",BigInteger.valueOf(count),
                full,"TEST-publish",true,"TEST-catalog",Instant.parse("2025-01-01T00:00:00Z"));
    }
    private PatternDetailResponse pattern(String run,String id,long[][] ids) {
        var groups=new ArrayList<PatternDetailResponse.Itemset>();var canonical=new StringBuilder("<");int count=0;
        for(int i=0;i<ids.length;i++) {
            var items=new ArrayList<PatternDetailResponse.Item>();canonical.append('(');
            for(int j=0;j<ids[i].length;j++) {
                if(j>0) canonical.append(' ');canonical.append(ids[i][j]);count++;
                items.add(new PatternDetailResponse.Item(j+1,ids[i][j],PatternDetailResponse.MappingStatus.MISSING,null,null,null,null));
            }
            canonical.append(')');groups.add(new PatternDetailResponse.Itemset(i+1,items));
        }
        return new PatternDetailResponse(run,id,canonical.append('>').toString(),"9007199254740993",.5,1.2,.4,"TEST-weight",
                count,ids.length,"18014398509481986",PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),groups);
    }
    private List<DictionaryItem> dictionary(String run) {
        return List.of(new DictionaryItem(run,1,"L","Laptop snapshot","SKU-L"),new DictionaryItem(run,2,"M","Mouse snapshot","SKU-M"),
                new DictionaryItem(run,3,"K","Keyboard snapshot","SKU-K"),new DictionaryItem(run,4,"H","Headset snapshot","SKU-H"),
                new DictionaryItem(run,5,"X","Other snapshot","SKU-X"));
    }
    private CatalogProduct product(String id,String category,String status,Inventory stock) {
        return new CatalogProduct(id,category,status,"ACTIVE",new BigDecimal("100.00"),"VND","0",stock,"TEST-inventory");
    }
    private Map<String,CatalogProduct> catalog() {
        return Map.of("L",product("L","laptops","ACTIVE",Inventory.UNKNOWN),"M",product("M","accessories","ACTIVE",Inventory.ELIGIBLE),
                "K",product("K","accessories","ACTIVE",Inventory.ELIGIBLE),"H",product("H","accessories","ACTIVE",Inventory.ELIGIBLE),
                "X",product("X","other","ACTIVE",Inventory.ELIGIBLE));
    }
    private Input input(List<PatternDetailResponse> patterns) {
        return new Input(evidence("R","SUCCEEDED",Purpose.DEMO_CATALOG,patterns.size(),true),policy(EligibilityMode.CATALOG_ONLY),patterns,dictionary("R"),catalog());
    }
    private List<String> codes(PatternResult r) { return r.reasons().stream().map(Reason::code).toList(); }
    @Test void acceptsBundleAndSequenceWithoutFlatteningOrRecomputingMetrics() {
        var a=pattern("R","1",new long[][]{{1,2}});var b=pattern("R","2",new long[][]{{1},{2}});
        var result=evaluator.evaluate(input(List.of(b,a)));
        assertTrue(result.completed());assertEquals(2,result.accepted());
        var ca=result.patterns().get(0).candidate();var cb=result.patterns().get(1).candidate();
        assertEquals("DRAFT",ca.status());assertSame(a,ca.source());assertSame(b,cb.source());
        assertEquals(1,ca.itemsets().size());assertEquals(2,cb.itemsets().size());
        assertEquals("9007199254740993",ca.source().supportAbs());
        assertEquals("NOT_EVALUATED",ca.itemsets().getFirst().items().getFirst().stockCheck());
        assertEquals("snapshot",ca.snapshotId());assertEquals("R",ca.runId());assertEquals("TEST-v1",ca.evaluationPolicyVersion());
    }
    @Test void acceptsFourDistinctProductsAtUpperBoundary() {
        var r=evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1,2,3,4}}))));
        assertEquals(1,r.accepted());
    }
    @Test void mappingUsesOnlySourceRunEvenWithSameItemIdElsewhere() {
        var p=pattern("R","1",new long[][]{{1,2}});var base=input(List.of(p));
        var dict=new ArrayList<>(dictionary("R"));dict.add(new DictionaryItem("OTHER",1,"X","WRONG","WRONG"));
        var r=evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),dict,base.catalog()));
        assertEquals("L",r.patterns().getFirst().candidate().itemsets().getFirst().items().getFirst().productId());
        var other=pattern("OTHER","1",new long[][]{{1,2}});
        var otherDict=List.of(new DictionaryItem("OTHER",1,"L","Different snapshot","Different SKU"),
                new DictionaryItem("OTHER",2,"M","Other mouse","Other SKU"));
        var otherResult=evaluator.evaluate(new Input(evidence("OTHER","SUCCEEDED",Purpose.DEMO_CATALOG,1,true),base.policy(),
                List.of(other),otherDict,base.catalog()));
        assertEquals("Different snapshot",otherResult.patterns().getFirst().candidate().itemsets().getFirst().items().getFirst().nameSnapshot());
    }
    @Test void missingMappingDoesNotInferCategoriesAndAmbiguousMappingFails() {
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));
        var r=evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),dictionary("OTHER"),base.catalog())).patterns().getFirst();
        assertEquals(CheckStatus.FAIL,r.checks().get(Check.MAPPING));assertEquals(CheckStatus.NOT_EVALUATED,r.checks().get(Check.CATEGORIES));
        assertEquals(List.of("ITEM_MAPPING_MISSING","ITEM_MAPPING_MISSING"),codes(r));
        var dict=new ArrayList<>(base.dictionary());dict.add(dict.getFirst());
        assertTrue(codes(evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),dict,base.catalog())).patterns().getFirst()).contains("ITEM_MAPPING_AMBIGUOUS"));
    }
    @Test void repeatedProductAndOutOfRangeHaveStableMultipleReasons() {
        var r=evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1},{1},{1}})))).patterns().getFirst();
        assertEquals("PRODUCT_COUNT_OUT_OF_RANGE",r.reasons().getFirst().code());assertTrue(codes(r).contains("REPEATED_PRODUCT"));
        var repeated=evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1},{1,2}})))).patterns().getFirst();
        assertEquals(CheckStatus.PASS,repeated.checks().get(Check.PRODUCT_COUNT));assertTrue(codes(repeated).contains("REPEATED_PRODUCT"));
        var five=evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1,2,3,4,5}})))).patterns().getFirst();
        assertTrue(codes(five).contains("PRODUCT_COUNT_OUT_OF_RANGE"));
    }
    @Test void wrongCategoriesAndIneligibleProductsAreRejected() {
        assertTrue(codes(evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{2,3}})))).patterns().getFirst()).contains("LAPTOP_COUNT_INVALID"));
        assertTrue(codes(evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1,5}})))).patterns().getFirst()).contains("CATEGORY_NOT_ALLOWED"));
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));var cat=new java.util.HashMap<>(base.catalog());
        cat.put("M",product("M","accessories","INACTIVE",Inventory.ELIGIBLE));
        assertTrue(codes(evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),base.dictionary(),cat)).patterns().getFirst()).contains("PRODUCT_NOT_ELIGIBLE"));
    }
    @Test void catalogMissingBlocksDependentChecks() {
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));
        var r=evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),base.dictionary(),Map.of())).patterns().getFirst();
        assertTrue(codes(r).contains("PRODUCT_NOT_FOUND"));assertEquals(CheckStatus.NOT_EVALUATED,r.checks().get(Check.ELIGIBILITY));
    }
    @Test void inventoryUnknownAndFalseAreDifferent() {
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));var cat=new java.util.HashMap<>(base.catalog());
        cat.put("M",product("M","accessories","ACTIVE",Inventory.INELIGIBLE));
        var r=evaluator.evaluate(new Input(base.evidence(),policy(EligibilityMode.INVENTORY_AWARE),base.patterns(),base.dictionary(),cat)).patterns().getFirst();
        assertTrue(codes(r).contains("ELIGIBILITY_UNVERIFIED"));assertTrue(codes(r).contains("PRODUCT_NOT_SALEABLE"));
    }
    @Test void structureAndOwnershipFailBeforeMapping() {
        var base=input(List.of(pattern("WRONG","1",new long[][]{{1,2}})));
        var r=evaluator.evaluate(base).patterns().getFirst();assertEquals(List.of("PATTERN_RUN_MISMATCH"),codes(r));
        assertEquals(CheckStatus.NOT_EVALUATED,r.checks().get(Check.MAPPING));
        var duplicate=evaluator.evaluate(input(List.of(pattern("R","1",new long[][]{{1,1,2}})))).patterns().getFirst();
        assertEquals(List.of("PATTERN_STRUCTURE_INVALID"),codes(duplicate));
    }
    @Test void missingSourceMetricIsNeverZeroFilled() {
        var p=pattern("R","1",new long[][]{{1,2}});
        var invalid=new PatternDetailResponse(p.runId(),p.patternId(),p.canonicalPattern(),p.supportAbs(),p.supportRatio(),p.weightedSupport(),
                null,p.patternWeightPolicy(),p.patternItemCount(),p.itemsetCount(),p.sequenceCount(),p.integrityStatus(),p.issues(),p.itemsets());
        var r=evaluator.evaluate(input(List.of(invalid))).patterns().getFirst();
        assertTrue(codes(r).contains("SOURCE_METRICS_UNAVAILABLE"));assertNull(r.candidate());
    }
    @ParameterizedTest @ValueSource(strings={"RUNNING","QUEUED","FAILED","LEGACY_UNVERIFIED"})
    void unfinishedRunBlocksSession(String status) {
        var r=evaluator.evaluate(new Input(evidence("R",status,Purpose.DEMO_CATALOG,0,true),policy(EligibilityMode.CATALOG_ONLY),List.of(),dictionary("R"),catalog()));
        assertFalse(r.completed());assertEquals(0,r.examined());assertTrue(r.patterns().isEmpty());
    }
    @Test void benchmarkPurposePolicyAndCompletionBlockAtSessionLevel() {
        var base=input(List.of());
        for(var purpose:List.of(Purpose.BENCHMARK,Purpose.UNCONFIRMED))
            assertFalse(evaluator.evaluate(new Input(evidence("R","SUCCEEDED",purpose,0,true),base.policy(),List.of(),base.dictionary(),base.catalog())).completed());
        assertTrue(evaluator.evaluate(new Input(base.evidence(),null,List.of(),base.dictionary(),base.catalog())).sessionErrors().contains("CANDIDATE_POLICY_UNCONFIRMED"));
        assertTrue(evaluator.evaluate(new Input(evidence("R","SUCCEEDED",Purpose.DEMO_CATALOG,0,false),base.policy(),List.of(),base.dictionary(),base.catalog())).sessionErrors().contains("RESULTS_INCOMPLETE"));
    }
    @Test void validEmptyRunCompletesWithZeroCounts() {
        var r=evaluator.evaluate(input(List.of()));assertTrue(r.completed());assertEquals(0,r.examined());assertEquals(0,r.accepted());assertEquals(0,r.rejected());
    }
    @Test void unstableInputsAndDuplicatePatternIdsNeverReturnPartialResults() {
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));var e=base.evidence();
        var unstable=new Evidence(e.runId(),e.runStatus(),e.snapshotId(),e.snapshotValidation(),e.purpose(),e.purposeConfirmationRef(),
                e.publishedCount(),true,e.publishedEvidenceRef(),false,e.catalogSnapshotRef(),e.evaluatedAt());
        var r=evaluator.evaluate(new Input(unstable,base.policy(),base.patterns(),base.dictionary(),base.catalog()));
        assertFalse(r.completed());assertEquals(List.of("EVALUATION_ABORTED"),r.sessionErrors());assertTrue(r.patterns().isEmpty());
        var duplicate=evaluator.evaluate(new Input(evidence("R","SUCCEEDED",Purpose.DEMO_CATALOG,2,true),base.policy(),
                List.of(base.patterns().getFirst(),base.patterns().getFirst()),base.dictionary(),base.catalog()));
        assertTrue(duplicate.sessionErrors().contains("RESULTS_INCOMPLETE"));assertEquals(0,duplicate.examined());
    }
    @Test void policyNeedsExplicitConfirmationAndDisjointCategories() {
        var base=input(List.of());
        for(var policy:List.of(new Policy("TEST","",Set.of("laptops"),Set.of("accessories"),EligibilityMode.CATALOG_ONLY),
                new Policy("TEST","TEST-ref",Set.of("same"),Set.of("same"),EligibilityMode.CATALOG_ONLY))) {
            assertTrue(evaluator.evaluate(new Input(base.evidence(),policy,List.of(),base.dictionary(),base.catalog()))
                    .sessionErrors().contains("CANDIDATE_POLICY_UNCONFIRMED"));
        }
    }
    @Test void missingCategoryAndTwoLaptopStructureAreExplicit() {
        var base=input(List.of(pattern("R","1",new long[][]{{1,2}})));var cat=new java.util.HashMap<>(base.catalog());
        cat.put("M",new CatalogProduct("M","accessories","ACTIVE",null,new BigDecimal("1.00"),"VND","0",Inventory.ELIGIBLE,"TEST"));
        assertTrue(codes(evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),base.dictionary(),cat)).patterns().getFirst()).contains("CATEGORY_NOT_FOUND"));
        cat.put("M",product("M","laptops","ACTIVE",Inventory.ELIGIBLE));
        assertTrue(codes(evaluator.evaluate(new Input(base.evidence(),base.policy(),base.patterns(),base.dictionary(),cat)).patterns().getFirst()).contains("LAPTOP_COUNT_INVALID"));
    }
}
