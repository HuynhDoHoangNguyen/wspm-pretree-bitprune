package vn.edu.wspm.backend.services.combo.persistence;

import java.util.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Internal transactional writer. Explicit server authority required; no bean/endpoint/default approvals. */
public final class DraftBatchWriter {
    private static final Logger log=LoggerFactory.getLogger(DraftBatchWriter.class);
    private static final List<String> BLOCKERS=List.of("POLICY_EVIDENCE_STORAGE_UNCONFIRMED",
            "IDEMPOTENCY_STORAGE_UNCONFIRMED","COMMERCIAL_WRITER_CONTRACT_UNCONFIRMED");
    private final JdbcDraftBatchStore store;
    private final DraftBatchAuthority authority;
    private final Clock clock;
    /** Legacy/default wiring remains deny-only, never a fixture fallback. */
    public DraftBatchWriter(){store=null;authority=null;clock=null;}
    public DraftBatchWriter(JdbcDraftBatchStore store,DraftBatchAuthority authority,Clock serverClock) {
        this.store=Objects.requireNonNull(store);this.authority=Objects.requireNonNull(authority);this.clock=Objects.requireNonNull(serverClock);
    }
    public WriteResult write(Request request) {
        return execute(request,false);
    }
    /** Read-only recovery; absence stays UNKNOWN and never causes an automatic INSERT. */
    public WriteResult reconcile(Request request){return execute(request,true);}
    private WriteResult execute(Request request,boolean reconcile) {
        if(store==null) {
            var validation=new DraftBatchPlanner().plan(request);
            if(!validation.valid())return new WriteResult("INVALID",validation.errors(),null);
            log.warn("Draft batch write blocked: server authority/storage wiring unavailable");
            var blockers=new ArrayList<>(BLOCKERS);blockers.add("TRUSTED_BATCH_AUTHORITY_UNAVAILABLE");
            return new WriteResult("BLOCKED",blockers,validation.plan());
        }
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())return result("BLOCKED","OUTER_TRANSACTION_UNSUPPORTED",null);
        var limits=store.limits();
        if(request==null)return result("INVALID","INPUT_INVALID",null);
        if(!bounded(request,limits))return result("INVALID","RESOURCE_LIMIT_EXCEEDED",null);
        var serverRequest=new Request(request.grouping(),request.context(),request.preview(),request.writeIdentity(),request.batchId(),request.createdBy(),clock.instant().truncatedTo(ChronoUnit.MICROS),request.fields());
        final DraftBatchWrite.Validation validation;
        try {validation=new DraftBatchPlanner().plan(serverRequest);}
        catch(IllegalArgumentException | NullPointerException invalid){return result("INVALID","INPUT_INVALID",null);}
        if(!validation.valid())return new WriteResult(validation.errors().stream().anyMatch(e->e.endsWith("UNCONFIRMED"))?"BLOCKED":"INVALID",validation.errors(),null);
        var plan=validation.plan();
        try {
            // Planner/ranker already canonicalize groups; sort the caller enumeration for identity hashing too.
            var grouping=new vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.Result(true,List.of(),request.grouping().groups().stream()
                    .sorted(Comparator.comparing(g->g.structureKey().serialized())).toList());
            byte[] content=BatchCanonical.encode(JdbcDraftBatchStore.map("version","COMBO_DRAFT_CONTENT_V1","grouping",grouping,"plan",plan),limits.maxContentBytes());
            final DraftBatchAuthority.Decision decision;
            try {decision=authority.authorize(serverRequest,plan,JdbcDraftBatchStore.hash(content));}
            catch(RuntimeException unavailable) {log.error("Draft batch authority unavailable");return result("BLOCKED","AUTHORITY_UNAVAILABLE",plan);}
            if(decision==null)return result("BLOCKED","AUTHORITY_UNAVAILABLE",plan);
            if(!decision.blockers().isEmpty())return new WriteResult("BLOCKED",decision.blockers(),plan);
            var auth=decision.authorization();
            if(!valid(auth,plan,JdbcDraftBatchStore.hash(content),limits))return result("BLOCKED","AUTHORITY_BINDING_INVALID",plan);
            byte[] evidence=BatchCanonical.encode(JdbcDraftBatchStore.map("version","COMBO_DRAFT_EVIDENCE_V1","runId",plan.context().runId(),
                    "miningReference",plan.context().publishedEvidenceRef(),"authorization",auth),limits.maxEvidenceBytes());
            var outcome=reconcile?store.reconcile(plan,content,evidence):store.persist(serverRequest,plan,auth,content,evidence);
            log.info("Draft batch operation completed: status={}, combos={}",outcome.status(),plan.combos().size());
            return outcome;
        }catch(JdbcDraftBatchStore.Rejected invalid){return result("BLOCKED",invalid.code(),plan);}
        catch(IllegalArgumentException invalid){return result("INVALID","PAYLOAD_OR_EVIDENCE_INVALID",plan);}
        catch(org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException unavailable) {
            log.error("Draft batch authority/database unavailable");return result("BLOCKED","AUTHORITY_OR_DATABASE_UNAVAILABLE",plan);
        }
    }
    private static boolean valid(DraftBatchAuthority.Authorization a,Plan p,String content,JdbcDraftBatchStore.Limits limits) {
        if(a==null||!content.equals(a.contentDigest())||!hash(a.runFactsDigest())||!hash(a.miningReceiptDigest())
                ||a.resolvedEvidence().length<1||a.resolvedEvidence().length>limits.maxEvidenceBytes()||a.approvals()==null)return false;
        var v=a.approvals();var c=p.configuration();
        return text(v.generatorVersion())&&"PRODUCT_ITEMSETS_LENGTH_V1".equals(v.groupingVersion())
                &&p.context().policyConfirmationRef().equals(v.evaluationRef())&&c.weightPolicy().confirmationRef().equals(v.weightRef())
                &&c.rankingConfirmationRef().equals(v.rankingRef())&&c.anchorConfirmationRef().equals(v.anchorRef())
                &&text(v.commercialPolicyRef())&&p.context().catalogSnapshotRef().equals(v.catalogRef())
                &&DraftBatchAuthority.miningReference(p.context().runId(),a.miningReceiptDigest()).equals(p.context().publishedEvidenceRef());
    }
    private static boolean bounded(Request r,JdbcDraftBatchStore.Limits l) {
        if(r.grouping()!=null) {
            if(r.grouping().groups().size()>l.maxGroups())return false;long sources=0;
            for(var g:r.grouping().groups()) {
                sources+=g.sources().size();if(sources>l.maxSources())return false;
                for(var s:g.sources()) {
                    var c=s.candidate();if(c==null)continue;
                    if(c.itemsets().size()>4||c.source()==null||c.source().itemsets().size()>4)return false;
                    if(c.itemsets().stream().mapToLong(set->set.items().size()).sum()>4
                            ||c.source().itemsets().stream().mapToLong(set->set.items().size()).sum()>4)return false;
                }
            }
        }
        if(r.fields().size()>l.maxGroups()||r.fields().values().stream().mapToLong(f->f.items().size()).sum()>l.maxItems())return false;
        return r.writeIdentity()==null||r.writeIdentity().getBytes(StandardCharsets.UTF_8).length<=800;
    }
    private static boolean hash(String s){return s!=null&&s.matches("[a-f0-9]{64}");}
    private static boolean text(String s){return s!=null&&!s.isBlank()&&s.length()<=200&&s.indexOf('\0')<0&&StandardCharsets.UTF_8.newEncoder().canEncode(s);}
    private static WriteResult result(String status,String code,Plan p){log.warn("Draft batch operation rejected: code={}",code);return new WriteResult(status,List.of(code),p);}
}
