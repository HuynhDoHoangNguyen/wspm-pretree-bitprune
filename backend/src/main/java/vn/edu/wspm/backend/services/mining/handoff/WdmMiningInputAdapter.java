package vn.edu.wspm.backend.services.mining.handoff;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;
import vn.edu.wspm.backend.services.dataset.validation.StoredValidationReport;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineVerifier;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineProof;
import vn.edu.wspm.config.*;

/** Internal server-owned handoff only. Never converts raw CSV, runs an engine or publishes a run. */
public final class WdmMiningInputAdapter {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(WdmMiningInputAdapter.class);
    public record Limits(int maxArtifactBytes, int maxItems, long maxOccurrences) {
        public Limits { if (maxArtifactBytes < 1 || maxItems < 1 || maxOccurrences < 1) throw new IllegalArgumentException("INVALID_LIMITS"); }
    }
    /** Binds labels to the cleaned conversion publication, not merely a snapshot ID or raw hash. */
    public record Binding(String snapshotId, String attemptId, String validationSha256, String rawSha256,
                          String rawSizeBytes, String artifactVersion, Map<String,String> policies,
                          String representation, String cleaningPolicy, String sequenceSha256, long sequenceSize,
                          String mappingSha256, long mappingSize, ConversionArtifactResult.Statistics statistics) {
        public Binding { policies = Map.copyOf(policies); }
        @Override public String toString() { return "WdmBinding[redacted]"; }
    }
    public static final class Artifacts {
        private final Binding binding;
        private final byte[] sequence, mapping;
        private final Map<Integer,String> products;
        private Artifacts(Binding b, byte[] s, byte[] m, Map<Integer,String> p) {
            binding=b; sequence=s.clone(); mapping=m.clone(); products=Collections.unmodifiableMap(new TreeMap<>(p));
        }
        public Binding binding() { return binding; }
        public byte[] sequenceBytes() { return sequence.clone(); }
        public byte[] mappingBytes() { return mapping.clone(); }
        public Map<Integer,String> products() { return products; }
        @Override public String toString() { return "VerifiedWdmArtifacts[redacted]"; }
    }
    public record Label(int itemId, String productId, String nameSnapshot, String skuSnapshot) {}
    public enum LabelState { MISSING, CONFLICT, RESOLVED }
    public record Labels(LabelState state, Binding binding, String sourcePolicy, String sourceReference, List<Label> items) {
        public Labels { items=List.copyOf(items); }
        public static Labels missing() { return new Labels(LabelState.MISSING,null,null,null,List.of()); }
        @Override public String toString() { return "HistoricalLabels[state="+state+"]"; }
    }
    /** Must verify paired historical fields against CLEANED_KEPT_RECORDS and the complete binding.
     * A nonblank ref or caller-supplied labels is not such verification. No catalog fallback. */
    public interface HistoricalLabels { Labels resolve(Artifacts artifacts); }
    public record Configuration(String algorithm, MinimumSupport minimumSupport) {
        public Configuration { Objects.requireNonNull(algorithm); Objects.requireNonNull(minimumSupport); }
    }
    public enum EngineState { BLOCKED, UNSUPPORTED, READY_FOR_PRETREE }
    public enum DictionaryState { MISSING, CONFLICT, INVALID, RESOLVED }
    public static final class Handoff {
        private final Artifacts artifacts;
        private final Labels labels;
        private final DictionaryState dictionaryState;
        private final EngineState engineState;
        private final EngineProof build;
        private final PreparedSnapshotMiner.Input input;
        private final List<String> blockers;
        private Handoff(Artifacts a, Labels l, DictionaryState d, EngineState e, EngineProof b, PreparedSnapshotMiner.Input i, List<String> errors) {
            artifacts=a; labels=d==DictionaryState.RESOLVED?l:null; dictionaryState=d; engineState=e; build=b; input=i; blockers=List.copyOf(errors);
        }
        public Artifacts artifacts() { return artifacts; }
        public Optional<Labels> labels() { return Optional.ofNullable(labels); }
        public DictionaryState dictionaryState() { return dictionaryState; }
        public EngineState engineState() { return engineState; }
        public Optional<EngineProof> build() { return Optional.ofNullable(build); }
        public Optional<PreparedSnapshotMiner.Input> minerInput() { return Optional.ofNullable(input); }
        public List<String> blockers() { return blockers; }
        @Override public String toString() { return "WdmHandoff[engineState="+engineState+", published=false]"; }
    }
    public static final class InputException extends IllegalArgumentException {
        public InputException(String code) { super(code); }
    }
    private final ConversionReadService reads;
    private final ConversionArtifactCodec codec;
    private final Limits limits;
    private final HistoricalLabels historical;
    private final EngineVerifier engine;
    public WdmMiningInputAdapter(ConversionReadService reads, ConversionArtifactCodec codec, Limits limits,
                                 HistoricalLabels historical, EngineVerifier engine) {
        this.reads=Objects.requireNonNull(reads); this.codec=Objects.requireNonNull(codec); this.limits=Objects.requireNonNull(limits);
        this.historical=Objects.requireNonNull(historical); this.engine=Objects.requireNonNull(engine);
    }
    public Handoff prepare(String snapshotId, Configuration configuration) {
        Objects.requireNonNull(configuration);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw bad("ACTIVE_TRANSACTION_UNSUPPORTED");
        StoredValidationReport.canonicalId(snapshotId);
        // Official reader bounds its own file IO. Additional adapter caps are checked before getter copies.
        var bundle=reads.verified(snapshotId); var p=bundle.result();
        if (p==null || !snapshotId.equals(p.snapshotId()) || !uuid(p.attemptId()) || !hash(p.validationSha256())
                || !hash(bundle.rawSha256()) || bundle.rawSizeBytes()==null || !bundle.rawSizeBytes().matches("[1-9][0-9]{0,18}")) throw bad("PUBLICATION_INVALID");
        if (p.sequence()==null || p.mapping()==null || p.statistics()==null) throw bad("PUBLICATION_INVALID");
        for (var f:List.of(p.sequence(),p.mapping())) {
            if (f.sizeBytes()<1 || f.sizeBytes()>limits.maxArtifactBytes()) throw bad("ARTIFACT_LIMIT_EXCEEDED");
            if (!hash(f.sha256())) throw bad("PUBLICATION_INVALID");
        }
        if (p.statistics().distinctItems()>limits.maxItems() || p.statistics().totalItemOccurrences()>limits.maxOccurrences()) throw bad("ARTIFACT_LIMIT_EXCEEDED");
        byte[] sequence=bundle.sequenceBytes(limits.maxArtifactBytes()), mapping=bundle.mappingBytes(limits.maxArtifactBytes());
        if (sequence.length!=p.sequence().sizeBytes() || mapping.length!=p.mapping().sizeBytes()
                || !ConversionArtifactCodec.sha256(sequence).equals(p.sequence().sha256())
                || !ConversionArtifactCodec.sha256(mapping).equals(p.mapping().sha256())) throw bad("ARTIFACT_BINDING_MISMATCH");
        final ConversionArtifactCodec.Verified verified;
        final ConversionArtifactCodec.DictionaryDocument document;
        try {
            verified=codec.verify(sequence,mapping);
            document=JsonMapper.builder().build().readValue(mapping,ConversionArtifactCodec.DictionaryDocument.class);
        } catch (RuntimeException failure) { throw bad("ARTIFACT_INVALID"); }
        if (!verified.statistics().equals(p.statistics())) throw bad("STATISTICS_MISMATCH");
        var products=new TreeMap<Integer,String>();
        document.items().forEach(item->products.put(item.itemId(),item.productId())); // codec owns uniqueness/order/domains
        var binding=new Binding(snapshotId,p.attemptId(),p.validationSha256(),bundle.rawSha256(),bundle.rawSizeBytes(),
                ConversionArtifactCodec.VERSION,ConversionArtifactCodec.POLICIES,"CLEANED_KEPT_RECORDS",
                OrderCleaningPipeline.POLICY_VERSION,p.sequence().sha256(),sequence.length,p.mapping().sha256(),mapping.length,p.statistics());
        var artifacts=new Artifacts(binding,sequence,mapping,products);
        var errors=new ArrayList<String>();
        Labels labels;
        try { labels=historical.resolve(artifacts); }
        catch (RuntimeException unavailable) { labels=Labels.missing(); }
        if (labels==null || labels.state()==null || labels.state()==LabelState.MISSING) { labels=Labels.missing(); errors.add("HISTORICAL_LABELS_UNCONFIRMED"); }
        else if (labels.state()==LabelState.CONFLICT) errors.add("HISTORICAL_LABELS_CONFLICT");
        else if (!binding.equals(labels.binding()) || !text(labels.sourcePolicy(),160) || !text(labels.sourceReference(),256)) errors.add("HISTORICAL_LABEL_BINDING_INVALID");
        var dictionary=new TreeMap<Integer,PreparedSnapshotMiner.DictionaryItem>();
        if (errors.isEmpty()) {
            if (labels.items().size()!=products.size()) errors.add("HISTORICAL_LABEL_COVERAGE_INVALID");
            else for (var label:labels.items()) {
                if (!Objects.equals(products.get(label.itemId()),label.productId()) || !text(label.nameSnapshot(),200)
                        || !text(label.skuSnapshot(),80) || dictionary.putIfAbsent(label.itemId(),new PreparedSnapshotMiner.DictionaryItem(
                                label.productId(),label.nameSnapshot(),label.skuSnapshot()))!=null) errors.add("HISTORICAL_LABEL_COVERAGE_INVALID");
            }
        }
        DictionaryState dictionaryState=errors.isEmpty()?DictionaryState.RESOLVED:labels.state()==LabelState.MISSING
                ?DictionaryState.MISSING:labels.state()==LabelState.CONFLICT?DictionaryState.CONFLICT:DictionaryState.INVALID;
        boolean supported="WSPM_PRETREE".equals(configuration.algorithm());
        if (!supported) errors.add("ALGORITHM_UNSUPPORTED_BY_PREPARED_BOUNDARY");
        var resolved=new MinimumSupportResolver().resolve(configuration.minimumSupport(),Math.toIntExact(p.statistics().sequenceCount()));
        if (!(resolved.effectiveThreshold()>0)) errors.add("ZERO_EFFECTIVE_THRESHOLD_UNSUPPORTED");
        EngineProof build=null;
        if (supported) {
            try { build=engine.verify().orElse(null); } catch (RuntimeException unavailable) { /* blocked below */ }
            if (build==null) errors.add("ENGINE_REVISION_UNVERIFIED");
            else if (!text(build.revision(),160) || !hash(build.artifactSha256()) || !text(build.verificationRef(),255)) { errors.add("ENGINE_PROOF_INVALID"); build=null; }
        }
        var input=errors.isEmpty()?new PreparedSnapshotMiner.Input(snapshotId,binding.rawSha256(),sequence,binding.sequenceSha256(),
                binding.policies().get("mapping"),build.revision(),dictionary,configuration.minimumSupport()):null;
        log.debug("WDM handoff verified: items={}, engineInputAvailable={}, blockers={}",products.size(),input!=null,errors.size());
        return new Handoff(artifacts,labels,dictionaryState,!supported?EngineState.UNSUPPORTED:input==null?EngineState.BLOCKED:EngineState.READY_FOR_PRETREE,build,input,errors);
    }
    static boolean hash(String s) { return s!=null && s.matches("[0-9a-f]{64}"); }
    static boolean uuid(String s) { return s!=null && s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"); }
    static boolean text(String s,int max) { return s!=null && !s.isBlank() && s.codePointCount(0,s.length())<=max && s.indexOf('\0')<0 && StandardCharsets.UTF_8.newEncoder().canEncode(s); }
    private static InputException bad(String code) { return new InputException(code); }
}
