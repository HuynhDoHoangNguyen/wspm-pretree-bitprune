package vn.edu.wspm.backend.services.combo.ranking;

import java.math.BigInteger;
import java.util.List;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;

public final class ComboPreview {
    private ComboPreview() { }
    /** Confirmations must be resolved by trusted server integration, not client-supplied flags. */
    public record Configuration(String rankingPolicy,String rankingConfirmationRef,
            WeightPolicyConfirmation weightPolicy,String anchorPolicy,String anchorConfirmationRef,
            BigInteger k,BigInteger approvedKCap,String kCapConfirmationRef) { }
    public record AnchorFlag(long itemsetOrder,long itemOrder,String productId,boolean anchorEligible) { }
    public record GroupEligibility(Group group,List<AnchorFlag> items) {
        public GroupEligibility { items=List.copyOf(items); }
    }
    public record RankedGroup(int rank,Group group) { }
    public record Result(boolean completed,List<String> errors,String anchorProductId,Context context,
            Configuration configuration,int eligibleCount,List<RankedGroup> items,
            List<GroupEligibility> structuralEligibility) {
        public Result { errors=List.copyOf(errors);items=List.copyOf(items);structuralEligibility=List.copyOf(structuralEligibility); }
    }
}
