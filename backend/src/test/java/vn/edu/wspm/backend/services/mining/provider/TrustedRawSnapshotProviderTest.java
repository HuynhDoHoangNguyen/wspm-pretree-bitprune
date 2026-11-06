package vn.edu.wspm.backend.services.mining.provider;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.MultipartConfigElement;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.conversion.*;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.backend.services.mining.provider.SnapshotInputReader.*;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.*;

class TrustedRawSnapshotProviderTest {
    @TempDir Path root;
    private RawSnapshotStorage storage;
    private RawSnapshotStorage.StoredRaw stored;
    private Metadata metadata;
    private byte[] csv;
    private static final OrderSnapshotConverter.Limits LIMITS=new OrderSnapshotConverter.Limits(10_000,20,300,20_000);
    private static final LocalDateTime TIME=LocalDateTime.parse("2026-01-01T00:00:00");
    @BeforeEach void setup() throws Exception {
        storage=new RawSnapshotStorage(root.toString(),new MultipartConfigElement("",20_000,21_000,0));
        csv=(OrderSnapshotConverter.HEADER+"\nO1,9007199254740993,c-A,2026-01-01T00:00:00.000000Z,COMPLETED,P,OLD,Historical,1,10.00,0.00,10.00,VND\n")
                .getBytes(StandardCharsets.UTF_8);
        acquire(csv);
    }
    private void acquire(byte[] raw) throws Exception {
        stored=storage.save(new ByteArrayInputStream(raw));
        metadata=new Metadata(stored.snapshotId(),"CSV","PENDING",stored.reference(),stored.sha256(),
                Long.toString(stored.sizeBytes()),"wdm-order-csv-v1","CSV_UPLOAD",null,"ordered_at",
                "COMPLETED_CURRENT_AT_ACQUISITION",null,null,TIME,"orders.csv","{fixture:raw-acquisition}");
    }
    private static Configuration config() { return new Configuration(LIMITS,MinimumSupport.absolute(.4),OrderSnapshotConverter.POLICY); }
    private static Optional<SourceProof> source(Metadata m) {
        return Optional.of(new SourceProof(m.binding(),"unit-fixture-namespace","CONTROLLED_FIXTURE_ONLY"));
    }
    private static Optional<EngineProof> engine() {
        // Actual class bytes hashed in controlled fixture; not a production artifact/build registry.
        try(var input=PreparedSnapshotMiner.class.getResourceAsStream("PreparedSnapshotMiner.class")) {
            return Optional.of(new EngineProof("fixture-class-digest",PreparedSnapshotMiner.sha256(input.readAllBytes()),"UNIT_CLASS_RESOURCE"));
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private SnapshotInputReader reader() {
        return (id,products)-> {
            var matches=new HashMap<String,List<String>>();
            products.forEach(p->matches.put(p,List.of(p)));
            return new Read(Optional.of(metadata),matches);
        };
    }
    private TrustedRawSnapshotProvider provider(SnapshotInputReader r) {
        return new TrustedRawSnapshotProvider(r,storage,config(),TrustedRawSnapshotProviderTest::source,TrustedRawSnapshotProviderTest::engine);
    }
    private static void code(String expected,org.junit.jupiter.api.function.Executable action) {
        var ex=assertThrows(ProviderException.class,action); assertEquals(expected,ex.code()); assertNull(ex.getCause());
    }
    private Metadata changed(String hash,String size,String reference,String status,String report) {
        return new Metadata(metadata.id(),metadata.sourceType(),status,reference,hash,size,metadata.contractVersion(),
                metadata.acquisition(),metadata.sourceTimeZone(),metadata.timeField(),metadata.statusPolicy(),
                metadata.fromTime(),metadata.toTime(),metadata.createdAt(),metadata.sourceName(),report);
    }
    @Test void realStorageConverterReadyHandoffBindsEveryMinerInputFieldAndIsImmutable() {
        var handoff=provider(reader()).prepare(stored.snapshotId());
        assertEquals(State.READY_FOR_MINING,handoff.state()); assertTrue(handoff.blockers().isEmpty());
        var converted=handoff.converted(); var input=handoff.minerInput().orElseThrow();
        assertEquals(stored.snapshotId(),input.snapshotId()); assertEquals(stored.sha256(),input.rawSha256());
        assertArrayEquals(converted.spmf(),input.spmf()); assertEquals(converted.sequenceSha256(),input.sequenceSha256());
        assertEquals(converted.dictionary(),input.dictionary()); assertEquals(converted.policy(),input.conversionPolicy());
        assertEquals(MinimumSupport.absolute(.4),input.minimumSupport());
        assertEquals("fixture-class-digest",input.engineRevision());
        assertEquals("CSV_UPLOAD",handoff.provenance().acquisition()); assertEquals("CSV",handoff.provenance().sourceType());
        assertNull(handoff.provenance().fromTime()); assertNull(handoff.provenance().sourceTimeZone());
        assertEquals(metadata.binding(),handoff.provenance().metadataBinding());
        byte[] data=input.spmf(); data[0]=0; assertEquals('1',input.spmf()[0]);
        assertThrows(UnsupportedOperationException.class,()->input.dictionary().clear());
        assertThrows(UnsupportedOperationException.class,()->handoff.blockers().add("x"));
        assertFalse(handoff.toString().contains("c-A"));
        // Engine invoked only by unit fixture, never inside provider.
        assertEquals(Map.of("<(1)>",1),new PreparedSnapshotMiner().mine(input).canonicalSupports());
    }
    @Test void missingNamespaceAndRevisionBlockInputButPreserveConversion() {
        var provider=new TrustedRawSnapshotProvider(reader(),storage,config(),m->Optional.empty(),Optional::empty);
        var out=provider.prepare(stored.snapshotId());
        assertEquals(State.BLOCKED,out.state()); assertTrue(out.minerInput().isEmpty());
        assertEquals(List.of("SOURCE_NAMESPACE_UNVERIFIED","ENGINE_REVISION_UNVERIFIED"),out.blockers());
        assertEquals(1,out.converted().statistics().sequenceCount());
    }
    @Test void missingOnlyOneTrustConditionNeverBuildsReadyInput() {
        var sourceMissing=new TrustedRawSnapshotProvider(reader(),storage,config(),m->Optional.empty(),TrustedRawSnapshotProviderTest::engine);
        assertEquals(List.of("SOURCE_NAMESPACE_UNVERIFIED"),sourceMissing.prepare(stored.snapshotId()).blockers());
        var engineMissing=new TrustedRawSnapshotProvider(reader(),storage,config(),TrustedRawSnapshotProviderTest::source,Optional::empty);
        assertEquals(List.of("ENGINE_REVISION_UNVERIFIED"),engineMissing.prepare(stored.snapshotId()).blockers());
    }
    @Test void invalidIdsAndUnsupportedPolicyFailBeforeReads() {
        var reads=new AtomicInteger(); SnapshotInputReader noReads=(id,p)->{reads.incrementAndGet();throw new AssertionError();};
        code("INVALID_SNAPSHOT_ID",()->provider(noReads).prepare("../raw.csv"));
        var unsupported=new TrustedRawSnapshotProvider(noReads,storage,new Configuration(LIMITS,MinimumSupport.absolute(.4),"OTHER"),
                TrustedRawSnapshotProviderTest::source,TrustedRawSnapshotProviderTest::engine);
        code("UNSUPPORTED_CONVERSION_POLICY",()->unsupported.prepare(stored.snapshotId())); assertEquals(0,reads.get());
        assertThrows(IllegalArgumentException.class,()->new OrderSnapshotConverter.Limits(-1,2,2,2));
        assertThrows(NullPointerException.class,()->new Configuration(null,MinimumSupport.absolute(.4),OrderSnapshotConverter.POLICY));
    }
    @Test void missingAndUnsupportedSnapshotStatusAreNotEmpty() {
        code("SNAPSHOT_NOT_FOUND",()->provider((id,p)->new Read(Optional.empty(),Map.of())).prepare(stored.snapshotId()));
        for(String status:List.of("INVALID","LEGACY_UNVERIFIED","other")) {
            metadata=changed(metadata.rawHash(),metadata.rawSize(),metadata.rawReference(),status,metadata.completeReport());
            code("SNAPSHOT_STATUS_UNSUPPORTED",()->provider(reader()).prepare(stored.snapshotId()));
        }
    }
    @Test void validatesMetadataAndLimitsBeforeStorageOrConversion() {
        metadata=changed(metadata.rawHash(),"999999999",metadata.rawReference(),"PENDING",metadata.completeReport());
        code("RAW_LIMIT_EXCEEDED",()->provider(reader()).prepare(stored.snapshotId()));
        metadata=changed("bad",metadata.rawSize(),metadata.rawReference(),"PENDING",metadata.completeReport());
        code("METADATA_INCONSISTENT",()->provider(reader()).prepare(stored.snapshotId()));
    }
    @Test void rawMissingCorruptHashAndSizeMismatchesAreDistinctFromEmpty() throws Exception {
        var file=root.resolve(stored.reference());
        Files.move(file,root.resolve("owned-missing-probe")); // temp fixture owned by this test only
        code("RAW_NOT_FOUND",()->provider(reader()).prepare(stored.snapshotId()));
        Files.move(root.resolve("owned-missing-probe"),file);
        metadata=changed("0".repeat(64),metadata.rawSize(),metadata.rawReference(),"PENDING",metadata.completeReport());
        code("RAW_UNVERIFIABLE",()->provider(reader()).prepare(stored.snapshotId()));
        metadata=changed(stored.sha256(),Long.toString(stored.sizeBytes()+1),metadata.rawReference(),"PENDING",metadata.completeReport());
        code("RAW_UNVERIFIABLE",()->provider(reader()).prepare(stored.snapshotId()));
    }
    @Test void corruptedActualBytesAndPathEscapeAreRejected() throws Exception {
        byte[] changed=csv.clone(); changed[changed.length-2]='X'; Files.write(root.resolve(stored.reference()),changed);
        code("RAW_UNVERIFIABLE",()->provider(reader()).prepare(stored.snapshotId()));
        metadata=changed(metadata.rawHash(),metadata.rawSize(),"../raw.csv","PENDING",metadata.completeReport());
        code("RAW_REFERENCE_INVALID",()->provider(reader()).prepare(stored.snapshotId()));
    }
    @Test void invalidCsvHasSafeTypedFailureNoRawCauseOrPartialOutput() throws Exception {
        acquire("private raw text".getBytes(StandardCharsets.UTF_8));
        var ex=assertThrows(ProviderException.class,()->provider(reader()).prepare(stored.snapshotId()));
        assertEquals("CSV_INVALID",ex.code()); assertNotNull(ex.detailCode());
        assertFalse(ex.toString().contains("private raw text")); assertNull(ex.getCause());
    }
    @Test void emptyDoesNotConsultEngineVerifierOrCreateInput() throws Exception {
        acquire((OrderSnapshotConverter.HEADER+"\n").getBytes(StandardCharsets.UTF_8));
        var p=new TrustedRawSnapshotProvider(reader(),storage,config(),m->Optional.empty(),()->{throw new AssertionError("engine called");});
        var out=p.prepare(stored.snapshotId());
        assertEquals(State.EMPTY,out.state()); assertTrue(out.minerInput().isEmpty());
        assertEquals(0,out.converted().statistics().totalItemOccurrences());
        assertEquals(List.of("SOURCE_NAMESPACE_UNVERIFIED"),out.blockers());
    }
    @Test void metadataChangedOrRemovedDuringReadIsDetected() {
        var original=metadata;
        var calls=new AtomicInteger();
        SnapshotInputReader r=(id,p)->{
            if(calls.incrementAndGet()==1) return new Read(Optional.of(original),Map.of());
            return new Read(Optional.of(changed(original.rawHash(),original.rawSize(),original.rawReference(),"VALID","{changed}")),Map.of("P",List.of("P")));
        };
        code("METADATA_CHANGED",()->provider(r).prepare(stored.snapshotId()));
        calls.set(0);
        code("METADATA_CHANGED",()->provider((id,p)->new Read(calls.incrementAndGet()==1?Optional.of(original):Optional.empty(),Map.of())).prepare(stored.snapshotId()));
    }
    @Test void metadataChangedBySlowSourceVerifierIncludingEmptyIsDetected() throws Exception {
        for(boolean empty:List.of(false,true)) {
            if(empty) acquire((OrderSnapshotConverter.HEADER+"\n").getBytes(StandardCharsets.UTF_8));
            var p=new TrustedRawSnapshotProvider(reader(),storage,config(),m->{
                metadata=changed(m.rawHash(),m.rawSize(),m.rawReference(),"VALID","changed-by-verifier");
                return source(m);
            },TrustedRawSnapshotProviderTest::engine);
            code("METADATA_CHANGED",()->p.prepare(stored.snapshotId()));
        }
    }
    @Test void productMissingAliasCollisionAndIncompleteCheckDoNotFallback() {
        for(var expected:Map.of("PRODUCT_NOT_FOUND",List.<String>of(),"PRODUCT_IDENTITY_COLLISION",List.of("p"),
                "PRODUCT_CHECK_UNVERIFIED",List.of("NOT_CHECKED")).entrySet()) {
            SnapshotInputReader r=(id,p)->new Read(Optional.of(metadata),expected.getKey().equals("PRODUCT_CHECK_UNVERIFIED")?
                    Map.of():Map.of("P",expected.getValue()));
            code(expected.getKey(),()->provider(r).prepare(stored.snapshotId()));
        }
    }
    @Test void wrongSourceProofOrEngineProofNeverBecomesReady() {
        var sourceBad=new TrustedRawSnapshotProvider(reader(),storage,config(),m->Optional.of(new SourceProof("other","ns","ref")),TrustedRawSnapshotProviderTest::engine);
        code("SOURCE_PROOF_INVALID",()->sourceBad.prepare(stored.snapshotId()));
        var engineBad=new TrustedRawSnapshotProvider(reader(),storage,config(),TrustedRawSnapshotProviderTest::source,
                ()->Optional.of(new EngineProof("revision","bad-hash","ref")));
        code("ENGINE_PROOF_INVALID",()->engineBad.prepare(stored.snapshotId()));
    }
    @Test void databaseClaimRequiresMetadataAndServerAttestationAndExplicitScope() {
        metadata=new Metadata(metadata.id(),"DATABASE","PENDING",metadata.rawReference(),metadata.rawHash(),metadata.rawSize(),
                metadata.contractVersion(),"DATABASE_EXPORT","UTC",metadata.timeField(),metadata.statusPolicy(),
                TIME,TIME.plusDays(1),TIME,"orders",metadata.completeReport());
        var noAttestation=new TrustedRawSnapshotProvider(reader(),storage,config(),m->Optional.empty(),TrustedRawSnapshotProviderTest::engine);
        assertEquals(State.BLOCKED,noAttestation.prepare(stored.snapshotId()).state());
        metadata=new Metadata(metadata.id(),metadata.sourceType(),metadata.validationStatus(),metadata.rawReference(),metadata.rawHash(),metadata.rawSize(),
                metadata.contractVersion(),metadata.acquisition(),metadata.sourceTimeZone(),metadata.timeField(),metadata.statusPolicy(),
                TIME.plusDays(1),TIME.plusDays(2),TIME,"orders",metadata.completeReport());
        code("EXPORT_SCOPE_MISMATCH",()->provider(reader()).prepare(stored.snapshotId()));
    }
    @Test void fabricatedUploadBoundsOrSourceUpgradeIsRejected() {
        metadata=new Metadata(metadata.id(),"DATABASE","PENDING",metadata.rawReference(),metadata.rawHash(),metadata.rawSize(),
                metadata.contractVersion(),"CSV_UPLOAD",null,metadata.timeField(),metadata.statusPolicy(),null,null,TIME,"name",metadata.completeReport());
        code("SOURCE_METADATA_UNSUPPORTED",()->provider(reader()).prepare(stored.snapshotId()));
    }
    @Test void repositoryAndStorageFailuresAreSafeNeverEmpty() {
        code("METADATA_READ_UNAVAILABLE",()->provider((id,p)->{throw new DataAccessResourceFailureException("SQL secret raw payload");}).prepare(stored.snapshotId()));
        var fake=org.mockito.Mockito.mock(RawSnapshotStorage.class);
        try { org.mockito.Mockito.when(fake.verifiedBytes(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenThrow(new IOException("private path")); }
        catch(IOException impossible){throw new AssertionError(impossible);}
        var p=new TrustedRawSnapshotProvider(reader(),fake,config(),TrustedRawSnapshotProviderTest::source,TrustedRawSnapshotProviderTest::engine);
        code("RAW_UNVERIFIABLE",()->p.prepare(stored.snapshotId()));
    }
    @Test void outerTransactionAndZeroEffectiveThresholdAreRejected() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {code("ACTIVE_TRANSACTION_UNSUPPORTED",()->provider(reader()).prepare(stored.snapshotId()));}
        finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        var p=new TrustedRawSnapshotProvider(reader(),storage,new Configuration(LIMITS,MinimumSupport.absolute(0),OrderSnapshotConverter.POLICY),
                TrustedRawSnapshotProviderTest::source,TrustedRawSnapshotProviderTest::engine);
        code("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED",()->p.prepare(stored.snapshotId()));
    }
    @Test void readProjectionCopiesCatalogCollections() {
        var list=new ArrayList<>(List.of("P")); var matches=new HashMap<String,List<String>>(); matches.put("P",list);
        var read=new Read(Optional.of(metadata),matches); list.clear(); matches.clear();
        assertEquals(List.of("P"),read.productMatches().get("P"));
        assertThrows(UnsupportedOperationException.class,()->read.productMatches().clear());
        assertFalse(metadata.toString().contains("orders.csv"));
    }
}
