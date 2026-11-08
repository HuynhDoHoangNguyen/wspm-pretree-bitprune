package vn.edu.wspm.backend.services.combo.review;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;
import vn.edu.wspm.backend.services.combo.admin.ComboReviewReadiness;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;
import static org.junit.jupiter.api.Assertions.*;

class ComboReviewDeciderTest {
    private final Policy policy=new Policy("TEST-only-TV2-05-proposal-confirmed");
    private final Evidence evidence=new Evidence(true,true);
    private final Reviewer reviewer=new Reviewer(true,true,"admin-A");
    private final ComboReviewDecider decider=new ComboReviewDecider(Clock.fixed(Instant.parse("2026-11-05T02:00:00.123456789Z"),ZoneOffset.UTC));
    private Bundle bundle(String status,Audit audit) {
        var metrics=new Metrics("9007199254740993",.5,.4);
        var row=new Row("C","Name","SEQUENTIAL_CROSS_SELL",status,"B","DRAFT","R","SUCCEEDED","2026-01-01T00:00:00.000000Z",
                audit,metrics,"Explanation","VND",BigDecimal.TEN,BigDecimal.TEN,"RANK","W","1");
        return new Bundle(row,List.of(new Item(1,1,"L","1",BigDecimal.ONE,true,false,"Laptop","L"),
                new Item(2,1,"M","1",BigDecimal.ONE,false,false,"Mouse","M")),
                List.of(new Source("18446744073709551615",true,"R","<(1)(2)>",metrics,4.0,"W")));
    }
    private Bundle draft() { return bundle("DRAFT",new Audit(null,null,null,null)); }
    private Result decide(Bundle b,Command c) { return decider.decide(b,c,reviewer,policy,evidence,TokenVerification.MATCHED); }
    private Command command(String decision,String note) { return new Command(decision,note,"TEST-token"); }
    @Test void confirmedFixtureReadAndSubmitShareRulesAndProduceProposalOnly() {
        var b=draft();var assessment=new ComboReviewRules().assess(b,policy,evidence);
        assertEquals(List.of("APPROVE","REJECT"),assessment.allowedActions());
        var result=decide(b,command("APPROVE",null));assertEquals("READY_TO_APPLY",result.outcome());
        assertEquals("DRAFT",result.currentStatus());assertEquals("APPROVED",result.proposedChange().targetStatus());
        assertEquals("admin-A",result.proposedChange().proposedAudit().reviewedBy());
        assertEquals("2026-11-05T02:00:00.123456Z",result.proposedChange().proposedAudit().reviewedAt());
        assertNull(result.proposedChange().proposedAudit().reviewNote());assertEquals("DRAFT",b.row().comboStatus());
        assertEquals(BigDecimal.TEN,b.row().referenceSalePrice());assertEquals("DRAFT",b.row().batchStatus());
        assertEquals("<(1)(2)>",b.sources().getFirst().canonicalPattern());
    }
    @Test void defaultProductionAssessmentAndServiceRemainBlocked() {
        var b=draft();assertTrue(new ComboReviewReadiness().assess(b).allowedActions().isEmpty());
        var service=new ComboReviewService();var r=service.review("C",command("APPROVE",null));
        assertEquals("BLOCKED",r.outcome());assertNull(r.proposedChange());assertNull(r.storedAudit());
        assertTrue(r.issues().containsAll(List.of("ADMIN_AUTH_UNCONFIRMED","REVIEW_TOKEN_UNVERIFIED","WRITER_LOCK_PROTOCOL_UNCONFIRMED")));
        assertEquals(r,service.review("C",command("APPROVE",null)));
    }
    @Test void provenanceIssuesBlockBothReadAndSubmit() {
        var b=draft();var bad=new Bundle(b.row(),b.items(),List.of(new Source("1",true,"OTHER","x",new Metrics("2",.1,.9),1.0,"W")));
        var a=new ComboReviewRules().assess(bad,policy,evidence);assertTrue(a.allowedActions().isEmpty());
        var r=decide(bad,command("REJECT","Reason"));assertEquals("BLOCKED",r.outcome());assertNull(r.proposedChange());
        assertTrue(r.issues().containsAll(List.of("SOURCE_RUN_MISMATCH","PRIMARY_METRICS_MISMATCH")));
        assertEquals(a.blockingIssues().stream().map(Issue::code).toList(),r.issues());
    }
    @Test void missingPolicyEvidenceAndWriterLockEachPreventEligibility() {
        for(var e:List.of(new Evidence(false,true),new Evidence(true,false),Evidence.unverified())) {
            assertTrue(new ComboReviewRules().assess(draft(),policy,e).allowedActions().isEmpty());
            assertEquals("BLOCKED",decider.decide(draft(),command("APPROVE",null),reviewer,policy,e,TokenVerification.MATCHED).outcome());
        }
        assertTrue(new ComboReviewRules().assess(draft(),Policy.unconfirmed(),evidence).allowedActions().isEmpty());
        assertTrue(decider.decide(draft(),command("APPROVE",null),reviewer,Policy.unconfirmed(),evidence,TokenVerification.MATCHED).issues().contains("REVIEW_POLICY_UNCONFIRMED"));
    }
    @Test void staleAndUnverifiedTokensDoNotProduceChange() {
        for(var token:List.of(TokenVerification.STALE,TokenVerification.UNVERIFIED)) {
            var r=decider.decide(draft(),command("APPROVE",null),reviewer,policy,evidence,token);
            assertNull(r.proposedChange());assertTrue(r.issues().contains(token==TokenVerification.STALE?"STALE_REVIEW_DATA":"REVIEW_TOKEN_UNVERIFIED"));
        }
        assertTrue(decide(draft(),new Command("APPROVE",null,null)).issues().contains("REVIEW_TOKEN_UNVERIFIED"));
    }
    @Test void unicodeNoteBoundsRequiredAndOptionalWhitespaceUnderTestPolicy() {
        assertTrue(decide(draft(),command("REJECT"," \t ")).issues().contains("REVIEW_NOTE_REQUIRED"));
        assertNull(decide(draft(),command("APPROVE"," \t ")).proposedChange().proposedAudit().reviewNote());
        String emoji=new String(Character.toChars(0x1F600));
        assertEquals("READY_TO_APPLY",decide(draft(),command("REJECT",emoji.repeat(1000))).outcome());
        assertTrue(decide(draft(),command("REJECT",emoji.repeat(1001))).issues().contains("INVALID_REVIEW_NOTE"));
        assertEquals("Reason\nsecond line",decide(draft(),command("REJECT","  Reason\nsecond line  ")).proposedChange().proposedAudit().reviewNote());
        assertTrue(decide(draft(),command("APPROVE","bad\0note")).issues().contains("INVALID_REVIEW_NOTE"));
    }
    @Test void sameDecisionKeepsWinningAuditEvenWhenNoteReviewerTokenOrEvidenceDiffer() {
        var audit=new Audit("winner",null,"2026-11-04T00:00:00.000000Z","original");
        for(String status:List.of("APPROVED","REJECTED")) {
            var b=bundle(status,audit);var c=command(status.equals("APPROVED")?"APPROVE":"REJECT","different valid note");
            var r=decider.decide(b,c,new Reviewer(true,true,"admin-B"),policy,Evidence.unverified(),TokenVerification.STALE);
            assertEquals("ALREADY_DECIDED",r.outcome());assertSame(audit,r.storedAudit());assertNull(r.proposedChange());
        }
        assertTrue(decide(bundle("REJECTED",audit),command("REJECT"," ")).issues().contains("REVIEW_NOTE_REQUIRED"));
    }
    @Test void oppositeDecisionsAndPublishedArchivedNeverReopen() {
        var audit=new Audit("winner",null,"2026-11-04T00:00:00.000000Z","reason");
        for(String status:List.of("APPROVED","REJECTED","PUBLISHED","ARCHIVED","PENDING")) {
            var c=command(status.equals("APPROVED")?"REJECT":"APPROVE","valid");var r=decide(bundle(status,audit),c);
            assertEquals("CONFLICT",r.outcome());assertEquals(status,r.currentStatus());assertNull(r.proposedChange());
        }
    }
    @Test void authIdentityNotFoundAndCommandShapeAreDistinct() {
        assertTrue(decider.decide(null,command("APPROVE",null),null,policy,evidence,TokenVerification.MATCHED).issues().contains("UNAUTHORIZED"));
        assertTrue(decider.decide(null,command("APPROVE",null),new Reviewer(true,false,"u"),policy,evidence,TokenVerification.MATCHED).issues().contains("FORBIDDEN"));
        assertTrue(decider.decide(null,command("APPROVE",null),new Reviewer(true,true,null),policy,evidence,TokenVerification.MATCHED).issues().contains("REVIEWER_IDENTITY_INVALID"));
        assertTrue(decide(null,command("APPROVE",null)).issues().contains("COMBO_NOT_FOUND"));
        assertTrue(decide(draft(),command("PUBLISHED",null)).issues().contains("INVALID_REVIEW_COMMAND"));
    }
    @Test void inconsistentAuditBlocksDraftAndTerminalRetry() {
        var b=bundle("DRAFT",new Audit("old",null,"2026-01-01T00:00:00.000000Z",null));
        assertTrue(decide(b,command("APPROVE",null)).issues().contains("REVIEW_AUDIT_INCONSISTENT"));
        assertTrue(decide(bundle("APPROVED",new Audit(null,null,null,null)),command("APPROVE",null)).issues().contains("REVIEW_AUDIT_INCONSISTENT"));
    }
}
