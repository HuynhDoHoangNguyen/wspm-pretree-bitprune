package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.tree.PrefixTree;

class MeasuredBitPruneRunTest {

    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    private BitPruneMiningResult createDummyResult(long sequenceCount) {
        PrefixTree tree = new PrefixTree();
        tree.freeze();
        BitPruneStatistics stats = new BitPruneStatistics(
                sequenceCount, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new BitPruneMiningResult(tree, Collections.emptyMap(), stats);
    }

    private BitPruneAlgorithmMetrics createDummyMetrics() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        return new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);
    }

    private BitPruneRunMetadata createDummyZeroMetadata() {
        var minsup = resolver.resolve(MinimumSupport.absolute(1.0), 0);
        return new BitPruneRunMetadata(0, OptionalDouble.empty(), minsup);
    }

    private BitPruneRunMetadata createDummyPositiveMetadata(int sequenceCount) {
        var minsup = resolver.resolve(MinimumSupport.percentage(14.0), sequenceCount);
        return new BitPruneRunMetadata(8, OptionalDouble.of(0.18182), minsup);
    }

    @Test
    @DisplayName("Class must be an immutable Java record")
    void classIsRecord() {
        assertTrue(MeasuredBitPruneRun.class.isRecord());
    }

    @Test
    @DisplayName("Null result, metrics, or metadata must be rejected")
    void nullArgumentsRejected() {
        BitPruneMiningResult result = createDummyResult(0);
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        BitPruneRunMetadata metadata = createDummyZeroMetadata();

        var err1 = assertThrows(NullPointerException.class,
                () -> new MeasuredBitPruneRun(null, metrics, metadata));
        assertTrue(err1.getMessage().contains("result must not be null"));

        var err2 = assertThrows(NullPointerException.class,
                () -> new MeasuredBitPruneRun(result, null, metadata));
        assertTrue(err2.getMessage().contains("metrics must not be null"));

        var err3 = assertThrows(NullPointerException.class,
                () -> new MeasuredBitPruneRun(result, metrics, null));
        assertTrue(err3.getMessage().contains("metadata must not be null"));
    }

    @Test
    @DisplayName("Sequence count mismatch between metadata and result statistics must be rejected")
    void sequenceCountMismatchRejected() {
        BitPruneMiningResult result7 = createDummyResult(7);
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        BitPruneRunMetadata metadata5 = createDummyPositiveMetadata(5);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new MeasuredBitPruneRun(result7, metrics, metadata5));
        assertTrue(error.getMessage().contains("metadata sequence count (5) must equal result statistics sequence count (7)"));
    }

    @Test
    @DisplayName("Accessors return exact components")
    void accessorsReturnExactComponents() {
        BitPruneMiningResult result = createDummyResult(7);
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        BitPruneRunMetadata metadata = createDummyPositiveMetadata(7);
        var run = new MeasuredBitPruneRun(result, metrics, metadata);

        assertEquals(result, run.result());
        assertEquals(metrics, run.metrics());
        assertEquals(metadata, run.metadata());
    }

    @Test
    @DisplayName("Equality and hashCode behave as expected for record")
    void equalityAndHashCode() {
        BitPruneMiningResult result1 = createDummyResult(7);
        BitPruneAlgorithmMetrics metrics1 = createDummyMetrics();
        BitPruneRunMetadata metadata1 = createDummyPositiveMetadata(7);

        var run1 = new MeasuredBitPruneRun(result1, metrics1, metadata1);
        var run2 = new MeasuredBitPruneRun(result1, metrics1, metadata1);

        assertEquals(run1, run2);
        assertEquals(run1.hashCode(), run2.hashCode());

        var prep2 = new PhaseExecutionMetrics(15L, 100L, 120L, 130L);
        var dfs2 = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var metrics2 = new BitPruneAlgorithmMetrics(prep2, dfs2, 40L, 145L, 160L);
        var run3 = new MeasuredBitPruneRun(result1, metrics2, metadata1);

        assertNotEquals(run1, run3);

        BitPruneRunMetadata metadataDiff = new BitPruneRunMetadata(
                9,
                OptionalDouble.of(0.20),
                resolver.resolve(MinimumSupport.percentage(14.0), 7));
        var run4 = new MeasuredBitPruneRun(result1, metrics1, metadataDiff);

        assertNotEquals(run1, run4);
    }

    @Test
    @DisplayName("toString contains component representation")
    void toStringRepresentation() {
        BitPruneMiningResult result = createDummyResult(7);
        BitPruneAlgorithmMetrics metrics = createDummyMetrics();
        BitPruneRunMetadata metadata = createDummyPositiveMetadata(7);
        var run = new MeasuredBitPruneRun(result, metrics, metadata);

        String str = run.toString();
        assertNotNull(str);
        assertTrue(str.contains("MeasuredBitPruneRun"));
        assertTrue(str.contains("result="));
        assertTrue(str.contains("metrics="));
        assertTrue(str.contains("metadata="));
    }
}
