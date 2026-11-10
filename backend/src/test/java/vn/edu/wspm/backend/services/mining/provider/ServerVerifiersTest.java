package vn.edu.wspm.backend.services.mining.provider;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.jar.*;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.edu.wspm.backend.config.mining.MiningPreparationConfiguration;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.mining.conversion.OrderSnapshotConverter;
import vn.edu.wspm.backend.services.mining.provider.SnapshotInputReader.*;

class ServerVerifiersTest {
    /** Explicit packaged-layout fixture entry, never used by application wiring.
     * Args are approved fixture digest, fixture build identity, owned temp-storage directory.
     * Allows testing the real default LoadedEngineVerifier under Boot's nested-JAR loader. */
    public static void main(String[] args) throws Exception {
        if(args.length!=3)throw new IllegalArgumentException("Expected fixture digest, revision and temp root");
        var fixture=new ServerVerifiersTest();fixture.root=Path.of(args[2]);fixture.setup();
        var p=properties();p.setProperty("engine-content-sha256",args[0]);p.setProperty("engine-revision",args[1]);
        p.setProperty("snapshot."+fixture.metadata.id()+".namespace","controlled-fixture-namespace");
        p.setProperty("snapshot."+fixture.metadata.id()+".metadata-binding",fixture.metadata.binding());
        var service=MiningPreparationService.configured(p,(id,products)->new Read(Optional.of(fixture.metadata),
                products.isEmpty()?Map.of():Map.of("P",List.of("P"))),fixture.storage);
        var result=service.prepare(fixture.metadata.id());
        if(result.state()!=TrustedRawSnapshotProvider.State.READY_FOR_MINING || result.minerInput().isEmpty())
            throw new IllegalStateException("Packaged fixture provider not ready");
        if(result.provenance().sourceProof().isEmpty() || result.provenance().engineProof().isEmpty())
            throw new IllegalStateException("Real verifiers did not supply fixture proofs");
        System.out.println("PACKAGED_PROVIDER_READY_WITH_REAL_VERIFIERS");
    }
    @TempDir Path root;
    RawSnapshotStorage storage;
    Metadata metadata;
    @BeforeEach void setup() throws Exception {
        storage=new RawSnapshotStorage(root.resolve("raw").toString(),new MultipartConfigElement("",10000,11000,0));
        var bytes=(OrderSnapshotConverter.HEADER+"\nO,1,c,2026-01-01T00:00:00.000000Z,COMPLETED,P,S,History,1,1.00,0.00,1.00,VND\n").getBytes(StandardCharsets.UTF_8);
        var raw=storage.save(new ByteArrayInputStream(bytes));
        metadata=new Metadata(raw.snapshotId(),"CSV","PENDING",raw.reference(),raw.sha256(),Long.toString(raw.sizeBytes()),
                "wdm-order-csv-v1","CSV_UPLOAD",null,"ordered_at","COMPLETED_CURRENT_AT_ACQUISITION",null,null,
                LocalDateTime.parse("2026-01-01T00:00:00"),"upload.csv","{} acquisition fixture");
    }
    OperatorSourceVerifier source(String binding) {
        return new OperatorSourceVerifier("unit-app-context",Map.of(metadata.id(),new OperatorSourceVerifier.Assignment(binding,"operator-namespace")),storage);
    }
    @Test void realSourceChecksOwnershipBytesAndAssignmentWithoutClaimingDatabaseOrigin() throws Exception {
        var verifier=source(metadata.binding());
        var proof=verifier.check(metadata).proof().orElseThrow();
        assertEquals(metadata.binding(),proof.metadataBinding());assertEquals("operator-namespace",proof.namespace());
        assertTrue(proof.verificationRef().contains("CSV_UPLOAD_OPERATOR_ASSIGNED_V1"));
        assertEquals("OPERATOR_ASSIGNMENT_MISMATCH",source("0".repeat(64)).check(metadata).reason());
        assertEquals("SNAPSHOT_NAMESPACE_UNASSIGNED",new OperatorSourceVerifier("context",Map.of(),storage).check(metadata).reason());
        Files.write(root.resolve("raw").resolve(metadata.rawReference()),new byte[]{1});
        assertEquals("ACQUISITION_ARTIFACT_UNVERIFIABLE",verifier.check(metadata).reason());
    }
    @Test void absentContextWrongSourceAndFabricatedScopeCannotGetProof() {
        assertEquals("APPLICATION_CONTEXT_UNCONFIGURED",new OperatorSourceVerifier(null,Map.of(),storage).check(metadata).reason());
        var db=new Metadata(metadata.id(),"DATABASE","PENDING",metadata.rawReference(),metadata.rawHash(),metadata.rawSize(),metadata.contractVersion(),
                "DATABASE_EXPORT","UTC",metadata.timeField(),metadata.statusPolicy(),metadata.createdAt(),metadata.createdAt().plusDays(1),
                metadata.createdAt(),metadata.sourceName(),metadata.completeReport());
        assertEquals("DATABASE_ACQUISITION_ATTESTATION_UNAVAILABLE",source(db.binding()).check(db).reason());
        var scoped=new Metadata(metadata.id(),"CSV","PENDING",metadata.rawReference(),metadata.rawHash(),metadata.rawSize(),metadata.contractVersion(),
                "CSV_UPLOAD","UTC",metadata.timeField(),metadata.statusPolicy(),metadata.createdAt(),metadata.createdAt().plusDays(1),
                metadata.createdAt(),metadata.sourceName(),metadata.completeReport());
        assertEquals("ACQUISITION_METADATA_INCONSISTENT",source(scoped.binding()).check(scoped).reason());
    }
    @Test void artifactDigestCoversEveryEntryNotJustEngineClassAndIgnoresZipPackaging() throws Exception {
        var a=jar("a.jar","one",false);var b=jar("b.jar","one",true);var changed=jar("changed.jar","two",false);
        assertEquals(digest(a),digest(b)); assertNotEquals(digest(a),digest(changed));
        try(var loader=new URLClassLoader(new URL[]{a.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            var anchor=loader.loadClass("vn.edu.wspm.config.MinimumSupportMode");
            var check=new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(digest(a),"independent-fixture-build"),List.of(anchor)).check();
            assertTrue(check.proof().isPresent());assertEquals(digest(a),check.proof().get().artifactSha256());
            assertEquals("ENGINE_DIGEST_MISMATCH",new LoadedEngineVerifier(new LoadedEngineVerifier.Expected("0".repeat(64),"build"),List.of(anchor)).check().reason());
            assertEquals("ENGINE_EXPECTATION_UNCONFIGURED",new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(digest(a),null),List.of(anchor)).check().reason());
        }
        try(var loader=new URLClassLoader(new URL[]{changed.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            assertEquals("ENGINE_DIGEST_MISMATCH",new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(digest(a),"approved-build"),
                    List.of(loader.loadClass("vn.edu.wspm.config.MinimumSupportMode"))).check().reason());
        }
    }
    @Test void explodedMissingExpectationAndUnreadableJarStayUnavailable() throws Exception {
        assertEquals("ENGINE_EXPECTATION_UNCONFIGURED",new LoadedEngineVerifier(null).check().reason());
        assertEquals("ENGINE_LAYOUT_UNSUPPORTED",new LoadedEngineVerifier(new LoadedEngineVerifier.Expected("0".repeat(64),"revision")).check().reason());
        Path jar=jar("lost.jar","one",false);
        // Load class, close URLClassLoader, then remove owned artifact: no fallback to class bytes.
        var loader=new URLClassLoader(new URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader());
        var anchor=loader.loadClass("vn.edu.wspm.config.MinimumSupportMode");String expected=digest(jar);loader.close();
        Files.delete(jar);
        assertTrue(new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(expected,"revision"),List.of(anchor)).verify().isEmpty());
    }
    @Test void splitClasspathAnchorsCannotBeVerifiedAsOneArtifact() throws Exception {
        var a=jar("one.jar","one",false);var b=jar("two.jar","one",false);
        try(var x=new URLClassLoader(new URL[]{a.toUri().toURL()},ClassLoader.getPlatformClassLoader());
            var y=new URLClassLoader(new URL[]{b.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            assertEquals("ENGINE_CLASSPATH_SPLIT",new LoadedEngineVerifier(new LoadedEngineVerifier.Expected(digest(a),"r"),List.of(
                    x.loadClass("vn.edu.wspm.config.MinimumSupportMode"),y.loadClass("vn.edu.wspm.config.MinimumSupportMode"))).check().reason());
        }
    }
    @Test void realVerifiersThroughProviderDoNotForgeReadyUnderExplodedTests() {
        var props=properties();props.setProperty("snapshot."+metadata.id()+".namespace","operator-namespace");
        props.setProperty("snapshot."+metadata.id()+".metadata-binding",metadata.binding());
        var read=(SnapshotInputReader)(id,products)->new Read(Optional.of(metadata),products.isEmpty()?Map.of():Map.of("P",List.of("P")));
        var service=MiningPreparationService.configured(props,read,storage);
        assertTrue(service.configured());var handoff=service.prepare(metadata.id());
        assertTrue(handoff.provenance().sourceProof().isPresent());
        assertEquals(List.of("ENGINE_REVISION_UNVERIFIED"),handoff.blockers());assertTrue(handoff.minerInput().isEmpty());
        assertEquals("ENGINE_LAYOUT_UNSUPPORTED",service.engineVerifier().orElseThrow().check().reason());
    }
    @Test void springWiringWithoutConfigDoesNotResolveDataSourceOrStorageOrRunAnything() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.register(MiningPreparationConfiguration.class);context.refresh();
            var service=context.getBean(MiningPreparationService.class);
            assertFalse(service.configured());assertEquals("PREPARATION_CONFIG_MISSING",service.unavailableReason().orElseThrow());
        }
    }
    @Test void configValidationIsUnavailableRatherThanStartupFailure() throws Exception {
        assertFalse(MiningPreparationService.fromFile(null,null,null).configured());
        var props=properties();props.remove("minsup-value");
        assertFalse(MiningPreparationService.configured(props,(id,p)->{throw new AssertionError();},storage).configured());
        props=properties();props.setProperty("max-rows","-1");
        assertFalse(MiningPreparationService.configured(props,(id,p)->{throw new AssertionError();},storage).configured());
        var file=root.resolve("configuration.properties");try(var w=Files.newBufferedWriter(file)){properties().store(w,"unit configuration");}
        var ds=org.mockito.Mockito.mock(javax.sql.DataSource.class);
        assertTrue(MiningPreparationService.fromFile(file.toString(),ds,storage).configured());
        org.mockito.Mockito.verifyNoInteractions(ds);
        assertEquals("PREPARATION_CONFIG_UNREADABLE",MiningPreparationService.fromFile(root.resolve("absent").toString(),ds,storage).unavailableReason().orElseThrow());
    }
    private static Properties properties() {
        var p=new Properties();p.setProperty("application-context","unit-context");p.setProperty("conversion-policy",OrderSnapshotConverter.POLICY);
        p.setProperty("max-raw-bytes","10000");p.setProperty("max-rows","20");p.setProperty("max-field-codepoints","300");
        p.setProperty("max-output-bytes","20000");p.setProperty("minsup-mode","ABSOLUTE");p.setProperty("minsup-value","0.4");
        p.setProperty("engine-content-sha256","0".repeat(64));p.setProperty("engine-revision","fixture-not-production");return p;
    }
    private Path jar(String name,String otherContent,boolean reverse) throws Exception {
        Path file=root.resolve(name);String entry="vn/edu/wspm/config/MinimumSupportMode.class";
        byte[] code;try(var input=vn.edu.wspm.config.MinimumSupportMode.class.getResourceAsStream("MinimumSupportMode.class")){code=input.readAllBytes();}
        var entries=new LinkedHashMap<String,byte[]>();entries.put(entry,code);entries.put("other-resource",otherContent.getBytes(StandardCharsets.UTF_8));
        var names=new ArrayList<>(entries.keySet());if(reverse)Collections.reverse(names);
        try(var output=new JarOutputStream(Files.newOutputStream(file))) {
            for(String key:names){var e=new JarEntry(key);e.setTime(reverse?123456789L:1);output.putNextEntry(e);output.write(entries.get(key));output.closeEntry();}
        }
        return file;
    }
    private static String digest(Path path) throws Exception {try(var j=new JarFile(path.toFile())){return MiningArtifactDigest.digest(j);}}
}
