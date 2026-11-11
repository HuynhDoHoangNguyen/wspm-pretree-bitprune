package vn.edu.wspm.backend.services.run.execution;

/** Count-based execution/retention bounds, not a per-job byte/heap guarantee. */
public record ExecutionLimits(int parallelism, int queueCapacity, long shutdownGraceMillis) {
    public ExecutionLimits {
        if (parallelism <= 0 || queueCapacity <= 0 || shutdownGraceMillis < 0) {
            throw new IllegalArgumentException("Execution workers/queue must be positive and shutdown grace nonnegative");
        }
        Math.addExact(parallelism, queueCapacity);
    }
    public int capacity() { return Math.addExact(parallelism, queueCapacity); }
}
