package vn.edu.wspm.backend.repositories;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.services.combo.review.*;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;
import static org.junit.jupiter.api.Assertions.*;

/** Generation and review are real; policy and producer witness are controlled test-only whitelists. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WriterEvidenceReviewAuthorityMySqlTest {
    final ComboGenerationMySqlTest g=new ComboGenerationMySqlTest();
    WriterEvidenceReviewAuthority inspector,authority;
    WriterEvidenceReviewAuthority.Inspection expected;
    ComboManagementService service;
    String id,actor;
    final AtomicReference<String> version=new AtomicReference<>("TEST_REVIEW_V1");
    TransactionTemplate tx;
    @BeforeAll void schema(){g.schema();g.f.jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('next04-admin','ADMIN','Test review admin')");}
    @AfterAll void close(){g.close();}
    @BeforeEach void fixture() {
        g.fixture();var out=g.service().generate(g.intent());assertEquals("INSERTED",out.status(),out.issues().toString());id=out.receipt().comboIds().values().iterator().next();
        actor="A"+UUID.randomUUID().toString().replace("-","");
        g.f.jdbc.update("INSERT INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES(?,?,'controlled-test-only','Reviewer','ACTIVE')",actor,actor);
        g.f.jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'next04-admin')",actor);
        tx=new TransactionTemplate(new DataSourceTransactionManager(g.f.ds));tx.setReadOnly(true);tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        inspector=new WriterEvidenceReviewAuthority(g.f.ds,500,4*1024*1024,b->ComboReviewAuthority.Check.unavailable());
        expected=tx.execute(t->inspector.inspect(new JdbcComboAdminReader(g.f.ds).detailCurrent(id)));
        assertTrue(expected.valid(),expected.issue());version.set("TEST_REVIEW_V1");
        authority=new WriterEvidenceReviewAuthority(g.f.ds,500,4*1024*1024,b->{
            var current=inspector.inspect(b);boolean verified=current.valid()&&expected.writerRoot().equals(current.writerRoot())&&expected.protectedFacts().equals(current.protectedFacts());
            return new ComboReviewAuthority.Check(new Policy(version.get()),new Evidence(verified,verified),version.get());
        });
        service=new ComboManagementService(g.f.ds,new JdbcComboAdminReader(g.f.ds),authority,Clock.fixed(g.f.audit.plusSeconds(10),ZoneOffset.UTC));
    }
    AdminPrincipal principal(){return new AdminPrincipal(actor,List.of("ADMIN"));}
    @Test void integrityDoesNotGrantReviewWithoutSeparatePolicyAndTransaction() {
        var denied=new ComboManagementService(g.f.ds,new JdbcComboAdminReader(g.f.ds),inspector,Clock.systemUTC()).detail(id);
        assertTrue(denied.assessment().allowedActions().isEmpty());assertNull(denied.reviewToken());
        assertEquals("REVIEW_TRANSACTION_REQUIRED",inspector.inspect(new JdbcComboAdminReader(g.f.ds).detail(id)).issue());
    }
    @Test void generationBackedReviewPersistsAuditAndAllowsTerminalRetryWithoutWeakeningWriterConflict() {
        var detail=service.detail(id);assertEquals(List.of("APPROVE","REJECT"),detail.assessment().allowedActions());
        var applied=service.review(id,new Command("APPROVE",null,detail.reviewToken()),principal());assertEquals("APPLIED",applied.outcome(),applied.issues().toString());
        assertEquals(actor,applied.storedAudit().reviewedBy());
        var retry=service.review(id,new Command("APPROVE","not a replacement note","stale-token"),principal());assertEquals("ALREADY_DECIDED",retry.outcome());assertEquals(applied.storedAudit(),retry.storedAudit());
        assertEquals("CONFLICT",service.review(id,new Command("REJECT","opposite",detail.reviewToken()),principal()).outcome());
        assertEquals("CONFLICT",g.service().generate(g.intent()).status());
    }
    @Test void opaqueEvidenceWithMatchingHashIsNotTreatedAsApprovedGenerationEvidence() {
        var token=service.detail(id).reviewToken();byte[] tamper={4,5,6};
        g.f.jdbc.update("UPDATE COMBO_BATCH_WRITE_EVIDENCE SET evidence_bytes=?,evidence_digest=UNHEX(SHA2(?,256)) WHERE batch_id=?",tamper,tamper,g.f.batch);
        assertTrue(tx.execute(t->inspector.inspect(new JdbcComboAdminReader(g.f.ds).detailCurrent(id))).valid());
        assertTrue(service.detail(id).assessment().allowedActions().isEmpty());
        assertEquals("BLOCKED",service.review(id,new Command("APPROVE",null,token),principal()).outcome());assertEquals("DRAFT",g.f.jdbc.queryForObject("SELECT status FROM COMBO WHERE combo_id=?",String.class,id));
    }
    @Test void changedProtectedItemsAndFreshCatalogAreRejectedByReadAndSubmit() {
        var token=service.detail(id).reviewToken();
        g.f.jdbc.update("UPDATE PRODUCT SET version=version+1 WHERE product_id=?",g.f.products.getFirst());
        assertTrue(service.detail(id).assessment().allowedActions().isEmpty());assertEquals("BLOCKED",service.review(id,new Command("APPROVE",null,token),principal()).outcome());
        g.f.jdbc.update("UPDATE COMBO_ITEM SET quantity=2 WHERE combo_id=?",id);
        var observed=tx.execute(t->inspector.inspect(new JdbcComboAdminReader(g.f.ds).detailCurrent(id)));
        assertEquals("WRITER_PROTECTED_PAYLOAD_CHANGED",observed.issue());assertTrue(service.detail(id).assessment().allowedActions().isEmpty());
    }
    @Test void policyVersionChangesInvalidateCr1WithoutChangingTokenFormatOrActorRules() {
        var token=service.detail(id).reviewToken();assertTrue(token.startsWith("CR1:"));version.set("TEST_REVIEW_V2");
        assertTrue(service.review(id,new Command("APPROVE",null,token),principal()).issues().contains("STALE_REVIEW_DATA"));
        assertEquals("DRAFT",g.f.jdbc.queryForObject("SELECT status FROM COMBO WHERE combo_id=?",String.class,id));
    }
    @Test void capsBlockBeforeEvidenceCopiesOrLargeRowMaterialization() {
        var cap=new WriterEvidenceReviewAuthority(g.f.ds,500,1,b->ComboReviewAuthority.Check.unavailable());
        assertEquals("WRITER_EVIDENCE_LIMIT_EXCEEDED",tx.execute(t->cap.inspect(new JdbcComboAdminReader(g.f.ds).detailCurrent(id))).issue());
    }
}
