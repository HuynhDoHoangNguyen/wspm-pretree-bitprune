package vn.edu.wspm.backend.dto.pattern;

import java.util.List;

public record PatternListResponse(String runId, String runStatus, ResultState resultState,
        String runPatternTotal, String filteredTotal, int page, int size,
        List<Summary> items, WeightFilter weightFilter) {
    public PatternListResponse { items = List.copyOf(items); }
    public PatternListResponse(String runId,String runStatus,ResultState resultState,
            String runPatternTotal,String filteredTotal,int page,int size,List<Summary> items) {
        this(runId,runStatus,resultState,runPatternTotal,filteredTotal,page,size,items,
                new WeightFilter(false,"POLICY_NOT_CONFIRMED",null));
    }
    public record WeightFilter(boolean enabled,String reason,String policy) { }

    public enum ResultState { READY, NOT_READY, FAILED, UNVERIFIED }

    public record Summary(String patternId, String runId, String canonicalPattern,
            String supportAbs, Double supportRatio, Double weightedSupport,
            Double patternWeight, String patternWeightPolicy,
            int patternItemCount, int itemsetCount) { }
}
