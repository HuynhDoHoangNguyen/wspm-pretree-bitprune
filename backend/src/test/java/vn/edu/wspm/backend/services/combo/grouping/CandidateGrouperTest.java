package vn.edu.wspm.backend.services.combo.grouping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import static org.junit.jupiter.api.Assertions.*;

class CandidateGrouperTest {
    private final CandidateGrouper grouper=new CandidateGrouper();
    private final WeightPolicyConfirmation approval=new WeightPolicyConfirmation("TEST-WEIGHT","TEST-confirmation");
    private Candidate candidate(String id,String support,Double weight,String canonical,String[][] products) {
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
        var source=new PatternDetailResponse("R",id,canonical,support,.25,123.0,weight,"TEST-WEIGHT",total,groups.size(),"100",
                PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),originals);
        return new Candidate("DRAFT","R","S",id,source,"TEST-EVAL","TEST-EVAL-REF",EligibilityMode.CATALOG_ONLY,
                "TEST-published","TEST-catalog",Instant.parse("2025-01-01T00:00:00Z"),groups);
    }
    private SessionResult session(List<Candidate> candidates) {
        var checks=new EnumMap<Check,CheckStatus>(Check.class);for(var c:Check.values()) checks.put(c,CheckStatus.PASS);
        var patterns=candidates.stream().map(c->new PatternResult(c.patternId(),Decision.ACCEPT,List.of(),checks,c)).toList();
        return new SessionResult(true,List.of(),patterns.size(),patterns.size(),0,patterns);
    }
    private Result group(List<Candidate> candidates) {
        return grouper.group(session(candidates),Context.from(candidates.getFirst()),approval);
    }
    private Candidate bundle(String id,String support,double weight,String canonical) {
        return candidate(id,support,weight,canonical,new String[][]{{"L","M"}});
    }
    @Test void memberOrderNormalizesOnlyTheKeyAndSourceStructuresStayOriginal() {
        var a=bundle("1","8",.4,"<(1 2)>");
        var b=candidate("2","7",.9,"<(2 1)>",new String[][]{{"M","L"}});
        var r=group(List.of(b,a));assertTrue(r.completed());assertEquals(1,r.groups().size());
        var g=r.groups().getFirst();assertEquals(List.of(List.of("L","M")),g.structureKey().productItemsets());
        assertEquals("1",g.primary().patternId());assertEquals("8",g.supportAbs());assertEquals(.4,g.patternWeight());
        assertEquals(.25,g.supportRatio());assertEquals(2,g.sources().size());
        assertSame(a,g.primary());assertEquals("M",g.sources().get(1).candidate().itemsets().getFirst().items().getFirst().productId());
        assertSame(a.itemsets().getFirst(),g.itemsets().getFirst());
        assertEquals(1,g.sources().stream().filter(Source::primary).count());
    }
    @Test void bundleForwardReverseAndMixedStaySeparateAndClassify() {
        var candidates=List.of(bundle("1","1",.1,"<(1 2)>"),
                candidate("2","1",.1,"<(1)(2)>",new String[][]{{"L"},{"M"}}),
                candidate("3","1",.1,"<(2)(1)>",new String[][]{{"M"},{"L"}}),
                candidate("4","1",.1,"<(1 2)(3)>",new String[][]{{"L","M"},{"K"}}));
        var r=group(candidates);assertEquals(4,r.groups().size());
        assertEquals(Set.of(ComboKind.SAME_ORDER_BUNDLE,ComboKind.SEQUENTIAL_CROSS_SELL,ComboKind.MIXED_SEQUENCE),
                r.groups().stream().map(Group::comboKind).collect(java.util.stream.Collectors.toSet()));
        assertEquals(4,r.groups().stream().map(Group::structureKey).distinct().count());
    }
    @Test void primaryComparesExactSupportBeyondDoubleThenWeightThenFullCanonicalThenNumericId() {
        assertPrimary("2",List.of(bundle("1","9007199254740992",.9,"a"),bundle("2","9007199254740993",.1,"z")));
        assertPrimary("2",List.of(bundle("1","8",.1,"a"),bundle("2","8",.2,"z")));
        String prefix="x".repeat(3000);
        assertPrimary("2",List.of(bundle("1","8",.2,prefix+"z"),bundle("2","8",.2,prefix+"a")));
        assertPrimary("2",List.of(bundle("10","8",.2,"same"),bundle("2","8",.2,"same")));
        assertPrimary("18446744073709551614",List.of(bundle("18446744073709551615","8",.2,"same"),bundle("18446744073709551614","8",.2,"same")));
    }
    @Test void utf8UnsignedBytesAreUsedInsteadOfUtf16AndSignedZeroIsATie() {
        String supplementary=new String(Character.toChars(0x10000));
        assertPrimary("2",List.of(bundle("1","1",.2,supplementary),bundle("2","1",.2,"\uE000")));
        assertPrimary("2",List.of(bundle("10","1",-0.0,"same"),bundle("2","1",0.0,"same")));
    }
    @Test void inputShuffleAndSameSourceRepeatAreDeterministic() {
        var a=bundle("1","8",.4,"a");var b=bundle("2","7",.9,"b");
        var c=candidate("3","9",.1,"c",new String[][]{{"L"},{"M"}});
        var first=group(List.of(a,b,c));var reversed=group(List.of(c,b,a,a));
        assertEquals(first,reversed);assertEquals(2,first.groups().stream().mapToInt(g->g.sources().size()).max().orElse(0));
    }
    @Test void samePatternIdWithDifferentContentsBlocksAllGroups() {
        var r=group(List.of(bundle("1","1",.1,"a"),bundle("1","2",.1,"a")));
        assertFalse(r.completed());assertTrue(r.errors().contains("SOURCE_CONTENT_CONFLICT"));assertTrue(r.groups().isEmpty());
    }
    @Test void runAndSessionContextMustMatch() {
        var a=bundle("1","8",.4,"a");var b=copyContext(bundle("2","7",.1,"b"),"OTHER","OTHER-CATALOG");
        var r=grouper.group(session(List.of(a,b)),Context.from(a),approval);
        assertTrue(r.errors().contains("SOURCE_RUN_MISMATCH"));assertTrue(r.groups().isEmpty());
        var other=copyContext(bundle("2","7",.1,"b"),"R","OTHER-CATALOG");
        assertTrue(grouper.group(session(List.of(a,other)),Context.from(a),approval).errors().contains("EVALUATION_CONTEXT_MISMATCH"));
    }
    @Test void noPolicyApprovalOrDifferentPolicyNeverFallsBack() {
        var a=bundle("1","8",.4,"a");
        assertTrue(grouper.group(session(List.of(a)),Context.from(a),null).errors().contains("POLICY_UNCONFIRMED"));
        assertTrue(grouper.group(session(List.of(a)),Context.from(a),new WeightPolicyConfirmation("OTHER","TEST"))
                .errors().contains("POLICY_MISMATCH"));
    }
    @Test void nullOrNonFiniteWeightBlocksInsteadOfUsingWeightedSupport() {
        for(Double weight:new Double[]{null,Double.NaN,Double.POSITIVE_INFINITY}) {
            var a=candidate("1","1",weight,"a",new String[][]{{"L","M"}});
            var r=group(List.of(a));assertTrue(r.errors().contains("WEIGHT_UNAVAILABLE"));assertTrue(r.groups().isEmpty());
        }
    }
    @Test void emptyAndUnfinishedAndAcceptWithoutCandidate() {
        var a=bundle("1","1",.1,"a");var context=Context.from(a);
        assertEquals(new Result(true,List.of(),List.of()),grouper.group(session(List.of()),context,null));
        assertTrue(grouper.group(new SessionResult(false,List.of("ABORTED"),0,0,0,List.of()),context,approval)
                .errors().contains("SESSION_NOT_COMPLETED"));
        var invalid=new PatternResult("1",Decision.ACCEPT,List.of(),Map.of(),null);
        var r=grouper.group(new SessionResult(true,List.of(),1,1,0,List.of(invalid)),context,approval);
        assertTrue(r.errors().contains("INPUT_INVALID"));assertTrue(r.groups().isEmpty());
    }
    @Test void structuralFramingAndExactIdCaseDoNotCollide() {
        var r=group(List.of(candidate("1","1",.1,"a",new String[][]{{"a:b|c","L"}}),
                candidate("2","1",.1,"b",new String[][]{{"a","b|c:L"}}),
                candidate("3","1",.1,"c",new String[][]{{"A","L"}}),candidate("4","1",.1,"d",new String[][]{{"a","L"}})));
        assertEquals(4,r.groups().size());assertEquals(4,r.groups().stream().map(g->g.structureKey().serialized()).distinct().count());
    }
    @Test void mixedSourcePoliciesBlockTheWholeSession() {
        var a=bundle("1","8",.4,"a");var b=bundle("2","7",.1,"b");var s=b.source();
        var changed=new PatternDetailResponse(s.runId(),s.patternId(),s.canonicalPattern(),s.supportAbs(),s.supportRatio(),s.weightedSupport(),
                s.patternWeight(),"OTHER-POLICY",s.patternItemCount(),s.itemsetCount(),s.sequenceCount(),s.integrityStatus(),s.issues(),s.itemsets());
        var other=new Candidate(b.status(),b.runId(),b.snapshotId(),b.patternId(),changed,b.evaluationPolicyVersion(),b.policyConfirmationRef(),
                b.eligibilityMode(),b.publishedEvidenceRef(),b.catalogSnapshotRef(),b.evaluatedAt(),b.itemsets());
        var r=group(List.of(a,other));assertTrue(r.errors().contains("POLICY_MISMATCH"));assertTrue(r.groups().isEmpty());
    }
    @Test void actualEvaluatorOutputCanBeGroupedWithoutAnAdapterChangingTheSource() {
        var c=bundle("1","8",.4,"<(1 2)>");
        var evidence=new Evidence("R","SUCCEEDED","S","VALID",Purpose.DEMO_CATALOG,"TEST-purpose",java.math.BigInteger.ONE,
                true,"TEST-published",true,"TEST-catalog",c.evaluatedAt());
        var policy=new Policy("TEST-EVAL","TEST-EVAL-REF",Set.of("laptop"),Set.of("accessory"),EligibilityMode.CATALOG_ONLY);
        var catalog=Map.of("L",new CatalogProduct("L","laptop","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null),
                "M",new CatalogProduct("M","accessory","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null));
        var input=new Input(evidence,policy,List.of(c.source()),List.of(new DictionaryItem("R",1,"L","Laptop","L"),
                new DictionaryItem("R",2,"M","Mouse","M")),catalog);
        var evaluated=new vn.edu.wspm.backend.services.combo.evaluation.PatternCandidateEvaluator().evaluate(input);
        assertEquals(1,evaluated.accepted());
        var actual=evaluated.patterns().getFirst().candidate();
        var grouped=grouper.group(evaluated,Context.from(actual),approval);
        assertTrue(grouped.completed());assertSame(c.source(),grouped.groups().getFirst().primary().source());
        assertEquals("DRAFT",grouped.groups().getFirst().status());
    }
    private void assertPrimary(String id,List<Candidate> candidates) {
        var r=group(candidates);assertTrue(r.completed(),r.errors().toString());assertEquals(id,r.groups().getFirst().primary().patternId());
    }
    private Candidate copyContext(Candidate c,String run,String catalog) {
        var s=c.source();var source=new PatternDetailResponse(run,s.patternId(),s.canonicalPattern(),s.supportAbs(),s.supportRatio(),s.weightedSupport(),
                s.patternWeight(),s.patternWeightPolicy(),s.patternItemCount(),s.itemsetCount(),s.sequenceCount(),s.integrityStatus(),s.issues(),s.itemsets());
        return new Candidate(c.status(),run,c.snapshotId(),c.patternId(),source,c.evaluationPolicyVersion(),c.policyConfirmationRef(),
                c.eligibilityMode(),c.publishedEvidenceRef(),catalog,c.evaluatedAt(),c.itemsets());
    }
}
