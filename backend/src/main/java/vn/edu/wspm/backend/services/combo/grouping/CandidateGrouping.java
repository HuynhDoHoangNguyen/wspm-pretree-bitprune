package vn.edu.wspm.backend.services.combo.grouping;

import java.time.Instant;
import java.util.List;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.Candidate;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.EligibilityMode;
import vn.edu.wspm.backend.services.combo.evaluation.CandidateEvaluation.Itemset;

public final class CandidateGrouping {
    private CandidateGrouping() { }
    public enum ComboKind { SAME_ORDER_BUNDLE, SEQUENTIAL_CROSS_SELL, MIXED_SEQUENCE }
    public record Context(String runId,String snapshotId,String evaluationPolicyVersion,
            String policyConfirmationRef,EligibilityMode eligibilityMode,String publishedEvidenceRef,
            String catalogSnapshotRef,Instant evaluatedAt) {
        public static Context from(Candidate c) {
            return new Context(c.runId(),c.snapshotId(),c.evaluationPolicyVersion(),c.policyConfirmationRef(),
                    c.eligibilityMode(),c.publishedEvidenceRef(),c.catalogSnapshotRef(),c.evaluatedAt());
        }
    }
    /** Supplied only by trusted server integration; no production registry or fallback exists here. */
    public record WeightPolicyConfirmation(String policy,String confirmationRef) { }
    public record StructureKey(String version,List<List<String>> productItemsets,String serialized) {
        public StructureKey { productItemsets=productItemsets.stream().map(List::copyOf).toList(); }
    }
    public record Source(Candidate candidate,boolean primary) { }
    public record Group(String status,Context context,StructureKey structureKey,ComboKind comboKind,
            Candidate primary,List<Source> sources,List<Itemset> itemsets,String supportAbs,
            Double supportRatio,Double patternWeight,String patternWeightPolicy,String weightConfirmationRef) {
        public Group { sources=List.copyOf(sources);itemsets=List.copyOf(itemsets); }
    }
    public record Result(boolean completed,List<String> errors,List<Group> groups) {
        public Result { errors=List.copyOf(errors);groups=List.copyOf(groups); }
    }
}
