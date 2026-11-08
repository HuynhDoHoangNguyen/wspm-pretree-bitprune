package vn.edu.wspm.backend.dto.run;

import java.time.Instant;

/** Creation snapshot, not a live status read. Accepted does not mean mining succeeded. */
public record RunAcceptedResponse(String runId, String snapshotId, String algorithm,
                                  String status, Instant createdAt) {}
