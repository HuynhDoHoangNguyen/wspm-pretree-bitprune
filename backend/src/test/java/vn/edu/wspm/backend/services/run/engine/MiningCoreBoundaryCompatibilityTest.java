package vn.edu.wspm.backend.services.run.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Real Java-core compatibility probes; NOT an implementation/test of the future dispatcher. */
class MiningCoreBoundaryCompatibilityTest {
    // Synthetic sequential fixture: both within-itemset and later-itemset occurrences.
    private static final String SPMF = "1 2 -1 1 -1 -2\n1 -1 2 -1 -2\n1 2 -1 -2\n";
    private static final Map<String, Integer> EXPECTED = Map.of(
            "<(1)>", 3, "<(2)>", 3, "<(1 2)>", 2, "<(1)(1)>", 1,
            "<(1)(2)>", 1, "<(2)(1)>", 1, "<(1 2)(1)>", 1);

    static Stream<MinimumSupport> supported() {
        return Stream.of(MinimumSupport.absolute(.1), MinimumSupport.percentage(14),
                MinimumSupport.absolute(Double.MIN_VALUE), MinimumSupport.percentage(100),
                MinimumSupport.absolute(Double.MAX_VALUE));
    }

    @ParameterizedTest @MethodSource("supported")
    void measuredApisProvideCompleteImmutableBoundaryResults(MinimumSupport configuration) throws Exception {
        var database = new SpmfSequenceParser().parse(new StringReader(SPMF));
        var request = new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, database, configuration);
        var resolved = new RunConfigurationValidityGate().requireSupported(
                new MinimumSupportResolver().resolve(request.minimumSupport(), database.size()));
        MiningEngineResult preTree;
        try (var sampler = new ExecutionMemorySampler()) {
            var measured = new WspmPreTreeMiner().mineMeasured(database, configuration, sampler, ResultRetentionPolicy.SUPPORT_ONLY);
            var raw = measured.result(); var m = measured.metrics();
            assertFalse(raw.hasEncodings());
            assertThrows(UnsupportedOperationException.class, raw::encodings);
            preTree = new MiningEngineResult(request.algorithm(), raw.supports(), raw.minimumSupport(),
                    raw.distinctItemCount(), raw.globalMeanWeight().orElseThrow(),
                    new MiningEngineResult.PreTreeStatistics(raw.statistics()),
                    new MiningEngineResult.Metrics(m.preprocessing(), m.dfsMining(), m.algorithmNanos(),
                            m.heapAfterMiningBytes(), m.observedPeakHeapAlgorithmBytes()));
            assertSame(raw.statistics(), ((MiningEngineResult.PreTreeStatistics) preTree.statistics()).original());
            assertEquals(raw.statistics().candidateCount(), preTree.statistics().candidateCount());
        }
        MiningEngineResult bitPrune;
        try (var sampler = new ExecutionMemorySampler()) {
            var measured = new BitPruneMiner().mineMeasured(database, configuration, sampler);
            var raw = measured.result(); var meta = measured.metadata(); var m = measured.metrics();
            bitPrune = new MiningEngineResult(MiningAlgorithm.WSPM_BITPRUNE, raw.supports(), meta.minimumSupport(),
                    meta.distinctItemCount(), meta.globalMeanWeight().orElseThrow(),
                    new MiningEngineResult.BitPruneStatisticsView(raw.statistics()),
                    new MiningEngineResult.Metrics(m.preprocessing(), m.dfsMining(), m.completeAlgorithmNanos(),
                            m.heapAfterMiningBytes(), m.observedPeakHeapAlgorithmBytes()));
            assertSame(raw.statistics(), ((MiningEngineResult.BitPruneStatisticsView) bitPrune.statistics()).original());
            assertEquals(raw.statistics().generatedExtensions(), bitPrune.statistics().candidateCount());
        }
        var full = new WspmPreTreeMiner().mine(database, configuration, ResultRetentionPolicy.FULL_ENCODING);
        assertEquals(full.supports(), preTree.supports());
        assertEquals(preTree.supports(), bitPrune.supports());
        assertEquals(preTree.patternCount(), bitPrune.patternCount());
        var expected = configuration.configuredValue() == 100 || configuration.configuredValue() == Double.MAX_VALUE
                ? Map.<String, Integer>of() : EXPECTED;
        assertEquals(expected, preTree.supports().entrySet().stream().collect(
                Collectors.toMap(e -> e.getKey().serialize(), Map.Entry::getValue)));
        for (var result : new MiningEngineResult[]{preTree, bitPrune}) {
            assertEquals(2, result.distinctItemCount());
            assertEquals(.5, result.globalMeanWeight());
            assertEquals(database.size(), result.minimumSupport().numberOfSequences());
            assertEquals(resolved.mode(), result.minimumSupport().mode());
            assertEquals(Double.doubleToRawLongBits(resolved.configuredValue()), Double.doubleToRawLongBits(result.minimumSupport().configuredValue()));
            assertEquals(resolved.relativeMinimumSupport(), result.minimumSupport().relativeMinimumSupport());
            assertEquals(Double.doubleToRawLongBits(resolved.effectiveThreshold()), Double.doubleToRawLongBits(result.minimumSupport().effectiveThreshold()));
            assertThrows(UnsupportedOperationException.class, () -> result.supports().clear());
        }
    }

    static Stream<MinimumSupport> unsupported() {
        return Stream.of(MinimumSupport.absolute(0), MinimumSupport.absolute(-0.0),
                MinimumSupport.percentage(0), MinimumSupport.percentage(-0.0),
                MinimumSupport.percentage(Double.MIN_VALUE));
    }

    @ParameterizedTest @MethodSource("unsupported")
    void documentedPreflightRejectsZeroAndUnderflowBeforeAnyMinerCall(MinimumSupport configuration) throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader(SPMF));
        for (var algorithm : MiningAlgorithm.values()) {
            var request = new MiningEngineRequest(algorithm, db, configuration);
            var resolved = new MinimumSupportResolver().resolve(request.minimumSupport(), db.size());
            var failure = assertThrows(ApplicationException.class,
                    () -> new RunConfigurationValidityGate().requireSupported(resolved));
            assertEquals(422, failure.status().value());
            assertEquals("ZERO_EFFECTIVE_THRESHOLD", failure.details().getFirst().code());
        }
    }
}
