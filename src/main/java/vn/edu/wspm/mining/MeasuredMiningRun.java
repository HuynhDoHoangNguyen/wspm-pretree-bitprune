package vn.edu.wspm.mining;

import java.util.Objects;
import vn.edu.wspm.measurement.WspmAlgorithmMetrics;

/** Completed mining result paired with immutable algorithm-scope measurements. */
public record MeasuredMiningRun(WspmMiningResult result, WspmAlgorithmMetrics metrics) {

    public MeasuredMiningRun {
        Objects.requireNonNull(result, "mining result must not be null");
        Objects.requireNonNull(metrics, "algorithm metrics must not be null");
    }
}
