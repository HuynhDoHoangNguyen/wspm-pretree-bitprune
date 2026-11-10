package vn.edu.wspm.backend.repositories;

import java.time.*;
import java.util.*;
import java.math.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.services.combo.admin.ComboAdminService;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import vn.edu.wspm.backend.services.combo.generation.ComboGenerationService;
import vn.edu.wspm.backend.services.combo.persistence.*;
import vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;
import vn.edu.wspm.backend.services.mining.handoff.*;
import vn.edu.wspm.backend.services.mining.provider.TrustedRawSnapshotProvider.EngineProof;
import static vn.edu.wspm.backend.services.combo.generation.ComboGenerationService.*;
import static org.junit.jupiter.api.Assertions.*;

/** Controlled producer/business witness + actual orchestration/writer/DB. Not mining publisher output. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComboGenerationMySqlTest {
    final DraftBatchWriterMySqlTest f=new DraftBatchWriterMySqlTest();
    final PersistedMiningResultBoundary.Limits readLimits=new PersistedMiningResultBoundary.Limits(100,100,1000,1000000);
    PersistedMiningResultBoundary.Result full;
    Grant grant;
    String approvedDigest;
    JdbcDraftBatchStore.MiningBinding root;
    byte[] controlledReceipt;
    @BeforeAll void schema(){f.schema();}
    @AfterAll void close(){f.close();}
    @BeforeEach void fixture() {
        f.fixture();root=f.store.miningBinding(f.run);
        controlledReceipt=f.jdbc.queryForObject("SELECT manifest_bytes FROM MINING_RUN_COMPLETION WHERE run_id=?",byte[].class,f.run);
        var patterns=f.request.grouping().groups().stream().flatMap(g->g.sources().stream()).map(s->s.candidate().source())
                .distinct().sorted(Comparator.comparing(p->new BigInteger(p.patternId()))).toList();
        var stats=new ConversionArtifactResult.Statistics(1,0,1,2,3,3,3,3,3);
        var b=new WdmMiningInputAdapter.Binding(f.snapshot,"00000000-0000-0000-0000-000000000002","d".repeat(64),"a".repeat(64),"900",
                ConversionArtifactCodec.VERSION,ConversionArtifactCodec.POLICIES,"CLEANED_KEPT_RECORDS",OrderCleaningPipeline.POLICY_VERSION,
                "b".repeat(64),30,"c".repeat(64),300,stats);
        var run=new PersistedMiningResultBoundary.Run(f.run,f.snapshot,"SUCCEEDED","WSPM_PRETREE","ABSOLUTE",.1,.1,
                new EngineProof("CONTROLLED_TEST_BUILD","b".repeat(64),"FIXTURE_ONLY"),b);
        var dictionary=f.jdbc.query("SELECT item_id,product_id,product_name_snapshot,sku_snapshot,occurrence_count,sequence_support,item_weight FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id",
                (rs,n)->new PersistedMiningResultBoundary.DictionaryItem(f.run,rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getLong(6),rs.getDouble(7)),f.run);
        full=new PersistedMiningResultBoundary.Result(run,new PersistedMiningResultBoundary.DictionaryBinding(b,"CONTROLLED_TEST_HISTORY","FIXTURE_ONLY"),dictionary,patterns,
                new PersistedMiningResultBoundary.DurableEvidence("CONTROLLED_TEST_V3_LINK",root.receiptDigest(),controlledReceipt));
        var catalog=new HashMap<String,CatalogProduct>();
        f.request.grouping().groups().forEach(g->g.itemsets().forEach(s->s.items().forEach(i->catalog.put(i.productId(),i.catalogEvidence()))));
        var context=f.request.context();
        approvedDigest=PersistedMiningResultBoundary.payloadDigest(full,readLimits.maxPayloadBytes());
        grant=new Grant(approvedDigest,Purpose.DEMO_CATALOG,"CONTROLLED_TEST_PURPOSE",
                new Policy(context.evaluationPolicyVersion(),context.policyConfirmationRef(),Set.of("LCAT"),Set.of("ACAT"),context.eligibilityMode()),
                catalog,context.catalogSnapshotRef(),context.evaluatedAt(),f.request.preview().configuration(),f.products.getFirst(),f.batch,null,
                f.request.fields(),"CONTROLLED_TEST_GENERATOR","CONTROLLED_TEST_COMMERCIAL",new byte[]{2,3,4});
    }
    Intent intent(){return new Intent(f.run,f.key);}
    PersistedMiningResultBoundary boundary() {
        return new PersistedMiningResultBoundary((id,l)->Optional.of(full),(r,d)->{
            if(!approvedDigest.equals(d) || !Arrays.equals(controlledReceipt,r.evidence().bytes()) || !root.receiptDigest().equals(r.evidence().identity()))return Optional.empty();
            return Optional.of(new PersistedMiningResultBoundary.Completion(f.run,f.snapshot,d,r.evidence().protocol(),r.evidence().identity()));
        },readLimits);
    }
    ComboGenerationService service(DataSource ds,Clock clock) {
        return new ComboGenerationService(boundary(),(i,r)->{
            if(!i.equals(intent()) || !r.completion().orElseThrow().payloadDigest().equals(grant.fullPayloadDigest()))return BusinessDecision.unavailable();
            return new BusinessDecision(List.of(),grant);
        },r->Optional.of(new WriterWitness(approvedDigest,root.factsDigest(),root.receiptDigest(),"CONTROLLED_TEST_FULL_PAYLOAD_TO_V3_LINK")),new JdbcDraftBatchStore(ds,f.limits),clock);
    }
    ComboGenerationService service(){return service(f.ds,Clock.fixed(f.audit,ZoneOffset.UTC));}
    Grant change(Map<vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.StructureKey,Fields> fields,byte[] evidence) {
        return new Grant(approvedDigest,grant.purpose(),grant.purposeReference(),grant.evaluationPolicy(),grant.catalog(),grant.catalogReference(),grant.evaluatedAt(),
                grant.ranking(),grant.anchorProductId(),grant.batchId(),grant.createdBy(),fields,grant.generatorVersion(),grant.commercialPolicyReference(),evidence);
    }
    @Test void completeGenerationPersistsAllGroupsBeyondTopKAndReadsThemThroughComboReader() {
        var out=service().generate(intent());assertEquals("INSERTED",out.status(),out.issues().toString());assertEquals(3,out.groupCount());assertEquals(1,out.previewCount());assertNotNull(out.receipt());
        assertEquals(3,out.decisions().stream().filter(d->d.decision()==Decision.ACCEPT).count());assertEquals(3,f.count("COMBO"));
        var reader=new ComboAdminService(new JdbcComboAdminReader(f.ds));var page=reader.list(f.batch,null,null,0,20);assertEquals("3",page.filteredTotal());
        for(var combo:out.receipt().comboIds().values()) {
            var detail=reader.detail(combo);assertEquals("DRAFT",detail.data().comboStatus());
            assertEquals(1,detail.sources().stream().filter(s->s.primary()).count());assertFalse(detail.itemsets().isEmpty());
            assertEquals(.5,detail.data().metrics().patternWeight());assertEquals("1",detail.data().metrics().supportAbs());
            assertTrue(detail.itemsets().stream().flatMap(s->s.items().stream()).allMatch(i->i.nameSnapshot().startsWith("Historic ")));
        }
        assertEquals(7,f.jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",Integer.class,f.batch));
    }
    @Test void retryKeepsIdsAuditAndChangedCommercialOrEvidenceConflictsWithoutPartialWrites() {
        var first=service().generate(intent());assertEquals("INSERTED",first.status(),first.issues().toString());
        var retry=service(f.ds,Clock.fixed(f.audit.plusSeconds(500),ZoneOffset.UTC)).generate(intent());
        assertEquals("NO_OP",retry.status(),retry.issues().toString());assertEquals(first.receipt(),retry.receipt());
        var original=grant;grant=change(grant.fields(),new byte[]{8,9});assertEquals("CONFLICT",service().generate(intent()).status());
        var c=original.ranking();
        grant=new Grant(approvedDigest,original.purpose(),original.purposeReference(),original.evaluationPolicy(),original.catalog(),original.catalogReference(),original.evaluatedAt(),
                new vn.edu.wspm.backend.services.combo.ranking.ComboPreview.Configuration(c.rankingPolicy(),c.rankingConfirmationRef(),c.weightPolicy(),c.anchorPolicy(),c.anchorConfirmationRef(),BigInteger.TWO,c.approvedKCap(),c.kCapConfirmationRef()),
                original.anchorProductId(),original.batchId(),original.createdBy(),original.fields(),original.generatorVersion(),original.commercialPolicyReference(),original.resolvedBusinessEvidence());
        assertEquals("CONFLICT",service().generate(intent()).status());
        grant=original;var fields=new HashMap<>(grant.fields());var key=fields.keySet().iterator().next();var x=fields.get(key);
        fields.put(key,new Fields(x.comboId(),x.name()+" changed",x.explanation(),x.currency(),x.referenceOriginalPrice(),x.referenceSalePrice(),x.priceEvidenceRef(),x.discountType(),x.items()));
        grant=change(fields,grant.resolvedBusinessEvidence());assertEquals("CONFLICT",service().generate(intent()).status());assertEquals(3,f.count("COMBO"));
    }
    @Test void staleCatalogAndRunFactsAreCaughtByWriterCurrentStateRechecks() {
        f.jdbc.update("UPDATE PRODUCT SET version=version+1 WHERE product_id=?",f.products.getFirst());
        var stale=service().generate(intent());assertEquals("CONFLICT",stale.status(),stale.issues().toString());assertTrue(stale.issues().contains("CATALOG_BINDING_CHANGED"));f.cleanBatch();
        f.jdbc.update("UPDATE PATTERN SET support_abs=2 WHERE pattern_id=?",new BigDecimal(f.patternIds.getFirst()));
        var changed=service().generate(intent());assertEquals("CONFLICT",changed.status());assertTrue(changed.issues().contains("MINING_EVIDENCE_CHANGED"));f.cleanBatch();
    }
    @Test void invalidFullPayloadAndMissingCommercialContextNeverWrite() {
        var old=full;
        full=new PersistedMiningResultBoundary.Result(full.run(),full.dictionaryBinding(),full.dictionary().subList(0,2),full.patterns(),full.evidence());
        assertEquals("INVALID_RESULT",service().generate(intent()).status());f.cleanBatch();
        full=old;grant=change(Map.of(),grant.resolvedBusinessEvidence());
        var out=service().generate(intent());assertEquals("INVALID",out.status(),out.issues().toString());assertTrue(out.issues().contains("COMBO_FIELDS_INCOMPLETE"));f.cleanBatch();
    }
    @Test void reorderedFullInputStillUsesExistingDeterministicGroupingAndPrimaryRules() {
        var reversed=new ArrayList<>(full.patterns());Collections.reverse(reversed);
        full=new PersistedMiningResultBoundary.Result(full.run(),full.dictionaryBinding(),full.dictionary(),reversed,full.evidence());
        approvedDigest=PersistedMiningResultBoundary.payloadDigest(full,readLimits.maxPayloadBytes());grant=change(grant.fields(),grant.resolvedBusinessEvidence());
        var out=service().generate(intent());assertEquals("INSERTED",out.status(),out.issues().toString());
        assertEquals(new HashSet<>(f.request.fields().values().stream().map(Fields::comboId).toList()),new HashSet<>(out.receipt().comboIds().values()));
        assertEquals(new HashSet<>(f.patternIds),new HashSet<>(f.jdbc.queryForList("SELECT CAST(s.pattern_id AS CHAR) FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=? AND s.is_primary=1",String.class,f.batch)));
    }
    @Test void actualCommitWithSimulatedLostReplyStaysUnknownUntilExplicitReconcile() {
        var fault=new DraftBatchWriterMySqlTest.FaultSource(f.ds,null,null,true,false,false);
        var unknown=service(fault,Clock.fixed(f.audit,ZoneOffset.UTC)).generate(intent());
        assertEquals("UNKNOWN",unknown.status(),unknown.issues().toString());assertNull(unknown.receipt());assertEquals(1,f.count("COMBO_BATCH"));
        var recovered=service().reconcile(intent());assertEquals("NO_OP",recovered.status(),recovered.issues().toString());assertNotNull(recovered.receipt());
    }
    @Test void rollbackAtCommitErrorDoesNotInsertDuringReconciliation() {
        var fault=new DraftBatchWriterMySqlTest.FaultSource(f.ds,null,null,false,true,false);
        assertEquals("UNKNOWN",service(fault,Clock.fixed(f.audit,ZoneOffset.UTC)).generate(intent()).status());f.cleanBatch();
        assertEquals("UNKNOWN",service().reconcile(intent()).status());f.cleanBatch();
    }
}
