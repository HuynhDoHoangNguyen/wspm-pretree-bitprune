package vn.edu.wspm.backend.services.mining.handoff;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineProof;
import vn.edu.wspm.config.MinimumSupport;
import static vn.edu.wspm.backend.services.mining.handoff.WdmMiningInputAdapter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WdmMiningInputAdapterTest {
    static final String ID="11111111-2222-4333-8444-555555555555", ATTEMPT="00000000-0000-0000-0000-000000000002";
    static final Limits LIMITS=new Limits(10000,100,100);
    final ConversionReadService reads=mock(ConversionReadService.class);
    final ConversionReadService.VerifiedBundle bundle=mock(ConversionReadService.VerifiedBundle.class);
    final ConversionArtifactCodec codec=new ConversionArtifactCodec(JsonMapper.builder().build(),new ValidationLimits(1000,100,10000,10000,10000,1));
    byte[] sequence,mapping;
    ConversionArtifactResult publication;
    @BeforeEach void setup() {
        sequence=bytes("12 27 -1 -2\n12 -1 27 -1 -2\n");
        mapping=codec.mappingBytes(new ProductItemDictionary(Map.of("product-B",27,"product-A",12)));
        publish(sequence,mapping);
        when(reads.verified(ID)).thenReturn(bundle);
        when(bundle.rawSha256()).thenReturn("a".repeat(64)); when(bundle.rawSizeBytes()).thenReturn("900");
    }
    static byte[] bytes(String s) {return s.getBytes(StandardCharsets.UTF_8);}
    void publish(byte[] s,byte[] m) {
        publication=new ConversionArtifactResult(ID,ATTEMPT,"d".repeat(64),
                new ConversionArtifactResult.ArtifactFile("private",ConversionArtifactCodec.sha256(s),s.length),
                new ConversionArtifactResult.ArtifactFile("private",ConversionArtifactCodec.sha256(m),m.length),
                new ConversionArtifactResult.Statistics(2,1,1,3,2,4,2,2,2));
        when(bundle.result()).thenReturn(publication);
        when(bundle.sequenceBytes(anyInt())).thenAnswer(call->s.clone());when(bundle.mappingBytes(anyInt())).thenAnswer(call->m.clone());
    }
    static EngineProof build() {return new EngineProof("controlled-test-build","b".repeat(64),"TEST_ONLY_BUILD_WITNESS");}
    static Labels labels(Artifacts a) {
        return new Labels(LabelState.RESOLVED,a.binding(),"TEST_CLEANED_HISTORY","TEST_ONLY_SOURCE",
                a.products().entrySet().stream().map(e->new Label(e.getKey(),e.getValue(),"Historical "+e.getKey(),"SKU-"+e.getKey())).toList());
    }
    WdmMiningInputAdapter adapter(HistoricalLabels history) {return new WdmMiningInputAdapter(reads,codec,LIMITS,history,()->Optional.of(build()));}
    static Configuration config() {return new Configuration("WSPM_PRETREE",MinimumSupport.absolute(.4));}
    Handoff ready() {return adapter(WdmMiningInputAdapterTest::labels).prepare(ID,config());}
    static Binding representation(Binding b,String representation) {
        return new Binding(b.snapshotId(),b.attemptId(),b.validationSha256(),b.rawSha256(),b.rawSizeBytes(),b.artifactVersion(),b.policies(),
                representation,b.cleaningPolicy(),b.sequenceSha256(),b.sequenceSize(),b.mappingSha256(),b.mappingSize(),b.statistics());
    }
    @Test void verifiedNonDenseArtifactsKeepAssociationsBytesHashesAndExactEngineOutput() {
        var handoff=ready();var a=handoff.artifacts();
        assertEquals(EngineState.READY_FOR_PRETREE,handoff.engineState());
        assertEquals(Map.of(12,"product-A",27,"product-B"),a.products());
        assertArrayEquals(sequence,a.sequenceBytes());assertArrayEquals(mapping,a.mappingBytes());
        assertEquals(publication.mapping().sha256(),a.binding().mappingSha256());
        assertEquals(ATTEMPT,a.binding().attemptId()); assertEquals(publication.validationSha256(),a.binding().validationSha256());
        assertEquals("CLEANED_KEPT_RECORDS",a.binding().representation());
        var result=new PreparedSnapshotMiner().mine(handoff.minerInput().orElseThrow());
        assertEquals(Map.of("<(12)>",2,"<(27)>",2,"<(12 27)>",1,"<(12)(27)>",1),result.canonicalSupports());
        assertFalse(result.publishBlockers().isEmpty());verify(reads).verified(ID);
    }
    @Test void inputMapInsertionOrderDoesNotChangeWdmCanonicalBytesButReorderedWireIsRejected() {
        assertArrayEquals(mapping,codec.mappingBytes(new ProductItemDictionary(new LinkedHashMap<>(Map.of("product-A",12,"product-B",27)))));
        String reordered="{\"formatVersion\":\"CONVERSION_ARTIFACT_V1\",\"policyVersion\":\"GLOBAL_PRODUCT_ITEM_INT_V1\",\"items\":[{\"itemId\":27,\"productId\":\"product-B\"},{\"itemId\":12,\"productId\":\"product-A\"}]}\n";
        publish(sequence,bytes(reordered));
        assertEquals("ARTIFACT_INVALID",assertThrows(InputException.class,this::ready).getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"missing","duplicate-id","duplicate-product","conflict","zero"})
    void mappingFailuresRespectExistingCodecContract(String kind) {
        String content=new String(mapping,StandardCharsets.UTF_8);
        content=switch(kind) {
            case "missing" -> content.replace(",{\"itemId\":27,\"productId\":\"product-B\"}","");
            case "duplicate-id" -> content.replace("\"itemId\":27","\"itemId\":12");
            case "duplicate-product" -> content.replace("product-B","product-A");
            case "zero" -> content.replace("\"itemId\":12","\"itemId\":0");
            default -> content.replace("\"itemId\":27","\"itemId\":28");
        };
        publish(sequence,bytes(content));assertEquals("ARTIFACT_INVALID",assertThrows(InputException.class,this::ready).getMessage());
    }
    @ParameterizedTest @ValueSource(strings={"missing","conflict","raw","unbound","association","duplicate","unpaired"})
    void labelsCannotBecomeReadyWithoutPairedCleanedPublicationBinding(String kind) {
        var result=adapter(a->{
            var l=labels(a);
            return switch(kind) {
                case "missing" -> Labels.missing();
                case "conflict" -> new Labels(LabelState.CONFLICT,a.binding(),null,null,List.of());
                case "raw" -> new Labels(LabelState.RESOLVED,representation(a.binding(),"RAW_CSV"),l.sourcePolicy(),l.sourceReference(),l.items());
                case "unbound" -> new Labels(LabelState.RESOLVED,null,l.sourcePolicy(),l.sourceReference(),l.items());
                case "association" -> new Labels(LabelState.RESOLVED,a.binding(),l.sourcePolicy(),l.sourceReference(),List.of(new Label(12,"other","Name","SKU"),l.items().get(1)));
                case "duplicate" -> new Labels(LabelState.RESOLVED,a.binding(),l.sourcePolicy(),l.sourceReference(),List.of(l.items().getFirst(),l.items().getFirst()));
                default -> new Labels(LabelState.RESOLVED,a.binding(),l.sourcePolicy(),l.sourceReference(),List.of(new Label(12,"product-A","Name",null),l.items().get(1)));
            };
        }).prepare(ID,config());
        assertEquals(EngineState.BLOCKED,result.engineState());assertTrue(result.minerInput().isEmpty());assertFalse(result.blockers().isEmpty());
        assertTrue(result.labels().isEmpty());assertNotEquals(DictionaryState.RESOLVED,result.dictionaryState());
        assertArrayEquals(mapping,result.artifacts().mappingBytes());
    }
    @Test void unsupportedAlgorithmNeverInvokesPretreeBuildGateAndMissingBuildIsBlocked() {
        var engine=mock(vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineVerifier.class);
        var adapter=new WdmMiningInputAdapter(reads,codec,LIMITS,WdmMiningInputAdapterTest::labels,engine);
        var unsupported=adapter.prepare(ID,new Configuration("WSPM_BITPRUNE",MinimumSupport.absolute(.4)));
        assertEquals(EngineState.UNSUPPORTED,unsupported.engineState());assertTrue(unsupported.minerInput().isEmpty());verifyNoInteractions(engine);
        when(engine.verify()).thenReturn(Optional.empty());assertTrue(adapter.prepare(ID,config()).blockers().contains("ENGINE_REVISION_UNVERIFIED"));
        when(engine.verify()).thenReturn(Optional.of(new EngineProof("","b".repeat(64),"fixture")));
        assertTrue(adapter.prepare(ID,config()).blockers().contains("ENGINE_PROOF_INVALID"));
    }
    @Test void byteHashStatisticsAndLimitsAreVerifiedBeforeHandoff() {
        when(bundle.sequenceBytes(anyInt())).thenReturn(bytes("27 -1 -2\n"));
        assertEquals("ARTIFACT_BINDING_MISMATCH",assertThrows(InputException.class,this::ready).getMessage());
        setup();
        when(bundle.result()).thenReturn(new ConversionArtifactResult(ID,ATTEMPT,publication.validationSha256(),publication.sequence(),publication.mapping(),
                new ConversionArtifactResult.Statistics(2,1,1,3,2,4,1,3,2)));
        assertEquals("STATISTICS_MISMATCH",assertThrows(InputException.class,this::ready).getMessage());
        setup();clearInvocations(bundle);
        var tiny=new WdmMiningInputAdapter(reads,codec,new Limits(1,100,100),Artifacts->Labels.missing(),()->Optional.empty());
        assertEquals("ARTIFACT_LIMIT_EXCEEDED",assertThrows(InputException.class,()->tiny.prepare(ID,config())).getMessage());
        verify(bundle,never()).sequenceBytes(anyInt());verify(bundle,never()).mappingBytes(anyInt());
    }
    @Test void immutableEnvelopeAndOuterTransactionAndZeroThresholdGates() {
        var h=ready();h.artifacts().sequenceBytes()[0]='9';h.artifacts().mappingBytes()[0]='X';
        assertArrayEquals(sequence,h.artifacts().sequenceBytes());assertArrayEquals(mapping,h.artifacts().mappingBytes());
        assertThrows(UnsupportedOperationException.class,()->h.artifacts().products().put(1,"other"));
        assertThrows(UnsupportedOperationException.class,()->h.artifacts().binding().policies().clear());
        assertThrows(UnsupportedOperationException.class,()->h.labels().orElseThrow().items().clear());
        assertTrue(adapter(a->Labels.missing()).prepare(ID,new Configuration("WSPM_PRETREE",MinimumSupport.absolute(0)))
                .blockers().contains("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {assertEquals("ACTIVE_TRANSACTION_UNSUPPORTED",assertThrows(InputException.class,this::ready).getMessage());}
        finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
    }
}
