package vn.edu.wspm.backend.services.combo.ranking;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.evaluation.PatternCandidateEvaluator;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouper;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview.Result;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import static vn.edu.wspm.backend.services.combo.ranking.ComboPreview.*;
import static org.junit.jupiter.api.Assertions.*;

class ComboPreviewRankerTest {
    private final ComboPreviewRanker ranker=new ComboPreviewRanker();
    private final WeightPolicyConfirmation weight=new WeightPolicyConfirmation("TEST-WEIGHT","TEST-weight-approved");
    private Candidate candidate(String id,String support,double w,String canonical,String[][] products) {
        var groups=new ArrayList<Itemset>();var originals=new ArrayList<PatternDetailResponse.Itemset>();int total=0;long itemId=0;
        for(int i=0;i<products.length;i++) {
            var items=new ArrayList<Item>();var sourceItems=new ArrayList<PatternDetailResponse.Item>();
            for(int j=0;j<products[i].length;j++) {
                String p=products[i][j];itemId++;total++;
                var catalog=new CatalogProduct(p,"cat","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null);
                items.add(new Item(j+1,itemId,p,"Snapshot-"+p,"SKU-"+p,catalog,"ACCESSORY","NOT_EVALUATED"));
                sourceItems.add(new PatternDetailResponse.Item(j+1,itemId,PatternDetailResponse.MappingStatus.MAPPED,p,"Snapshot-"+p,"SKU-"+p,null));
            }
            groups.add(new Itemset(i+1,items));originals.add(new PatternDetailResponse.Itemset(i+1,sourceItems));
        }
        var source=new PatternDetailResponse("R",id,canonical,support,.5,3.0,w,"TEST-WEIGHT",total,groups.size(),"100",
                PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),originals);
        return new Candidate("DRAFT","R","S",id,source,"TEST-EVAL","TEST-policy",EligibilityMode.CATALOG_ONLY,
                "TEST-publish","TEST-catalog",Instant.parse("2025-01-01T00:00:00Z"),groups);
    }
    private Candidate bundle(String id,String product,String support,double w,String canonical) {
        return candidate(id,support,w,canonical,new String[][]{{"L",product}});
    }
    private CandidateGrouping.Result grouped(List<Candidate> sources) {
        var checks=new EnumMap<Check,CheckStatus>(Check.class);for(var c:Check.values()) checks.put(c,CheckStatus.PASS);
        var results=sources.stream().map(c->new PatternResult(c.patternId(),Decision.ACCEPT,List.of(),checks,c)).toList();
        return new CandidateGrouper().group(new SessionResult(true,List.of(),results.size(),results.size(),0,results),context(),weight);
    }
    private Context context() { return Context.from(bundle("1","M","1",.1,"a")); }
    private Configuration config(BigInteger k) {
        return new Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST-ranking",weight,"FIRST_ITEMSET_V1","TEST-anchor",k,null,null);
    }
    private List<String> ids(Result result) { return result.items().stream().map(r->r.group().primary().patternId()).toList(); }

