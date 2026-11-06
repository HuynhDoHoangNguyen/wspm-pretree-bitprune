package vn.edu.wspm.backend.services.combo.review;

import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Trusted server-local policy/evidence adapter. Called inside snapshot/locked transaction.
 * Must only use local transactional data: no remote, filesystem, engine IO or browser proofs.
 * Must verify writer/mining evidence and current eligibility, not infer approval from refs/counts.
 * No production implementation is supplied until that authority is available. */
@FunctionalInterface
public interface ComboReviewAuthority {
    Check verify(Bundle current);
    record Check(Policy policy,Evidence evidence,String binding) {
        public static Check unavailable(){return new Check(Policy.unconfirmed(),Evidence.unverified(),"UNAVAILABLE");}
    }
}
