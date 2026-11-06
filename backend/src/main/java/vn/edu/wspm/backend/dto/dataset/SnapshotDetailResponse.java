package vn.edu.wspm.backend.dto.dataset;

import java.time.Instant;

/** Explicit read model; no entity, filesystem path or arbitrary report JSON is serialized. */
public record SnapshotDetailResponse(
        String snapshotId, String sourceType, String sourceName, String validationStatus,
        String conversionStatus, String statisticsStatus, String rawSha256, String rawSizeBytes,
        String sequenceCount, Instant fromTime, Instant toTime, Instant createdAt,
        String rawAvailability, Provenance provenance) {
    public record Provenance(String contractVersion, String acquisition, String sourceTimeZone,
                             String timeField, String statusPolicy) {}
    @Override public String toString() { return "SnapshotDetailResponse[snapshotId=" + snapshotId + "]"; }
}
