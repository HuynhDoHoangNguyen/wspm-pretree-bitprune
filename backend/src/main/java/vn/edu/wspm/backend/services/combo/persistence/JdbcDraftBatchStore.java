package vn.edu.wspm.backend.services.combo.persistence;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.services.mining.PreparedSnapshotMiner;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchAuthority.*;

/** Existing planner rows only. No engine, remote verification or file IO under the write transaction. */
public final class JdbcDraftBatchStore {
    public record Limits(int maxGroups,int maxItems,int maxSources,int maxRunRows,
                         int maxContentBytes,int maxEvidenceBytes,int maxRepresentationBytes) {
        public Limits {
            if(maxGroups<1||maxItems<1||maxSources<1||maxRunRows<1||maxContentBytes<1||maxContentBytes>1048576
                    ||maxEvidenceBytes<1||maxEvidenceBytes>1048576||maxRepresentationBytes<1||maxRepresentationBytes>2097152)
                throw new IllegalArgumentException("INVALID_WRITER_LIMITS");
        }
    }
    public record MiningBinding(String factsDigest,String receiptDigest) {}
    public static final class Rejected extends IllegalArgumentException {
        private final String code;
        public Rejected(String code){super(code);this.code=code;}
        public String code(){return code;}
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate writeTx,readTx;
    private final Limits limits;
    private final java.util.function.BiFunction<String,Boolean,MiningBinding> nativeBinding;
    public JdbcDraftBatchStore(DataSource dataSource,Limits limits) {
        this(dataSource,limits,null);
    }
    /** Native verifier runs on this transaction's JDBC connection; never starts an independent read. */
    public JdbcDraftBatchStore(DataSource dataSource,Limits limits,
            java.util.function.BiFunction<String,Boolean,MiningBinding> nativeBinding) {
        this.nativeBinding=nativeBinding;
        this.limits=Objects.requireNonNull(limits);jdbc=new JdbcTemplate(dataSource);
        var manager=new DataSourceTransactionManager(dataSource);
        writeTx=new TransactionTemplate(manager);readTx=new TransactionTemplate(manager);
        for(var t:List.of(writeTx,readTx)) {
            t.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            t.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        }
        readTx.setReadOnly(true);
    }
    public Limits limits(){return limits;}
    public boolean nativeProtocol(){return nativeBinding!=null;}
    /** Change-detection fingerprint ONLY, not a mining completion issuer or approval provider. */
    public MiningBinding miningBinding(String runId) {return readTx.execute(tx->binding(runId,false));}
    private MiningBinding binding(String runId,boolean lock) {
        if(nativeBinding!=null)return nativeBinding.apply(runId,lock);
        String suffix=lock?" FOR SHARE":"";
        var run=rows("SELECT * FROM MINING_RUN WHERE run_id=?"+suffix,runId);
        if(run.size()!=1||!runId.equals(run.getFirst().get("run_id"))||!"SUCCEEDED".equals(run.getFirst().get("status")))throw reject("RUN_NOT_PUBLISH_COMPLETE");
        var completion=rows("SELECT * FROM MINING_RUN_COMPLETION WHERE run_id=?"+suffix,runId);
        var input=rows("SELECT * FROM MINING_RUN_INPUT_BINDING WHERE run_id=?"+suffix,runId);
        if(completion.size()!=1||input.size()!=1)throw reject("MINING_COMPLETION_UNAVAILABLE");
        var c=completion.getFirst();
        if(!Arrays.equals((byte[])c.get("receipt_digest"),digest((byte[])c.get("manifest_bytes"))))throw reject("MINING_RECEIPT_CORRUPT");
        var facts=new LinkedHashMap<String,Object>();facts.put("version","COMBO_RUN_BINDING_V1");facts.put("run",run);
        facts.put("receipt",completion);facts.put("input",input);
        facts.put("snapshot",rows("SELECT s.* FROM DATASET_SNAPSHOT s JOIN MINING_RUN r ON r.snapshot_id=s.snapshot_id WHERE r.run_id=?"+suffix,runId));
        facts.put("patterns",rows("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id"+suffix,runId));
        facts.put("patternItems",rows("SELECT i.* FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id WHERE p.run_id=? ORDER BY i.pattern_id,i.itemset_order,i.item_order"+suffix,runId));
        facts.put("dictionary",rows("SELECT * FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id"+suffix,runId));
        return new MiningBinding(hash(BatchCanonical.encode(facts,limits.maxRepresentationBytes())),HexFormat.of().formatHex((byte[])c.get("receipt_digest")));
    }
    public WriteResult persist(Request request,Plan plan,Authorization auth,byte[] content,byte[] evidence) {
        var bodyCompleted=new java.util.concurrent.atomic.AtomicBoolean();
        try {
            return writeTx.execute(tx->{
                // Parent lock serializes cooperating writers of the same run before identity lookup.
                var run=rows("SELECT run_id,snapshot_id FROM MINING_RUN WHERE run_id=? FOR UPDATE",plan.context().runId());
                if(run.size()!=1||!plan.context().snapshotId().equals(run.getFirst().get("snapshot_id")))throw reject("SOURCE_RUN_MISMATCH");
                if(nativeBinding!=null){
                    var nativeNow=binding(plan.context().runId(),true);
                    if(!nativeNow.factsDigest().equals(auth.runFactsDigest())||!nativeNow.receiptDigest().equals(auth.miningReceiptDigest()))throw reject("MINING_EVIDENCE_CHANGED");
                }
                var old=rows("SELECT * FROM COMBO_BATCH_WRITE_EVIDENCE WHERE request_identity=? FOR UPDATE",key(plan));
                if(!old.isEmpty())return contextMatches(old.getFirst(),auth.resolvedEvidence())?reuse(plan,content,evidence,old.getFirst()):failure("CONFLICT","RESOLVED_CONTEXT_CHANGED",plan);
                var current=binding(plan.context().runId(),true);
                if(!current.factsDigest().equals(auth.runFactsDigest())||!current.receiptDigest().equals(auth.miningReceiptDigest()))throw reject("MINING_EVIDENCE_CHANGED");
                String reference=nativeBinding==null?miningReference(plan.context().runId(),current.receiptDigest()):nativeReference(plan.context().runId(),current.receiptDigest());
                if(!reference.equals(plan.context().publishedEvidenceRef()))throw reject("MINING_REFERENCE_MISMATCH");
                verifySourcesAndCatalog(request,plan);
                insert(plan);
                byte[] expected=representation(plan);
                byte[] actual=representation(plan.batch().batchId());
                if(!Arrays.equals(expected,actual))throw reject("WRITE_READBACK_MISMATCH");
                jdbc.update("""
                    INSERT INTO COMBO_BATCH_WRITE_EVIDENCE(request_identity,batch_id,run_id,content_version,
                      content_digest,content_bytes,evidence_digest,evidence_bytes,mining_receipt_digest,
                      expected_representation,representation_digest,created_at)
                    VALUES(?,?,?,1,?,?,?,?,?,?,?,?)
                    """,key(plan),plan.batch().batchId(),plan.context().runId(),digest(content),content,digest(evidence),evidence,
                        HexFormat.of().parseHex(auth.miningReceiptDigest()),actual,digest(actual),utc(plan.batch().createdAt()));
                if(nativeBinding!=null)jdbc.update("UPDATE COMBO_BATCH_WRITE_EVIDENCE SET resolved_context=? WHERE batch_id=?",
                        auth.resolvedEvidence(),plan.batch().batchId());
                // Read the durable envelope too, before the transaction is allowed to commit.
                var stored=rows("SELECT * FROM COMBO_BATCH_WRITE_EVIDENCE WHERE request_identity=?",key(plan));
                if(!contextMatches(stored.getFirst(),auth.resolvedEvidence()))throw reject("WRITE_READBACK_MISMATCH");
                var checked=reuse(plan,content,evidence,stored.getFirst());
                if(!"NO_OP".equals(checked.status()))throw reject("WRITE_READBACK_MISMATCH");
                bodyCompleted.set(true);
                return success("INSERTED",plan,content,evidence,plan.batch().createdAt());
            });
        } catch(Rejected rejected) {return failure("CONFLICT",rejected.code(),plan);}
        catch(DataAccessException | TransactionException failure) {
            // Body completed => commit/cleanup may have failed. Never infer rollback or write FAILED.
            if(bodyCompleted.get())return failure("UNKNOWN","COMMIT_OUTCOME_UNKNOWN",plan);
            if(failure instanceof DataIntegrityViolationException) {
                var found=reconcile(plan,content,evidence,auth.resolvedEvidence());
                return "UNKNOWN".equals(found.status())?failure("CONFLICT","STORAGE_CONSTRAINT_CONFLICT",plan):found;
            }
            return failure(failure instanceof DataAccessException?"ROLLED_BACK":"UNKNOWN",
                    failure instanceof DataAccessException?"WRITE_ROLLED_BACK":"TRANSACTION_OUTCOME_UNCONFIRMED",plan);
        }
    }
    /** Independent connection/transaction; never writes, inserts missing children or resets lifecycle. */
    public WriteResult reconcile(Plan plan,byte[] content,byte[] evidence) {
        return reconcile(plan,content,evidence,null);
    }
    public WriteResult reconcile(Plan plan,byte[] content,byte[] evidence,byte[] expectedContext) {
        try {
            return readTx.execute(tx->{
                if(nativeBinding!=null){var nativeNow=binding(plan.context().runId(),false);
                    if(!nativeReference(plan.context().runId(),nativeNow.receiptDigest()).equals(plan.context().publishedEvidenceRef()))return failure("CONFLICT","MINING_EVIDENCE_CHANGED",plan);}
                var found=rows("SELECT * FROM COMBO_BATCH_WRITE_EVIDENCE WHERE request_identity=?",key(plan));
                return found.isEmpty()?failure("UNKNOWN","NO_DURABLE_WRITE_RECEIPT",plan):contextMatches(found.getFirst(),expectedContext)
                    ?reuse(plan,content,evidence,found.getFirst()):failure("CONFLICT","RESOLVED_CONTEXT_CHANGED",plan);
            });
        } catch(DataAccessException | TransactionException | Rejected failure) {return failure("UNKNOWN","RECONCILIATION_UNAVAILABLE",plan);}
    }
    private boolean contextMatches(Map<String,Object> saved,byte[] expected){return nativeBinding==null||expected!=null&&Arrays.equals(expected,(byte[])saved.get("resolved_context"));}
    private WriteResult reuse(Plan p,byte[] content,byte[] evidence,Map<String,Object> stored) {
        if(!p.batch().batchId().equals(stored.get("batch_id"))||!p.context().runId().equals(stored.get("run_id"))
                ||!Arrays.equals(content,(byte[])stored.get("content_bytes"))||!Arrays.equals(evidence,(byte[])stored.get("evidence_bytes")))
            return failure("CONFLICT","WRITE_IDENTITY_CONTENT_CONFLICT",p);
        if(!Arrays.equals(digest(content),(byte[])stored.get("content_digest"))||!Arrays.equals(digest(evidence),(byte[])stored.get("evidence_digest")))
            return failure("CONFLICT","WRITE_RECEIPT_CORRUPT",p);
        Instant at=instant(stored.get("created_at"));
        byte[] expected=representation(withTime(p,at)),saved=(byte[])stored.get("expected_representation");
        if(!Arrays.equals(expected,saved)||!Arrays.equals(digest(saved),(byte[])stored.get("representation_digest"))
                ||!Arrays.equals(saved,representation(p.batch().batchId())))return failure("CONFLICT","PERSISTED_BATCH_INCONSISTENT",p);
        return success("NO_OP",withTime(p,at),content,evidence,at);
    }
    private void verifySourcesAndCatalog(Request request,Plan plan) {
        if(plan.batch().createdBy()!=null) {
            var actor=rows("SELECT user_id,status FROM USER_ACCOUNT WHERE user_id=? FOR SHARE",plan.batch().createdBy());
            if(actor.size()!=1||!plan.batch().createdBy().equals(actor.getFirst().get("user_id"))||!"ACTIVE".equals(actor.getFirst().get("status")))throw reject("ACTOR_UNAVAILABLE");
            if(nativeBinding!=null&&rows("SELECT r.code FROM USER_ROLE u JOIN ROLE r ON r.role_id=u.role_id WHERE u.user_id=? FOR SHARE",plan.batch().createdBy()).stream().noneMatch(r->"ADMIN".equals(r.get("code"))))throw reject("ACTOR_UNAVAILABLE");
        }
        for(var group:request.grouping().groups())for(var link:group.sources()) {
            var candidate=link.candidate();var source=candidate.source();
            var patterns=rows("SELECT * FROM PATTERN WHERE pattern_id=? FOR SHARE",new BigDecimal(candidate.patternId()));
            if(patterns.size()!=1)throw reject("SOURCE_PATTERN_MISSING");var row=patterns.getFirst();
            if(!plan.context().runId().equals(row.get("run_id"))||!source.canonicalPattern().equals(row.get("canonical_pattern"))
                    ||!source.supportAbs().equals(row.get("support_abs").toString())
                    ||!same(source.supportRatio(),row.get("support_ratio"))||!same(source.weightedSupport(),row.get("weighted_support"))
                    ||!same(source.patternWeight(),row.get("pattern_weight"))||!Objects.equals(source.patternWeightPolicy(),row.get("pattern_weight_policy"))
                    ||source.patternItemCount()!=((Number)row.get("pattern_item_count")).longValue()
                    ||source.itemsetCount()!=((Number)row.get("itemset_count")).longValue()
                    ||!PreparedSnapshotMiner.sha256(source.canonicalPattern().getBytes(StandardCharsets.UTF_8)).equals(row.get("canonical_pattern_hash")))throw reject("SOURCE_PATTERN_CHANGED");
            var expected=new ArrayList<Map<String,Object>>();
            for(var set:source.itemsets())for(var item:set.items())expected.add(map("pattern_id",new BigInteger(candidate.patternId()),"itemset_order",set.itemsetOrder(),"item_order",item.itemOrder(),"item_id",item.itemId()));
            if(!equalRows(expected,rows("SELECT * FROM PATTERN_ITEM WHERE pattern_id=? ORDER BY itemset_order,item_order FOR SHARE",new BigDecimal(candidate.patternId()))))throw reject("SOURCE_STRUCTURE_CHANGED");
            for(var set:candidate.itemsets())for(var item:set.items()) {
                var dictionary=rows("SELECT product_id,product_name_snapshot,sku_snapshot FROM RUN_ITEM_STATISTIC WHERE run_id=? AND item_id=? FOR SHARE",plan.context().runId(),item.sourceItemId());
                if(dictionary.size()!=1||!item.productId().equals(dictionary.getFirst().get("product_id"))
                        ||!item.nameSnapshot().equals(dictionary.getFirst().get("product_name_snapshot"))
                        ||!item.skuSnapshot().equals(dictionary.getFirst().get("sku_snapshot")))throw reject("DICTIONARY_BINDING_CHANGED");
                var catalog=rows("SELECT p.product_id,p.category_id,p.status,c.status AS category_status,p.current_price,p.currency_code,p.version FROM PRODUCT p JOIN CATEGORY c ON c.category_id=p.category_id WHERE p.product_id=? FOR SHARE",item.productId());
                var e=item.catalogEvidence();
                if(catalog.size()!=1||e==null)throw reject("CATALOG_BINDING_CHANGED");var c=catalog.getFirst();
                if(!item.productId().equals(c.get("product_id"))||!e.categoryId().equals(c.get("category_id"))||!e.productStatus().equals(c.get("status"))
                        ||!e.categoryStatus().equals(c.get("category_status"))||e.price().compareTo((BigDecimal)c.get("current_price"))!=0
                        ||!e.currency().equals(c.get("currency_code"))||!e.version().equals(c.get("version").toString()))throw reject("CATALOG_BINDING_CHANGED");
            }
        }
    }
    private void insert(Plan p) {
        var b=p.batch();
        jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k,category_filter_enabled,min_product_count,max_product_count,status,created_by,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                b.batchId(),b.runId(),b.rankingPolicy(),b.patternWeightPolicy(),b.topK(),b.categoryFilterEnabled(),b.minProductCount(),b.maxProductCount(),b.status(),b.createdBy(),utc(b.createdAt()));
        for(var c:p.combos())jdbc.update("INSERT INTO COMBO(combo_id,batch_id,name,combo_kind,status,discount_type,stacking_policy,currency_code,reference_original_price,reference_sale_price,support_abs,support_ratio,pattern_weight,explanation,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                c.comboId(),c.batchId(),c.name(),c.comboKind(),c.status(),c.discountType(),c.stackingPolicy(),c.currencyCode(),c.referenceOriginalPrice(),c.referenceSalePrice(),new BigDecimal(c.supportAbs()),c.supportRatio(),c.patternWeight(),c.explanation(),utc(c.createdAt()));
        for(var i:p.items())jdbc.update("INSERT INTO COMBO_ITEM(combo_id,product_id,itemset_order,item_order,quantity,unit_price_snapshot,is_anchor_eligible,is_discount_eligible) VALUES(?,?,?,?,?,?,?,?)",
                i.comboId(),i.productId(),i.itemsetOrder(),i.itemOrder(),i.quantity(),i.unitPriceSnapshot(),i.anchorEligible(),i.discountEligible());
        for(var s:p.sources())jdbc.update("INSERT INTO COMBO_SOURCE(combo_id,pattern_id,is_primary) VALUES(?,?,?)",s.comboId(),new BigDecimal(s.patternId()),s.primary());
    }
    private byte[] representation(String batchId) {
        return encodeRepresentation(rows("SELECT * FROM COMBO_BATCH WHERE batch_id=?",batchId),
                rows("SELECT * FROM COMBO WHERE batch_id=?",batchId),
                rows("SELECT i.* FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",batchId),
                rows("SELECT s.* FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=?",batchId));
    }
    /** Review normalizes only its own mutable status/audit; all other columns stay protected. */
    public byte[] reviewRepresentation(String batchId) {
        var combos=rows("SELECT * FROM COMBO WHERE batch_id=?",batchId);
        for(var c:combos){if(!Set.of("DRAFT","APPROVED","REJECTED").contains(c.get("status")))throw reject("COMBO_LIFECYCLE_UNSUPPORTED");
            c.put("status","DRAFT");c.put("reviewed_at",null);c.put("reviewed_by",null);c.put("review_note",null);}
        return encodeRepresentation(rows("SELECT * FROM COMBO_BATCH WHERE batch_id=?",batchId),combos,
                rows("SELECT i.* FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",batchId),
                rows("SELECT s.* FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=?",batchId));
    }
    private byte[] representation(Plan p) {
        var b=p.batch();var batch=map("batch_id",b.batchId(),"run_id",b.runId(),"ranking_policy",b.rankingPolicy(),"pattern_weight_policy",b.patternWeightPolicy(),"top_k",b.topK(),
                "category_filter_enabled",b.categoryFilterEnabled(),"min_product_count",b.minProductCount(),"max_product_count",b.maxProductCount(),"status",b.status(),"created_by",b.createdBy(),"activated_by",null,"created_at",b.createdAt(),"activated_at",null);
        var combos=new ArrayList<Map<String,Object>>();var items=new ArrayList<Map<String,Object>>();var sources=new ArrayList<Map<String,Object>>();
        for(var c:p.combos())combos.add(map("combo_id",c.comboId(),"batch_id",c.batchId(),"name",c.name(),"combo_kind",c.comboKind(),"status",c.status(),"discount_type",c.discountType(),"discount_value",null,"max_discount_amount",null,
                "stacking_policy",c.stackingPolicy(),"currency_code",c.currencyCode(),"reference_original_price",c.referenceOriginalPrice(),"reference_sale_price",c.referenceSalePrice(),"support_abs",new BigInteger(c.supportAbs()),"support_ratio",c.supportRatio(),
                "pattern_weight",c.patternWeight(),"explanation",c.explanation(),"valid_from",null,"valid_to",null,"created_at",c.createdAt(),"reviewed_at",null,"reviewed_by",null,"review_note",null,"published_at",null));
        for(var i:p.items())items.add(map("combo_id",i.comboId(),"product_id",i.productId(),"itemset_order",i.itemsetOrder(),"item_order",i.itemOrder(),"quantity",i.quantity(),"unit_price_snapshot",i.unitPriceSnapshot(),"is_anchor_eligible",i.anchorEligible(),"is_discount_eligible",i.discountEligible()));
        for(var s:p.sources())sources.add(map("combo_id",s.comboId(),"pattern_id",new BigInteger(s.patternId()),"is_primary",s.primary()));
        return encodeRepresentation(List.of(batch),combos,items,sources);
    }
    public byte[] plannedRepresentation(Plan plan){return representation(plan);}
    private byte[] encodeRepresentation(List<Map<String,Object>> batch,List<Map<String,Object>> combos,List<Map<String,Object>> items,List<Map<String,Object>> sources) {
        return BatchCanonical.encode(map("version","COMBO_DB_REPRESENTATION_V1","batch",sorted(batch),"combos",sorted(combos),"items",sorted(items),"sources",sorted(sources)),limits.maxRepresentationBytes());
    }
    private List<Map<String,Object>> sorted(List<Map<String,Object>> rows) {
        return rows.stream().sorted((a,b)->Arrays.compareUnsigned(BatchCanonical.encode(a,limits.maxRepresentationBytes()),BatchCanonical.encode(b,limits.maxRepresentationBytes()))).toList();
    }
    private boolean equalRows(List<Map<String,Object>> a,List<Map<String,Object>> b) {return Arrays.equals(BatchCanonical.encode(sorted(a),limits.maxRepresentationBytes()),BatchCanonical.encode(sorted(b),limits.maxRepresentationBytes()));}
    private List<Map<String,Object>> rows(String sql,Object... args) {
        // Bound driver-side buffering too. All SQL is constant-owned; the limit is validated server config.
        String lock=sql.endsWith(" FOR SHARE")?" FOR SHARE":sql.endsWith(" FOR UPDATE")?" FOR UPDATE":"";
        String boundedSql=sql.substring(0,sql.length()-lock.length())+" LIMIT "+((long)limits.maxRunRows()+1)+lock;
        return jdbc.query(boundedSql,rs->{
            var result=new ArrayList<Map<String,Object>>();var meta=rs.getMetaData();
            while(rs.next()) {
                if(result.size()>=limits.maxRunRows())throw reject("DATABASE_ROW_LIMIT_EXCEEDED");
                var row=new LinkedHashMap<String,Object>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),rs.getObject(i));result.add(row);
            }
            return result;
        },args);
    }
    static Plan withTime(Plan p,Instant at) {
        var b=p.batch();var batch=new BatchRow(b.batchId(),b.runId(),b.rankingPolicy(),b.patternWeightPolicy(),b.topK(),b.categoryFilterEnabled(),b.minProductCount(),b.maxProductCount(),b.status(),b.createdBy(),at);
        return new Plan(p.writeIdentity(),p.context(),p.configuration(),batch,p.combos().stream().map(c->new ComboRow(c.comboId(),c.batchId(),c.name(),c.comboKind(),c.status(),c.discountType(),c.stackingPolicy(),c.currencyCode(),c.referenceOriginalPrice(),c.referenceSalePrice(),c.supportAbs(),c.supportRatio(),c.patternWeight(),c.explanation(),at)).toList(),p.items(),p.sources(),p.comboIds(),p.suppliedFields());
    }
    static WriteResult success(String status,Plan p,byte[] content,byte[] evidence,Instant at) {return new WriteResult(status,List.of(),p,new Receipt(p.batch().batchId(),p.comboIds(),at,hash(content),hash(evidence)));}
    static WriteResult failure(String status,String code,Plan p){return new WriteResult(status,List.of(code),p);}
    static String hash(byte[] bytes){return PreparedSnapshotMiner.sha256(bytes);}
    static byte[] digest(byte[] bytes){return HexFormat.of().parseHex(hash(bytes));}
    static byte[] key(Plan p){return p.writeIdentity().getBytes(StandardCharsets.UTF_8);}
    static LocalDateTime utc(Instant t){return LocalDateTime.ofInstant(t,ZoneOffset.UTC);}
    static Instant instant(Object t){return t instanceof java.sql.Timestamp ts?ts.toLocalDateTime().toInstant(ZoneOffset.UTC):((LocalDateTime)t).toInstant(ZoneOffset.UTC);}
    private static boolean same(Double expected,Object actual){return expected!=null&&actual instanceof Number n&&Double.isFinite(n.doubleValue())&&expected.doubleValue()==n.doubleValue();}
    static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static Rejected reject(String code){return new Rejected(code);}
}
