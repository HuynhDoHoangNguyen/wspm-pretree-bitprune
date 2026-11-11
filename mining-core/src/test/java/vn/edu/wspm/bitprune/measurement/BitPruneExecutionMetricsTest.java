package vn.edu.wspm.bitprune.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;

class BitPruneExecutionMetricsTest {

    private BitPruneAlgorithmMetrics createSampleAlgorithmMetrics() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        return new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);
    }

    @Test
    @DisplayName("Class must be an immutable Java record")
    void classIsRecord() {
        assertTrue(BitPruneExecutionMetrics.class.isRecord());
    }

    @Test
    @DisplayName("Output I/O absent returns 0 nanoseconds")
    void outputIoAbsentReturnsZeroNanos() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();
        var execution = new BitPruneExecutionMetrics(
                datasetIo, algorithm, Optional.empty(), 60L, 200L, 5L);

        assertEquals(0L, execution.outputIoNanos());
        assertTrue(execution.outputIo().isEmpty());
    }

    @Test
    @DisplayName("Output I/O present returns exact duration")
    void outputIoPresentReturnsDuration() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();
        var outputIo = new PhaseExecutionMetrics(12L, 145L, 148L, 155L);
        var execution = new BitPruneExecutionMetrics(
                datasetIo, algorithm, Optional.of(outputIo), 70L, 200L, 5L);

        assertEquals(12L, execution.outputIoNanos());
        assertTrue(execution.outputIo().isPresent());
        assertEquals(outputIo, execution.outputIo().get());
    }

    @Test
    @DisplayName("Null components must be rejected")
    void nullComponentsRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();
        var outputIo = Optional.of(new PhaseExecutionMetrics(12L, 145L, 148L, 155L));

        assertThrows(NullPointerException.class,
                () -> new BitPruneExecutionMetrics(null, algorithm, outputIo, 70L, 200L, 5L));
        assertThrows(NullPointerException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, null, outputIo, 70L, 200L, 5L));
        assertThrows(NullPointerException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, null, 70L, 200L, 5L));
    }

    @Test
    @DisplayName("Negative total execution duration must be rejected")
    void negativeTotalDurationRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), -1L, 200L, 5L));
        assertTrue(error.getMessage().contains("total execution nanoseconds must not be negative"));
    }

    @Test
    @DisplayName("Negative total peak heap must be rejected")
    void negativeTotalPeakHeapRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();

        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), 60L, -1L, 5L));
        assertTrue(error.getMessage().contains("observed peak heap total must not be negative"));
    }

    @Test
    @DisplayName("Total execution time smaller than sum of sub-scopes must be rejected")
    void totalDurationSmallerThanSubscopesRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics(); // completeAlgorithmNanos = 35L
        // Without output: 15 + 35 = 50 required
        var errorNoOut = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), 49L, 200L, 5L));
        assertTrue(errorNoOut.getMessage().contains("total execution time must cover dataset I/O, algorithm, and output I/O"));

        var boundaryNoOut = new BitPruneExecutionMetrics(
                datasetIo, algorithm, Optional.empty(), 50L, 200L, 5L);
        assertEquals(50L, boundaryNoOut.totalExecutionNanos());

        // With output: 15 + 35 + 12 = 62 required
        var outputIo = new PhaseExecutionMetrics(12L, 145L, 148L, 155L);
        var errorWithOut = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.of(outputIo), 61L, 200L, 5L));
        assertTrue(errorWithOut.getMessage().contains("total execution time must cover dataset I/O, algorithm, and output I/O"));

        var boundaryWithOut = new BitPruneExecutionMetrics(
                datasetIo, algorithm, Optional.of(outputIo), 62L, 200L, 5L);
        assertEquals(62L, boundaryWithOut.totalExecutionNanos());

        var withCoordinationOverhead = new BitPruneExecutionMetrics(
                datasetIo, algorithm, Optional.of(outputIo), 75L, 200L, 5L);
        assertEquals(75L, withCoordinationOverhead.totalExecutionNanos());
    }

    @Test
    @DisplayName("Total peak heap not covering all sub-scope peaks must be rejected")
    void totalPeakNotCoveringSubscopesRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 180L);
        var algorithm = createSampleAlgorithmMetrics(); // observedPeakHeapAlgorithmBytes = 160L
        var outputIo = new PhaseExecutionMetrics(12L, 145L, 148L, 190L);

        // Does not cover dataset I/O peak (180)
        var errorDataset = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), 60L, 179L, 5L));
        assertTrue(errorDataset.getMessage().contains("total peak must cover every measured scope"));

        // Does not cover algorithm peak (160)
        var lowDatasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var errorAlgo = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(lowDatasetIo, algorithm, Optional.empty(), 60L, 159L, 5L));
        assertTrue(errorAlgo.getMessage().contains("total peak must cover every measured scope"));

        // Does not cover output I/O peak (190)
        var errorOutput = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(lowDatasetIo, algorithm, Optional.of(outputIo), 70L, 189L, 5L));
        assertTrue(errorOutput.getMessage().contains("total peak must cover every measured scope"));
    }

    @Test
    @DisplayName("Sampling interval zero or negative must be rejected")
    void samplingIntervalZeroOrNegativeRejected() {
        var datasetIo = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm = createSampleAlgorithmMetrics();

        var errorZero = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), 60L, 200L, 0L));
        assertTrue(errorZero.getMessage().contains("sampling interval must be positive"));

        var errorNeg = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneExecutionMetrics(datasetIo, algorithm, Optional.empty(), 60L, 200L, -5L));
        assertTrue(errorNeg.getMessage().contains("sampling interval must be positive"));
    }

    @Test
    @DisplayName("Duration addition overflow must be rejected by checked arithmetic")
    void overflowWhenAddingDurationsRejected() {
        var prep = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var dfs = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var algo = new BitPruneAlgorithmMetrics(prep, dfs, Long.MAX_VALUE - 5L, 145L, 160L);

        var datasetIoOverflow = new PhaseExecutionMetrics(10L, 50L, 90L, 110L);
        assertThrows(ArithmeticException.class,
                () -> new BitPruneExecutionMetrics(datasetIoOverflow, algo, Optional.empty(), Long.MAX_VALUE, 200L, 5L));

        var algoSmall = new BitPruneAlgorithmMetrics(prep, dfs, 35L, 145L, 160L);
        var datasetIoLarge = new PhaseExecutionMetrics(Long.MAX_VALUE - 40L, 50L, 90L, 110L);
        var outputIoOverflow = new PhaseExecutionMetrics(10L, 145L, 148L, 155L);
        assertThrows(ArithmeticException.class,
                () -> new BitPruneExecutionMetrics(datasetIoLarge, algoSmall, Optional.of(outputIoOverflow), Long.MAX_VALUE, 200L, 5L));
    }

    @Test
    @DisplayName("Memory byte to decimal Megabyte conversion")
    void bytesToMegabytesConversion() {
        assertEquals(1.0, BitPruneExecutionMetrics.bytesToMegabytes(1_000_000L));
        assertEquals(1.048576, BitPruneExecutionMetrics.bytesToMegabytes(1_048_576L));
        assertEquals(0.0, BitPruneExecutionMetrics.bytesToMegabytes(0L));
        assertEquals(500.0, BitPruneExecutionMetrics.bytesToMegabytes(500_000_000L));
    }

    @Test
    @DisplayName("Nanoseconds to decimal milliseconds conversion")
    void nanosecondsToMillisecondsConversion() {
        assertEquals(1.0, BitPruneExecutionMetrics.nanosecondsToMilliseconds(1_000_000L));
        assertEquals(0.0, BitPruneExecutionMetrics.nanosecondsToMilliseconds(0L));
        assertEquals(0.001, BitPruneExecutionMetrics.nanosecondsToMilliseconds(1_000L));
        assertEquals(123.456789, BitPruneExecutionMetrics.nanosecondsToMilliseconds(123_456_789L));
    }

    @Test
    @DisplayName("Conversion functions produce finite numbers without NaN or Infinity for valid long inputs")
    void conversionNoNaNOrInfinityForValidInputs() {
        long[] testInputs = {0L, 1L, 1_000_000L, 1_000_000_000L, Long.MAX_VALUE};
        for (long input : testInputs) {
            double mb = BitPruneExecutionMetrics.bytesToMegabytes(input);
            assertFalse(Double.isNaN(mb), "MB should not be NaN for " + input);
            assertFalse(Double.isInfinite(mb), "MB should not be infinite for " + input);

            double ms = BitPruneExecutionMetrics.nanosecondsToMilliseconds(input);
            assertFalse(Double.isNaN(ms), "MS should not be NaN for " + input);
            assertFalse(Double.isInfinite(ms), "MS should not be infinite for " + input);
        }
    }

    @Test
    @DisplayName("Negative input for conversion functions must be rejected")
    void negativeInputForConversionRejected() {
        var errorBytes = assertThrows(IllegalArgumentException.class,
                () -> BitPruneExecutionMetrics.bytesToMegabytes(-1L));
        assertTrue(errorBytes.getMessage().contains("bytes must not be negative"));

        assertThrows(IllegalArgumentException.class,
                () -> BitPruneExecutionMetrics.bytesToMegabytes(Long.MIN_VALUE));

        var errorNanos = assertThrows(IllegalArgumentException.class,
                () -> BitPruneExecutionMetrics.nanosecondsToMilliseconds(-1L));
        assertTrue(errorNanos.getMessage().contains("nanoseconds must not be negative"));

        assertThrows(IllegalArgumentException.class,
                () -> BitPruneExecutionMetrics.nanosecondsToMilliseconds(Long.MIN_VALUE));
    }

    @Test
    @DisplayName("Record equality, hashCode, and toString contracts")
    void equalityHashCodeAndToString() {
        var datasetIo1 = new PhaseExecutionMetrics(15L, 50L, 90L, 110L);
        var algorithm1 = createSampleAlgorithmMetrics();
        var outputIo1 = Optional.of(new PhaseExecutionMetrics(12L, 145L, 148L, 155L));

        var exec1 = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, outputIo1, 70L, 200L, 5L);
        var exec2 = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, outputIo1, 70L, 200L, 5L);

        var datasetDiff = new PhaseExecutionMetrics(16L, 50L, 90L, 110L);
        var execDiffDataset = new BitPruneExecutionMetrics(
                datasetDiff, algorithm1, outputIo1, 70L, 200L, 5L);

        var prepDiff = new PhaseExecutionMetrics(12L, 100L, 120L, 130L);
        var dfs1 = new PhaseExecutionMetrics(20L, 120L, 140L, 150L);
        var algoDiff = new BitPruneAlgorithmMetrics(prepDiff, dfs1, 35L, 145L, 160L);
        var execDiffAlgo = new BitPruneExecutionMetrics(
                datasetIo1, algoDiff, outputIo1, 70L, 200L, 5L);

        var execDiffOutput = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, Optional.empty(), 70L, 200L, 5L);

        var execDiffTotalNanos = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, outputIo1, 75L, 200L, 5L);

        var execDiffTotalPeak = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, outputIo1, 70L, 210L, 5L);

        var execDiffInterval = new BitPruneExecutionMetrics(
                datasetIo1, algorithm1, outputIo1, 70L, 200L, 10L);

        assertEquals(exec1, exec2);
        assertEquals(exec1.hashCode(), exec2.hashCode());
        assertEquals(exec1, exec1);

        assertNotEquals(exec1, null);
        assertNotEquals(exec1, "different type");
        assertNotEquals(exec1, execDiffDataset);
        assertNotEquals(exec1, execDiffAlgo);
        assertNotEquals(exec1, execDiffOutput);
        assertNotEquals(exec1, execDiffTotalNanos);
        assertNotEquals(exec1, execDiffTotalPeak);
        assertNotEquals(exec1, execDiffInterval);

        String str = exec1.toString();
        assertNotNull(str);
        assertTrue(str.contains("BitPruneExecutionMetrics"));
        assertTrue(str.contains("totalExecutionNanos=70"));
        assertTrue(str.contains("observedPeakHeapTotalBytes=200"));
        assertTrue(str.contains("samplingIntervalMillis=5"));
    }
}

