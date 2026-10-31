package vn.edu.wspm.measurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.mining.MeasuredBitPruneRun;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * C13.4 — Memory Sampler Lifecycle & Failure Cleanup Evidence.
 *
 * <p>Verifies deterministic lifecycle management, caller ownership, worker thread termination,
 * exception cleanup, double-close idempotency, and isolated multi-run behavior when
 * {@link ExecutionMemorySampler} is used with {@link BitPruneMiner#mineMeasured}.</p>
 */
class BitPruneExecutionMemoryLifecycleTest {

    private static final Path TABLE1_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");
    private static final MinimumSupport TABLE1_MIN_SUP = MinimumSupport.percentage(14.0);

    private SequenceDatabase table1Database;
    private BitPruneMiner miner;

    @BeforeEach
    void setUp() throws IOException {
        table1Database = new SequenceDatabaseParser().parse(TABLE1_PATH, SequenceDatabaseFormat.SPMF_SEQUENTIAL);
        miner = new BitPruneMiner();
    }

    @Test
    @DisplayName("1. defaultIntervalIsFiveMilliseconds: default sampler reports 5 ms interval")
    void defaultIntervalIsFiveMilliseconds() {
        try (var sampler = new ExecutionMemorySampler()) {
            assertEquals(5L, sampler.samplingIntervalMillis());
            assertTrue(sampler.isSamplingThreadAlive());
        }
    }

    @Test
    @DisplayName("2. successfulMeasuredMiningLeavesSamplerOpenForCaller: miner does not close caller sampler")
    void successfulMeasuredMiningLeavesSamplerOpenForCaller() {
        var sampler = new ExecutionMemorySampler();
        try (sampler) {
            MeasuredBitPruneRun run = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            assertNotNull(run);

            // Sampler thread remains alive and operational for the caller after miner returns
            assertTrue(sampler.isSamplingThreadAlive());
            var postRunCheckpoint = sampler.checkpoint();
            assertNotNull(postRunCheckpoint);
            assertTrue(postRunCheckpoint.heapUsedBytes() >= 0L);
        }
        // Once caller closes via try-with-resources, worker thread is terminated
        assertFalse(sampler.isSamplingThreadAlive());
    }

    @Test
    @DisplayName("3. tryWithResourcesClosesSamplerAfterSuccessfulMining: worker thread terminates on normal close")
    void tryWithResourcesClosesSamplerAfterSuccessfulMining() {
        ExecutionMemorySampler samplerRef;
        try (var sampler = new ExecutionMemorySampler()) {
            samplerRef = sampler;
            MeasuredBitPruneRun run = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            assertNotNull(run);
            assertTrue(sampler.isSamplingThreadAlive());
        }
        assertFalse(samplerRef.isSamplingThreadAlive());
    }

    @Test
    @DisplayName("4. tryWithResourcesClosesSamplerAfterEmptyPercentageFailure: worker thread terminated after exception")
    void tryWithResourcesClosesSamplerAfterEmptyPercentageFailure() {
        SequenceDatabase emptyDb = new SequenceDatabase(List.of());
        MinimumSupport percentSup = MinimumSupport.percentage(14.0);
        var sampler = new ExecutionMemorySampler();

        try {
            assertThrows(IllegalArgumentException.class, () -> {
                try (sampler) {
                    miner.mineMeasured(emptyDb, percentSup, sampler);
                }
            });
        } finally {
            assertFalse(sampler.isSamplingThreadAlive(), "sampler worker thread must not leak on percentage error");
        }
    }

    @Test
    @DisplayName("5. tryWithResourcesClosesSamplerAfterR6Rejection: worker thread terminated after R6 rejection")
    void tryWithResourcesClosesSamplerAfterR6Rejection() {
        MinimumSupport zeroSup = MinimumSupport.percentage(0.0);
        var sampler = new ExecutionMemorySampler();

        try {
            assertThrows(IllegalArgumentException.class, () -> {
                try (sampler) {
                    miner.mineMeasured(table1Database, zeroSup, sampler);
                }
            });
        } finally {
            assertFalse(sampler.isSamplingThreadAlive(), "sampler worker thread must not leak on R6 rejection");
        }
    }

    @Test
    @DisplayName("6. tryWithResourcesClosesSamplerAfterNullOrInvalidInputFailure: worker thread terminated after NPE")
    void tryWithResourcesClosesSamplerAfterNullOrInvalidInputFailure() {
        var sampler = new ExecutionMemorySampler();

        try {
            assertThrows(NullPointerException.class, () -> {
                try (sampler) {
                    miner.mineMeasured(null, TABLE1_MIN_SUP, sampler);
                }
            });
        } finally {
            assertFalse(sampler.isSamplingThreadAlive(), "sampler worker thread must not leak on null input");
        }

        var sampler2 = new ExecutionMemorySampler();
        try {
            assertThrows(NullPointerException.class, () -> {
                try (sampler2) {
                    miner.mineMeasured(table1Database, null, sampler2);
                }
            });
        } finally {
            assertFalse(sampler2.isSamplingThreadAlive(), "sampler worker thread must not leak on null minsup");
        }
    }

    @Test
    @DisplayName("7. doubleCloseIsIdempotent: consecutive close calls succeed without error and do not resurrect thread")
    void doubleCloseIsIdempotent() {
        var sampler = new ExecutionMemorySampler();
        assertTrue(sampler.isSamplingThreadAlive());

        sampler.close();
        assertFalse(sampler.isSamplingThreadAlive());

        // Second close must be a safe no-op
        sampler.close();
        assertFalse(sampler.isSamplingThreadAlive());
    }

    @Test
    @DisplayName("8. repeatedRunsUseIndependentSamplers: separate samplers do not share state or threads")
    void repeatedRunsUseIndependentSamplers() {
        var sampler1 = new ExecutionMemorySampler();
        var sampler2 = new ExecutionMemorySampler();

        try (sampler1; sampler2) {
            assertNotSame(sampler1, sampler2);
            assertTrue(sampler1.isSamplingThreadAlive());
            assertTrue(sampler2.isSamplingThreadAlive());

            MeasuredBitPruneRun run1 = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler1);
            MeasuredBitPruneRun run2 = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler2);

            assertNotSame(run1, run2);
            assertNotSame(run1.result(), run2.result());
            assertEquals(run1.result().supports(), run2.result().supports());
            assertEquals(run1.result().statistics(), run2.result().statistics());
        }

        assertFalse(sampler1.isSamplingThreadAlive());
        assertFalse(sampler2.isSamplingThreadAlive());
    }

    @Test
    @DisplayName("9. observedPeakIncludesBoundarySnapshots: peak is greater than or equal to both boundary snapshots")
    void observedPeakIncludesBoundarySnapshots() {
        try (var sampler = new ExecutionMemorySampler()) {
            var start = sampler.checkpoint();
            var end = sampler.checkpoint();
            long peak = sampler.observedPeakBytes(start, end);

            assertTrue(start.heapUsedBytes() >= 0L);
            assertTrue(end.heapUsedBytes() >= 0L);
            assertTrue(peak >= start.heapUsedBytes());
            assertTrue(peak >= end.heapUsedBytes());
        }
    }

    @Test
    @DisplayName("10. customPositiveIntervalIsReported: custom positive interval is preserved and reported")
    void customPositiveIntervalIsReported() {
        try (var sampler = new ExecutionMemorySampler(10L)) {
            assertEquals(10L, sampler.samplingIntervalMillis());
            assertTrue(sampler.isSamplingThreadAlive());
        }
    }

    @Test
    @DisplayName("11. zeroOrNegativeIntervalIsRejected: zero or negative interval throws IllegalArgumentException")
    void zeroOrNegativeIntervalIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionMemorySampler(0L));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionMemorySampler(-1L));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionMemorySampler(-100L));
    }

    @Test
    @DisplayName("12. measuredAndUnmeasuredResultsRemainEquivalent: lifecycle test confirms complete output equality")
    void measuredAndUnmeasuredResultsRemainEquivalent() {
        BitPruneMiningResult unmeasured = miner.mine(table1Database, TABLE1_MIN_SUP);

        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun measuredRun = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            BitPruneMiningResult measured = measuredRun.result();

            assertEquals(unmeasured.supports(), measured.supports());
            assertEquals(unmeasured.acceptedPatterns(), measured.acceptedPatterns());
            assertEquals(unmeasured.prefixTree().preOrderPatterns(), measured.prefixTree().preOrderPatterns());
            assertEquals(unmeasured.statistics(), measured.statistics());
        }
    }
}

