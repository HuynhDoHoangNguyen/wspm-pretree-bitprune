package vn.edu.wspm.backend.dto.pattern;

import java.util.List;

public record PatternListResponse(String runId, String runStatus, ResultState resultState,
        String runPatternTotal, String filteredTotal, int page, int size,
        List<Summary> items) {
    public PatternListResponse { items = List.copyOf(items); }

    public enum ResultState { READY, NOT_READY, FAILED, UNVERIFIED }

    public record Summary(String patternId, String runId, String canonicalPattern,
            String supportAbs, Double supportRatio, Double weightedSupport,
            Double patternWeight, String patternWeightPolicy,
            int patternItemCount, int itemsetCount) { }
}
