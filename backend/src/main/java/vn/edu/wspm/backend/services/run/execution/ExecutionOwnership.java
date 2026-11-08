package vn.edu.wspm.backend.services.run.execution;

import java.util.UUID;

/** Durable fencing identity, separate from the in-memory outcome acknowledgement token. */
public record ExecutionOwnership(String ownerId, String attemptId, long leaseMillis) {
    public ExecutionOwnership {
        UUID.fromString(ownerId); UUID.fromString(attemptId);
        if (leaseMillis <= 0 || leaseMillis > Long.MAX_VALUE / 1000) throw new IllegalArgumentException("Invalid execution lease");
    }
    @Override public String toString() { return "ExecutionOwnership[redacted]"; }
}
