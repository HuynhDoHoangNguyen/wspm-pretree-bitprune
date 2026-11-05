package vn.edu.wspm.backend.services.combo.review;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Deliberately blocked production entry point. No transaction/HTTP registration until the missing contracts exist. */
@Service
public final class ComboReviewService {
    private static final Logger log=LoggerFactory.getLogger(ComboReviewService.class);
    public Result review(String comboId,Command command) {
        if(!ComboReviewDecider.validId(comboId) || !ComboReviewDecider.validCommand(command))
            return new Result("BLOCKED",null,null,List.of("INVALID_REVIEW_COMMAND"),null);
        log.warn("Combo review blocked: server verification contracts unavailable");
        return new Result("BLOCKED",null,null,List.of("ADMIN_AUTH_UNCONFIRMED","REVIEW_POLICY_UNCONFIRMED",
                "REVIEW_EVIDENCE_UNVERIFIED","REVIEW_TOKEN_UNVERIFIED","WRITER_LOCK_PROTOCOL_UNCONFIRMED"),null);
    }
}
