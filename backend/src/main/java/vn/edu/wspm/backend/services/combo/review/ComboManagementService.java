package vn.edu.wspm.backend.services.combo.review;

import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.*;
import vn.edu.wspm.backend.services.combo.admin.*;
import vn.edu.wspm.backend.services.combo.persistence.BatchCanonical;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Internal read/locked review orchestration; one eligibility rule for read and submit. */
@Service
public final class ComboManagementService {
    private static final org.slf4j.Logger log=LoggerFactory.getLogger(ComboManagementService.class);
    private final JdbcTemplate jdbc;
    private final JdbcComboAdminReader reader;
    private final ComboReviewAuthority authority;
    private final Clock clock;
    private final TransactionTemplate reads,writes;
    public record NotePolicy(boolean confirmed,boolean rejectRequired,int maxCodePoints) {}
    public record ReviewDetail(Row data,List<Itemset> itemsets,List<Source> sources,Assessment assessment,
                               String reviewToken,NotePolicy notePolicy) {}
    @Autowired public ComboManagementService(DataSource ds,JdbcComboAdminReader reader,ObjectProvider<ComboReviewAuthority> authorities) {
        this(ds,reader,Optional.ofNullable(authorities.getIfUnique()).orElse(b->ComboReviewAuthority.Check.unavailable()),Clock.systemUTC());
    }
    public ComboManagementService(DataSource ds,JdbcComboAdminReader reader,ComboReviewAuthority authority,Clock clock) {
        jdbc=new JdbcTemplate(ds);this.reader=reader;this.authority=authority;this.clock=clock;
        var manager=new DataSourceTransactionManager(ds);reads=new TransactionTemplate(manager);writes=new TransactionTemplate(manager);
        for(var t:List.of(reads,writes)){t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
        reads.setReadOnly(true);
    }
    private ComboAdminService admin() {
        return new ComboAdminService(new ComboAdminReader(){
            public Snapshot list(Filters f,long o,int s){return reader.listCurrent(f,o,s);}
            public Bundle detail(String id){return reader.detailCurrent(id);}
        },b->{var c=check(b);return new ComboReviewRules().assess(b,c);});
    }
    public Page list(String batch,String status,String kind,Integer page,Integer size) {
        return reads.execute(tx->admin().list(batch,status,kind,page,size));
    }
    public Page batchCombos(String batch,String status,String kind,Integer page,Integer size) {
        // List only: approval authority is evaluated when a specific detail is opened.
        return reads.execute(tx->new ComboAdminService(new ComboAdminReader(){
            public Snapshot list(Filters f,long o,int s){return reader.listCurrent(f,o,s);}
            public Bundle detail(String id){return reader.detailCurrent(id);}
        }).list(batch,status,kind,page,size));
    }
    public record BatchSummary(String batchId,String runId,String status,String createdAt,String comboCount,String draftCount,String approvedCount,String rejectedCount,String topK) {}
    public record BatchPage(int page,int size,String total,List<BatchSummary> items) {}
    /** Lightweight history: page parents first, aggregate only their children; no review/evidence IO. */
    public BatchPage batches(Integer page,Integer size) {
        int p=page==null?0:page,s=size==null?20:size;
        if(p<0||s<1||s>100)throw new vn.edu.wspm.backend.exception.ApplicationException(vn.edu.wspm.backend.exception.ErrorCode.INVALID_PAGE);
        return reads.execute(tx->{
            String total=jdbc.queryForObject("SELECT CAST(COUNT(*) AS CHAR) FROM COMBO_BATCH",String.class);
            var items=jdbc.query("""
                    SELECT b.batch_id,b.run_id,b.status,CAST(b.top_k AS CHAR) AS top_k,DATE_FORMAT(b.created_at,'%Y-%m-%dT%H:%i:%s.%fZ') AS created,
                      CAST(COUNT(c.combo_id) AS CHAR) AS total,
                      CAST(COALESCE(SUM(c.status='DRAFT'),0) AS CHAR) AS drafts,
                      CAST(COALESCE(SUM(c.status='APPROVED'),0) AS CHAR) AS approved,
                      CAST(COALESCE(SUM(c.status='REJECTED'),0) AS CHAR) AS rejected
                    FROM (SELECT batch_id,run_id,status,created_at,top_k FROM COMBO_BATCH
                          ORDER BY created_at DESC,batch_id DESC LIMIT ? OFFSET ?) b
                    LEFT JOIN COMBO c ON c.batch_id=b.batch_id
                    GROUP BY b.batch_id,b.run_id,b.status,b.created_at,b.top_k
                    ORDER BY b.created_at DESC,b.batch_id DESC
                    """,(r,n)->new BatchSummary(r.getString("batch_id"),r.getString("run_id"),r.getString("status"),r.getString("created"),r.getString("total"),r.getString("drafts"),r.getString("approved"),r.getString("rejected"),r.getString("top_k")),s,(long)p*s);
            log.debug("Combo batch history: page={}, rows={}",p,items.size());
            return new BatchPage(p,s,total,List.copyOf(items));
        });
    }
    public ReviewDetail detail(String id) {
        return reads.execute(tx->{
            var d=admin().detail(id);var b=reader.detailCurrent(id);var c=check(b);
            var assessment=new ComboReviewRules().assess(b,c);
            return new ReviewDetail(d.data(),d.itemsets(),d.sources(),assessment,
                    assessment.allowedActions().isEmpty()?null:token(b,c,false),
                    new NotePolicy(c.policy()!=null&&c.policy().confirmed(),true,1000));
        });
    }
    private ComboReviewAuthority.Check check(ComboAdminReader.Bundle b) {
        var c=authority.verify(b);return c==null?ComboReviewAuthority.Check.unavailable():c;
    }
    public Result review(String id,Command command,AdminPrincipal principal) {
        if(principal==null)throw new vn.edu.wspm.backend.exception.ApplicationException(vn.edu.wspm.backend.exception.ErrorCode.AUTHENTICATION_REQUIRED);
        if(!principal.roles().contains("ADMIN"))throw new vn.edu.wspm.backend.exception.ApplicationException(vn.edu.wspm.backend.exception.ErrorCode.ACCESS_DENIED);
        if(!ComboReviewDecider.validId(id)||!ComboReviewDecider.validCommand(command))return error("INVALID_REVIEW_COMMAND");
        if(TransactionSynchronizationManager.isActualTransactionActive())return error("OUTER_TRANSACTION_UNSUPPORTED");
        var completed=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            var locator=reads.execute(tx->jdbc.queryForList("SELECT b.run_id,c.batch_id FROM COMBO c JOIN COMBO_BATCH b ON b.batch_id=c.batch_id WHERE c.combo_id=?",id));
            if(locator.isEmpty())return error("COMBO_NOT_FOUND");
            String run=locator.getFirst().get("run_id").toString(),batch=locator.getFirst().get("batch_id").toString();
            return writes.execute(tx->{
                // Locator came from an independent read; no old RR view before acquiring parent locks.
                jdbc.queryForList("SELECT run_id FROM MINING_RUN WHERE run_id=? FOR UPDATE",run);
                var parents=jdbc.queryForList("SELECT batch_id,run_id FROM COMBO_BATCH WHERE batch_id=? FOR UPDATE",batch);
                if(parents.size()!=1||!run.equals(parents.getFirst().get("run_id")))return error("STALE_REVIEW_DATA");
                var locked=jdbc.queryForList("SELECT combo_id,batch_id FROM COMBO WHERE combo_id=? FOR UPDATE",id);
                if(locked.size()!=1||!id.equals(locked.getFirst().get("combo_id"))||!batch.equals(locked.getFirst().get("batch_id")))return error("STALE_REVIEW_DATA");
                // Current account/roles, not just claims captured at HTTP ingress.
                var actor=jdbc.queryForList("SELECT user_id,status FROM USER_ACCOUNT WHERE user_id=? FOR SHARE",principal.userId());
                var roles=jdbc.queryForList("SELECT r.code FROM USER_ROLE ur JOIN ROLE r ON r.role_id=ur.role_id WHERE ur.user_id=? FOR SHARE",principal.userId());
                if(actor.size()!=1||!principal.userId().equals(actor.getFirst().get("user_id"))||!"ACTIVE".equals(actor.getFirst().get("status"))||roles.stream().noneMatch(r->"ADMIN".equals(r.get("code"))))
                    return error("REVIEWER_IDENTITY_INVALID");
                // Establish the RR read view only after current locks were acquired.
                lockFacts(id,run,batch);
                var b=reader.detailCurrent(id);var c=check(b);
                var verification=Objects.equals(command.expectedReviewToken(),token(b,c,true))?TokenVerification.MATCHED:TokenVerification.STALE;
                var decision=new ComboReviewDecider(clock).decide(b,command,new Reviewer(true,true,principal.userId()),c.policy(),c.evidence(),verification);
                if(!"READY_TO_APPLY".equals(decision.outcome()))return decision;
                var change=decision.proposedChange();var audit=change.proposedAudit();
                int changed=jdbc.update("UPDATE COMBO SET status=?,reviewed_by=?,reviewed_at=?,review_note=? WHERE combo_id=? AND status='DRAFT'",
                        change.targetStatus(),audit.reviewedBy(),LocalDateTime.parse(audit.reviewedAt().substring(0,audit.reviewedAt().length()-1)),audit.reviewNote(),id);
                if(changed!=1)throw new IllegalStateException("REVIEW_UPDATE_CONFLICT");
                var after=reader.detailCurrent(id);
                if(!change.targetStatus().equals(after.row().comboStatus())||!audit.equals(after.row().review()))throw new IllegalStateException("REVIEW_READBACK_MISMATCH");
                completed.set(true);log.info("Combo review applied: decision={}",command.decision());
                return new Result("APPLIED",after.row().comboStatus(),after.row().review(),List.of(),null);
            });
        }catch(org.springframework.dao.DataAccessException | TransactionException failure) {
            log.error("Combo review transaction failed: type={}",failure.getClass().getSimpleName());
            boolean unknown=completed.get()||failure instanceof TransactionException;
            return new Result(unknown?"UNKNOWN":"ROLLED_BACK",null,null,List.of(unknown?"TRANSACTION_OUTCOME_UNKNOWN":"REVIEW_ROLLED_BACK"),null);
        }
    }
    private void lockFacts(String id,String run,String batch) { facts(id,run,batch,true); }
    private String token(ComboAdminReader.Bundle b,ComboReviewAuthority.Check c,boolean lock) {
        return "CR1:"+PreparedSnapshotMiner.sha256(BatchCanonical.encode(List.of("COMBO_REVIEW_V1",facts(b.row().comboId(),b.row().runId(),b.row().batchId(),lock),c),4*1024*1024));
    }
    private List<Object> facts(String id,String run,String batch,boolean lock) {
        String suffix=lock?" FOR SHARE":"";var all=new ArrayList<Object>();
        all.add(jdbc.queryForList("SELECT * FROM MINING_RUN WHERE run_id=?"+suffix,run));
        all.add(jdbc.queryForList("SELECT * FROM COMBO_BATCH WHERE batch_id=?"+suffix,batch));
        all.add(jdbc.queryForList("SELECT * FROM COMBO WHERE combo_id=?"+suffix,id));
        all.add(jdbc.queryForList("SELECT * FROM COMBO_ITEM WHERE combo_id=? ORDER BY itemset_order,item_order"+suffix,id));
        all.add(jdbc.queryForList("SELECT s.*,p.run_id,p.canonical_pattern,p.support_abs,p.support_ratio,p.pattern_weight,p.weighted_support,p.pattern_weight_policy FROM COMBO_SOURCE s JOIN PATTERN p ON p.pattern_id=s.pattern_id WHERE s.combo_id=? ORDER BY s.pattern_id"+suffix,id));
        all.add(jdbc.queryForList("SELECT pi.* FROM PATTERN_ITEM pi JOIN COMBO_SOURCE s ON s.pattern_id=pi.pattern_id WHERE s.combo_id=? ORDER BY pi.pattern_id,pi.itemset_order,pi.item_order"+suffix,id));
        all.add(jdbc.queryForList("SELECT d.* FROM DATASET_SNAPSHOT d JOIN MINING_RUN r ON r.snapshot_id=d.snapshot_id WHERE r.run_id=?"+suffix,run));
        all.add(jdbc.queryForList("SELECT d.* FROM RUN_ITEM_STATISTIC d JOIN COMBO_ITEM i ON i.product_id=d.product_id WHERE d.run_id=? AND i.combo_id=? ORDER BY d.item_id"+suffix,run,id));
        all.add(jdbc.queryForList("SELECT p.*,c.status AS category_status FROM PRODUCT p JOIN COMBO_ITEM i ON i.product_id=p.product_id JOIN CATEGORY c ON c.category_id=p.category_id WHERE i.combo_id=? ORDER BY p.product_id"+suffix,id));
        return all;
    }
    private static Result error(String code){return new Result("BLOCKED",null,null,List.of(code),null);}
}
