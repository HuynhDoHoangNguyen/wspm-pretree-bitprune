package vn.edu.wspm.backend.services.combo.ranking;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.wspm.backend.services.combo.ranking.ComboPreview.Result;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouper;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping;
import vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;
import static vn.edu.wspm.backend.services.combo.ranking.ComboPreview.*;

/** Pure DRAFT preview; no storefront visibility, stock check, persistence or implicit approval. */
public final class ComboPreviewRanker {
    private static final Logger log=LoggerFactory.getLogger(ComboPreviewRanker.class);
    public Result preview(CandidateGrouping.Result input,Context context,String anchorProductId,Configuration config) {
        var errors=new TreeSet<String>();
        if(input==null || !input.completed() || !input.errors().isEmpty()) errors.add("GROUPING_NOT_COMPLETED");
        boolean all=context!=null&&vn.edu.wspm.backend.services.combo.generation.ProductSelection.flexiblePolicy(context.evaluationPolicyVersion())&&blank(anchorProductId);
        if(!all&&(blank(anchorProductId) || anchorProductId.codePointCount(0,anchorProductId.length())>40
                || !StandardCharsets.UTF_8.newEncoder().canEncode(anchorProductId))) errors.add("INVALID_ANCHOR");
        if(config==null) errors.add("CONFIGURATION_REQUIRED");
        else {
            if(!"SUPPORT_THEN_PATTERN_WEIGHT_V1".equals(config.rankingPolicy())) errors.add("UNSUPPORTED_RANKING_POLICY");
            if(blank(config.rankingConfirmationRef())) errors.add("RANKING_POLICY_UNCONFIRMED");
            if(!"FIRST_ITEMSET_V1".equals(config.anchorPolicy())) errors.add("UNSUPPORTED_ANCHOR_POLICY");
            if(blank(config.anchorConfirmationRef())) errors.add("ANCHOR_POLICY_UNCONFIRMED");
            if(config.weightPolicy()==null || blank(config.weightPolicy().policy()) || blank(config.weightPolicy().confirmationRef()))
                errors.add("POLICY_UNCONFIRMED");
            if(config.k()==null || config.k().signum()<=0) errors.add("INVALID_K");
            if(config.approvedKCap()!=null) {
                if(config.approvedKCap().signum()<=0) errors.add("INVALID_K_CAP");
                if(blank(config.kCapConfirmationRef())) errors.add("K_CAP_UNCONFIRMED");
                if(config.k()!=null && config.k().compareTo(config.approvedKCap())>0) errors.add("INVALID_K");
            }
        }
        if(!errors.isEmpty()) return failure(errors,anchorProductId,context,config);

        // Reuse IMP-08 validation before anchor filtering. Invalid off-anchor groups
        // must not disappear and turn a malformed session into a successful preview.
        var sourceRows=new ArrayList<PatternResult>();
        var checks=new EnumMap<Check,CheckStatus>(Check.class);for(var check:Check.values()) checks.put(check,CheckStatus.PASS);
        for(var group:input.groups()) {
            if(group.context()==null || !group.context().equals(context)) errors.add("EVALUATION_CONTEXT_MISMATCH");
            for(var source:group.sources()) {
                if(source.candidate()==null) errors.add("INPUT_INVALID");
                else sourceRows.add(new PatternResult(source.candidate().patternId(),Decision.ACCEPT,List.of(),checks,source.candidate()));
            }
        }
        if(!errors.isEmpty()) return failure(errors,anchorProductId,context,config);
        var session=new SessionResult(true,List.of(),sourceRows.size(),sourceRows.size(),0,sourceRows);
        var verified=new CandidateGrouper().group(session,context,config.weightPolicy());
        if(!verified.completed()) return failure(verified.errors(),anchorProductId,context,config);
        var expected=new java.util.HashMap<StructureKey,Group>();
        for(var group:verified.groups()) expected.put(group.structureKey(),group);
        var seen=new java.util.HashSet<StructureKey>();
        for(var group:input.groups()) {
            if(!seen.add(group.structureKey()) || !group.equals(expected.get(group.structureKey()))) errors.add("GROUP_PROVENANCE_INVALID");
        }
        if(input.groups().size()!=expected.size()) errors.add("GROUP_PROVENANCE_INVALID");
        if(!errors.isEmpty()) return failure(errors,anchorProductId,context,config);

        var eligibility=new ArrayList<GroupEligibility>();var eligible=new ArrayList<Group>();
        // Enumerate flags in IMP-08 structural order, not query anchor/rank order.
        for(var group:verified.groups()) {
            var flags=new ArrayList<AnchorFlag>();
            long first=group.itemsets().getFirst().itemsetOrder();
            for(var set:group.itemsets()) for(var item:set.items()) flags.add(new AnchorFlag(set.itemsetOrder(),item.itemOrder(),
                    item.productId(),group.comboKind()==ComboKind.SAME_ORDER_BUNDLE || set.itemsetOrder()==first));
            eligibility.add(new GroupEligibility(group,flags));
            // Category-selection preview filters membership across the whole Combo.
            // Structural anchor flags remain first-itemset facts for persistence/review.
            boolean categoryView=vn.edu.wspm.backend.services.combo.generation.CategorySelection.VERSION.equals(context.evaluationPolicyVersion());
            if(all||flags.stream().anyMatch(flag->(categoryView||flag.anchorEligible()) && flag.productId().equals(anchorProductId))) eligible.add(group);
        }
        eligible.sort((a,b)->CandidateGrouper.primaryOrder().compare(a.primary(),b.primary()));
        int limit=config.k().min(BigInteger.valueOf(eligible.size())).intValueExact();
        var ranked=new ArrayList<RankedGroup>();
        for(int i=0;i<limit;i++) ranked.add(new RankedGroup(i+1,eligible.get(i)));
        log.debug("Combo preview completed: eligible={}, returned={}",eligible.size(),ranked.size());
        return new Result(true,List.of(),anchorProductId,context,config,eligible.size(),ranked,eligibility);
    }
    private static boolean blank(String value) { return value==null || value.isBlank(); }
    private static Result failure(java.util.Collection<String> errors,String anchor,Context context,Configuration config) {
        log.warn("Combo preview blocked: {}",errors);
        return new Result(false,List.copyOf(errors),anchor,context,config,0,List.of(),List.of());
    }
}
