package vn.edu.wspm.backend.dto.dataset;

import java.time.Instant;

/** Public acquisition metadata; internal artifact references and placeholder counts stay private. */
public record DatasetSnapshotResponse(
        String snapshotId, String sourceType, String sourceName, String validationStatus,
        String conversionStatus, String statisticsStatus, String rawSha256, String rawSizeBytes,
        String sequenceCount, Instant fromTime, Instant toTime, Instant createdAt) {
    @Override public String toString() {
        return "DatasetSnapshotResponse[snapshotId=" + snapshotId + ", metadata=redacted]";
    }
}
