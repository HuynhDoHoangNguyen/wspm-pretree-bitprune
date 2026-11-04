package vn.edu.wspm.backend.services.combo.persistence;

import java.math.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.grouping.*;
import vn.edu.wspm.backend.services.combo.ranking.*;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;
import static org.junit.jupiter.api.Assertions.*;

class DraftBatchPlannerTest {
    private final DraftBatchPlanner planner=new DraftBatchPlanner();
    private final WeightPolicyConfirmation approval=new WeightPolicyConfirmation("TEST-W","TEST-approval");
    private Candidate candidate(String id,String[][] products) {
        var sets=new ArrayList<Itemset>();var originals=new ArrayList<PatternDetailResponse.Itemset>();long itemId=0;
        for(int i=0;i<products.length;i++) {
            var items=new ArrayList<Item>();var raw=new ArrayList<PatternDetailResponse.Item>();
            for(int j=0;j<products[i].length;j++) {
                String p=products[i][j];itemId++;
                var catalog=new CatalogProduct(p,"cat","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null);
                items.add(new Item(j+1,itemId,p,p,p,catalog,"ACCESSORY","NOT_EVALUATED"));
                raw.add(new PatternDetailResponse.Item(j+1,itemId,PatternDetailResponse.MappingStatus.MAPPED,p,p,p,null));
            }
            sets.add(new Itemset(i+1,items));originals.add(new PatternDetailResponse.Itemset(i+1,raw));
        }
        var source=new PatternDetailResponse("R",id,"fixture-"+id,"8",.8,4.0,.5,"TEST-W",(int)itemId,sets.size(),"10",
                PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),originals);
        return new Candidate("DRAFT","R","S",id,source,"TEST-E","TEST-policy",EligibilityMode.CATALOG_ONLY,
                "TEST-published","TEST-catalog",Instant.parse("2025-01-01T00:00:00Z"),sets);
    }
    private Request request(BigInteger k) {
        var candidates=List.of(candidate("1",new String[][]{{"L","M"}}),candidate("2",new String[][]{{"L"},{"K"}}),
                candidate("3",new String[][]{{"L","M"}}));
        var checks=new EnumMap<Check,CheckStatus>(Check.class);for(var c:Check.values()) checks.put(c,CheckStatus.PASS);
        var results=candidates.stream().map(c->new PatternResult(c.patternId(),Decision.ACCEPT,List.of(),checks,c)).toList();
        var context=Context.from(candidates.getFirst());
        var grouping=new CandidateGrouper().group(new SessionResult(true,List.of(),3,3,0,results),context,approval);
        var config=new ComboPreview.Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST-rank",approval,"FIRST_ITEMSET_V1","TEST-anchor",k,null,null);
        var preview=new ComboPreviewRanker().preview(grouping,context,"L",config);
        var fields=new HashMap<StructureKey,Fields>();
        for(var g:grouping.groups()) {
            var values=new HashMap<Position,ItemValues>();
            for(var set:g.itemsets()) for(var item:set.items()) values.put(new Position(set.itemsetOrder(),item.itemOrder()),
                    new ItemValues(item.productId(),1,BigDecimal.ONE,"TEST-price",false));
            fields.put(g.structureKey(),new Fields("combo-"+g.primary().patternId(),"Fixture name","Fixture explanation","VND",
                    new BigDecimal("2.00"),new BigDecimal("2.00"),"TEST-price","NONE",values));
        }
        return new Request(grouping,context,preview,"TEST-write","batch-1",null,Instant.parse("2025-01-01T00:00:00Z"),fields);
    }
    private Request with(Request r,ComboPreview.Result preview,Map<StructureKey,Fields> fields) {
        return new Request(r.grouping(),r.context(),preview,r.writeIdentity(),r.batchId(),r.createdBy(),r.createdAt(),fields);
    }
    private ComboPreview.Result flags(Request r,List<ComboPreview.GroupEligibility> flags) {
        var p=r.preview();return new ComboPreview.Result(p.completed(),p.errors(),p.anchorProductId(),p.context(),p.configuration(),p.eligibleCount(),p.items(),flags);
    }
    @Test void mapsAllGroupsOutsideTopKWithOriginalPositionsSourcesAndDraftStatus() {
        var r=request(BigInteger.ONE);assertEquals(1,r.preview().items().size());
        var v=planner.plan(r);assertTrue(v.valid(),v.errors().toString());var p=v.plan();
        assertEquals(2,p.combos().size());assertEquals(4,p.items().size());assertEquals(3,p.sources().size());
        assertEquals("DRAFT",p.batch().status());assertTrue(p.combos().stream().allMatch(c->c.status().equals("DRAFT")));
        assertTrue(p.items().stream().anyMatch(i->i.productId().equals("K") && i.itemsetOrder()==2 && !i.anchorEligible()));
        for(var combo:p.combos()) assertEquals(1,p.sources().stream().filter(s->s.comboId().equals(combo.comboId()) && s.primary()).count());
        assertEquals(p,planner.plan(r).plan());
    }
    @Test void absentAndAlteredFlagsBlockEntirePlan() {
        var r=request(BigInteger.ONE);var all=r.preview().structuralEligibility();
        assertTrue(planner.plan(with(r,flags(r,List.of(all.getFirst())),r.fields())).errors().contains("ANCHOR_FLAGS_INCOMPLETE"));
        var old=all.getFirst();var changed=new ArrayList<>(old.items());var a=changed.getFirst();
        changed.set(0,new ComboPreview.AnchorFlag(a.itemsetOrder(),a.itemOrder(),a.productId(),!a.anchorEligible()));
        var altered=new ArrayList<>(all);altered.set(0,new ComboPreview.GroupEligibility(old.group(),changed));
        var v=planner.plan(with(r,flags(r,altered),r.fields()));assertFalse(v.valid());assertNull(v.plan());
    }
    @Test void topKMustFitUnsignedColumnEvenWhenLogicalPreviewAcceptsIt() {
        var r=request(new BigInteger("4294967296"));assertTrue(r.preview().completed());
        assertTrue(planner.plan(r).errors().contains("TOP_K_OUT_OF_COLUMN_RANGE"));
        assertTrue(planner.plan(request(new BigInteger("4294967295"))).valid());
    }
    @Test void missingMetadataNullCurrencyAndMissingPositionFailWithoutPartialPlan() {
        var r=request(BigInteger.ONE);var entries=new HashMap<>(r.fields());var key=entries.keySet().iterator().next();var f=entries.get(key);
        entries.put(key,new Fields(f.comboId(),null,f.explanation(),null,null,f.referenceSalePrice(),null,"NONE",Map.of()));
        var v=planner.plan(with(r,r.preview(),entries));assertFalse(v.valid());assertNull(v.plan());
        assertTrue(v.errors().containsAll(List.of("COMBO_FIELDS_INCOMPLETE","ITEM_FIELDS_INCOMPLETE")));
        entries.put(key,new Fields(f.comboId(),f.name(),f.explanation(),null,f.referenceOriginalPrice(),f.referenceSalePrice(),f.priceEvidenceRef(),"NONE",f.items()));
        assertFalse(planner.plan(with(r,r.preview(),entries)).valid());
    }
    @Test void duplicateStorageIdsAndDiscountChangesAreRejected() {
        var r=request(BigInteger.ONE);var entries=new HashMap<StructureKey,Fields>();
        r.fields().forEach((key,f)->entries.put(key,new Fields("same",f.name(),f.explanation(),f.currency(),f.referenceOriginalPrice(),f.referenceSalePrice(),f.priceEvidenceRef(),"PERCENTAGE",f.items())));
        var v=planner.plan(with(r,r.preview(),entries));assertTrue(v.errors().containsAll(List.of("INVALID_STORAGE_ID","DISCOUNT_OUT_OF_SCOPE")));
    }
    @Test void invalidPrimaryOrRunCannotBeHiddenByPreviewSubset() {
        var r=request(BigInteger.ONE);var original=r.grouping().groups().stream().filter(g->g.sources().size()>1).findFirst().orElseThrow();
        for(var links:List.of(original.sources().stream().map(s->new Source(s.candidate(),false)).toList(),
                original.sources().stream().map(s->new Source(s.candidate(),true)).toList())) {
            var bad=new Group(original.status(),original.context(),original.structureKey(),original.comboKind(),original.primary(),links,
                    original.itemsets(),original.supportAbs(),original.supportRatio(),original.patternWeight(),original.patternWeightPolicy(),original.weightConfirmationRef());
            var groups=new ArrayList<>(r.grouping().groups());groups.set(groups.indexOf(original),bad);
            var q=new Request(new CandidateGrouping.Result(true,List.of(),groups),r.context(),r.preview(),r.writeIdentity(),r.batchId(),null,r.createdAt(),r.fields());
            assertFalse(planner.plan(q).valid());
        }
        var c=r.context();var foreign=new Context("OTHER",c.snapshotId(),c.evaluationPolicyVersion(),c.policyConfirmationRef(),c.eligibilityMode(),c.publishedEvidenceRef(),c.catalogSnapshotRef(),c.evaluatedAt());
        assertFalse(planner.plan(new Request(r.grouping(),foreign,r.preview(),r.writeIdentity(),r.batchId(),null,r.createdAt(),r.fields())).valid());
    }
    @Test void writeAndRetryAlwaysBlockUntilDurableContractsAreApproved() {
        var writer=new DraftBatchWriter();var r=request(BigInteger.ONE);var first=writer.write(r);
        assertEquals("BLOCKED",first.status());assertNotNull(first.proposedPlan());assertEquals(first,writer.write(r));
        assertTrue(first.errors().contains("IDEMPOTENCY_STORAGE_UNCONFIRMED"));
        var altered=new Request(r.grouping(),r.context(),r.preview(),r.writeIdentity(),"different-batch",null,r.createdAt(),r.fields());
        assertEquals("BLOCKED",writer.write(altered).status()); // No claim that this is DB conflict detection.
    }
    @Test void emptyBatchAndIncompleteInputNeverProduceWritePlan() {
        var r=request(BigInteger.ONE);var empty=new CandidateGrouping.Result(true,List.of(),List.of());
        var preview=new ComboPreviewRanker().preview(empty,r.context(),"L",r.preview().configuration());
        var v=planner.plan(new Request(empty,r.context(),preview,r.writeIdentity(),r.batchId(),null,r.createdAt(),Map.of()));
        assertTrue(v.errors().contains("EMPTY_BATCH_POLICY_UNCONFIRMED"));assertNull(v.plan());
        assertEquals("INVALID",new DraftBatchWriter().write(null).status());
    }
}

