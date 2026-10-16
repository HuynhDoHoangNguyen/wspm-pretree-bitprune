package vn.edu.wspm.experiments;

import java.util.Objects;
import vn.edu.wspm.measurement.WspmExecutionMetrics;
import vn.edu.wspm.mining.WspmMiningResult;

/** Immutable output of one complete standalone WSPM_PreTree execution. */
record WspmExecutionRun(WspmMiningResult miningResult, WspmExecutionMetrics metrics) {

    WspmExecutionRun {
        Objects.requireNonNull(miningResult, "mining result must not be null");
        Objects.requireNonNull(metrics, "execution metrics must not be null");
    }
}
