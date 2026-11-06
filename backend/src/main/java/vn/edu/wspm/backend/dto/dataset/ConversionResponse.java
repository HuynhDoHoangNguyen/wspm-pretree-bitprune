package vn.edu.wspm.backend.dto.dataset;

import java.time.Instant;

/** Public conversion projection; never includes storage references, attempt IDs or arbitrary report JSON. */
public record ConversionResponse(String snapshotId, String conversionStatus, String statisticsStatus,
        Instant startedAt, Instant completedAt, Instant failedAt, String failureCode, String artifactVersion,
        Statistics statistics, Artifact sequence, Artifact mapping) {
    public record Statistics(String sequenceCount, String singleItemsetSequenceCount, String multiItemsetSequenceCount,
            String itemsetCount, String distinctItemCount, String totalItemOccurrences,
            String minSequenceLength, String maxSequenceLength, double averageSequenceLength) {}
    public record Artifact(String sha256, String sizeBytes, String downloadUrl) {}
    @Override public String toString() { return "ConversionResponse[snapshotId=" + snapshotId + ", status=" + conversionStatus + "]"; }
}
