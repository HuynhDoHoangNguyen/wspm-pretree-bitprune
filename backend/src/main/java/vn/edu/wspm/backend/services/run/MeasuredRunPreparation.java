package vn.edu.wspm.backend.services.run;

import java.util.Objects;

/** Execution observation, separate from immutable mining configuration. */
public record MeasuredRunPreparation(PreparedRun input, long datasetIoNanos) {
    public MeasuredRunPreparation {
        Objects.requireNonNull(input);
        if (datasetIoNanos < 0) throw new IllegalArgumentException("Nonnegative input I/O required");
    }
    @Override public String toString() { return "MeasuredRunPreparation[redacted]"; }
}
