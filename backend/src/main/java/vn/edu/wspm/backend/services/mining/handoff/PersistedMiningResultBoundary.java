package vn.edu.wspm.backend.services.mining.handoff;

import java.math.BigInteger;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.persistence.BatchCanonical;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineProof;
import vn.edu.wspm.model.*;
import vn.edu.wspm.config.*;

/** Read-only contract pending producer protocol. No DAO, receipt issuer or evaluator authority. */
public final class PersistedMiningResultBoundary {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PersistedMiningResultBoundary.class);
    public record Limits(int maxPatterns,int maxDictionary,int maxItems,int maxPayloadBytes) {
        public Limits { if(maxPatterns<0 || maxDictionary<1 || maxItems<1 || maxPayloadBytes<1) throw new IllegalArgumentException("INVALID_LIMITS"); }
    }
    public record Run(String runId,String snapshotId,String status,String algorithm,String minsupMode,
                      double configuredMinsup,double effectiveThreshold,EngineProof build,WdmMiningInputAdapter.Binding input) {}
    public record DictionaryItem(String runId,int itemId,String productId,String nameSnapshot,String skuSnapshot,
                                 long occurrenceCount,long sequenceSupport,Double itemWeight) {}
    /** Complete collections from ONE producer-consistent read; lists never represent a UI page. */
    public record DictionaryBinding(WdmMiningInputAdapter.Binding input,String sourcePolicy,String sourceReference) {}
    public record DurableEvidence(String protocol,String identity,byte[] bytes) {
        public DurableEvidence { bytes=bytes.clone(); }
        @Override public byte[] bytes() {return bytes.clone();}
        @Override public String toString() {return "DurableEvidence[redacted]";}
    }
    public record Result(Run run,DictionaryBinding dictionaryBinding,List<DictionaryItem> dictionary,
                         List<PatternDetailResponse> patterns,DurableEvidence evidence) {
        public Result { dictionary=List.copyOf(dictionary); patterns=List.copyOf(patterns); }
        @Override public String toString() { return "PersistedMiningResult[redacted]"; }
    }
    /** Implementations must cap counts/bytes BEFORE materializing and read run/input/dictionary/all
     * patterns/items/evidence under one consistent snapshot. No page HTTP aggregation. */
    public interface Source { Optional<Result> readConsistent(String runId,Limits limits); }
    /** Producer-owned durable protocol adapter. Must validate an actual committed receipt/witness,
     * completeness and build/input ownership against the SAME read snapshot. A ref/hash/count alone
     * is insufficient. Never implement as `new Completion(...)` based only on caller claims. */
    public interface CompletionVerifier { Optional<Completion> verify(Result result,String payloadDigest); }
    public record Completion(String runId,String snapshotId,String payloadDigest,String producerProtocol,String durableIdentity) {
        @Override public String toString() { return "VerifiedCompletion[redacted]"; }
    }
    public enum State { UNAVAILABLE, NOT_READY, INVALID, READY }
    public static final class Read {
        private final State state;
        private final Result result;
        private final Completion completion;
        private final List<String> issues,comboBlockers;
        private Read(State s,Result r,Completion c,List<String> i,List<String> b) {state=s;result=r;completion=c;issues=List.copyOf(i);comboBlockers=List.copyOf(b);}
        public State state() { return state; }
        /** Partial/invalid payloads never escape as consumer-ready data. */
        public Optional<Result> result() { return Optional.ofNullable(result); }
        public Optional<Completion> completion() { return Optional.ofNullable(completion); }
        public List<String> issues() { return issues; }
        public List<String> comboBlockers() { return comboBlockers; }
        @Override public String toString() { return "FullResultRead[state="+state+"]"; }
    }
    private final Source source;
    private final CompletionVerifier completions;
    private final Limits limits;
    public PersistedMiningResultBoundary(Source source,CompletionVerifier completions,Limits limits) {
        this.source=Objects.requireNonNull(source);this.completions=Objects.requireNonNull(completions);this.limits=Objects.requireNonNull(limits);
    }
    /** Explicit deny-only production placeholder until a producer contract is implemented. */
    public static PersistedMiningResultBoundary unavailable(Limits limits) {
        return new PersistedMiningResultBoundary((id,l)->Optional.empty(),(r,d)->Optional.empty(),limits);
    }
    public Read read(String runId) {
        if(!WdmMiningInputAdapter.text(runId,40))return failure(State.INVALID,"INVALID_RUN_ID");
        if(TransactionSynchronizationManager.isActualTransactionActive())return failure(State.INVALID,"ACTIVE_TRANSACTION_UNSUPPORTED");
        final Result result;
        try {result=source.readConsistent(runId,limits).orElse(null);}catch(RuntimeException unavailable){return failure(State.UNAVAILABLE,"FULL_RESULT_SOURCE_UNAVAILABLE");}
        if(result==null)return failure(State.UNAVAILABLE,"FULL_RESULT_SOURCE_UNAVAILABLE");
        var r=result.run();
        if(r==null || !runId.equals(r.runId()))return failure(State.INVALID,"WRONG_RUN_OWNERSHIP");
        if(!"SUCCEEDED".equals(r.status())) {
            return Set.of("QUEUED","RUNNING","FAILED","LEGACY_UNVERIFIED").contains(Objects.toString(r.status(),""))
                    ?failure(State.NOT_READY,"RUN_NOT_READY"):failure(State.INVALID,"INVALID_RUN_STATE");
        }
        String issue;
        try {issue=validate(result);}catch(RuntimeException malformed){issue="FULL_RESULT_INVALID";}
        if(issue!=null)return failure(State.INVALID,issue);
        if(result.evidence()==null)return failure(State.NOT_READY,"COMPLETION_EVIDENCE_MISSING");
        if(!WdmMiningInputAdapter.text(result.evidence().protocol(),160) || !WdmMiningInputAdapter.text(result.evidence().identity(),256)
                || result.evidence().bytes.length<1 || result.evidence().bytes.length>limits.maxPayloadBytes())return failure(State.INVALID,"COMPLETION_EVIDENCE_INVALID");
        final Completion proof;
        final String digest;
        try {digest=payloadDigest(result,limits.maxPayloadBytes());proof=completions.verify(result,digest).orElse(null);}
        catch(RuntimeException unavailable){return failure(State.UNAVAILABLE,"COMPLETION_VERIFIER_UNAVAILABLE");}
        if(proof==null)return failure(State.NOT_READY,"COMPLETION_EVIDENCE_MISSING");
        if(!r.runId().equals(proof.runId()) || !r.snapshotId().equals(proof.snapshotId()) || !digest.equals(proof.payloadDigest())
                || !result.evidence().protocol().equals(proof.producerProtocol()) || !result.evidence().identity().equals(proof.durableIdentity()))
            return failure(State.INVALID,"COMPLETION_EVIDENCE_INVALID");
        var blockers=new ArrayList<String>();
        blockers.add("COMBO_AUTHORITY_UNCONFIRMED"); // No purpose/catalog/commercial/policy approval is conferred here.
        if(result.patterns().stream().anyMatch(p->p.patternWeight()==null || p.patternWeightPolicy()==null))blockers.add("APPLICATION_WEIGHT_UNAVAILABLE");
        log.debug("Full result validated: patterns={}, dictionaryItems={}, comboAuthorityConfirmed=false",result.patterns().size(),result.dictionary().size());
        return new Read(State.READY,result,proof,List.of(),blockers);
    }
    /** Engineering fingerprint of complete observed payload. Does not itself prove publication. */
    public static String payloadDigest(Result result,int maxBytes) {
        return ConversionArtifactCodec.sha256(BatchCanonical.encode(List.of("TV2_FULL_RESULT_V1",result.run(),
                result.dictionaryBinding(),result.dictionary(),result.patterns()),maxBytes));
    }
    private String validate(Result value) {
        var r=value.run();var b=r.input();
        if(b==null || !r.snapshotId().equals(b.snapshotId()) || !WdmMiningInputAdapter.uuid(b.snapshotId())
                || !WdmMiningInputAdapter.uuid(b.attemptId()) || !WdmMiningInputAdapter.hash(b.sequenceSha256())
                || !WdmMiningInputAdapter.hash(b.mappingSha256()) || !WdmMiningInputAdapter.hash(b.validationSha256())
                || !WdmMiningInputAdapter.hash(b.rawSha256()) || b.sequenceSize()<1 || b.mappingSize()<1
                || !"CONVERSION_ARTIFACT_V1".equals(b.artifactVersion()) || !"CLEANED_KEPT_RECORDS".equals(b.representation())
                || !ConversionArtifactCodec.POLICIES.equals(b.policies())
                || !vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline.POLICY_VERSION.equals(b.cleaningPolicy())
                || b.statistics()==null || b.statistics().sequenceCount()<1 || b.statistics().sequenceCount()>Integer.MAX_VALUE)
            return "INPUT_BINDING_INVALID";
        if(!Set.of("WSPM_PRETREE","WSPM_BITPRUNE").contains(Objects.toString(r.algorithm(),""))
                || !Set.of("PERCENT","ABSOLUTE").contains(Objects.toString(r.minsupMode(),""))
                || !Double.isFinite(r.configuredMinsup()) || r.configuredMinsup()<0 || r.minsupMode().equals("PERCENT")&&r.configuredMinsup()>100
                || !Double.isFinite(r.effectiveThreshold()) || !(r.effectiveThreshold()>0))return "CONFIGURATION_INVALID";
        var resolved=new MinimumSupportResolver().resolve(new MinimumSupport(r.configuredMinsup(),MinimumSupportMode.valueOf(r.minsupMode())),Math.toIntExact(b.statistics().sequenceCount()));
        if(Double.doubleToLongBits(resolved.effectiveThreshold())!=Double.doubleToLongBits(r.effectiveThreshold()))return "CONFIGURATION_INVALID";
        if(r.build()==null || !WdmMiningInputAdapter.hash(r.build().artifactSha256()) || !WdmMiningInputAdapter.text(r.build().revision(),160)
                || !WdmMiningInputAdapter.text(r.build().verificationRef(),255))return "BUILD_PROVENANCE_MISSING";
        var stats=b.statistics();
        if(stats.singleItemsetSequences()<0 || stats.multiItemsetSequences()<0 || stats.distinctItems()<1
                || stats.singleItemsetSequences()+stats.multiItemsetSequences()!=stats.sequenceCount()
                || stats.itemsetCount()<stats.sequenceCount() || stats.totalItemOccurrences()<stats.itemsetCount()
                || stats.minSequenceLength()<1 || stats.maxSequenceLength()<stats.minSequenceLength()
                || !Double.isFinite(stats.averageSequenceLength()) || Double.doubleToLongBits(stats.averageSequenceLength())
                    !=Double.doubleToLongBits((double)stats.totalItemOccurrences()/stats.sequenceCount()))return "STATISTICS_INVALID";
        if(value.dictionary().size()>limits.maxDictionary() || value.patterns().size()>limits.maxPatterns())return "FULL_RESULT_LIMIT_EXCEEDED";
        var db=value.dictionaryBinding();
        if(db==null || !b.equals(db.input()) || !WdmMiningInputAdapter.text(db.sourcePolicy(),160)
                || !WdmMiningInputAdapter.text(db.sourceReference(),256))return "DICTIONARY_BINDING_INVALID";
        var dictionary=new HashMap<Integer,DictionaryItem>();var products=new HashSet<String>();long occurrences=0;
        for(var d:value.dictionary()) {
            if(!r.runId().equals(d.runId()))return "WRONG_RUN_OWNERSHIP";
            if(d.itemId()<1 || !WdmMiningInputAdapter.text(d.productId(),40) || !WdmMiningInputAdapter.text(d.nameSnapshot(),200)
                    || !WdmMiningInputAdapter.text(d.skuSnapshot(),80) || dictionary.putIfAbsent(d.itemId(),d)!=null || !products.add(d.productId()))return "DICTIONARY_INVALID";
            if(d.occurrenceCount()<1 || d.sequenceSupport()<1 || d.sequenceSupport()>stats.sequenceCount()
                    || d.sequenceSupport()>d.occurrenceCount() || d.itemWeight()==null || !finite(d.itemWeight()) || d.itemWeight()>1)return "DICTIONARY_STATISTICS_INVALID";
            try {occurrences=Math.addExact(occurrences,d.occurrenceCount());}catch(ArithmeticException overflow){return "DICTIONARY_STATISTICS_INVALID";}
        }
        if(dictionary.isEmpty() || dictionary.size()!=b.statistics().distinctItems())return "DICTIONARY_INCOMPLETE";
        if(occurrences!=stats.totalItemOccurrences())return "DICTIONARY_STATISTICS_INVALID";
        var ids=new HashSet<String>();var canonicals=new HashSet<String>();long totalItems=0;
        for(var p:value.patterns()) {
            if(!r.runId().equals(p.runId()))return "WRONG_RUN_OWNERSHIP";
            if(p.patternId()==null || !p.patternId().matches("[1-9][0-9]{0,19}") || new BigInteger(p.patternId()).bitLength()>64
                    || !ids.add(p.patternId()) || !WdmMiningInputAdapter.text(p.canonicalPattern(),limits.maxPayloadBytes())
                    || !canonicals.add(p.canonicalPattern()))return "PATTERN_IDENTITY_INVALID";
            if(p.supportAbs()==null || !p.supportAbs().matches("[1-9][0-9]{0,18}")
                    || new BigInteger(p.supportAbs()).compareTo(BigInteger.valueOf(b.statistics().sequenceCount()))>0
                    || !Long.toString(b.statistics().sequenceCount()).equals(p.sequenceCount()))return "PATTERN_SUPPORT_INVALID";
            if(p.integrityStatus()!=PatternDetailResponse.IntegrityStatus.COMPLETE || !p.issues().isEmpty())return "PATTERN_INCOMPLETE";
            if(!finite(p.supportRatio()) || !finite(p.weightedSupport()) || !finite(p.patternWeight())
                    || (p.patternWeight()==null)!=(p.patternWeightPolicy()==null)
                    || p.patternWeightPolicy()!=null&&!WdmMiningInputAdapter.text(p.patternWeightPolicy(),80))return "PATTERN_METRICS_INVALID";
            if(p.itemsets().isEmpty() || p.itemsets().size()!=p.itemsetCount())return "PATTERN_ITEMS_INCOMPLETE";
            var sets=new ArrayList<Itemset>();long count=0,position=0;
            for(var group:p.itemsets()) {
                if(group.itemsetOrder()!=++position || group.items().isEmpty())return "PATTERN_ITEMS_INCOMPLETE";
                var items=new ArrayList<Item>();long itemPosition=0,previous=0;
                for(var item:group.items()) {
                    if(++totalItems>limits.maxItems())return "FULL_RESULT_LIMIT_EXCEEDED";
                    var d=dictionary.get((int)item.itemId());
                    if(item.itemId()<1 || item.itemId()>Integer.MAX_VALUE || item.itemOrder()!=++itemPosition || item.itemId()<=previous || d==null
                            || item.mappingStatus()!=PatternDetailResponse.MappingStatus.MAPPED || !d.productId().equals(item.productId())
                            || !d.nameSnapshot().equals(item.nameSnapshot()) || !d.skuSnapshot().equals(item.skuSnapshot()) || !finite(item.itemWeight()))return "PATTERN_ITEMS_INCOMPLETE";
                    if(!Objects.equals(d.itemWeight(),item.itemWeight()))return "PATTERN_ITEM_STATISTICS_MISMATCH";
                    items.add(new Item((int)item.itemId()));previous=item.itemId();count++;
                }
                sets.add(new Itemset(items));
            }
            if(count!=p.patternItemCount() || !new Pattern(sets).serialize().equals(p.canonicalPattern()))return "PATTERN_STRUCTURE_MISMATCH";
        }
        try {payloadDigest(value,limits.maxPayloadBytes());}catch(RuntimeException oversize){return "FULL_RESULT_PAYLOAD_INVALID";}
        return null;
    }
    private static boolean finite(Double d) { return d==null || Double.isFinite(d)&&d>=0; }
    private static Read failure(State state,String code) {
        log.debug("Full result not ready for consumers: state={}, code={}",state,code);
        return new Read(state,null,null,List.of(code),List.of("COMBO_AUTHORITY_UNCONFIRMED"));
    }
}
