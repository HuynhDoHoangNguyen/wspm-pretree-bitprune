package vn.edu.wspm.backend.services.combo.review;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.Audit;
import vn.edu.wspm.backend.repositories.ComboAdminReader.Bundle;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Pure decision planner. READY_TO_APPLY is a proposal, never a committed result. */
public final class ComboReviewDecider {
    private final Clock clock;
    private final ComboReviewRules rules=new ComboReviewRules();
    public ComboReviewDecider(Clock clock) { this.clock=Objects.requireNonNull(clock); }
    public Result decide(Bundle bundle,Command command,Reviewer reviewer,Policy policy,Evidence evidence,TokenVerification token) {
        if(reviewer==null || !reviewer.authenticated()) return error("UNAUTHORIZED");
        if(!reviewer.admin()) return error("FORBIDDEN");
        if(!validId(reviewer.mappedUserAccountId())) return error("REVIEWER_IDENTITY_INVALID");
        if(!validCommand(command)) return error("INVALID_REVIEW_COMMAND");
        // Do not silently approve the proposal's note and DRAFT-only policies for production.
        if(policy==null || !policy.confirmed()) return error("REVIEW_POLICY_UNCONFIRMED");
        String note=command.reviewNote()==null?null:command.reviewNote().strip();
        if(note!=null && note.isEmpty()) note=null;
        if("REJECT".equals(command.decision()) && note==null) return error("REVIEW_NOTE_REQUIRED");
        if(note!=null && (note.codePointCount(0,note.length())>1000 || !StandardCharsets.UTF_8.newEncoder().canEncode(note)
                || note.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t'))) return error("INVALID_REVIEW_NOTE");
        if(bundle==null) return error("COMBO_NOT_FOUND");
        var row=bundle.row();String target="APPROVE".equals(command.decision())?"APPROVED":"REJECTED";
        if("APPROVED".equals(row.comboStatus()) || "REJECTED".equals(row.comboStatus())) {
            var audit=row.review();
            if(audit==null || !validId(audit.reviewedBy()) || audit.reviewedAt()==null
                    || "REJECTED".equals(row.comboStatus()) && (audit.reviewNote()==null || audit.reviewNote().isBlank())) return error("REVIEW_AUDIT_INCONSISTENT");
            // No token refresh, evidence re-evaluation or audit rewrite on a terminal no-op.
            return new Result(target.equals(row.comboStatus())?"ALREADY_DECIDED":"CONFLICT",row.comboStatus(),audit,
                    target.equals(row.comboStatus())?List.of():List.of("REVIEW_DECISION_CONFLICT"),null);
        }
        if(!"DRAFT".equals(row.comboStatus())) return new Result("CONFLICT",row.comboStatus(),row.review(),List.of("COMBO_STATE_NOT_REVIEWABLE"),null);
        var assessment=rules.assess(bundle,policy,evidence);
        if(assessment.allowedActions().isEmpty()) return new Result("BLOCKED",row.comboStatus(),row.review(),assessment.blockingIssues().stream().map(i->i.code()).toList(),null);
        if(command.expectedReviewToken()==null || command.expectedReviewToken().isBlank() || token==null || token==TokenVerification.UNVERIFIED) return error("REVIEW_TOKEN_UNVERIFIED");
        if(token==TokenVerification.STALE) return error("STALE_REVIEW_DATA");
        var instant=clock.instant().truncatedTo(ChronoUnit.MICROS);
        int year=instant.atOffset(ZoneOffset.UTC).getYear();if(year<1000 || year>9999) return error("SERVER_TIME_OUT_OF_RANGE");
        String time=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC).format(instant);
        var proposed=new Audit(reviewer.mappedUserAccountId(),null,time,note);
        return new Result("READY_TO_APPLY",row.comboStatus(),row.review(),List.of(),new ProposedChange("DRAFT",target,proposed));
    }
    public static boolean validCommand(Command c) { return c!=null && ("APPROVE".equals(c.decision()) || "REJECT".equals(c.decision())); }
    public static boolean validId(String id) { return id!=null && !id.isBlank() && id.codePointCount(0,id.length())<=40
            && id.indexOf('\0')<0 && StandardCharsets.UTF_8.newEncoder().canEncode(id); }
    private static Result error(String code) { return new Result("BLOCKED",null,null,List.of(code),null); }
}
