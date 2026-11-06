package vn.edu.wspm.backend.services.mining.provider;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.jar.JarFile;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.*;

/** No HEAD/class hash fallback. Trusted deployment config binds revision to a whole-artifact digest.
 * Trusts the JVM loader/deployment; this is integrity, not artifact signing or business approval. */
public final class LoadedEngineVerifier implements EngineVerifier {
    public record Expected(String digest, String revision) {}
    public record Check(Optional<EngineProof> proof,String reason) {}
    private final Expected expected;
    private final List<Class<?>> anchors;
    public LoadedEngineVerifier(Expected expected) {
        this(expected,List.of(vn.edu.wspm.mining.WspmPreTreeMiner.class,vn.edu.wspm.io.SpmfSequenceParser.class,
                vn.edu.wspm.config.MinimumSupportResolver.class,vn.edu.wspm.statistics.ItemStatisticsCalculator.class,
                vn.edu.wspm.model.Pattern.class));
    }
    // Package-private for isolated artifact/classloader fixtures; production anchors are fixed above.
    LoadedEngineVerifier(Expected expected,List<Class<?>> anchors) { this.expected=expected;this.anchors=List.copyOf(anchors); }
    @Override public Optional<EngineProof> verify() { return check().proof(); }
    public Check check() {
        if(expected==null || expected.digest()==null || !expected.digest().matches("[0-9a-f]{64}")
                || !OperatorSourceVerifier.validText(expected.revision(),160)) return blocked("ENGINE_EXPECTATION_UNCONFIGURED");
        try {
            JarURLConnection engine=null; String origin=null;
            for(var anchor:anchors) {
                URL resource=anchor.getResource("/"+anchor.getName().replace('.','/')+".class");
                if(resource==null) return blocked("ENGINE_ARTIFACT_MISSING");
                var connection=resource.openConnection();
                if(!(connection instanceof JarURLConnection jar)) return blocked("ENGINE_LAYOUT_UNSUPPORTED");
                // Own a separate non-cached JarFile for hashing; never retain/close a loader-owned handle.
                jar.setUseCaches(false);
                String current=jar.getJarFileURL().toExternalForm();
                if(origin!=null && !origin.equals(current)) return blocked("ENGINE_CLASSPATH_SPLIT");
                origin=current; engine=jar;
            }
            if(engine==null) return blocked("ENGINE_ARTIFACT_MISSING");
            String actual;
            try(JarFile jar=engine.getJarFile()) { actual=MiningArtifactDigest.digest(jar); }
            if(!expected.digest().equals(actual)) return blocked("ENGINE_DIGEST_MISMATCH");
            return new Check(Optional.of(new EngineProof(expected.revision(),actual,MiningArtifactDigest.SCHEME+":"+actual)),null);
        } catch(IOException | SecurityException | IllegalStateException failure) { return blocked("ENGINE_ARTIFACT_UNREADABLE"); }
    }
    private static Check blocked(String reason) { return new Check(Optional.empty(),reason); }
    /** No Spring context: deployment can probe loaded packaged core with PropertiesLauncher. */
    public static void main(String[] args) {
        if(args.length!=2) throw new IllegalArgumentException("Supply expected digest and deployment revision");
        var result=new LoadedEngineVerifier(new Expected(args[0],args[1])).check();
        System.out.println(result.proof().isPresent()?"ENGINE_VERIFIED:"+MiningArtifactDigest.SCHEME:result.reason());
        if(result.proof().isEmpty()) System.exit(2);
    }
}
