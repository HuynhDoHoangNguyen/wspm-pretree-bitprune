package vn.edu.wspm.backend.services.combo.generation;

import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.edu.wspm.backend.config.mining.ComboGenerationConfiguration;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview;
import vn.edu.wspm.backend.services.combo.persistence.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;
import vn.edu.wspm.backend.services.mining.handoff.*;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineProof;
import static vn.edu.wspm.backend.services.combo.generation.ComboGenerationService.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ComboGenerationServiceTest {
    final Instant time=Instant.parse("2026-01-01T00:00:00Z");
    final JdbcDraftBatchStore store=mock(JdbcDraftBatchStore.class);
    final BusinessAuthority business=mock(BusinessAuthority.class);
    final WriterWitnessResolver witnesses=mock(WriterWitnessResolver.class);
    final Intent intent=new Intent("run-A","identity-A");
    final PersistedMiningResultBoundary.Limits readLimits=new PersistedMiningResultBoundary.Limits(10,10,100,100000);
    PersistedMiningResultBoundary.Result full;
    Grant grant;
    @BeforeEach void setup() {
        when(store.limits()).thenReturn(new JdbcDraftBatchStore.Limits(10,40,40,500,1048576,1048576,2097152));
        var b=new WdmMiningInputAdapter.Binding("11111111-2222-4333-8444-555555555555","00000000-0000-0000-0000-000000000002",
                "a".repeat(64),"b".repeat(64),"900",ConversionArtifactCodec.VERSION,ConversionArtifactCodec.POLICIES,"CLEANED_KEPT_RECORDS",OrderCleaningPipeline.POLICY_VERSION,
                "c".repeat(64),20,"d".repeat(64),200,new ConversionArtifactResult.Statistics(1,1,0,1,2,2,2,2,2));
        var run=new PersistedMiningResultBoundary.Run("run-A",b.snapshotId(),"SUCCEEDED","WSPM_PRETREE","ABSOLUTE",.1,.1,new EngineProof("TEST","e".repeat(64),"TEST_ONLY"),b);
        var dictionary=List.of(new PersistedMiningResultBoundary.DictionaryItem("run-A",12,"A","History A","SKU-A",1,1,.5),new PersistedMiningResultBoundary.DictionaryItem("run-A",27,"B","History B","SKU-B",1,1,.5));
        var items=List.of(new PatternDetailResponse.Item(1,12,PatternDetailResponse.MappingStatus.MAPPED,"A","History A","SKU-A",.5),new PatternDetailResponse.Item(2,27,PatternDetailResponse.MappingStatus.MAPPED,"B","History B","SKU-B",.5));
        var p=new PatternDetailResponse("run-A","1","<(12 27)>","1",1.0,.5,.5,"TEST-W",2,1,"1",PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),List.of(new PatternDetailResponse.Itemset(1,items)));
        full=new PersistedMiningResultBoundary.Result(run,new PersistedMiningResultBoundary.DictionaryBinding(b,"TEST_HISTORY","TEST_SOURCE"),dictionary,List.of(p),new PersistedMiningResultBoundary.DurableEvidence("TEST_PROTOCOL","TEST_RECEIPT",new byte[]{1}));
        var policy=new Policy("TEST-E","TEST-E-ref",Set.of("L"),Set.of("M"),EligibilityMode.CATALOG_ONLY);
        var config=new ComboPreview.Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1","TEST-rank",new WeightPolicyConfirmation("TEST-W","TEST-weight"),"FIRST_ITEMSET_V1","TEST-anchor",BigInteger.ONE,null,null);
        var key=new StructureKey("PRODUCT_ITEMSETS_LENGTH_V1",List.of(List.of("A","B")),"PRODUCT_ITEMSETS_LENGTH_V1|1|2|1:A1:B");
        var fields=new Fields("combo-A","Explicit name","Explicit explanation","VND",new BigDecimal("2"),new BigDecimal("2"),"TEST-price","NONE",Map.of(new Position(1,1),new ItemValues("A",1,BigDecimal.ONE,"TEST-price",false),new Position(1,2),new ItemValues("B",1,BigDecimal.ONE,"TEST-price",false)));
        grant=new Grant(digest(),Purpose.DEMO_CATALOG,"TEST-purpose",policy,Map.of("A",product("A","L"),"B",product("B","M")),"TEST-catalog",time,config,"A","batch-A",null,Map.of(key,fields),"TEST-generator","TEST-commercial",new byte[]{2});
        when(business.resolve(any(),any())).thenAnswer(c->new BusinessDecision(List.of(),grant));
        when(witnesses.resolve(any())).thenAnswer(c->Optional.of(new WriterWitness(digest(),"f".repeat(64),"a".repeat(64),"TEST_LINK")));
        when(store.persist(any(),any(),any(),any(),any())).thenReturn(new WriteResult("INSERTED",List.of(),null));
    }
    CatalogProduct product(String id,String category){return new CatalogProduct(id,category,"ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null);}
    String digest(){return PersistedMiningResultBoundary.payloadDigest(full,readLimits.maxPayloadBytes());}
    PersistedMiningResultBoundary source(){return new PersistedMiningResultBoundary((id,l)->Optional.of(full),(r,d)->Optional.of(new PersistedMiningResultBoundary.Completion(r.run().runId(),r.run().snapshotId(),d,r.evidence().protocol(),r.evidence().identity())),readLimits);}
    ComboGenerationService service(){return new ComboGenerationService(source(),business,witnesses,store,Clock.fixed(time.plusSeconds(999),ZoneOffset.UTC));}
    @Test void readyFullResultIsReproducedAndPassedToExistingExecutableWriter() {
        var out=service().generate(intent);assertEquals("INSERTED",out.status(),out.issues().toString());assertEquals(1,out.groupCount());assertEquals(Decision.ACCEPT,out.decisions().getFirst().decision());
        verify(store).persist(any(),argThat(p->p.sources().size()==1&&p.sources().getFirst().primary()&&p.items().getFirst().productId().equals("A")),any(),any(),any());
        verify(business,times(1)).resolve(any(),any());verify(witnesses,times(1)).resolve(any());
    }
    @ParameterizedTest @ValueSource(strings={"UNAVAILABLE","NOT_READY","INVALID"})
    void unavailableOrInvalidFullResultNeverReachesAuthorityOrWriter(String status) {
        var boundary=mock(PersistedMiningResultBoundary.class);var read=mock(PersistedMiningResultBoundary.Read.class);
        when(read.state()).thenReturn(PersistedMiningResultBoundary.State.valueOf(status));when(read.issues()).thenReturn(List.of("CONTROLLED_FAILURE"));when(boundary.read("run-A")).thenReturn(read);
        var out=new ComboGenerationService(boundary,business,witnesses,store,Clock.systemUTC()).generate(intent);
        assertNotEquals("INSERTED",out.status());verifyNoInteractions(business,witnesses);verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    @Test void zeroPatternsAndMissingWeightDoNotBecomeMiningErrorsOrEmptyWrites() {
        var original=full;
        full=new PersistedMiningResultBoundary.Result(full.run(),full.dictionaryBinding(),full.dictionary(),List.of(),full.evidence());
        assertEquals("EMPTY_RESULT",service().generate(intent).status());
        var p=original.patterns().getFirst();
        var missing=new PatternDetailResponse(p.runId(),p.patternId(),p.canonicalPattern(),p.supportAbs(),p.supportRatio(),p.weightedSupport(),null,null,p.patternItemCount(),p.itemsetCount(),p.sequenceCount(),p.integrityStatus(),p.issues(),p.itemsets());
        full=new PersistedMiningResultBoundary.Result(original.run(),original.dictionaryBinding(),original.dictionary(),List.of(missing),original.evidence());
        assertEquals(List.of("APPLICATION_WEIGHT_UNAVAILABLE"),service().generate(intent).issues());verifyNoInteractions(business,witnesses);
        verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    @Test void missingBusinessWitnessOrUnboundContextRemainBlocked() {
        when(business.resolve(any(),any())).thenReturn(BusinessDecision.unavailable());assertEquals("BLOCKED",service().generate(intent).status());
        when(business.resolve(any(),any())).thenReturn(new BusinessDecision(List.of(),null));assertEquals(List.of("BUSINESS_CONTEXT_INVALID"),service().generate(intent).issues());
        when(business.resolve(any(),any())).thenAnswer(c->new BusinessDecision(List.of(),grant));
        when(witnesses.resolve(any())).thenReturn(Optional.empty());assertEquals(List.of("WRITER_MINING_WITNESS_UNAVAILABLE"),service().generate(intent).issues());
        verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    Grant modified(String digest,ComboPreview.Configuration ranking,Map<String,CatalogProduct> catalog,String commercial) {
        return new Grant(digest,grant.purpose(),grant.purposeReference(),grant.evaluationPolicy(),catalog,grant.catalogReference(),grant.evaluatedAt(),ranking,
                grant.anchorProductId(),grant.batchId(),grant.createdBy(),grant.fields(),grant.generatorVersion(),commercial,grant.resolvedBusinessEvidence());
    }
    @Test void wrongFullBindingUnconfirmedWeightAndMissingCommercialNeverReachStore() {
        var original=grant;grant=modified("0".repeat(64),grant.ranking(),grant.catalog(),grant.commercialPolicyReference());
        assertEquals(List.of("BUSINESS_CONTEXT_INVALID"),service().generate(intent).issues());
        grant=original;grant=modified(digest(),grant.ranking(),grant.catalog(),null);
        assertEquals(List.of("BUSINESS_CONTEXT_INVALID"),service().generate(intent).issues());
        grant=original;var c=grant.ranking();
        grant=modified(digest(),new ComboPreview.Configuration(c.rankingPolicy(),c.rankingConfirmationRef(),new WeightPolicyConfirmation("TEST-W",null),c.anchorPolicy(),c.anchorConfirmationRef(),c.k(),c.approvedKCap(),c.kCapConfirmationRef()),grant.catalog(),grant.commercialPolicyReference());
        var out=service().generate(intent);assertEquals("BLOCKED",out.status());assertTrue(out.issues().contains("POLICY_UNCONFIRMED"));
        verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    @Test void rejectedPatternsKeepReasonCodesAndDoNotImplicitlyCreateEmptyBatch() {
        var catalog=new HashMap<>(grant.catalog());catalog.put("B",new CatalogProduct("B","M","INACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null));
        grant=modified(digest(),grant.ranking(),catalog,grant.commercialPolicyReference());
        var out=service().generate(intent);assertEquals("EMPTY_GENERATION",out.status(),out.issues().toString());
        assertEquals(Decision.REJECT,out.decisions().getFirst().decision());assertTrue(out.decisions().getFirst().reasons().contains("PRODUCT_NOT_ELIGIBLE"));
        verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"INSERTED","NO_OP","CONFLICT","BLOCKED","UNKNOWN","ROLLED_BACK"})
    void writerOutcomesAreNotReinterpretedOrAutomaticallyRetried(String outcome) {
        when(store.persist(any(),any(),any(),any(),any())).thenReturn(new WriteResult(outcome,List.of(),null));
        assertEquals(outcome,service().generate(intent).status());verify(store,times(1)).persist(any(),any(),any(),any(),any());
    }
    @Test void reconcileOnlyCallsExistingReadOnlyMechanism() {
        when(store.reconcile(any(),any(),any())).thenReturn(new WriteResult("UNKNOWN",List.of("NO_DURABLE_WRITE_RECEIPT"),null));
        assertEquals("UNKNOWN",service().reconcile(intent).status());verify(store).reconcile(any(),any(),any());verify(store,never()).persist(any(),any(),any(),any(),any());
    }
    @Test void missingProductionBeansLeaveServiceUnavailableWithoutDatasourceOrFixture() {
        try(var context=new AnnotationConfigApplicationContext(ComboGenerationConfiguration.class)) {
            var out=context.getBean(ComboGenerationService.class).generate(intent);assertEquals("UNAVAILABLE",out.status());
            assertTrue(out.issues().containsAll(List.of("FULL_RESULT_BOUNDARY_UNAVAILABLE","BUSINESS_AUTHORITY_UNAVAILABLE","WRITER_MINING_WITNESS_UNAVAILABLE","DRAFT_STORE_UNAVAILABLE")));
        }
        assertEquals("INVALID",service().generate(new Intent("run-A"," ")).status());
    }
}
