package vn.edu.wspm.backend.services.run.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.mining.WspmMiningStatistics;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;

class MiningEngineResultContractTest {
    private static final Pattern I = Pattern.of(Itemset.of(new Item(1), new Item(2)));
    private static final Pattern S = Pattern.of(Itemset.of(new Item(1)), Itemset.of(new Item(2)));
    private static final MiningEngineResult.Metrics METRICS = new MiningEngineResult.Metrics(
            new PhaseExecutionMetrics(2, 10, 12, 14), new PhaseExecutionMetrics(3, 12, 15, 16), 6, 15, 16);

    private MiningEngineResult result(Map<Pattern, Integer> supports, int sequences, int patterns) {
        return new MiningEngineResult(MiningAlgorithm.WSPM_PRETREE, supports,
                new MinimumSupportResolver().resolve(MinimumSupport.absolute(0.1), sequences), 2, 0.5,
                new MiningEngineResult.PreTreeStatistics(new WspmMiningStatistics(
                        sequences, patterns, 0, 0, 0, 0, 0, 0, patterns, 0)), METRICS);
    }

    @Test void immutableSnapshotPreservesTemporalIdentitySupportAndOrder() {
        var source = new LinkedHashMap<Pattern, Integer>();
        source.put(S, 2); source.put(I, 1);
        var result = result(source, 3, 2);
        source.clear();
        assertEquals(List.of(S, I), List.copyOf(result.supports().keySet()));
        assertEquals(Map.of(S, 2, I, 1), result.supports());
        assertEquals(2, result.patternCount());
        assertThrows(UnsupportedOperationException.class, () -> result.supports().put(S, 3));
        assertThrows(UnsupportedOperationException.class, () -> result.supports().entrySet().iterator().next().setValue(3));
    }

    @Test void rejectsWrongPatternCountRootAndImpossibleSupports() {
        assertThrows(IllegalArgumentException.class, () -> result(Map.of(I, 1), 3, 0));
        for (int support : new int[]{-1, 0, 4}) {
            assertThrows(IllegalArgumentException.class, () -> result(Map.of(I, support), 3, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> result(Map.of(Pattern.empty(), 1), 3, 1));
        var nullSupport = new LinkedHashMap<Pattern, Integer>(); nullSupport.put(I, null);
        assertThrows(NullPointerException.class, () -> result(nullSupport, 3, 1));
        var nullPattern = new LinkedHashMap<Pattern, Integer>(); nullPattern.put(null, 1);
        assertThrows(NullPointerException.class, () -> result(nullPattern, 3, 1));
    }

    @Test void rejectsWrongAlgorithmCountsMetadataAndUnsupportedThreshold() {
        var valid = result(Map.of(I, 1), 3, 1);
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult(
                MiningAlgorithm.WSPM_BITPRUNE, valid.supports(), valid.minimumSupport(), 2, 0.5, valid.statistics(), METRICS));
        var wrongCount = new MinimumSupportResolver().resolve(MinimumSupport.absolute(1), 2);
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult(
                valid.algorithm(), valid.supports(), wrongCount, 2, 0.5, valid.statistics(), METRICS));
        for (double mean : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult(
                    valid.algorithm(), valid.supports(), valid.minimumSupport(), 2, mean, valid.statistics(), METRICS));
        }
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult(
                valid.algorithm(), valid.supports(), valid.minimumSupport(), 0, 0.5, valid.statistics(), METRICS));
        for (double zero : new double[]{0, -0.0}) {
            var resolved = new MinimumSupportResolver().resolve(MinimumSupport.absolute(zero), 3);
            assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult(
                    valid.algorithm(), valid.supports(), resolved, 2, 0.5, valid.statistics(), METRICS));
        }
    }

    @Test void nullComponentsFailInsteadOfFabricatingObservations() {
        var valid = result(Map.of(), 3, 0);
        assertThrows(NullPointerException.class, () -> new MiningEngineResult(null, Map.of(), valid.minimumSupport(), 2, .5, valid.statistics(), METRICS));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult(valid.algorithm(), null, valid.minimumSupport(), 2, .5, valid.statistics(), METRICS));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult(valid.algorithm(), Map.of(), null, 2, .5, valid.statistics(), METRICS));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult(valid.algorithm(), Map.of(), valid.minimumSupport(), 2, .5, null, METRICS));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult(valid.algorithm(), Map.of(), valid.minimumSupport(), 2, .5, valid.statistics(), null));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult.PreTreeStatistics(null));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult.BitPruneStatisticsView(null));
    }

    @Test void metricsRequirePhaseContainmentAndJvmHeapBoundaries() {
        var prep = METRICS.preprocessing(); var dfs = METRICS.dfsMining();
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult.Metrics(prep, dfs, 4, 15, 16));
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult.Metrics(prep, dfs, -1, 15, 16));
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult.Metrics(prep, dfs, 6, -1, 16));
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineResult.Metrics(prep, dfs, 6, 15, 15));
        assertThrows(NullPointerException.class, () -> new MiningEngineResult.Metrics(null, dfs, 6, 15, 16));
    }

    @Test void requestRequiresStructuralInputsAndNonEmptyDatabase() throws Exception {
        var db = new vn.edu.wspm.io.SpmfSequenceParser().parse(new java.io.StringReader("1 -1 -2"));
        var config = MinimumSupport.absolute(0.0);
        // Construction is deliberately NOT an execution gate.
        assertSame(config, new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, config).minimumSupport());
        assertThrows(NullPointerException.class, () -> new MiningEngineRequest(null, db, config));
        assertThrows(NullPointerException.class, () -> new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, null, config));
        assertThrows(NullPointerException.class, () -> new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, null));
        assertThrows(IllegalArgumentException.class, () -> new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, new SequenceDatabase(List.of()), config));
    }
}
