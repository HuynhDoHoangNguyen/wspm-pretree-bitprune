package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import vn.edu.wspm.backend.services.combo.review.*;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Actual writer batches + controlled review authority; never runtime fixtures. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComboManagementMySqlTest {
    DraftBatchWriterMySqlTest fixture=new DraftBatchWriterMySqlTest();
    ComboManagementService service;String id,actor;
    final Clock clock=Clock.fixed(Instant.parse("2026-11-06T01:02:03.123456Z"),ZoneOffset.UTC);
    @BeforeAll void schema(){fixture.schema();fixture.jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('review-admin','ADMIN','Admin')");}
    @AfterAll void close(){fixture.close();}
    ComboReviewAuthority authority(){return b->fixture.batch.equals(b.row().batchId())
            ?new ComboReviewAuthority.Check(new Policy("CONTROLLED_REVIEW_V1"),new Evidence(true,true),"fixture-reviewed-writer-receipt")
            :ComboReviewAuthority.Check.unavailable();}
    @BeforeEach void setup(){
        fixture.fixture();var saved=fixture.writer().write(fixture.request);assertEquals("INSERTED",saved.status(),saved.errors().toString());
        id=saved.proposedPlan().combos().getFirst().comboId();actor="A"+UUID.randomUUID().toString().replace("-","");
        fixture.jdbc.update("INSERT INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES(?,?, 'fixture-only','Reviewer','ACTIVE')",actor,actor);
        fixture.jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'review-admin')",actor);
        service=service(fixture.ds);
    }
    ComboManagementService service(javax.sql.DataSource ds){return new ComboManagementService(ds,new JdbcComboAdminReader(ds),authority(),clock);}
    AdminPrincipal principal(){return new AdminPrincipal(actor,List.of("ADMIN"));}
    Command command(String decision,String note,String token){return new Command(decision,note,token);}
    @Test void batchHistoryPagesParentsAndLightListNeverInvokesReviewAuthority(){
        int before=fixture.jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
        fixture.jdbc.update("UPDATE COMBO_BATCH SET created_at='2098-01-01 00:00:00' WHERE batch_id=?",fixture.batch);
        fixture.jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k,created_at) SELECT 'history-new',run_id,ranking_policy,pattern_weight_policy,top_k,'2099-01-01 00:00:00' FROM COMBO_BATCH WHERE batch_id=?",fixture.batch);
        var fast=new ComboManagementService(fixture.ds,new JdbcComboAdminReader(fixture.ds),b->{throw new AssertionError("List must not verify review evidence");},clock);
        var first=fast.batches(0,1);assertEquals(Integer.toString(before+1),first.total());assertEquals(1,first.items().size());
        assertEquals("history-new",first.items().getFirst().batchId());assertEquals("0",first.items().getFirst().comboCount());
        var second=fast.batches(1,1);assertEquals(fixture.batch,second.items().getFirst().batchId());
        assertEquals("3",second.items().getFirst().comboCount());assertEquals("3",second.items().getFirst().draftCount());
        assertTrue(second.items().getFirst().createdAt().endsWith("Z"));assertTrue(fast.batches(before+1,1).items().isEmpty());
        assertEquals(1,fast.batchCombos(fixture.batch,null,null,0,1).items().size());
        assertThrows(vn.edu.wspm.backend.exception.ApplicationException.class,()->fast.batches(-1,20));
        assertThrows(vn.edu.wspm.backend.exception.ApplicationException.class,()->fast.batches(0,101));
        var token=service.detail(id).reviewToken();service.review(id,command("APPROVE",null,token),principal());
        var changed=fast.batches(1,1).items().getFirst();assertEquals("2",changed.draftCount());assertEquals("1",changed.approvedCount());
    }
    @Test void realWriterBatchListDetailApproveAuditAndIdempotencyRemainConsistent(){
        var list=service.list(fixture.batch,"DRAFT",null,0,20);assertEquals("3",list.scopeTotal());assertEquals("3",list.filteredTotal());
        var detail=service.detail(id);assertEquals(List.of("APPROVE","REJECT"),detail.assessment().allowedActions());assertNotNull(detail.reviewToken());
        var before=immutableRows();var applied=service.review(id,command("APPROVE",null,detail.reviewToken()),principal());
        assertEquals("APPLIED",applied.outcome(),applied.issues().toString());assertEquals(actor,applied.storedAudit().reviewedBy());assertEquals("APPROVED",applied.currentStatus());
        assertEquals(before,immutableRows());assertEquals("2",service.list(fixture.batch,"DRAFT",null,0,20).filteredTotal());
        var again=service.review(id,command("APPROVE","ignored retry note","old-token"),principal());
        assertEquals("ALREADY_DECIDED",again.outcome());assertEquals(applied.storedAudit(),again.storedAudit());
        assertEquals("CONFLICT",service.review(id,command("REJECT","opposite",detail.reviewToken()),principal()).outcome());
        assertEquals("CONFLICT",fixture.writer().write(fixture.request).status());
    }
    @Test void rejectNotesStaleTokensAndBatchStateAreRechecked(){
        var token=service.detail(id).reviewToken();
        assertTrue(service.review(id,command("REJECT"," ",token),principal()).issues().contains("REVIEW_NOTE_REQUIRED"));
        assertTrue(service.review(id,command("APPROVE","x".repeat(1001),token),principal()).issues().contains("INVALID_REVIEW_NOTE"));
        fixture.jdbc.update("UPDATE COMBO SET explanation='changed' WHERE combo_id=?",id);
        assertTrue(service.review(id,command("APPROVE",null,token),principal()).issues().contains("STALE_REVIEW_DATA"));
        var next=service.detail(id).reviewToken();fixture.jdbc.update("UPDATE COMBO_BATCH SET status='ACTIVE' WHERE batch_id=?",fixture.batch);
        assertTrue(service.detail(id).assessment().allowedActions().isEmpty());
        assertTrue(service.review(id,command("APPROVE",null,next),principal()).issues().contains("BATCH_NOT_REVIEWABLE"));
        fixture.jdbc.update("UPDATE COMBO_BATCH SET status='DRAFT' WHERE batch_id=?",fixture.batch);
        var rejected=service.review(id,command("REJECT","  reason  ",service.detail(id).reviewToken()),principal());
        assertEquals("APPLIED",rejected.outcome());assertEquals("reason",rejected.storedAudit().reviewNote());
    }
    @Test void productionMissingAuthorityAndCurrentRoleRemovalDeny(){
        var denied=new ComboManagementService(fixture.ds,new JdbcComboAdminReader(fixture.ds),b->ComboReviewAuthority.Check.unavailable(),clock);
        assertNull(denied.detail(id).reviewToken());assertTrue(denied.detail(id).assessment().blockingIssues().stream().anyMatch(i->i.code().equals("REVIEW_POLICY_UNCONFIRMED")));
        assertTrue(denied.review(id,command("APPROVE",null,"old"),principal()).issues().contains("REVIEW_POLICY_UNCONFIRMED"));
        var token=service.detail(id).reviewToken();fixture.jdbc.update("DELETE FROM USER_ROLE WHERE user_id=?",actor);
        assertTrue(service.review(id,command("APPROVE",null,token),principal()).issues().contains("REVIEWER_IDENTITY_INVALID"));
        assertEquals("DRAFT",service.detail(id).data().comboStatus());
    }
    @Test void catalogAndItemChangesInvalidateOldTokensWithoutChangingAudit(){
        var token=service.detail(id).reviewToken();
        fixture.jdbc.update("UPDATE PRODUCT SET version=version+1 WHERE product_id=?",fixture.products.getFirst());
        assertTrue(service.review(id,command("APPROVE",null,token),principal()).issues().contains("STALE_REVIEW_DATA"));
        var next=service.detail(id).reviewToken();fixture.jdbc.update("UPDATE COMBO_ITEM SET quantity=quantity+1 WHERE combo_id=?",id);
        assertTrue(service.review(id,command("APPROVE",null,next),principal()).issues().contains("STALE_REVIEW_DATA"));
        assertNull(service.detail(id).data().review().reviewedAt());assertEquals("DRAFT",service.detail(id).data().comboStatus());
    }
    @Test void concurrentOppositeAdminsCannotOverwriteAudit()throws Exception{
        String other="A"+UUID.randomUUID().toString().replace("-","");
        fixture.jdbc.update("INSERT INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES(?,?, 'fixture-only','Reviewer2','ACTIVE')",other,other);
        fixture.jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'review-admin')",other);
        var token=service.detail(id).reviewToken();var pool=Executors.newFixedThreadPool(2);var go=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{go.await();return service.review(id,command("APPROVE",null,token),principal());});
            var b=pool.submit(()->{go.await();return service.review(id,command("REJECT","reason",token),new AdminPrincipal(other,List.of("ADMIN")));});go.countDown();
            var x=a.get(20,TimeUnit.SECONDS);var y=b.get(20,TimeUnit.SECONDS);assertEquals(Set.of("APPLIED","CONFLICT"),Set.of(x.outcome(),y.outcome()));
            var winner="APPLIED".equals(x.outcome())?x:y;assertEquals(winner.storedAudit(),service.detail(id).data().review());
        }finally{go.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(20,TimeUnit.SECONDS));}
    }
    @Test void injectedFailureAfterRealStatusAndAuditUpdateRollsBackEverything(){
        var token=service.detail(id).reviewToken();var before=immutableRows();
        var ds=new DraftBatchWriterMySqlTest.FaultSource(fixture.ds,"UPDATE COMBO SET status=",null,false,false,false);
        var failed=service(ds).review(id,command("APPROVE",null,token),principal());
        assertEquals("ROLLED_BACK",failed.outcome(),failed.issues().toString());var after=service.detail(id);
        assertEquals("DRAFT",after.data().comboStatus());assertNull(after.data().review().reviewedBy());assertEquals(before,immutableRows());
    }
    private Object immutableRows(){
        // Strings/HEX avoid byte[] identity in Map equality; includes full evidence hashes and children/metrics.
        return List.of(fixture.jdbc.queryForList("SELECT i.* FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=? ORDER BY i.combo_id,i.itemset_order,i.item_order",fixture.batch),
                fixture.jdbc.queryForList("SELECT s.* FROM COMBO_SOURCE s JOIN COMBO c ON c.combo_id=s.combo_id WHERE c.batch_id=? ORDER BY s.combo_id,s.pattern_id",fixture.batch),
                fixture.jdbc.queryForList("SELECT combo_id,support_abs,support_ratio,pattern_weight,name,explanation FROM COMBO WHERE batch_id=? ORDER BY combo_id",fixture.batch),
                fixture.jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id",fixture.run),
                fixture.jdbc.queryForList("SELECT pi.* FROM PATTERN_ITEM pi JOIN PATTERN p ON p.pattern_id=pi.pattern_id WHERE p.run_id=? ORDER BY pi.pattern_id,pi.itemset_order,pi.item_order",fixture.run),
                fixture.jdbc.queryForList("SELECT HEX(content_digest),HEX(evidence_digest),HEX(representation_digest) FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",fixture.batch));
    }
}
