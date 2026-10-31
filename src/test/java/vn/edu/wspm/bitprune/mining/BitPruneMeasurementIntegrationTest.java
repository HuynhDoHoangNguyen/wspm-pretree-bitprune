package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;

/**
 * C13.3 — Measured Mining Execution Integration Tests.
 *
 * <p>Verifies measurement instrumentation parity, timing boundaries, sampler ownership,
 * and exact C11 result and C12 statistics non-regression.</p>
 */
class BitPruneMeasurementIntegrationTest {

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
    @DisplayName("1-4. Measured and unmeasured executions yield identical support maps, pattern order, PrefixTree pre-order, and C12 statistics")
    void measuredAndUnmeasuredResultsAndStatisticsAreIdentical() {
        BitPruneMiningResult unmeasured = miner.mine(table1Database, TABLE1_MIN_SUP);

        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun measuredRun = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            BitPruneMiningResult measured = measuredRun.result();

            // 1. Measured and unmeasured support maps match exactly
            assertEquals(unmeasured.supports(), measured.supports());

            // 2. Accepted pattern order matches exactly
            assertEquals(unmeasured.acceptedPatterns(), measured.acceptedPatterns());

            // 3. PrefixTree pre-order matches exactly
            assertEquals(unmeasured.prefixTree().preOrderPatterns(), measured.prefixTree().preOrderPatterns());

            // 4. All C12 statistics match exactly
            assertEquals(unmeasured.statistics(), measured.statistics());
        }
    }

    @Test
    @DisplayName("5-10. Algorithm metrics satisfy non-negativity and containment invariants")
    void metricsSatisfyContainmentAndNonNegativityInvariants() {
        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun measuredRun = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            BitPruneAlgorithmMetrics metrics = measuredRun.metrics();

            // 5. Preprocessing metrics are non-negative
            assertTrue(metrics.preprocessing().elapsedNanos() >= 0L);
            assertTrue(metrics.preprocessing().heapBeforeBytes() >= 0L);
            assertTrue(metrics.preprocessing().heapAfterBytes() >= 0L);
            assertTrue(metrics.preprocessing().observedPeakHeapBytes() >= 0L);

            // 6. DFS metrics are non-negative
            assertTrue(metrics.dfsMining().elapsedNanos() >= 0L);
            assertTrue(metrics.dfsMining().heapBeforeBytes() >= 0L);
            assertTrue(metrics.dfsMining().heapAfterBytes() >= 0L);
            assertTrue(metrics.dfsMining().observedPeakHeapBytes() >= 0L);

            // 7. Complete algorithm time covers preprocessing + DFS
            long subtotalNanos = metrics.preprocessing().elapsedNanos() + metrics.dfsMining().elapsedNanos();
            assertTrue(metrics.completeAlgorithmNanos() >= subtotalNanos,
                    "completeAlgorithmNanos (" + metrics.completeAlgorithmNanos()
                    + ") must be >= subtotal (" + subtotalNanos + ")");

            // 8. Algorithm peak covers preprocessing peak
            assertTrue(metrics.observedPeakHeapAlgorithmBytes() >= metrics.preprocessing().observedPeakHeapBytes());

            // 9. Algorithm peak covers DFS peak
            assertTrue(metrics.observedPeakHeapAlgorithmBytes() >= metrics.dfsMining().observedPeakHeapBytes());

            // 10. Algorithm peak covers heap after mining
            assertTrue(metrics.observedPeakHeapAlgorithmBytes() >= metrics.heapAfterMiningBytes());
        }
    }

    @Test
    @DisplayName("11-13. Caller-owned sampler remains alive after mining, is not closed by miner, and closed via try-with-resources")
    void callerOwnedSamplerLifecyclePreserved() {
        var sampler = new ExecutionMemorySampler();
        try (sampler) {
            MeasuredBitPruneRun run = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            assertNotNull(run);

            // 11-12. Caller-owned sampler is still operational and accepts checkpoints after mineMeasured
            var postCheckpoint = sampler.checkpoint();
            assertNotNull(postCheckpoint);
            assertTrue(postCheckpoint.heapUsedBytes() >= 0L);
        }
        // 13. Test successfully closed sampler using try-with-resources
    }

    @Test
    @DisplayName("14. Empty absolute database completes with valid metrics, root-only frozen result, and zero statistics")
    void emptyAbsoluteDatabaseProducesValidMetricsAndZeroResult() {
        SequenceDatabase emptyDb = new SequenceDatabase(List.of());
        MinimumSupport absoluteSup = MinimumSupport.absolute(1.0);

        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun run = miner.mineMeasured(emptyDb, absoluteSup, sampler);
            assertNotNull(run);

            BitPruneMiningResult result = run.result();
            assertTrue(result.prefixTree().isFrozen());
            assertEquals(1, result.prefixTree().nodeCount());
            assertTrue(result.supports().isEmpty());
            assertTrue(result.acceptedPatterns().isEmpty());

            BitPruneStatistics stats = result.statistics();
            assertEquals(0, stats.sequenceCount());
            assertEquals(0, stats.f1Count());
            assertEquals(0L, stats.generatedExtensions());
            assertEquals(0L, stats.cmapRejected());
            assertEquals(0L, stats.bitmapRejected());
            assertEquals(0L, stats.exactRejected());
            assertEquals(0L, stats.acceptedExtensions());
            assertEquals(0L, stats.expandedNodes());
            assertEquals(0L, stats.patternCount());
            assertEquals(0L, stats.exactVerifications());

            BitPruneAlgorithmMetrics metrics = run.metrics();
            assertTrue(metrics.preprocessing().elapsedNanos() >= 0L);
            assertTrue(metrics.dfsMining().elapsedNanos() >= 0L);
            assertTrue(metrics.completeAlgorithmNanos() >= metrics.preprocessing().elapsedNanos() + metrics.dfsMining().elapsedNanos());
            assertTrue(metrics.observedPeakHeapAlgorithmBytes() >= metrics.heapAfterMiningBytes());

            assertNotNull(sampler.checkpoint());
        }
    }

    @Test
    @DisplayName("15. Non-empty F1-empty database has no-op DFS, valid metrics, root-only result, and zero candidate statistics")
    void nonEmptyF1EmptyDatabaseHasValidNoOpDfsAndMetrics() {
        // Minsup = 100.0% so no items qualify for F1
        MinimumSupport impossibleSup = MinimumSupport.percentage(100.0);

        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun run = miner.mineMeasured(table1Database, impossibleSup, sampler);
            assertNotNull(run);

            BitPruneMiningResult result = run.result();
            assertTrue(result.prefixTree().isFrozen());
            assertEquals(1, result.prefixTree().nodeCount());
            assertTrue(result.supports().isEmpty());
            assertTrue(result.acceptedPatterns().isEmpty());

            BitPruneStatistics stats = result.statistics();
            assertEquals(table1Database.size(), stats.sequenceCount());
            assertEquals(0, stats.f1Count());
            assertEquals(0L, stats.generatedExtensions());
            assertEquals(0L, stats.patternCount());

            BitPruneAlgorithmMetrics metrics = run.metrics();
            assertTrue(metrics.preprocessing().elapsedNanos() >= 0L);
            assertTrue(metrics.dfsMining().elapsedNanos() >= 0L);
            assertTrue(metrics.completeAlgorithmNanos() >= metrics.preprocessing().elapsedNanos() + metrics.dfsMining().elapsedNanos());

            assertNotNull(sampler.checkpoint());
        }
    }

    @Test
    @DisplayName("16. Empty percentage database is rejected, caller sampler not closed")
    void emptyPercentageDatabaseRejectedAndSamplerNotClosed() {
        SequenceDatabase emptyDb = new SequenceDatabase(List.of());
        MinimumSupport percentSup = MinimumSupport.percentage(14.0);

        var sampler = new ExecutionMemorySampler();
        try (sampler) {
            assertThrows(IllegalArgumentException.class, () -> miner.mineMeasured(emptyDb, percentSup, sampler));
            assertNotNull(sampler.checkpoint());
        }
    }

    @Test
    @DisplayName("17. R6 zero-threshold is rejected, caller sampler not closed")
    void zeroThresholdRejectedAndSamplerNotClosed() {
        MinimumSupport zeroSup = MinimumSupport.percentage(0.0);

        var sampler = new ExecutionMemorySampler();
        try (sampler) {
            assertThrows(IllegalArgumentException.class, () -> miner.mineMeasured(table1Database, zeroSup, sampler));
            assertNotNull(sampler.checkpoint());
        }
    }

    @Test
    @DisplayName("18. Sampler is still closable by caller after mining exception")
    void samplerStillClosableByCallerAfterException() {
        var sampler = new ExecutionMemorySampler();
        try (sampler) {
            assertThrows(IllegalArgumentException.class,
                    () -> miner.mineMeasured(table1Database, MinimumSupport.percentage(0.0), sampler));
        }
    }

    @Test
    @DisplayName("19. Repeated measured runs do not share mutable state")
    void repeatedRunsDoNotShareState() {
        try (var sampler = new ExecutionMemorySampler()) {
            var run1 = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            var run2 = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);

            assertNotSame(run1.result(), run2.result());
            assertNotSame(run1.result().prefixTree(), run2.result().prefixTree());
            assertEquals(run1.result().supports(), run2.result().supports());
            assertEquals(run1.result().statistics(), run2.result().statistics());
        }
    }

    @Test
    @DisplayName("20. Legacy unmeasured runs execute normally without requiring sampler")
    void legacyRunDoesNotRequireSampler() {
        BitPruneMiningResult legacy = miner.mine(table1Database, TABLE1_MIN_SUP);
        assertNotNull(legacy);
        assertEquals(9, legacy.supports().size());
        assertEquals(9, legacy.acceptedPatterns().size());
        assertEquals(9L, legacy.statistics().patternCount());
    }

    @Test
    @DisplayName("21. Table 1 exact result and C12 statistics are verified and unchanged")
    void table1ExactResultAndStatisticsUnchanged() {
        try (var sampler = new ExecutionMemorySampler()) {
            MeasuredBitPruneRun run = miner.mineMeasured(table1Database, TABLE1_MIN_SUP, sampler);
            BitPruneMiningResult result = run.result();

            Item i1 = new Item(1);
            Item i2 = new Item(2);
            Item i3 = new Item(3);

            Map<Pattern, Integer> supports = result.supports();
            assertEquals(9, supports.size());
            assertEquals(7, supports.get(Pattern.singleton(i1)));
            assertEquals(7, supports.get(Pattern.of(Itemset.of(i1, i2))));
            assertEquals(6, supports.get(Pattern.of(Itemset.of(i1, i2), Itemset.of(i3))));
            assertEquals(6, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i1))));
            assertEquals(7, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i2))));
            assertEquals(6, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i3))));
            assertEquals(7, supports.get(Pattern.singleton(i2)));
            assertEquals(6, supports.get(Pattern.of(Itemset.of(i2), Itemset.of(i3))));
            assertEquals(6, supports.get(Pattern.singleton(i3)));

            BitPruneStatistics stats = result.statistics();
            assertEquals(7, stats.sequenceCount());
            assertEquals(3, stats.f1Count());
            assertEquals(34L, stats.generatedExtensions());
            assertEquals(7L, stats.generatedIExtensions());
            assertEquals(27L, stats.generatedSExtensions());
            assertEquals(0L, stats.cmapRejected());
            assertEquals(0L, stats.bitmapRejected());
            assertEquals(28L, stats.exactRejected());
            assertEquals(6L, stats.acceptedExtensions());
            assertEquals(9L, stats.expandedNodes());
            assertEquals(9L, stats.patternCount());
            assertEquals(34L, stats.exactVerifications());
            assertEquals(28L, stats.rejectedExtensions());
        }
    }

    @Test
    @DisplayName("Null arguments to mineMeasured must throw NullPointerException before mutation or checkpoint")
    void nullArgumentsThrowNullPointerException() {
        try (var sampler = new ExecutionMemorySampler()) {
            assertThrows(NullPointerException.class,
                    () -> miner.mineMeasured(null, TABLE1_MIN_SUP, sampler));
            assertThrows(NullPointerException.class,
                    () -> miner.mineMeasured(table1Database, null, sampler));
            assertThrows(NullPointerException.class,
                    () -> miner.mineMeasured(table1Database, TABLE1_MIN_SUP, null));
        }
    }
}
