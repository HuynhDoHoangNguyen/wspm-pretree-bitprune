package vn.edu.wspm.backend.services.mining.handoff;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;
import static vn.edu.wspm.backend.services.mining.handoff.PersistedMiningResultBoundary.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistedMiningResultBoundaryTest {
    static final Limits LIMITS=new Limits(100,100,1000,100000);
    final WdmMiningInputAdapter.Binding binding=new WdmMiningInputAdapter.Binding(WdmMiningInputAdapterTest.ID,WdmMiningInputAdapterTest.ATTEMPT,
            "d".repeat(64),"a".repeat(64),"900",ConversionArtifactCodec.VERSION,ConversionArtifactCodec.POLICIES,"CLEANED_KEPT_RECORDS",
            OrderCleaningPipeline.POLICY_VERSION,"b".repeat(64),30,"c".repeat(64),220,
            new ConversionArtifactResult.Statistics(2,1,1,3,2,4,2,2,2));
    Run run(String id,String status) {return new Run(id,binding.snapshotId(),status,"WSPM_PRETREE","ABSOLUTE",.4,.4,WdmMiningInputAdapterTest.build(),binding);}
    Item item(long order,long id,String name) {return new Item(order,id,MappingStatus.MAPPED,id==12?"product-A":"product-B",name,"SKU-"+id,.5);}
    PatternDetailResponse pattern(String run,String canonical,List<Itemset> sets,Double weight,String policy) {
        return new PatternDetailResponse(run,"18446744073709551615",canonical,"2",1.0,null,weight,policy,2,sets.size(),"2",IntegrityStatus.COMPLETE,List.of(),sets);
    }
    Result result() {
        return new Result(run("run-A","SUCCEEDED"),new DictionaryBinding(binding,"CONTROLLED_CLEANED_HISTORY","TEST_ONLY_SOURCE"),
                List.of(new DictionaryItem("run-A",12,"product-A","Name12","SKU-12",2,2,.5),new DictionaryItem("run-A",27,"product-B","Name27","SKU-27",2,2,.5)),
                List.of(pattern("run-A","<(12 27)>",List.of(new Itemset(1,List.of(item(1,12,"Name12"),item(2,27,"Name27")))),null,null)),
                new DurableEvidence("CONTROLLED_TEST_PROTOCOL","TEST_ONLY_RECEIPT",new byte[]{1,2,3}));
    }
    Result with(Result r,Run run,List<DictionaryItem> dictionary,List<PatternDetailResponse> patterns,DurableEvidence evidence) {
        return new Result(run,r.dictionaryBinding(),dictionary,patterns,evidence);
    }
    /** Controlled whitelist witness; production must verify its producer's actual durable protocol. */
    PersistedMiningResultBoundary boundary(Result actual,Result approved) {
        String expected=payloadDigest(approved,LIMITS.maxPayloadBytes());
        return new PersistedMiningResultBoundary((id,l)->Optional.of(actual),(r,d)-> {
            if(!expected.equals(d)||!Arrays.equals(r.evidence().bytes(),new byte[]{1,2,3}))return Optional.empty();
            return Optional.of(new Completion(r.run().runId(),r.run().snapshotId(),d,"CONTROLLED_TEST_PROTOCOL","TEST_ONLY_RECEIPT"));
        },LIMITS);
    }
    @Test void completeNonDenseResultPreservesUnsignedIdentityStructureAndNullMetricWithoutComboApproval() {
        var r=result();var read=boundary(r,r).read("run-A");
        assertEquals(State.READY,read.state());assertEquals(r,read.result().orElseThrow());
        assertNull(read.result().orElseThrow().patterns().getFirst().patternWeight());
        assertTrue(read.comboBlockers().contains("APPLICATION_WEIGHT_UNAVAILABLE"));
        assertTrue(read.comboBlockers().contains("COMBO_AUTHORITY_UNCONFIRMED"));
        assertEquals("18446744073709551615",read.result().orElseThrow().patterns().getFirst().patternId());
        assertThrows(UnsupportedOperationException.class,()->r.dictionary().clear());
        r.evidence().bytes()[0]=99;assertEquals(1,r.evidence().bytes()[0]);
    }
    @Test void successfulZeroPatternsIsDistinctFromNotReadyAndMissingEvidence() {
        var r=result();var zero=with(r,r.run(),r.dictionary(),List.of(),r.evidence());
        assertEquals(State.READY,boundary(zero,zero).read("run-A").state());
        var queued=with(r,run("run-A","QUEUED"),r.dictionary(),List.of(),null);
        var read=boundary(queued,zero).read("run-A");assertEquals(State.NOT_READY,read.state());assertTrue(read.result().isEmpty());
        var missing=with(zero,zero.run(),zero.dictionary(),List.of(),null);
        assertEquals(List.of("COMPLETION_EVIDENCE_MISSING"),boundary(missing,zero).read("run-A").issues());
    }
    @ParameterizedTest @ValueSource(strings={"run","dictionary-run","pattern-run","missing-dictionary","duplicate-dictionary","dictionary-statistics","missing-item","canonical","label","metric-pair","dictionary-binding","config","missing-build"})
    void corruptedOrCrossRunPayloadNeverBecomesConsumerReady(String kind) {
        var r=result();Result bad=r;var p=r.patterns().getFirst();
        switch(kind) {
            case "run" -> bad=with(r,run("other","SUCCEEDED"),r.dictionary(),r.patterns(),r.evidence());
            case "dictionary-run" -> bad=with(r,r.run(),List.of(new DictionaryItem("other",12,"product-A","Name12","SKU-12",2,2,.5),r.dictionary().get(1)),r.patterns(),r.evidence());
            case "pattern-run" -> bad=with(r,r.run(),r.dictionary(),List.of(pattern("other",p.canonicalPattern(),p.itemsets(),null,null)),r.evidence());
            case "missing-dictionary" -> bad=with(r,r.run(),r.dictionary().subList(0,1),r.patterns(),r.evidence());
            case "duplicate-dictionary" -> bad=with(r,r.run(),List.of(r.dictionary().getFirst(),r.dictionary().getFirst()),r.patterns(),r.evidence());
            case "dictionary-statistics" -> bad=with(r,r.run(),List.of(new DictionaryItem("run-A",12,"product-A","Name12","SKU-12",3,2,.5),r.dictionary().get(1)),r.patterns(),r.evidence());
            case "missing-item" -> bad=with(r,r.run(),r.dictionary(),List.of(pattern("run-A",p.canonicalPattern(),List.of(new Itemset(1,List.of(item(1,12,"Name12")))),null,null)),r.evidence());
            case "canonical" -> bad=with(r,r.run(),r.dictionary(),List.of(pattern("run-A","<(12)(27)>",p.itemsets(),null,null)),r.evidence());
            case "label" -> bad=with(r,r.run(),r.dictionary(),List.of(pattern("run-A",p.canonicalPattern(),List.of(new Itemset(1,List.of(item(1,12,"Current catalog name"),item(2,27,"Name27")))),null,null)),r.evidence());
            case "metric-pair" -> bad=with(r,r.run(),r.dictionary(),List.of(pattern("run-A",p.canonicalPattern(),p.itemsets(),0.0,null)),r.evidence());
            case "dictionary-binding" -> bad=new Result(r.run(),new DictionaryBinding(WdmMiningInputAdapterTest.representation(binding,"RAW_CSV"),"POLICY","REF"),r.dictionary(),r.patterns(),r.evidence());
            case "config" -> bad=with(r,new Run("run-A",binding.snapshotId(),"SUCCEEDED","WSPM_PRETREE","ABSOLUTE",.4,.5,r.run().build(),binding),r.dictionary(),r.patterns(),r.evidence());
            case "missing-build" -> bad=with(r,new Run("run-A",binding.snapshotId(),"SUCCEEDED","WSPM_PRETREE","ABSOLUTE",.4,.4,null,binding),r.dictionary(),r.patterns(),r.evidence());
        }
        var read=boundary(bad,r).read("run-A");assertEquals(State.INVALID,read.state());assertTrue(read.result().isEmpty());assertTrue(read.completion().isEmpty());
    }
    @Test void witnessCannotBeSubstitutedByStatusCountOrNonblankReference() {
        var r=result();
        var noVerifier=new PersistedMiningResultBoundary((id,l)->Optional.of(r),(v,d)->Optional.empty(),LIMITS);
        assertEquals(List.of("COMPLETION_EVIDENCE_MISSING"),noVerifier.read("run-A").issues());
        var wrong=new PersistedMiningResultBoundary((id,l)->Optional.of(r),(v,d)->Optional.of(new Completion("other",binding.snapshotId(),d,"CONTROLLED_TEST_PROTOCOL","TEST_ONLY_RECEIPT")),LIMITS);
        assertEquals(List.of("COMPLETION_EVIDENCE_INVALID"),wrong.read("run-A").issues());
        var tampered=with(r,r.run(),r.dictionary(),r.patterns(),new DurableEvidence("CONTROLLED_TEST_PROTOCOL","TEST_ONLY_RECEIPT",new byte[]{9}));
        assertEquals(State.NOT_READY,boundary(tampered,r).read("run-A").state());
        // Same count but different complete payload cannot use the whitelist witness.
        var other=with(r,r.run(),r.dictionary(),List.of(pattern("run-A","<(12)(27)>",List.of(new Itemset(1,List.of(item(1,12,"Name12"))),new Itemset(2,List.of(item(1,27,"Name27")))),null,null)),r.evidence());
        assertEquals(State.NOT_READY,boundary(other,r).read("run-A").state());
    }
    @Test void absentProviderAndLimitsAndSafeFailuresRemainExplicit() {
        assertEquals(State.UNAVAILABLE,PersistedMiningResultBoundary.unavailable(LIMITS).read("run-A").state());
        var r=result();
        var capped=new PersistedMiningResultBoundary((id,l)->Optional.of(r),(v,d)->Optional.empty(),new Limits(0,100,1000,100000));
        assertEquals(List.of("FULL_RESULT_LIMIT_EXCEEDED"),capped.read("run-A").issues());
        var broken=new PersistedMiningResultBoundary((id,l)->{throw new IllegalStateException("private SQL");},(v,d)->Optional.empty(),LIMITS);
        assertEquals(List.of("FULL_RESULT_SOURCE_UNAVAILABLE"),broken.read("run-A").issues());
        assertFalse(broken.read("run-A").toString().contains("private"));
        var payloadCap=new PersistedMiningResultBoundary((id,l)->Optional.of(r),(v,d)->Optional.empty(),new Limits(100,100,1000,64));
        assertEquals(List.of("FULL_RESULT_PAYLOAD_INVALID"),payloadCap.read("run-A").issues());
        var itemsCap=new PersistedMiningResultBoundary((id,l)->Optional.of(r),(v,d)->Optional.empty(),new Limits(100,100,1,100000));
        assertEquals(List.of("FULL_RESULT_LIMIT_EXCEEDED"),itemsCap.read("run-A").issues());
    }
}
