package vn.edu.wspm.backend.services.combo.review;

import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;
import vn.edu.wspm.backend.services.combo.persistence.BatchCanonical;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;

/** Transaction-local integrity/ownership adapter around the EXISTING review policy port.
 * Does not deserialize opaque authorization bytes or turn generation approval into review policy.
 * The delegate must validate issued generation/mining evidence and current review policy/freshness.
 * No default allow, remote/file IO, standalone transaction or production policy is supplied. */
public final class WriterEvidenceReviewAuthority implements ComboReviewAuthority {
    public record Inspection(String issue,String writerRoot,String protectedFacts) {
        public boolean valid(){return issue==null;}
        @Override public String toString(){return "ReviewEvidenceInspection[issue="+issue+"]";}
    }
    private final JdbcTemplate jdbc;
    private final int maxRows,maxBytes;
    private final ComboReviewAuthority policy;
    public WriterEvidenceReviewAuthority(DataSource ds,int maxRows,int maxBytes,ComboReviewAuthority trustedReviewPolicy) {
        jdbc=new JdbcTemplate(Objects.requireNonNull(ds));
        if(maxRows<1 || maxBytes<1 || maxBytes>4*1024*1024)throw new IllegalArgumentException("INVALID_REVIEW_LIMITS");
        this.maxRows=maxRows;this.maxBytes=maxBytes;policy=Objects.requireNonNull(trustedReviewPolicy);
    }
    @Override public Check verify(Bundle current) {
        var inspected=inspect(current);
        if(!inspected.valid())return new Check(ComboReview.Policy.unconfirmed(),ComboReview.Evidence.unverified(),inspected.issue());
        final Check decision;
        try {decision=policy.verify(current);}catch(RuntimeException unavailable){return Check.unavailable();}
        if(decision==null || decision.binding()==null)return Check.unavailable();
        return new Check(decision.policy(),decision.evidence(),hash(BatchCanonical.encode(
                List.of("WRITER_BACKED_REVIEW_V1",inspected.writerRoot(),inspected.protectedFacts(),decision.binding()),maxBytes)));
    }
    public Inspection inspect(Bundle current) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())return bad("REVIEW_TRANSACTION_REQUIRED");
        if(current==null || current.row()==null)return bad("REVIEW_COMBO_MISSING");
        try {
            String batch=current.row().batchId(),run=current.row().runId(),combo=current.row().comboId();
            var metadata=rows("SELECT batch_id,run_id,content_version,OCTET_LENGTH(content_bytes) cb,OCTET_LENGTH(evidence_bytes) eb,OCTET_LENGTH(expected_representation) rb FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",batch);
            if(metadata.size()!=1)return bad("WRITER_EVIDENCE_UNAVAILABLE");var m=metadata.getFirst();
            if(!batch.equals(m.get("batch_id")) || !run.equals(m.get("run_id")) || ((Number)m.get("content_version")).intValue()!=1)return bad("WRITER_EVIDENCE_OWNERSHIP_INVALID");
            long aggregate=0;for(String k:List.of("cb","eb","rb")){long n=((Number)m.get(k)).longValue();if(n<1 || n>maxBytes)return bad("WRITER_EVIDENCE_LIMIT_EXCEEDED");aggregate+=n;}
            if(aggregate>maxBytes)return bad("WRITER_EVIDENCE_LIMIT_EXCEEDED");
            var evidence=rows("SELECT * FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",batch).getFirst();
            for(var pair:List.of(List.of("content_bytes","content_digest"),List.of("evidence_bytes","evidence_digest"),List.of("expected_representation","representation_digest")))
                if(!validHash(evidence,pair.get(0),pair.get(1)))return bad("WRITER_EVIDENCE_CORRUPT");
            var runs=rows("SELECT * FROM MINING_RUN WHERE run_id=?",run);
            var inputs=rows("SELECT * FROM MINING_RUN_INPUT_BINDING WHERE run_id=?",run);
            var completions=rows("SELECT * FROM MINING_RUN_COMPLETION WHERE run_id=?",run);
            if(runs.size()!=1 || inputs.size()!=1 || completions.size()!=1)return bad("MINING_EVIDENCE_UNAVAILABLE");
            var input=inputs.getFirst();var completion=completions.getFirst();
            if(!run.equals(runs.getFirst().get("run_id")) || !run.equals(input.get("run_id")) || !run.equals(completion.get("run_id"))
                    || !runs.getFirst().get("snapshot_id").equals(input.get("snapshot_id"))
                    || !Arrays.equals((byte[])evidence.get("mining_receipt_digest"),(byte[])completion.get("receipt_digest"))
                    || !Arrays.equals((byte[])input.get("input_digest"),(byte[])completion.get("input_digest")))return bad("MINING_EVIDENCE_OWNERSHIP_INVALID");
            if(!validHash(input,"manifest_bytes","input_digest") || !validHash(completion,"manifest_bytes","receipt_digest"))return bad("MINING_EVIDENCE_CORRUPT");
            var batches=rows("SELECT * FROM COMBO_BATCH WHERE batch_id=?",batch);
            var combos=rows("SELECT * FROM COMBO WHERE batch_id=?",batch);
            if(batches.size()!=1 || !run.equals(batches.getFirst().get("run_id")) || combos.stream().noneMatch(c->combo.equals(c.get("combo_id"))))return bad("BATCH_OWNERSHIP_INVALID");
            // Only review-owned mutable columns are normalized. Publish/price/source/item mutations remain protected.
            for(var c:combos) {
                if(!Set.of("DRAFT","APPROVED","REJECTED").contains(Objects.toString(c.get("status"),"")))return bad("COMBO_LIFECYCLE_UNSUPPORTED");
                c.put("status","DRAFT");c.put("reviewed_at",null);c.put("reviewed_by",null);c.put("review_note",null);
            }
            var items=rows("SELECT i.* FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",batch);
            var sources=rows("SELECT s.* FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=?",batch);
            var projection=new LinkedHashMap<String,Object>();projection.put("version","COMBO_DB_REPRESENTATION_V1");
            projection.put("batch",sorted(batches));projection.put("combos",sorted(combos));projection.put("items",sorted(items));projection.put("sources",sorted(sources));
            if(!Arrays.equals((byte[])evidence.get("expected_representation"),BatchCanonical.encode(projection,maxBytes)))return bad("WRITER_PROTECTED_PAYLOAD_CHANGED");
            var facts=new LinkedHashMap<String,Object>();facts.put("version","REVIEW_CURRENT_FACTS_V1");facts.put("run",runs);facts.put("input",inputs);facts.put("completion",completions);
            facts.put("snapshot",rows("SELECT d.* FROM DATASET_SNAPSHOT d JOIN MINING_RUN r ON r.snapshot_id=d.snapshot_id WHERE r.run_id=?",run));
            facts.put("seal",rows("SELECT conversion_id,snapshot_id,conversion_policy,seal_version,seal_digest,raw_sha256,sequence_sha256,mapping_sha256 FROM SNAPSHOT_CONVERSION_SEAL WHERE conversion_id=?",input.get("conversion_id")));
            var patterns=rows("SELECT p.* FROM PATTERN p JOIN COMBO_SOURCE s ON s.pattern_id=p.pattern_id JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=? ORDER BY p.pattern_id",batch);
            if(patterns.stream().anyMatch(p->!run.equals(p.get("run_id"))))return bad("SOURCE_RUN_MISMATCH");
            facts.put("patterns",patterns);
            facts.put("patternItems",rows("SELECT pi.* FROM PATTERN_ITEM pi JOIN COMBO_SOURCE s ON s.pattern_id=pi.pattern_id JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=? ORDER BY pi.pattern_id,pi.itemset_order,pi.item_order",batch));
            facts.put("dictionary",rows("SELECT d.* FROM RUN_ITEM_STATISTIC d WHERE d.run_id=? ORDER BY d.item_id",run));
            facts.put("catalog",rows("SELECT p.*,cat.status AS review_category_status FROM PRODUCT p JOIN CATEGORY cat ON cat.category_id=p.category_id WHERE p.product_id IN (SELECT i.product_id FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?) ORDER BY p.product_id",batch));
            return new Inspection(null,hash(BatchCanonical.encode(evidence,maxBytes)),hash(BatchCanonical.encode(facts,maxBytes)));
        }catch(RuntimeException unavailable){return bad("REVIEW_EVIDENCE_UNAVAILABLE_OR_INVALID");}
    }
    private List<Map<String,Object>> rows(String sql,Object...args) {
        var result=jdbc.query(sql,ps->{ps.setMaxRows(maxRows+1);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);},(rs,n)->{
            Map<String,Object> row=new LinkedHashMap<>();var md=rs.getMetaData();for(int i=1;i<=md.getColumnCount();i++)row.put(md.getColumnLabel(i),rs.getObject(i));return row;
        });
        if(result.size()>maxRows)throw new IllegalArgumentException("REVIEW_ROW_LIMIT_EXCEEDED");return result;
    }
    private List<Map<String,Object>> sorted(List<Map<String,Object>> values){return values.stream().sorted((a,b)->Arrays.compareUnsigned(BatchCanonical.encode(a,maxBytes),BatchCanonical.encode(b,maxBytes))).toList();}
    private static boolean validHash(Map<String,Object> row,String bytes,String digest) {
        return row.get(bytes) instanceof byte[] b && row.get(digest) instanceof byte[] d && Arrays.equals(HexFormat.of().parseHex(hash(b)),d);
    }
    private static String hash(byte[] bytes){return ConversionArtifactCodec.sha256(bytes);}
    private static Inspection bad(String code){return new Inspection(code,null,null);}
}
