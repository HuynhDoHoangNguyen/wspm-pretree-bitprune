package vn.edu.wspm.backend.dto.run;

/** Informational projection of verified artifacts; never an execution authorization. */
public record MiningPreviewResponse(String snapshotId, String algorithm, String artifactVersion,
        String sequenceSha256, String mappingSha256, Statistics statistics,
        MinimumSupport minimumSupport, String executionEligibility) {
    public record Statistics(String sequenceCount, String singleItemsetSequenceCount,
            String multiItemsetSequenceCount, String itemsetCount, String distinctItemCount,
            String totalItemOccurrences, String minSequenceLength, String maxSequenceLength,
            double averageSequenceLength) {}
    public record MinimumSupport(String mode, double configuredValue, Double relativeFraction,
            double effectiveThreshold) {}
    @Override public String toString() { return "MiningPreviewResponse[snapshotId=" + snapshotId + "]"; }
}
