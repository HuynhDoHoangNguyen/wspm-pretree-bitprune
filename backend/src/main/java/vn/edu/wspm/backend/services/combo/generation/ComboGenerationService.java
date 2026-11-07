package vn.edu.wspm.backend.services.combo.generation;

import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.wspm.backend.services.combo.evaluation.*;
import vn.edu.wspm.backend.services.combo.grouping.*;
import vn.edu.wspm.backend.services.combo.ranking.*;
import vn.edu.wspm.backend.services.combo.persistence.*;
import vn.edu.wspm.backend.services.mining.handoff.PersistedMiningResultBoundary;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Internal application entry point. No HTTP approval input, producer IO in write transaction or automatic retry. */
public final class ComboGenerationService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ComboGenerationService.class);
    public record Intent(String runId,String writeIdentity) {}
    /** Resolve purpose, policy, categories/eligibility, actor, catalog AND every commercial field from
     * trusted local sources. Freeze evaluatedAt, IDs and evidence per identity for durable retries.
     * Nonblank references are shape checks, not authorization. Never bind this record from HTTP. */
    public interface BusinessAuthority { BusinessDecision resolve(Intent intent,PersistedMiningResultBoundary.Read fullRun); }
    public record BusinessDecision(List<String> blockers,Grant grant) {
        public BusinessDecision {blockers=List.copyOf(blockers);}
        public static BusinessDecision unavailable(){return new BusinessDecision(List.of("BUSINESS_AUTHORITY_UNAVAILABLE"),null);}
    }
    public record Grant(String fullPayloadDigest,Purpose purpose,String purposeReference,Policy evaluationPolicy,
                        Map<String,CatalogProduct> catalog,String catalogReference,Instant evaluatedAt,
                        ComboPreview.Configuration ranking,String anchorProductId,String batchId,String createdBy,
                        Map<CandidateGrouping.StructureKey,Fields> fields,String generatorVersion,
                        String commercialPolicyReference,byte[] resolvedBusinessEvidence) {
        public Grant {catalog=Map.copyOf(catalog);fields=Map.copyOf(fields);resolvedBusinessEvidence=resolvedBusinessEvidence.clone();}
        @Override public byte[] resolvedBusinessEvidence(){return resolvedBusinessEvidence.clone();}
        @Override public String toString(){return "GenerationGrant[redacted]";}
    }
    /** Must verify the producer witness is backed by the existing V3 receipt accepted by V4 and bind
     * it to THIS complete read. miningBinding() alone never establishes that equivalence. All IO
     * finishes before writer invocation. Missing/incompatible producer protocol must return empty. */
    public interface WriterWitnessResolver { Optional<WriterWitness> resolve(PersistedMiningResultBoundary.Read fullRun); }
    public record WriterWitness(String fullPayloadDigest,String runFactsDigest,String miningReceiptDigest,String linkageReference) {
        @Override public String toString(){return "WriterWitness[redacted]";}
    }
    public record DecisionSummary(String patternId,Decision decision,List<String> reasons) {
        public DecisionSummary {reasons=List.copyOf(reasons);}
    }
    public record Outcome(String status,List<String> issues,List<DecisionSummary> decisions,int groupCount,int previewCount,Receipt receipt) {
        public Outcome {issues=List.copyOf(issues);decisions=List.copyOf(decisions);}
    }
    private record Compiled(Request request,List<DecisionSummary> decisions,List<String> issues) {}
    public record EvidenceRoot(String version,String fullPayloadDigest,String producerProtocol,String durableIdentity,
                               String producerEvidenceSha256,String writerLinkageReference,String generatorVersion,String commercialPolicy,byte[] businessEvidence) {
        public EvidenceRoot {businessEvidence=businessEvidence.clone();}
        @Override public byte[] businessEvidence(){return businessEvidence.clone();}
        @Override public String toString(){return "GenerationEvidence[redacted]";}
    }
    private final PersistedMiningResultBoundary source;
    private final BusinessAuthority business;
    private final WriterWitnessResolver witnesses;
    private final JdbcDraftBatchStore store;
    private final Clock clock;
    private final List<String> unavailable;
    private ComboGenerationService(List<String> unavailable) {this.unavailable=List.copyOf(unavailable);source=null;business=null;witnesses=null;store=null;clock=null;}
    public ComboGenerationService(PersistedMiningResultBoundary source,BusinessAuthority business,WriterWitnessResolver witnesses,
                                  JdbcDraftBatchStore store,Clock serverClock) {
        this.source=Objects.requireNonNull(source);this.business=Objects.requireNonNull(business);this.witnesses=Objects.requireNonNull(witnesses);
        this.store=Objects.requireNonNull(store);clock=Objects.requireNonNull(serverClock);unavailable=List.of();
    }
    public static ComboGenerationService unavailable(List<String> reasons){return new ComboGenerationService(reasons.isEmpty()?List.of("GENERATION_UNAVAILABLE"):reasons);}
    public Outcome generate(Intent intent){return execute(intent,false);}
    /** Explicit read-only writer reconciliation. UNKNOWN never triggers another insert here. */
    public Outcome reconcile(Intent intent){return execute(intent,true);}
    private Outcome execute(Intent intent,boolean reconcile) {
        if(intent==null || !text(intent.runId(),40) || !text(intent.writeIdentity(),200)
                || intent.writeIdentity().getBytes(StandardCharsets.UTF_8).length>800)return out("INVALID","INVALID_GENERATION_IDENTITY");
        if(TransactionSynchronizationManager.isActualTransactionActive())return out("BLOCKED","OUTER_TRANSACTION_UNSUPPORTED");
        if(!unavailable.isEmpty())return new Outcome("UNAVAILABLE",unavailable,List.of(),0,0,null);
        var read=source.read(intent.runId());
        if(read.state()!=PersistedMiningResultBoundary.State.READY)return new Outcome(switch(read.state()) {
            case UNAVAILABLE -> "UNAVAILABLE";case NOT_READY -> "NOT_READY";default -> "INVALID_RESULT";
        },read.issues(),List.of(),0,0,null);
        var result=read.result().orElseThrow();
        if(result.patterns().size()>store.limits().maxRunRows() || result.dictionary().size()>store.limits().maxRunRows())return out("BLOCKED","GENERATION_RESOURCE_LIMIT_EXCEEDED");
        if(result.patterns().isEmpty())return out("EMPTY_RESULT","EMPTY_BATCH_POLICY_UNCONFIRMED");
        if(result.patterns().stream().anyMatch(p->p.patternWeight()==null || p.patternWeightPolicy()==null))return out("BLOCKED","APPLICATION_WEIGHT_UNAVAILABLE");
        final String digest=read.completion().orElseThrow().payloadDigest();
        final Grant grant;final WriterWitness witness;
        try {
            var decision=business.resolve(intent,read);
            if(decision==null)return out("BLOCKED","BUSINESS_AUTHORITY_UNAVAILABLE");
            if(!decision.blockers().isEmpty())return decision.blockers().stream().allMatch(s->s.matches("[A-Z][A-Z0-9_]{0,100}"))
                    ?new Outcome("BLOCKED",decision.blockers(),List.of(),0,0,null):out("BLOCKED","BUSINESS_CONTEXT_INVALID");
            grant=decision.grant();
            if(!valid(grant,digest,store.limits()))return out("BLOCKED","BUSINESS_CONTEXT_INVALID");
            witness=witnesses.resolve(read).orElse(null);
            if(witness==null)return out("BLOCKED","WRITER_MINING_WITNESS_UNAVAILABLE");
            if(!digest.equals(witness.fullPayloadDigest()) || !hash(witness.runFactsDigest()) || !hash(witness.miningReceiptDigest())
                    || !text(witness.linkageReference(),256))return out("BLOCKED","WRITER_MINING_WITNESS_INVALID");
        }catch(RuntimeException failure){log.warn("Generation authority unavailable");return out("BLOCKED","GENERATION_AUTHORITY_UNAVAILABLE");}
        String reference=DraftBatchAuthority.miningReference(intent.runId(),witness.miningReceiptDigest());
        final Compiled compiled;
        try {compiled=compile(intent,result,grant,reference);}catch(RuntimeException invalid){return out("BLOCKED","GENERATION_CONTEXT_INVALID");}
        if(!compiled.issues().isEmpty())return new Outcome("BLOCKED",compiled.issues(),compiled.decisions(),0,0,null);
        var request=compiled.request();
        if(request.grouping().groups().isEmpty())return new Outcome("EMPTY_GENERATION",List.of("EMPTY_BATCH_POLICY_UNCONFIRMED"),compiled.decisions(),0,0,null);
        // Reproduction is pure/bounded and uses captured immutable input. No Source/business/witness IO in callback.
        DraftBatchAuthority authority=(serverRequest,plan,contentDigest)->{
            var replay=compile(intent,result,grant,reference);
            if(!replay.issues().isEmpty())return DraftBatchAuthority.Decision.blocked("GENERATION_REPRODUCTION_FAILED");
            var planned=new DraftBatchPlanner().plan(replay.request());
            if(!planned.valid())return DraftBatchAuthority.Decision.blocked("GENERATION_REPRODUCTION_FAILED");
            if(!contentDigest.equals(contentDigest(replay.request(),planned.plan(),store.limits().maxContentBytes()))
                    || !Arrays.equals(BatchCanonical.encode(plan,store.limits().maxContentBytes()),BatchCanonical.encode(planned.plan(),store.limits().maxContentBytes())))
                return DraftBatchAuthority.Decision.blocked("GENERATION_CONTENT_BINDING_INVALID");
            var cfg=grant.ranking();
            byte[] evidence=BatchCanonical.encode(new EvidenceRoot("TV2_GENERATION_EVIDENCE_V1",digest,
                    result.evidence().protocol(),result.evidence().identity(),vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.sha256(result.evidence().bytes()),
                    witness.linkageReference(),grant.generatorVersion(),grant.commercialPolicyReference(),grant.resolvedBusinessEvidence()),store.limits().maxEvidenceBytes());
            return new DraftBatchAuthority.Decision(List.of(),new DraftBatchAuthority.Authorization(contentDigest,witness.runFactsDigest(),witness.miningReceiptDigest(),
                    new DraftBatchAuthority.Approvals(grant.generatorVersion(),"PRODUCT_ITEMSETS_LENGTH_V1",grant.evaluationPolicy().confirmationRef(),
                            cfg.weightPolicy().confirmationRef(),cfg.rankingConfirmationRef(),cfg.anchorConfirmationRef(),grant.commercialPolicyReference(),grant.catalogReference()),evidence));
        };
        var writer=new DraftBatchWriter(store,authority,clock);
        var written=reconcile?writer.reconcile(request):writer.write(request);
        log.info("Combo generation completed: outcome={}, groups={}",written.status(),request.grouping().groups().size());
        return new Outcome(written.status(),written.errors(),compiled.decisions(),request.grouping().groups().size(),request.preview().items().size(),written.receipt());
    }
    private Compiled compile(Intent intent,PersistedMiningResultBoundary.Result result,Grant g,String publishedReference) {
        var run=result.run();
        var evidence=new Evidence(run.runId(),"SUCCEEDED",run.snapshotId(),"VALID",g.purpose(),g.purposeReference(),
                BigInteger.valueOf(result.patterns().size()),true,publishedReference,true,g.catalogReference(),g.evaluatedAt());
        var dictionary=result.dictionary().stream().map(d->new DictionaryItem(d.runId(),d.itemId(),d.productId(),d.nameSnapshot(),d.skuSnapshot())).toList();
        var session=new PatternCandidateEvaluator().evaluate(new Input(evidence,g.evaluationPolicy(),result.patterns(),dictionary,g.catalog()));
        var summaries=session.patterns().stream().map(p->new DecisionSummary(p.patternId(),p.decision(),p.reasons().stream().map(Reason::code).toList())).toList();
        if(!session.completed())return new Compiled(null,summaries,session.sessionErrors());
        var p=g.evaluationPolicy();
        var context=new CandidateGrouping.Context(run.runId(),run.snapshotId(),p.version(),p.confirmationRef(),p.eligibilityMode(),publishedReference,g.catalogReference(),g.evaluatedAt());
        var grouped=new CandidateGrouper().group(session,context,g.ranking().weightPolicy());
        if(!grouped.completed())return new Compiled(null,summaries,grouped.errors());
        var preview=new ComboPreviewRanker().preview(grouped,context,g.anchorProductId(),g.ranking());
        if(!preview.completed())return new Compiled(null,summaries,preview.errors());
        var request=new Request(grouped,context,preview,intent.writeIdentity(),g.batchId(),g.createdBy(),g.evaluatedAt(),g.fields());
        return new Compiled(request,summaries,List.of());
    }
    private static boolean valid(Grant g,String digest,JdbcDraftBatchStore.Limits l) {
        return g!=null && digest.equals(g.fullPayloadDigest()) && g.purpose()==Purpose.DEMO_CATALOG && text(g.purposeReference(),256)
                && g.evaluationPolicy()!=null && g.ranking()!=null && g.ranking().weightPolicy()!=null && text(g.catalogReference(),256)
                && g.evaluatedAt()!=null && g.evaluatedAt().getNano()%1000==0 && text(g.generatorVersion(),160)
                && text(g.commercialPolicyReference(),200) && g.resolvedBusinessEvidence.length>0 && g.resolvedBusinessEvidence.length<=l.maxEvidenceBytes()
                && g.fields().size()<=l.maxGroups() && g.catalog().size()<=l.maxRunRows();
    }
    private static String contentDigest(Request r,Plan plan,int limit) {
        var grouping=new CandidateGrouping.Result(true,List.of(),r.grouping().groups().stream().sorted(Comparator.comparing(g->g.structureKey().serialized())).toList());
        return vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.sha256(BatchCanonical.encode(Map.of("version","COMBO_DRAFT_CONTENT_V1","grouping",grouping,"plan",plan),limit));
    }
    private static boolean hash(String s){return s!=null&&s.matches("[a-f0-9]{64}");}
    private static boolean text(String s,int max){return s!=null&&!s.isBlank()&&s.codePointCount(0,s.length())<=max&&s.indexOf('\0')<0&&StandardCharsets.UTF_8.newEncoder().canEncode(s);}
    private static Outcome out(String status,String issue){return new Outcome(status,List.of(issue),List.of(),0,0,null);}
}
