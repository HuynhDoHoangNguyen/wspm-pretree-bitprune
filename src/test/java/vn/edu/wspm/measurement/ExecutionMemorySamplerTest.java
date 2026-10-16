package vn.edu.wspm.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExecutionMemorySamplerTest {

    @Test
    void checkpointsAndObservedPeakIncludeBothBoundaries() {
        try (var sampler = new ExecutionMemorySampler()) {
            var start = sampler.checkpoint();
            byte[] allocation = new byte[128 * 1024];
            var end = sampler.checkpoint();

            long peak = sampler.observedPeakBytes(start, end);

            assertTrue(allocation.length > 0);
            assertTrue(start.heapUsedBytes() >= 0L);
            assertTrue(end.heapUsedBytes() >= 0L);
            assertTrue(peak >= Math.max(start.heapUsedBytes(), end.heapUsedBytes()));
        }
    }

    @Test
    void closeStopsTheSamplerThreadEvenWhenMeasuredCodeThrows() {
        var sampler = new ExecutionMemorySampler();

        assertThrows(IllegalStateException.class, () -> {
            try (sampler) {
                throw new IllegalStateException("measured failure");
            }
        });

        assertFalse(sampler.isSamplingThreadAlive());
    }

    @Test
    void consecutiveSamplersDoNotShareMutableState() {
        var first = new ExecutionMemorySampler();
        var firstCheckpoint = first.checkpoint();
        first.close();
        var second = new ExecutionMemorySampler();
        var secondCheckpoint = second.checkpoint();
        second.close();

        assertNotSame(first, second);
        assertTrue(firstCheckpoint.heapUsedBytes() >= 0L);
        assertTrue(secondCheckpoint.heapUsedBytes() >= 0L);
        assertFalse(first.isSamplingThreadAlive());
        assertFalse(second.isSamplingThreadAlive());
    }

    @Test
    void metricsAreImmutableAndValidatePeakRelationshipsAndDecimalMb() {
        var phase = new PhaseExecutionMetrics(10L, 100L, 120L, 130L);
        var algorithm = new WspmAlgorithmMetrics(phase, phase, 30L, 125L, 140L);
        var execution = new WspmExecutionMetrics(
                phase, algorithm, Optional.empty(), 50L, 150L, 5L);

        assertTrue(PhaseExecutionMetrics.class.isRecord());
        assertTrue(WspmAlgorithmMetrics.class.isRecord());
        assertTrue(WspmExecutionMetrics.class.isRecord());
        assertEquals(0L, execution.outputIoNanos());
        assertEquals(1.0, WspmExecutionMetrics.bytesToMegabytes(1_000_000L));
        assertEquals(1.048576, WspmExecutionMetrics.bytesToMegabytes(1_048_576L));
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseExecutionMetrics(0L, 10L, 20L, 19L));
        assertThrows(IllegalArgumentException.class,
                () -> new WspmAlgorithmMetrics(phase, phase, 30L, 141L, 140L));
        assertThrows(IllegalArgumentException.class,
                () -> new WspmExecutionMetrics(
                        phase, algorithm, Optional.empty(), 50L, 139L, 5L));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionMemorySampler(0L));
    }
}
