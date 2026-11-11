package vn.edu.wspm.backend.services.combo.review;

import java.util.ArrayList;
import java.util.List;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;
import vn.edu.wspm.backend.services.combo.admin.ComboReviewReadiness;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Shared read/submit eligibility, independent of the unresolved auth/token/storage adapters. */
public final class ComboReviewRules {
    public Assessment assess(Bundle bundle,ComboReviewAuthority.Check check){
        var assessment=assess(bundle,check.policy(),check.evidence());
        if("DRAFT".equals(bundle.row().comboStatus())&&"CATALOG_PRICE_OR_ELIGIBILITY_CHANGED".equals(check.binding())){
            var issues=new ArrayList<>(assessment.blockingIssues());issues.add(new Issue("CATALOG_PRICE_OR_ELIGIBILITY_CHANGED"));
            return new Assessment(assessment.primaryPatternId(),assessment.sourceCount(),List.of(),issues,assessment.warnings());
        }
        return assessment;
    }
    public Assessment assess(Bundle bundle,Policy policy,Evidence evidence) {
        var base=new ComboReviewReadiness().diagnostics(bundle);
        var issues=new ArrayList<>(base.blockingIssues());
        if("DRAFT".equals(bundle.row().comboStatus())) {
            if(policy==null || !policy.confirmed()) issues.add(new Issue("REVIEW_POLICY_UNCONFIRMED"));
            if(evidence==null || !evidence.dataAndFreshnessVerified()) issues.add(new Issue("REVIEW_EVIDENCE_UNVERIFIED"));
            if(evidence==null || !evidence.writerLockProtocolVerified()) issues.add(new Issue("WRITER_LOCK_PROTOCOL_UNCONFIRMED"));
            if(!"SUCCEEDED".equals(bundle.row().runStatus())) issues.add(new Issue("RUN_NOT_REVIEWABLE"));
            if(!issues.isEmpty()) issues.add(new Issue("ACTION_ELIGIBILITY_UNVERIFIED"));
        }
        var actions="DRAFT".equals(bundle.row().comboStatus()) && issues.isEmpty() ? List.of("APPROVE","REJECT"):List.<String>of();
        return new Assessment(base.primaryPatternId(),base.sourceCount(),actions,issues,base.warnings());
    }
}
