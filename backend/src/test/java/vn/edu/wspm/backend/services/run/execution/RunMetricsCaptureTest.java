package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;

class RunMetricsCaptureTest {
    @ParameterizedTest @ValueSource(longs = {0, 1, 999999, 1000000, 1000001, Long.MAX_VALUE})
    void independentFloorConversionRetainsAbsenceAndDoesNotOverflow(long nanos) {
        var capture = new RunMetricsCapture(0, 0, nanos, nanos, nanos, Long.MAX_VALUE, 0, Long.MAX_VALUE);
        var db = capture.persisted();
        assertEquals(nanos / 1_000_000, db.dfsMiningMs()); assertEquals(nanos / 1_000_000, db.completeAlgorithmMs());
        assertEquals(nanos / 1_000_000, db.totalExecutionMs()); assertNull(db.outputIoMs());
        assertEquals(0L, db.patternCount()); assertEquals(Long.MAX_VALUE, db.peakSampledJvmHeapBytes());
    }
    @Test void nativeMetricsAndCountersArePreservedForBothActualEngines() throws Exception {
        var input = ExecutionTestInputs.input();
        for (var algorithm : MiningAlgorithm.values()) {
            var request = new MiningEngineRequest(algorithm, input.engineRequest().database(), input.engineRequest().minimumSupport());
            var result = new CoreMiningEngineAdapter(new RunConfigurationValidityGate()).execute(request);
            var supports = result.supports();
            var capture = RunMetricsCapture.from(result, 777, result.metrics().completeAlgorithmNanos() + 1777);
            assertEquals(result.metrics().preprocessing().elapsedNanos(), capture.preprocessingNanos());
            assertEquals(result.metrics().dfsMining().elapsedNanos(), capture.dfsMiningNanos());
            assertEquals(result.metrics().completeAlgorithmNanos(), capture.completeAlgorithmNanos());
            assertEquals(result.metrics().observedPeakHeapAlgorithmBytes(), capture.peakSampledJvmHeapBytes());
            assertEquals(result.patternCount(), capture.patternCount()); assertSame(supports, result.supports());
            if (result.statistics() instanceof MiningEngineResult.PreTreeStatistics pre)
                assertEquals(pre.original().candidateCount(), capture.candidateCount());
            else assertEquals(((MiningEngineResult.BitPruneStatisticsView) result.statistics()).original().generatedExtensions(), capture.candidateCount());
        }
    }
    @Test void negativeAndNoncontainedMetricsAreRejectedWithoutAdditionOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(-1, 0, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(0, 0, 0, 0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(0, 0, 0, 0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(0, 0, 0, 0, 0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(0, 2, 3, 4, 5, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(3, 0, 0, 4, 6, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(0, Long.MAX_VALUE, 1, Long.MAX_VALUE, Long.MAX_VALUE, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RunMetricsCapture(1, 0, 0, Long.MAX_VALUE, Long.MAX_VALUE, 0, 0, 0));
    }
}
