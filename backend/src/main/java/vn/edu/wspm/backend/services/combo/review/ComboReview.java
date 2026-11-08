package vn.edu.wspm.backend.services.combo.review;

import java.util.List;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.Audit;

/** Internal decision models; verification inputs must never be bound from an HTTP request. */
public final class ComboReview {
    private ComboReview() { }
    public record Command(String decision,String reviewNote,String expectedReviewToken) { }
    /** Explicit confirmation of the TV2-05 proposal, not a policy registry implementation. */
    public record Policy(String confirmationRef) {
        public static Policy unconfirmed() { return new Policy(null); }
        public boolean confirmed() { return confirmationRef!=null && !confirmationRef.isBlank(); }
    }
    /** Results from future trusted server adapters; flags alone are not evidence of real verification. */
    public record Evidence(boolean dataAndFreshnessVerified,boolean writerLockProtocolVerified) {
        public static Evidence unverified() { return new Evidence(false,false); }
    }
    public record Reviewer(boolean authenticated,boolean admin,String mappedUserAccountId) { }
    public enum TokenVerification { MATCHED, STALE, UNVERIFIED }
    public record ProposedChange(String expectedStatus,String targetStatus,Audit proposedAudit) { }
    public record Result(String outcome,String currentStatus,Audit storedAudit,List<String> issues,ProposedChange proposedChange) {
        public Result { issues=List.copyOf(issues); }
    }
}
