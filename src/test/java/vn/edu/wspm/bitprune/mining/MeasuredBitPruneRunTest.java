package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.tree.PrefixTree;

class MeasuredBitPruneRunTest {

    private BitPruneMiningResult createDummyResult() {
        PrefixTree tree = new PrefixTree();
        tree.freeze();
        BitPruneStatistics stats = new BitPruneStatistics(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new BitPruneMiningResult(tree, Collections.emptyMap(), stats);
    }

    private BitPruneAlgorithmMetrics createDummyMetrics() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        return new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);
    }

    @Test
    @DisplayName("Class must be an immutable Java record")
    void classIsRecord() {
        assertTrue(MeasuredBitPruneRun.class.isRecord());
    }

    @Test
    @DisplayName("Null result must be rejected")
    void nullResultRejected() {
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        var error = assertThrows(NullPointerException.class, () -> new MeasuredBitPruneRun(null, metrics));
        assertTrue(error.getMessage().contains("result must not be null"));
    }

    @Test
    @DisplayName("Null metrics must be rejected")
    void nullMetricsRejected() {
        BitPruneMiningResult result = createDummyResult();
        var error = assertThrows(NullPointerException.class, () -> new MeasuredBitPruneRun(result, null));
        assertTrue(error.getMessage().contains("metrics must not be null"));
    }

    @Test
    @DisplayName("Accessors return exact components")
    void accessorsReturnExactComponents() {
        BitPruneMiningResult result = createDummyResult();
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        var run = new MeasuredBitPruneRun(result, metrics);

        assertEquals(result, run.result());
        assertEquals(metrics, run.metrics());
    }

    @Test
    @DisplayName("Equality and hashCode behave as expected for record")
    void equalityAndHashCode() {
        BitPruneMiningResult result1 = createDummyResult();
        BitPruneAlgorithmMetrics metrics1 = createDummyMetrics();
        var run1 = new MeasuredBitPruneRun(result1, metrics1);
        var run2 = new MeasuredBitPruneRun(result1, metrics1);

        assertEquals(run1, run2);
        assertEquals(run1.hashCode(), run2.hashCode());

        var prep2 = new PhaseExecutionMetrics(15L, 100L, 120L, 130L);
        var dfs2 = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var metrics2 = new BitPruneAlgorithmMetrics(prep2, dfs2, 40L, 145L, 160L);
        var run3 = new MeasuredBitPruneRun(result1, metrics2);

        assertNotEquals(run1, run3);
    }

    @Test
    @DisplayName("toString contains component representation")
    void toStringRepresentation() {
        BitPruneMiningResult result = createDummyResult();
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        var run = new MeasuredBitPruneRun(result, metrics);

        String str = run.toString();
        assertNotNull(str);
        assertTrue(str.contains("MeasuredBitPruneRun"));
        assertTrue(str.contains("result="));
        assertTrue(str.contains("metrics="));
    }
}

