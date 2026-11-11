package vn.edu.wspm.bitprune.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;

class BitPruneAlgorithmMetricsTest {

    @Test
    @DisplayName("Class must be an immutable Java record")
    void classIsRecord() {
        assertTrue(BitPruneAlgorithmMetrics.class.isRecord());
    }

    @Test
    @DisplayName("Valid inputs produce expected record properties")
    void validValuesAccepted() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var metrics = new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);

        assertEquals(prep, metrics.preprocessing());
        assertEquals(dfs, metrics.dfsMining());
        assertEquals(35L, metrics.completeAlgorithmNanos());
        assertEquals(145L, metrics.heapAfterMiningBytes());
        assertEquals(160L, metrics.observedPeakHeapAlgorithmBytes());
    }

    @Test
    @DisplayName("Null preprocessing metrics must be rejected")
    void nullPreprocessingRejected() {
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        var error = assertThrows(NullPointerException.class,
                () -> new BitPruneAlgorithmMetrics(null, dfs, 35L, 145L, 160L));
        assertTrue(error.getMessage().contains("preprocessing metrics must not be null"));
    }

    @Test
    @DisplayName("Null DFS mining metrics must be rejected")
    void nullDfsMetricsRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);

        var error = assertThrows(NullPointerException.class,
                () -> new BitPruneAlgorithmMetrics(prep, null, 35L, 145L, 160L));
        assertTrue(error.getMessage().contains("DFS mining metrics must not be null"));
    }

    @Test
    @DisplayName("Negative complete algorithm duration must be rejected")
    void negativeDurationRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, -1L, 145L, 160L));
        assertTrue(error.getMessage().contains("complete algorithm nanoseconds must not be negative"));
    }

    @Test
    @DisplayName("Negative heap metrics must be rejected")
    void negativeHeapRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        var errorAfter = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 35L, -1L, 160L));
        assertTrue(errorAfter.getMessage().contains("heap after mining must not be negative"));

        var errorPeak = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, -1L));
        assertTrue(errorPeak.getMessage().contains("observed peak heap algorithm must not be negative"));
    }

    @Test
    @DisplayName("Complete algorithm duration less than sum of preprocessing and DFS must be rejected")
    void algorithmTimeSmallerThanSubphasesRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        // Required duration is 10 + 20 = 30
        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 29L, 145L, 160L));
        assertTrue(error.getMessage().contains("complete algorithm time must cover preprocessing and DFS mining"));

        // Exact boundary (30) is accepted
        var boundary = new BitPruneAlgorithmMetrics(prep, dfs, 30L, 145L, 160L);
        assertEquals(30L, boundary.completeAlgorithmNanos());

        // Above boundary (including coordination overhead) is accepted
        var withOverhead = new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);
        assertEquals(35L, withOverhead.completeAlgorithmNanos());
    }

    @Test
    @DisplayName("Algorithm peak heap not covering preprocessing peak must be rejected")
    void algorithmPeakNotCoveringPreprocessingPeakRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 170L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 169L));
        assertTrue(error.getMessage().contains("algorithm peak must cover preprocessing, DFS, and final result construction"));
    }

    @Test
    @DisplayName("Algorithm peak heap not covering DFS peak must be rejected")
    void algorithmPeakNotCoveringDfsPeakRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 180L);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 179L));
        assertTrue(error.getMessage().contains("algorithm peak must cover preprocessing, DFS, and final result construction"));
    }

    @Test
    @DisplayName("Algorithm peak heap not covering heap-after-mining must be rejected")
    void algorithmPeakNotCoveringHeapAfterMiningRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneAlgorithmMetrics(prep, dfs, 35L, 190L, 189L));
        assertTrue(error.getMessage().contains("algorithm peak must cover preprocessing, DFS, and final result construction"));
    }

    @Test
    @DisplayName("Overflow in subphase duration addition must be rejected by checked arithmetic")
    void overflowInDurationAdditionRejected() {
        var prepOverflow = new PhaseExecutionMetrics(Long.MAX_VALUE - 5L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(10L, 120L, 140L, 150L);

        assertThrows(ArithmeticException.class,
                () -> new BitPruneAlgorithmMetrics(prepOverflow, dfs, Long.MAX_VALUE, 145L, 160L));
    }

    @Test
    @DisplayName("Record equality, hashCode, and toString contracts")
    void equalityHashCodeAndToString() {
        var prep1 = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs1 = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var metrics1 = new BitPruneAlgorithmMetrics(prep1, dfs1, 35L, 145L, 160L);
        var metrics2 = new BitPruneAlgorithmMetrics(prep1, dfs1, 35L, 145L, 160L);

        var prepDiff = new PhaseExecutionMetrics(12L, 100L, 120L, 130L);
        var metricsDiffPrep = new BitPruneAlgorithmMetrics(prepDiff, dfs1, 35L, 145L, 160L);
        var dfsDiff = new PhaseExecutionMetrics(22L, 120L, 140L, 150L);
        var metricsDiffDfs = new BitPruneAlgorithmMetrics(prep1, dfsDiff, 35L, 145L, 160L);
        var metricsDiffNanos = new BitPruneAlgorithmMetrics(prep1, dfs1, 40L, 145L, 160L);
        var metricsDiffHeapAfter = new BitPruneAlgorithmMetrics(prep1, dfs1, 35L, 146L, 160L);
        var metricsDiffPeak = new BitPruneAlgorithmMetrics(prep1, dfs1, 35L, 145L, 165L);

        assertEquals(metrics1, metrics2);
        assertEquals(metrics1.hashCode(), metrics2.hashCode());
        assertEquals(metrics1, metrics1);

        assertNotEquals(metrics1, null);
        assertNotEquals(metrics1, "other string");
        assertNotEquals(metrics1, metricsDiffPrep);
        assertNotEquals(metrics1, metricsDiffDfs);
        assertNotEquals(metrics1, metricsDiffNanos);
        assertNotEquals(metrics1, metricsDiffHeapAfter);
        assertNotEquals(metrics1, metricsDiffPeak);

        String str = metrics1.toString();
        assertNotNull(str);
        assertTrue(str.contains("BitPruneAlgorithmMetrics"));
        assertTrue(str.contains("completeAlgorithmNanos=35"));
        assertTrue(str.contains("heapAfterMiningBytes=145"));
        assertTrue(str.contains("observedPeakHeapAlgorithmBytes=160"));
    }
}

