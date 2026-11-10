package vn.edu.wspm.backend.services.run.execution;

public record LifecycleLimits(long leaseMillis, long heartbeatMillis, int recoveryBatch) {
    public LifecycleLimits {
        if (heartbeatMillis <= 0 || leaseMillis < Math.multiplyExact(heartbeatMillis, 3)
                || leaseMillis > Long.MAX_VALUE / 1000 || recoveryBatch <= 0) {
            throw new IllegalArgumentException("Lease must cover at least three positive heartbeat intervals; batch must be positive");
        }
    }
}
