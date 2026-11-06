package vn.edu.wspm.backend.dto.run;

import java.util.List;

/** Narrow persisted run selector, not an execution/readiness certificate. */
public record RunListResponse(int page, int size, String total, List<Summary> items) {
    public RunListResponse { items = List.copyOf(items); }
    public record Summary(String runId, String snapshotId, String algorithm, String status,
            String createdAt, String sequenceCount, List<String> issues) {
        public Summary { issues = List.copyOf(issues); }
    }
}
