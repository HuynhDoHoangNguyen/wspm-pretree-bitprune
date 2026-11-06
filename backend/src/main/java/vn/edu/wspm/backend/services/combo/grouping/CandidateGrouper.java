package vn.edu.wspm.backend.services.combo.grouping;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.*;
import static vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.*;

/** Independent logical grouping. Does not rank groups, assign anchors, persist or publish. */
public final class CandidateGrouper {
    private static final Logger log=LoggerFactory.getLogger(CandidateGrouper.class);
    private static final BigInteger MAX_UNSIGNED=new BigInteger("18446744073709551615");
    private static final Comparator<String> UTF8=(a,b)->Arrays.compareUnsigned(
            a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<Candidate> PRIMARY=(a,b)->{
        int order=new BigInteger(b.source().supportAbs()).compareTo(new BigInteger(a.source().supportAbs()));
        if(order!=0) return order;
        double aw=a.source().patternWeight(),bw=b.source().patternWeight();
        if(aw!=bw) return aw>bw?-1:1; // signed zero compares equal; no epsilon/rounding
        order=UTF8.compare(a.source().canonicalPattern(),b.source().canonicalPattern());
        return order!=0?order:new BigInteger(a.patternId()).compareTo(new BigInteger(b.patternId()));
    };
    /** Only valid after finite metrics and policy validation; shared with IMP-09. */
    public static Comparator<Candidate> primaryOrder() { return PRIMARY; }

    public Result group(SessionResult session,Context context,WeightPolicyConfirmation approval) {
        var errors=new TreeSet<String>();
        if(session==null || !session.completed() || !session.sessionErrors().isEmpty()) return failure("SESSION_NOT_COMPLETED");
        if(!validContext(context)) return failure("EVALUATION_CONTEXT_INVALID");
        if(session.examined()!=session.patterns().size() || session.accepted()<0 || session.rejected()<0
                || (long)session.accepted()+session.rejected()!=session.examined()) return failure("SESSION_COUNTS_INCONSISTENT");
        var accepted=new ArrayList<Candidate>();int rejected=0;
        for(var result:session.patterns()) {
            if(result.decision()==Decision.REJECT) {
                rejected++;
                if(result.candidate()!=null) errors.add("INPUT_INVALID");
                continue;
            }
            Candidate c=result.candidate();
            if(result.decision()!=Decision.ACCEPT || c==null || !java.util.Objects.equals(result.patternId(),c.patternId())
                    || !result.reasons().isEmpty() || result.checks().size()!=Check.values().length
                    || result.checks().values().stream().anyMatch(v->v!=CheckStatus.PASS)) {
                errors.add("INPUT_INVALID");continue;
            }
            if(!context.runId().equals(c.runId()) || c.source()==null || !context.runId().equals(c.source().runId()))
                errors.add("SOURCE_RUN_MISMATCH");
            else if(!context.equals(Context.from(c))) errors.add("EVALUATION_CONTEXT_MISMATCH");
            else if(!validCandidate(c)) errors.add("INPUT_INVALID");
            accepted.add(c);
        }
        if(accepted.size()!=session.accepted() || rejected!=session.rejected()) errors.add("SESSION_COUNTS_INCONSISTENT");
        if(!errors.isEmpty()) return failure(errors);
        if(accepted.isEmpty()) return new Result(true,List.of(),List.of());

        // Detect conflicting repeats before metric/policy evaluation; never retain the first version silently.
        var unique=new HashMap<String,Candidate>();
        for(var candidate:accepted) {
            var old=unique.putIfAbsent(candidate.patternId(),candidate);
            if(old!=null && !old.equals(candidate)) errors.add("SOURCE_CONTENT_CONFLICT");
        }
        if(!errors.isEmpty()) return failure(errors);
        var policies=new HashSet<String>();
        for(var candidate:unique.values()) {
            var source=candidate.source();policies.add(source.patternWeightPolicy());
            if(!unsigned(source.supportAbs(),false) || source.supportRatio()==null || !Double.isFinite(source.supportRatio())
                    || source.supportRatio()<0 || source.supportRatio()>1 || source.weightedSupport()==null
                    || !Double.isFinite(source.weightedSupport()) || source.weightedSupport()<0) errors.add("SOURCE_METRICS_UNAVAILABLE");
            if(source.patternWeight()==null || !Double.isFinite(source.patternWeight())) errors.add("WEIGHT_UNAVAILABLE");
        }
        if(policies.size()!=1 || policies.stream().anyMatch(CandidateGrouper::blank)) errors.add("POLICY_MISMATCH");
        if(approval==null || blank(approval.policy()) || blank(approval.confirmationRef())) errors.add("POLICY_UNCONFIRMED");
        else if(policies.size()==1 && !policies.contains(approval.policy())) errors.add("POLICY_MISMATCH");
        if(!errors.isEmpty()) return failure(errors);

        var buckets=new HashMap<List<List<String>>,List<Candidate>>();
        for(var candidate:unique.values()) buckets.computeIfAbsent(normalized(candidate),key->new ArrayList<>()).add(candidate);
        var groups=new ArrayList<Group>();
        for(var entry:buckets.entrySet()) {
            var sources=entry.getValue();sources.sort(PRIMARY);
            var primary=sources.getFirst();var source=primary.source();var key=structureKey(entry.getKey());
            ComboKind kind=primary.itemsets().size()==1?ComboKind.SAME_ORDER_BUNDLE:
                    primary.itemsets().stream().anyMatch(s->s.items().size()>1)?ComboKind.MIXED_SEQUENCE:ComboKind.SEQUENTIAL_CROSS_SELL;
            var links=sources.stream().sorted(Comparator.comparing(c->new BigInteger(c.patternId())))
                    .map(c->new Source(c,c.patternId().equals(primary.patternId()))).toList();
            groups.add(new Group("DRAFT",context,key,kind,primary,links,primary.itemsets(),source.supportAbs(),
                    source.supportRatio(),source.patternWeight(),source.patternWeightPolicy(),approval.confirmationRef()));
        }
        // Structural enumeration only, deliberately unrelated to cross-group support ranking/Top-K.
        groups.sort(Comparator.comparing(g->g.structureKey().serialized(),UTF8));
        log.info("Candidate grouping completed: sources={}, groups={}",unique.size(),groups.size());
        return new Result(true,List.of(),groups);
    }

    private static List<List<String>> normalized(Candidate candidate) {
        return candidate.itemsets().stream().map(s->s.items().stream().map(Item::productId).sorted(UTF8).toList()).toList();
    }
    private static StructureKey structureKey(List<List<String>> groups) {
        // UTF-8 byte-length framing, not delimiter joining. Exact nested arrays remain the identity.
        var encoded=new StringBuilder("PRODUCT_ITEMSETS_LENGTH_V1|").append(groups.size()).append('|');
        for(var group:groups) {
            encoded.append(group.size()).append('|');
            for(String id:group) encoded.append(id.getBytes(StandardCharsets.UTF_8).length).append(':').append(id);
        }
        return new StructureKey("PRODUCT_ITEMSETS_LENGTH_V1",groups,encoded.toString());
    }
    private static boolean validCandidate(Candidate c) {
        var source=c.source();
        if(!"DRAFT".equals(c.status()) || !unsigned(c.patternId(),true) || !c.patternId().equals(source.patternId())
                || blank(source.canonicalPattern()) || !StandardCharsets.UTF_8.newEncoder().canEncode(source.canonicalPattern())
                || c.itemsets().isEmpty() || c.itemsets().size()!=source.itemsets().size()) return false;
        var products=new HashSet<String>();long setOrder=0,total=0;
        for(int i=0;i<c.itemsets().size();i++) {
            var group=c.itemsets().get(i);var original=source.itemsets().get(i);
            if(group.itemsetOrder()!=++setOrder || group.itemsetOrder()!=original.itemsetOrder()
                    || group.items().isEmpty() || group.items().size()!=original.items().size()) return false;
            long order=0;
            for(int j=0;j<group.items().size();j++) {
                var item=group.items().get(j);var originalItem=original.items().get(j);
                if(item.itemOrder()!=++order || item.itemOrder()!=originalItem.itemOrder() || item.sourceItemId()<=0
                        || item.sourceItemId()>Integer.MAX_VALUE
                        || item.sourceItemId()!=originalItem.itemId() || blank(item.productId())
                        || !StandardCharsets.UTF_8.newEncoder().canEncode(item.productId()) || !products.add(item.productId())) return false;
                total++;
            }
        }
        return total==source.patternItemCount() && setOrder==source.itemsetCount() && products.size()>=2 && products.size()<=4;
    }
    private static boolean validContext(Context c) {
        return c!=null && !blank(c.runId()) && !blank(c.snapshotId()) && !blank(c.evaluationPolicyVersion())
                && !blank(c.policyConfirmationRef()) && c.eligibilityMode()!=null && !blank(c.publishedEvidenceRef())
                && !blank(c.catalogSnapshotRef()) && c.evaluatedAt()!=null;
    }
    private static boolean unsigned(String raw,boolean positive) {
        return raw!=null && raw.matches(positive?"[1-9][0-9]{0,19}":"[0-9]{1,20}")
                && new BigInteger(raw).compareTo(MAX_UNSIGNED)<=0;
    }
    private static boolean blank(String s) { return s==null || s.isBlank(); }
    private static Result failure(String code) { return failure(java.util.Set.of(code)); }
    private static Result failure(java.util.Collection<String> codes) {
        log.warn("Candidate grouping blocked: {}",codes);
        return new Result(false,List.copyOf(codes),List.of());
    }
}
