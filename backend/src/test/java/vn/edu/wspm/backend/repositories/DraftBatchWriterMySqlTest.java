package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.lang.reflect.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import vn.edu.wspm.backend.db.review.NullablePatternMetricPreflight;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.evaluation.*;
import vn.edu.wspm.backend.services.combo.grouping.*;
import vn.edu.wspm.backend.services.combo.ranking.*;
import vn.edu.wspm.backend.services.combo.persistence.*;
import vn.edu.wspm.backend.services.combo.admin.ComboAdminService;
import vn.edu.wspm.backend.services.mining.conversion.*;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
import static vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;

/** Relational/full-witness fixture only. It is not output from a real mining publisher. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DraftBatchWriterMySqlTest {
    OwnedMySql mysql;DataSource ds;JdbcTemplate jdbc;
    String run,snapshot,batch,key;List<String> products;List<String> patternIds;
    Request request;JdbcDraftBatchStore store;DraftBatchAuthority authority;
    final Instant audit=Instant.parse("2026-11-06T00:00:00.123456Z");
    final JdbcDraftBatchStore.Limits limits=new JdbcDraftBatchStore.Limits(10,40,40,500,1048576,1048576,2097152);
    final String weightPolicy="FIXTURE_WEIGHT_V1",evalRef="fixture-eval-approved",weightRef="fixture-weight-approved",
            rankRef="fixture-rank-approved",anchorRef="fixture-anchor-approved",catalogRef="fixture-catalog";
    @BeforeAll void schema() {
        mysql=new OwnedMySql();ds=mysql.dataSource();jdbc=new JdbcTemplate(ds);
        try {
            Path base=Files.isDirectory(Path.of("backend/migration-review"))?Path.of("backend/migration-review"):Path.of("migration-review");
            var configuration=Flyway.configure().dataSource(ds).locations("classpath:db/migration",
                    "filesystem:"+base.resolve("TV2-INT-08F").toAbsolutePath(),"filesystem:"+base.resolve("INT-09_10").toAbsolutePath())
                    .callbacks(new NullablePatternMetricPreflight()).cleanDisabled(true);
            assertEquals(3,configuration.target("3").load().migrate().migrationsExecuted);
            jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('migration-reference','CSV','fixture','VALID','{}',1)");
            var old=new PersistenceDraftMySqlTest();old.jdbc=jdbc;old.mysql=mysql;old.run="migration-reference";old.createRun(old.run,"migration-reference");
            String oldSeal=old.seal("migration-reference","old-seal",old.artifacts());old.input(old.run,"migration-reference",oldSeal,new byte[]{1});
            byte[] token=new byte[16];token[0]=1;old.ownership(1,token);
            old.receipt(1,token,0,0,jdbc.queryForObject("SELECT input_digest FROM MINING_RUN_INPUT_BINDING WHERE run_id=?",byte[].class,old.run));
            jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k) VALUES('old-batch',?,'UNCONFIRMED','UNCONFIRMED',1)",old.run);
            var oldBatch=jdbc.queryForMap("SELECT * FROM COMBO_BATCH WHERE batch_id='old-batch'");
            var oldMiningReceipt=jdbc.queryForMap("SELECT HEX(receipt_digest) digest,pattern_count FROM MINING_RUN_COMPLETION WHERE run_id=?",old.run);
            var flyway=configuration.target("4").load();assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(oldBatch,jdbc.queryForMap("SELECT * FROM COMBO_BATCH WHERE batch_id='old-batch'"));
            assertEquals(oldMiningReceipt,jdbc.queryForMap("SELECT HEX(receipt_digest) digest,pattern_count FROM MINING_RUN_COMPLETION WHERE run_id=?",old.run));
            jdbc.update("INSERT INTO CATEGORY(category_id,name,slug,status) VALUES('LCAT','Laptop','laptop','ACTIVE'),('ACAT','Accessory','accessory','ACTIVE')");
        }catch(RuntimeException | AssertionError failure){mysql.close();throw failure;}
    }
    @AfterAll void close(){if(mysql!=null)mysql.close();}
    @BeforeEach void fixture() {
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,20);
        run="R"+suffix;snapshot=UUID.randomUUID().toString();batch="B"+suffix;key="intent:"+run;
        products=List.of("P"+suffix+"A","P"+suffix+"B","P"+suffix+"C");
        for(int i=0;i<3;i++)jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES(?,?,?, ?,?,1,'ACTIVE')",
                products.get(i),products.get(i),"Current catalog",products.get(i),i==0?"LCAT":"ACAT");
        String header=OrderSnapshotConverter.HEADER+"\n";
        var text=new StringBuilder(header);
        for(int i=0;i<3;i++)text.append(i<2?"O1":"O2").append(',').append(i+1).append(",fixture-customer,2026-01-01T00:00:00.000000Z,COMPLETED,")
                .append(products.get(i)).append(",H-").append(i+1).append(",Historic ").append(i+1).append(",1,1.00,0.00,1.00,VND\n");
        byte[] raw=text.toString().getBytes(StandardCharsets.UTF_8);
        var converted=new OrderSnapshotConverter().convert(raw,new OrderSnapshotConverter.Limits(10000,20,300,20000));
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES(?,'CSV','controlled fixture','VALID','{}',1)",snapshot);
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES(?,?,'WSPM_PRETREE','SUCCEEDED','controlled fixture','ABSOLUTE',.1,.1,?,3)",run,snapshot,1.0/3);
        var dictionary=new ArrayList<DictionaryItem>();var catalog=new HashMap<String,CatalogProduct>();
        for(var item:converted.items()) {
            jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES(?,?,?,1,1,?,?,?)",
                    run,item.itemId(),item.productId(),1.0/3,item.nameSnapshot(),item.skuSnapshot());
            dictionary.add(new DictionaryItem(run,item.itemId(),item.productId(),item.nameSnapshot(),item.skuSnapshot()));
            catalog.put(item.productId(),new CatalogProduct(item.productId(),item.itemId()==1?"LCAT":"ACAT","ACTIVE","ACTIVE",BigDecimal.ONE,"VND","0",Inventory.UNKNOWN,null));
        }
        int[][][] definitions={{{1,2}},{{1},{3}},{{1,2},{3}}};
        var patterns=new ArrayList<PatternDetailResponse>();patternIds=new ArrayList<>();
        for(var definition:definitions) {
            String canonical="<"+Arrays.stream(definition).map(a->"("+Arrays.stream(a).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(" "))+")").collect(java.util.stream.Collectors.joining())+">";
            int count=Arrays.stream(definition).mapToInt(a->a.length).sum();
            jdbc.update("INSERT INTO PATTERN(run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(?,?,SHA2(?,256),1,1,?,?,?,.5,?)",
                    run,canonical,canonical,1.0/3,count,definition.length,weightPolicy);
            String id=jdbc.queryForObject("SELECT CAST(pattern_id AS CHAR) FROM PATTERN WHERE run_id=? AND canonical_pattern=?",String.class,run,canonical);patternIds.add(id);
            var sets=new ArrayList<PatternDetailResponse.Itemset>();
            for(int s=0;s<definition.length;s++) {
                var items=new ArrayList<PatternDetailResponse.Item>();
                for(int j=0;j<definition[s].length;j++) {
                    int itemId=definition[s][j];var item=converted.items().get(itemId-1);
                    jdbc.update("INSERT INTO PATTERN_ITEM VALUES(?,?,?,?)",new BigDecimal(id),s+1,j+1,itemId);
                    items.add(new PatternDetailResponse.Item(j+1,itemId,PatternDetailResponse.MappingStatus.MAPPED,item.productId(),item.nameSnapshot(),item.skuSnapshot(),1.0/3));
                }
                sets.add(new PatternDetailResponse.Itemset(s+1,items));
            }
            patterns.add(new PatternDetailResponse(run,id,canonical,"1",1.0,1.0/3,.5,weightPolicy,count,definition.length,"1",PatternDetailResponse.IntegrityStatus.COMPLETE,List.of(),sets));
        }
        // Controlled pre-existing mining witness. Combo writer never creates these rows.
        var f=new PersistenceDraftMySqlTest();f.jdbc=jdbc;f.mysql=mysql;f.run=run;
        String seal="S"+suffix;
        f.seal(snapshot,seal,new PersistenceDraftMySqlTest.Artifacts(raw,converted,"{\"controlledStats\":true}".getBytes(StandardCharsets.UTF_8),"{\"controlledSeal\":true}".getBytes(StandardCharsets.UTF_8)));
        jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET dictionary_count=3 WHERE conversion_id=?",seal);
        f.input(run,snapshot,seal,("mining:"+run).getBytes(StandardCharsets.UTF_8));
        byte[] token=new byte[16];token[0]=1;f.ownership(1,token);
        byte[] input=jdbc.queryForObject("SELECT input_digest FROM MINING_RUN_INPUT_BINDING WHERE run_id=?",byte[].class,run);
        f.receipt(1,token,3,7,input);jdbc.update("UPDATE MINING_RUN_COMPLETION SET dictionary_count=3 WHERE run_id=?",run);
        store=new JdbcDraftBatchStore(ds,limits);var rootWitness=store.miningBinding(run);
        String published=DraftBatchAuthority.miningReference(run,rootWitness.receiptDigest());
        var evidence=new Evidence(run,"SUCCEEDED",snapshot,"VALID",Purpose.DEMO_CATALOG,"fixture-purpose",BigInteger.valueOf(3),true,published,true,catalogRef,Instant.parse("2026-01-01T00:00:00Z"));
        var policy=new Policy("fixture-eval-v1",evalRef,Set.of("LCAT"),Set.of("ACAT"),EligibilityMode.CATALOG_ONLY);
        var evaluated=new PatternCandidateEvaluator().evaluate(new Input(evidence,policy,patterns,dictionary,catalog));
        assertTrue(evaluated.completed());assertEquals(3,evaluated.accepted());
        var context=CandidateGrouping.Context.from(evaluated.patterns().getFirst().candidate());
        var weight=new CandidateGrouping.WeightPolicyConfirmation(weightPolicy,weightRef);
        var grouping=new CandidateGrouper().group(evaluated,context,weight);assertTrue(grouping.completed());
        var configuration=new ComboPreview.Configuration("SUPPORT_THEN_PATTERN_WEIGHT_V1",rankRef,weight,"FIRST_ITEMSET_V1",anchorRef,BigInteger.ONE,null,null);
        var preview=new ComboPreviewRanker().preview(grouping,context,products.getFirst(),configuration);assertTrue(preview.completed());
        var fields=new HashMap<CandidateGrouping.StructureKey,Fields>();
        for(var g:grouping.groups()) {
            var items=new HashMap<Position,ItemValues>();for(var set:g.itemsets())for(var item:set.items())items.put(new Position(set.itemsetOrder(),item.itemOrder()),new ItemValues(item.productId(),1,BigDecimal.ONE,"fixture-price",false));
            var price=BigDecimal.valueOf(items.size());
            fields.put(g.structureKey(),new Fields("C"+suffix+"-"+g.primary().patternId(),"Combo "+g.primary().patternId(),"Explicit explanation","VND",price,price,"fixture-price","NONE",items));
        }
        request=new Request(grouping,context,preview,key,batch,null,Instant.parse("2020-01-01T00:00:00Z"),fields);
        authority=(r,p,digest)->{
            if(!p.context().runId().equals(run)||!evalRef.equals(p.context().policyConfirmationRef())||!weightPolicy.equals(p.configuration().weightPolicy().policy()))return DraftBatchAuthority.Decision.blocked("FIXTURE_POLICY_UNCONFIRMED");
            if(p.batch().createdBy()!=null)return DraftBatchAuthority.Decision.blocked("ACTOR_UNCONFIRMED");
            for(var field:p.suppliedFields().values()) {
                if(!field.priceEvidenceRef().equals("fixture-price")||!field.currency().equals("VND")||field.referenceOriginalPrice().compareTo(BigDecimal.valueOf(field.items().size()))!=0
                        ||field.items().values().stream().anyMatch(v->v.quantity()!=1||v.unitPriceSnapshot().compareTo(BigDecimal.ONE)!=0||!v.priceEvidenceRef().equals("fixture-price")))return DraftBatchAuthority.Decision.blocked("COMMERCIAL_POLICY_UNCONFIRMED");
            }
            return new DraftBatchAuthority.Decision(List.of(),new DraftBatchAuthority.Authorization(digest,rootWitness.factsDigest(),rootWitness.receiptDigest(),
                    new DraftBatchAuthority.Approvals("fixture-generator-v1","PRODUCT_ITEMSETS_LENGTH_V1",evalRef,weightRef,rankRef,anchorRef,"fixture-commercial-explicit-v1",catalogRef),
                    "Controlled immutable fixture mining/policy/commercial root; not production approval".getBytes(StandardCharsets.UTF_8)));
        };
    }
    DraftBatchWriter writer(){return writer(ds);}
    DraftBatchWriter writer(DataSource data){return new DraftBatchWriter(new JdbcDraftBatchStore(data,limits),authority,Clock.fixed(audit,ZoneOffset.UTC));}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE batch_id=?",Long.class,batch);}
    void cleanBatch(){
        assertEquals(0,count("COMBO_BATCH"));assertEquals(0,count("COMBO"));assertEquals(0,count("COMBO_BATCH_WRITE_EVIDENCE"));
        for(var f:request.fields().values())for(String table:List.of("COMBO_ITEM","COMBO_SOURCE"))
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE combo_id=?",Integer.class,f.comboId()));
    }
    @Test void insertsAllThreeKindsOutsideTopKWithExactPrimaryItemsMetricsAndReaderCompatibility() {
        assertEquals(1,request.preview().items().size());var out=writer().write(request);assertEquals("INSERTED",out.status(),out.errors().toString());
        assertNotNull(out.receipt());assertEquals(audit,out.receipt().createdAt());assertEquals(3,count("COMBO"));
        assertEquals(Set.of("SAME_ORDER_BUNDLE","SEQUENTIAL_CROSS_SELL","MIXED_SEQUENCE"),new HashSet<>(jdbc.queryForList("SELECT combo_kind FROM COMBO WHERE batch_id=?",String.class,batch)));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",Integer.class,batch));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=? AND s.is_primary=1",Integer.class,batch));
        var reader=new ComboAdminService(new JdbcComboAdminReader(ds));var page=reader.list(batch,null,null,0,20);
        assertEquals("3",page.filteredTotal());assertTrue(page.items().stream().allMatch(i->i.assessment().allowedActions().isEmpty()));
        assertTrue(out.proposedPlan().items().stream().anyMatch(i->i.itemsetOrder()==2&&!i.anchorEligible()));
        assertTrue(jdbc.queryForList("SELECT support_abs,pattern_weight,status FROM COMBO WHERE batch_id=?",batch).stream().allMatch(r->r.get("support_abs").toString().equals("1")&&((Number)r.get("pattern_weight")).doubleValue()==.5&&r.get("status").equals("DRAFT")));
    }
    @Test void retryWithDifferentClockAndMapOrderIsNoOpAndChangedContentConflicts() {
        var first=writer().write(request);assertEquals("INSERTED",first.status(),first.errors().toString());
        var fields=new LinkedHashMap<CandidateGrouping.StructureKey,Fields>();var keys=new ArrayList<>(request.fields().keySet());Collections.reverse(keys);keys.forEach(k->fields.put(k,request.fields().get(k)));
        var retry=new Request(request.grouping(),request.context(),request.preview(),key,batch,null,audit.plusSeconds(999),fields);
        var later=new DraftBatchWriter(store,authority,Clock.fixed(audit.plusSeconds(60),ZoneOffset.UTC)).write(retry);
        assertEquals("NO_OP",later.status(),later.errors().toString());assertEquals(first.receipt(),later.receipt());assertEquals(1,count("COMBO_BATCH"));
        var k=keys.getFirst();var f=fields.get(k);fields.put(k,new Fields(f.comboId(),f.name()+" changed",f.explanation(),f.currency(),f.referenceOriginalPrice(),f.referenceSalePrice(),f.priceEvidenceRef(),f.discountType(),f.items()));
        var changed=writer().write(new Request(request.grouping(),request.context(),request.preview(),key,batch,null,audit,fields));
        assertEquals("CONFLICT",changed.status());assertEquals(3,count("COMBO"));
    }
    @Test void missingEvidenceUnapprovedPolicyAndCommercialMetadataNeverWrite() {
        var noEvidence=new DraftBatchWriter(store,(r,p,d)->DraftBatchAuthority.Decision.blocked("FULL_RUN_EVIDENCE_UNAVAILABLE"),Clock.systemUTC());
        assertEquals("BLOCKED",noEvidence.write(request).status());cleanBatch();
        var noPolicy=new DraftBatchWriter(store,(r,p,d)->DraftBatchAuthority.Decision.blocked("WEIGHT_POLICY_UNCONFIRMED"),Clock.systemUTC());
        assertTrue(noPolicy.write(request).errors().contains("WEIGHT_POLICY_UNCONFIRMED"));cleanBatch();
        var missing=new Request(request.grouping(),request.context(),request.preview(),key,batch,null,audit,Map.of());
        assertEquals("INVALID",writer().write(missing).status());cleanBatch();
    }
    @Test void alteredAuthorityEvidenceConflictsAndWrongBindingOrCommercialTermsAreBlocked() {
        assertEquals("INSERTED",writer().write(request).status());
        DraftBatchAuthority changed=(r,p,d)->{
            var old=authority.authorize(r,p,d).authorization();
            return new DraftBatchAuthority.Decision(List.of(),new DraftBatchAuthority.Authorization(d,old.runFactsDigest(),old.miningReceiptDigest(),old.approvals(),new byte[]{1,2,3}));
        };
        assertEquals("CONFLICT",new DraftBatchWriter(store,changed,Clock.systemUTC()).write(request).status());
        DraftBatchAuthority wrong=(r,p,d)->{
            var old=authority.authorize(r,p,d).authorization();return new DraftBatchAuthority.Decision(List.of(),new DraftBatchAuthority.Authorization("0".repeat(64),old.runFactsDigest(),old.miningReceiptDigest(),old.approvals(),old.resolvedEvidence()));
        };
        assertTrue(new DraftBatchWriter(store,wrong,Clock.systemUTC()).write(request).errors().contains("AUTHORITY_BINDING_INVALID"));
        var r=variant(request,"-terms");var fields=new HashMap<>(r.fields());var k=fields.keySet().iterator().next();var f=fields.get(k);var items=new HashMap<>(f.items());var pos=items.keySet().iterator().next();var i=items.get(pos);
        items.put(pos,new ItemValues(i.productId(),2,i.unitPriceSnapshot(),i.priceEvidenceRef(),false));
        fields.put(k,new Fields(f.comboId(),f.name(),f.explanation(),f.currency(),f.referenceOriginalPrice(),f.referenceSalePrice(),f.priceEvidenceRef(),f.discountType(),items));
        var blocked=writer().write(new Request(r.grouping(),r.context(),r.preview(),r.writeIdentity(),r.batchId(),null,audit,fields));
        assertTrue(blocked.errors().contains("COMMERCIAL_POLICY_UNCONFIRMED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH WHERE batch_id=?",Integer.class,r.batchId()));
    }
    @Test void concurrentWritersCreateOnlyOneBatchAndReturnSameIds() throws Exception {
        var go=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{go.await();return writer().write(request);});var b=pool.submit(()->{go.await();return writer().write(request);});go.countDown();
            var x=a.get(25,TimeUnit.SECONDS);var y=b.get(25,TimeUnit.SECONDS);
            assertEquals(Set.of("INSERTED","NO_OP"),Set.of(x.status(),y.status()),x.errors()+" / "+y.errors());
            assertEquals(x.receipt(),y.receipt());assertEquals(1,count("COMBO_BATCH"));assertEquals(3,count("COMBO"));
        }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));}
    }
    @Test void insertFailuresRollbackEveryStageIncludingEvidence() {
        for(String prefix:List.of("INSERT INTO COMBO_BATCH(","INSERT INTO COMBO(","INSERT INTO COMBO_ITEM(","INSERT INTO COMBO_SOURCE(","INSERT INTO COMBO_BATCH_WRITE_EVIDENCE(")) {
            var fault=new FaultSource(ds,prefix,null,false,false,false);
            var out=writer(fault).write(request);assertEquals("ROLLED_BACK",out.status(),prefix+out.errors());cleanBatch();
        }
    }
    @Test void readbackTamperingBeforeCompletionRollsBackRatherThanIssuingReceipt() {
        var fault=new FaultSource(ds,null,batch,false,false,false);
        var out=writer(fault).write(request);assertEquals("CONFLICT",out.status(),out.errors().toString());assertTrue(out.errors().contains("WRITE_READBACK_MISMATCH"));cleanBatch();
    }
    @Test void retryDetectsMissingChangedAndExtraChildrenWithoutRepair() {
        int n=0;
        for(String mutation:List.of("missing","changed","extra")) {
            var r=variant(request,"-"+(++n));
            var out=writer().write(r);assertEquals("INSERTED",out.status(),out.errors().toString());String combo=out.proposedPlan().combos().getFirst().comboId();
            if(mutation.equals("missing"))jdbc.update("DELETE FROM COMBO_SOURCE WHERE combo_id=?",combo);
            else if(mutation.equals("changed"))jdbc.update("UPDATE COMBO_ITEM SET quantity=2 WHERE combo_id=?",combo);
            else {
                var ids=jdbc.queryForList("SELECT pattern_id FROM COMBO_SOURCE WHERE combo_id=?",Long.class,combo);
                String other=patternIds.stream().filter(id->!ids.contains(Long.parseLong(id))).findFirst().orElseThrow();
                jdbc.update("INSERT INTO COMBO_SOURCE VALUES(?,?,1)",combo,new BigDecimal(other));
            }
            var retry=writer().write(r);assertEquals("CONFLICT",retry.status());assertTrue(retry.errors().contains("PERSISTED_BATCH_INCONSISTENT"));
        }
    }
    @Test void commitReplyLostAfterActualCommitReturnsUnknownThenFreshReconcileNoOp() {
        var fault=new FaultSource(ds,null,null,true,false,false);
        var unknown=writer(fault).write(request);assertEquals("UNKNOWN",unknown.status(),unknown.errors().toString());assertNull(unknown.receipt());
        assertEquals(1,count("COMBO_BATCH"));var recovered=writer().reconcile(request);assertEquals("NO_OP",recovered.status(),recovered.errors().toString());
        assertEquals("NO_OP",writer().write(request).status());
    }
    @Test void simulatedRollbackAtCommitErrorStaysUnknownWithoutAutomaticInsertion() {
        var fault=new FaultSource(ds,null,null,false,true,false);
        assertEquals("UNKNOWN",writer(fault).write(request).status());cleanBatch();
        assertEquals("UNKNOWN",writer().reconcile(request).status());cleanBatch();
        assertEquals("INSERTED",writer().write(request).status());
    }
    @Test void changedRunFactsOrCatalogCannotBeWrittenFromOldWitness() {
        jdbc.update("UPDATE PRODUCT SET version=version+1 WHERE product_id=?",products.getFirst());
        var changed=writer().write(request);assertEquals("CONFLICT",changed.status());assertTrue(changed.errors().contains("CATALOG_BINDING_CHANGED"));cleanBatch();
        jdbc.update("UPDATE PATTERN SET support_abs=2 WHERE pattern_id=?",new BigDecimal(patternIds.getFirst()));
        assertEquals("CONFLICT",writer().write(request).status());cleanBatch();
    }
    @Test void sourceMovedToAnotherRunAndMissingReceiptCannotUseOldFullWitness() {
        String foreign="F"+run.substring(1);
        var fixture=new PersistenceDraftMySqlTest();fixture.jdbc=jdbc;fixture.createRun(foreign,snapshot);
        jdbc.update("UPDATE PATTERN SET run_id=? WHERE pattern_id=?",foreign,new BigDecimal(patternIds.getFirst()));
        var mismatch=writer().write(request);assertTrue(mismatch.errors().contains("MINING_EVIDENCE_CHANGED"));cleanBatch();
        jdbc.update("DELETE FROM MINING_RUN_COMPLETION WHERE run_id=?",run);
        assertTrue(writer().write(request).errors().contains("MINING_COMPLETION_UNAVAILABLE"));cleanBatch();
    }
    @Test void limitsEmptyAndBadIdsFailBeforeWritingOrClaiming() {
        var tooSmall=new JdbcDraftBatchStore(ds,new JdbcDraftBatchStore.Limits(2,40,40,500,1048576,1048576,2097152));
        assertTrue(new DraftBatchWriter(tooSmall,authority,Clock.systemUTC()).write(request).errors().contains("RESOURCE_LIMIT_EXCEEDED"));cleanBatch();
        var empty=new CandidateGrouping.Result(true,List.of(),List.of());
        var preview=new ComboPreviewRanker().preview(empty,request.context(),products.getFirst(),request.preview().configuration());
        assertTrue(writer().write(new Request(empty,request.context(),preview,key,batch,null,audit,Map.of())).errors().contains("EMPTY_BATCH_POLICY_UNCONFIRMED"));cleanBatch();
        assertEquals("INVALID",writer().write(new Request(request.grouping(),request.context(),request.preview(),key,"x".repeat(41),null,audit,request.fields())).status());cleanBatch();
        String maxId="B"+"😀".repeat(39);
        var accepted=writer().write(new Request(request.grouping(),request.context(),request.preview(),key,maxId,null,audit,request.fields()));
        assertEquals("INSERTED",accepted.status(),accepted.errors().toString());assertEquals(maxId,accepted.receipt().batchId());
    }
    Request variant(Request r,String suffix) {
        var fields=new HashMap<CandidateGrouping.StructureKey,Fields>();r.fields().forEach((k,f)->fields.put(k,new Fields(f.comboId()+suffix,f.name(),f.explanation(),f.currency(),f.referenceOriginalPrice(),f.referenceSalePrice(),f.priceEvidenceRef(),f.discountType(),f.items())));
        return new Request(r.grouping(),r.context(),r.preview(),r.writeIdentity()+suffix,r.batchId()+suffix,r.createdBy(),r.createdAt(),fields);
    }
    /** All JDBC delegates are real. Faults are named simulations, never claimed physical network loss. */
    static final class FaultSource extends AbstractDataSource {
        final DataSource delegate;final String afterInsert,tamperBatch;final boolean commitAfter,rollbackBefore,unused;
        final AtomicBoolean used=new AtomicBoolean();
        FaultSource(DataSource d,String insert,String batch,boolean after,boolean before,boolean unused){delegate=d;afterInsert=insert;tamperBatch=batch;commitAfter=after;rollbackBefore=before;this.unused=unused;}
        @Override public Connection getConnection()throws SQLException{return wrap(delegate.getConnection());}
        @Override public Connection getConnection(String u,String p)throws SQLException{return wrap(delegate.getConnection(u,p));}
        Connection wrap(Connection actual) {
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("commit")&&(commitAfter||rollbackBefore)&&used.compareAndSet(false,true)) {
                    if(commitAfter)actual.commit();else actual.rollback();throw new SQLException("Controlled commit-reply simulation");
                }
                Object value=invoke(actual,method,args);
                if(method.getName().equals("prepareStatement")&&value instanceof PreparedStatement statement&&args[0] instanceof String sql) {
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
                        if(m.getName().equals("executeQuery")&&tamperBatch!=null&&sql.startsWith("SELECT * FROM COMBO_BATCH WHERE")&&used.compareAndSet(false,true)) {
                            try(var q=actual.prepareStatement("UPDATE COMBO SET explanation='controlled corruption' WHERE batch_id=?")){q.setString(1,tamperBatch);q.executeUpdate();}
                        }
                        Object result=invoke(statement,m,a);
                        if(m.getName().equals("executeUpdate")&&afterInsert!=null&&sql.startsWith(afterInsert)&&used.compareAndSet(false,true))throw new SQLException("Controlled insert failure");
                        return result;
                    });
                }
                return value;
            });
        }
        static Object invoke(Object object,Method m,Object[] args)throws Throwable{try{return m.invoke(object,args);}catch(InvocationTargetException e){throw e.getCause();}}
    }
}