    @Test void filtersAnchorBeforeTopKAndPreservesDirectionAcrossAllKinds() {
        var g=grouped(List.of(bundle("1","M","10",.1,"b"),bundle("2","K","9",.9,"c"),
                candidate("3","11",.5,"a",new String[][]{{"M"},{"L"}}),
                candidate("4","8",.2,"d",new String[][]{{"L","M"},{"K"}})));
        var l=ranker.preview(g,context(),"L",config(BigInteger.TWO));
        assertTrue(l.completed(),l.errors().toString());assertEquals(List.of("1","2"),ids(l));
        assertEquals(3,l.eligibleCount());assertEquals(List.of(1,2),l.items().stream().map(RankedGroup::rank).toList());
        // Global first two would be3,1; filtering afterward would lose source2.
        assertEquals(List.of("3","1"),ids(ranker.preview(g,context(),"M",config(BigInteger.TWO))));
        assertEquals(List.of("2"),ids(ranker.preview(g,context(),"K",config(BigInteger.TWO))));
        var mixed=l.structuralEligibility().stream().filter(e->e.group().comboKind()==ComboKind.MIXED_SEQUENCE).findFirst().orElseThrow();
        assertTrue(mixed.items().get(0).anchorEligible());assertTrue(mixed.items().get(1).anchorEligible());
        assertFalse(mixed.items().get(2).anchorEligible());
        assertEquals(g.groups().size(),l.structuralEligibility().size());
    }
    @Test void exactSupportWeightCanonicalAndNumericIdTiesAreStableUnderShuffle() {
        var sources=List.of(bundle("11","A","9007199254740993",.1,"z"),bundle("12","B","9007199254740992",.9,"a"),
                bundle("13","C","8",.6,"z"),bundle("14","D","8",.5,"a"),
                bundle("15","E","7",.4,"x".repeat(2000)+"b"),bundle("16","F","7",.4,"x".repeat(2000)+"a"),
                bundle("10","G","6",.3,"same"),bundle("2","H","6",.3,"same"));
        var g=grouped(sources);var cfg=config(BigInteger.valueOf(20));
        var r=ranker.preview(g,context(),"L",cfg);
        assertEquals(List.of("11","12","13","14","16","15","2","10"),ids(r));
        var reverse=new ArrayList<>(g.groups());java.util.Collections.reverse(reverse);
        assertEquals(r,ranker.preview(new CandidateGrouping.Result(true,List.of(),reverse),context(),"L",cfg));
        assertEquals(g.groups().getFirst().context(),r.context());
    }
    @Test void lessThanKNoResultsAndExactAnchorIdentity() {
        var g=grouped(List.of(bundle("1","M","8",.4,"a")));
        assertEquals(1,ranker.preview(g,context(),"L",config(new BigInteger("999999999999999999999"))).items().size());
        assertTrue(ranker.preview(g,context(),"absent",config(BigInteger.ONE)).completed());
        assertTrue(ranker.preview(g,context(),"absent",config(BigInteger.ONE)).items().isEmpty());
        assertTrue(ranker.preview(g,context(),"l",config(BigInteger.ONE)).items().isEmpty());
        assertTrue(ranker.preview(g,context()," L",config(BigInteger.ONE)).items().isEmpty());
        for(String invalid:List.of(""," ","X".repeat(41))) {
            var r=ranker.preview(g,context(),invalid,config(BigInteger.ONE));
            assertFalse(r.completed());assertTrue(r.errors().contains("INVALID_ANCHOR"));
        }
    }
    @Test void kHasNoDefaultAndOnlyApprovedCapIsApplied() {
        var g=grouped(List.of(bundle("1","M","8",.4,"a")));
        for(BigInteger k:new BigInteger[]{null,BigInteger.ZERO,BigInteger.ONE.negate()}) {
            var r=ranker.preview(g,context(),"L",config(k));assertFalse(r.completed());assertTrue(r.errors().contains("INVALID_K"));
        }
        var capped=new Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST",weight,"FIRST_ITEMSET_V1","TEST",BigInteger.TWO,BigInteger.ONE,"TEST-cap");
        assertTrue(ranker.preview(g,context(),"L",capped).errors().contains("INVALID_K"));
        var unapproved=new Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST",weight,"FIRST_ITEMSET_V1","TEST",BigInteger.ONE,BigInteger.TWO,null);
        assertTrue(ranker.preview(g,context(),"L",unapproved).errors().contains("K_CAP_UNCONFIRMED"));
    }
    @Test void policyApprovalsAreExplicitAndDoNotFallBack() {
        var g=grouped(List.of(bundle("1","M","8",.4,"a")));
        assertTrue(ranker.preview(g,context(),"L",null).errors().contains("CONFIGURATION_REQUIRED"));
        var none=new Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1",null,null,"FIRST_ITEMSET_V1",null,BigInteger.ONE,null,null);
        var r=ranker.preview(g,context(),"L",none);assertFalse(r.completed());assertTrue(r.items().isEmpty());
        assertTrue(r.errors().containsAll(List.of("RANKING_POLICY_UNCONFIRMED","ANCHOR_POLICY_UNCONFIRMED","POLICY_UNCONFIRMED")));
        var wrong=new Configuration("SUPPORT_ONLY","TEST",new WeightPolicyConfirmation("OTHER","TEST"),"ANY_POSITION","TEST",BigInteger.ONE,null,null);
        assertTrue(ranker.preview(g,context(),"L",wrong).errors().contains("UNSUPPORTED_ANCHOR_POLICY"));
        var mixed=new Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST",new WeightPolicyConfirmation("OTHER","TEST"),"FIRST_ITEMSET_V1","TEST",BigInteger.ONE,null,null);
        assertTrue(ranker.preview(g,context(),"L",mixed).errors().contains("POLICY_MISMATCH"));
    }
    @Test void malformedOffAnchorGroupBlocksWholePreviewAndMetricsAreNotSynthesized() {
        var g=grouped(List.of(bundle("1","M","8",.4,"a")));var original=g.groups().getFirst();
        var bad=new Group(original.status(),original.context(),original.structureKey(),original.comboKind(),original.primary(),original.sources(),
                original.itemsets(),"999",original.supportRatio(),original.patternWeight(),original.patternWeightPolicy(),original.weightConfirmationRef());
        var r=ranker.preview(new CandidateGrouping.Result(true,List.of(),List.of(bad)),context(),"absent",config(BigInteger.ONE));
        assertFalse(r.completed());assertTrue(r.errors().contains("GROUP_PROVENANCE_INVALID"));assertTrue(r.items().isEmpty());
        assertTrue(ranker.preview(new CandidateGrouping.Result(false,List.of("FAILED"),List.of()),context(),"L",config(BigInteger.ONE))
                .errors().contains("GROUPING_NOT_COMPLETED"));
    }
    @Test void evaluatorGrouperRankerUnitChainKeepsSourceAndDraft() {
        var c=bundle("1","M","8",.4,"<(1 2)>");
        var e=new Evidence("R","SUCCEEDED","S","VALID",Purpose.DEMO_CATALOG,"TEST-purpose",BigInteger.ONE,true,
                "TEST-publish",true,"TEST-catalog",c.evaluatedAt());
        var p=new Policy("TEST-EVAL","TEST-policy",Set.of("laptop"),Set.of("accessory"),EligibilityMode.CATALOG_ONLY);
        var catalog=Map.of("L",new CatalogProduct("L","laptop","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null),
                "M",new CatalogProduct("M","accessory","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null));
        var evaluated=new PatternCandidateEvaluator().evaluate(new Input(e,p,List.of(c.source()),
                List.of(new DictionaryItem("R",1,"L","Laptop","L"),new DictionaryItem("R",2,"M","Mouse","M")),catalog));
        var groups=new CandidateGrouper().group(evaluated,context(),weight);
        var r=ranker.preview(groups,context(),"L",config(BigInteger.TWO));
        assertTrue(r.completed());assertEquals(List.of("1"),ids(r));
        assertSame(c.source(),r.items().getFirst().group().primary().source());
        assertEquals("DRAFT",r.items().getFirst().group().status());
    }
    @Test void missingWeightBlocksEvenForAnAnchorThatWouldMatchNoGroup() {
        var g=grouped(List.of(bundle("1","M","8",.4,"a")));var old=g.groups().getFirst();var c=old.primary();var s=c.source();
        var source=new PatternDetailResponse(s.runId(),s.patternId(),s.canonicalPattern(),s.supportAbs(),s.supportRatio(),s.weightedSupport(),
                null,s.patternWeightPolicy(),s.patternItemCount(),s.itemsetCount(),s.sequenceCount(),s.integrityStatus(),s.issues(),s.itemsets());
        var changed=new Candidate(c.status(),c.runId(),c.snapshotId(),c.patternId(),source,c.evaluationPolicyVersion(),c.policyConfirmationRef(),
                c.eligibilityMode(),c.publishedEvidenceRef(),c.catalogSnapshotRef(),c.evaluatedAt(),c.itemsets());
        var invalid=new Group(old.status(),old.context(),old.structureKey(),old.comboKind(),changed,List.of(new Source(changed,true)),
                old.itemsets(),old.supportAbs(),old.supportRatio(),null,old.patternWeightPolicy(),old.weightConfirmationRef());
        var r=ranker.preview(new CandidateGrouping.Result(true,List.of(),List.of(invalid)),context(),"absent",config(BigInteger.ONE));
        assertFalse(r.completed());assertTrue(r.errors().contains("WEIGHT_UNAVAILABLE"));assertTrue(r.items().isEmpty());
    }
    @Test void completedEmptyGroupingHasAnEmptySuccessfulPreviewWithExplicitPolicies() {
        var r=ranker.preview(new CandidateGrouping.Result(true,List.of(),List.of()),context(),"L",config(BigInteger.TWO));
        assertTrue(r.completed());assertEquals(0,r.eligibleCount());assertTrue(r.items().isEmpty());assertTrue(r.structuralEligibility().isEmpty());
    }
}
