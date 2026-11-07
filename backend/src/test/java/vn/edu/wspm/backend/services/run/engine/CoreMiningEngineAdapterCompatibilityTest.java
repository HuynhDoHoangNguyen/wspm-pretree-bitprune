package vn.edu.wspm.backend.services.run.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Real production adapter + actual miners; no HTTP, executor, DB or benchmark. */
class CoreMiningEngineAdapterCompatibilityTest {
    static Stream<MinimumSupport> supported() {
        return Stream.of(MinimumSupport.absolute(.1), MinimumSupport.absolute(.5), MinimumSupport.percentage(14),
                MinimumSupport.percentage(100), MinimumSupport.absolute(Double.MIN_VALUE), MinimumSupport.absolute(Double.MAX_VALUE));
    }

    @ParameterizedTest @MethodSource("supported")
    void adapterPreservesBothCompleteMapsMetadataAndIterationOrder(MinimumSupport config) throws Exception {
        var database = new SpmfSequenceParser().parse(new StringReader("1 2 -1 1 -1 -2\n1 -1 2 -1 -2\n1 2 -1 -2\n"));
        var adapter = new CoreMiningEngineAdapter(new RunConfigurationValidityGate());
        var preTree = adapter.execute(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, database, config));
        var bitPrune = adapter.execute(new MiningEngineRequest(MiningAlgorithm.WSPM_BITPRUNE, database, config));
        var directPre = new WspmPreTreeMiner().mine(database, config, ResultRetentionPolicy.FULL_ENCODING);
        var directBit = new BitPruneMiner().mine(database, config);
        assertEquals(directPre.supports(), preTree.supports()); assertEquals(directBit.supports(), bitPrune.supports());
        assertEquals(preTree.supports(), bitPrune.supports()); assertEquals(preTree.patternCount(), bitPrune.patternCount());
        assertEquals(List.copyOf(directPre.supports().keySet()), List.copyOf(preTree.supports().keySet()));
        assertEquals(List.copyOf(directBit.supports().keySet()), List.copyOf(bitPrune.supports().keySet()));
        var expected = config.configuredValue() == 100 || config.configuredValue() == Double.MAX_VALUE ? Map.<String, Integer>of()
                : Map.of("<(1)>", 3, "<(2)>", 3, "<(1 2)>", 2, "<(1)(1)>", 1,
                        "<(1)(2)>", 1, "<(2)(1)>", 1, "<(1 2)(1)>", 1);
        assertEquals(expected, preTree.supports().entrySet().stream().collect(Collectors.toMap(e -> e.getKey().serialize(), Map.Entry::getValue)));
        assertEquals(directPre.statistics().candidateCount(), preTree.statistics().candidateCount());
        assertEquals(directBit.statistics(), ((MiningEngineResult.BitPruneStatisticsView) bitPrune.statistics()).original());
        var resolved = new MinimumSupportResolver().resolve(config, database.size());
        for (var result : new MiningEngineResult[]{preTree, bitPrune}) {
            assertEquals(resolved.mode(), result.minimumSupport().mode());
            assertEquals(Double.doubleToRawLongBits(config.configuredValue()), Double.doubleToRawLongBits(result.minimumSupport().configuredValue()));
            assertEquals(resolved.relativeMinimumSupport(), result.minimumSupport().relativeMinimumSupport());
            assertEquals(Double.doubleToRawLongBits(resolved.effectiveThreshold()), Double.doubleToRawLongBits(result.minimumSupport().effectiveThreshold()));
            assertEquals(3, result.minimumSupport().numberOfSequences());
            assertEquals(2, result.distinctItemCount()); assertEquals(.5, result.globalMeanWeight());
            assertTrue(result.metrics().completeAlgorithmNanos() >= result.metrics().preprocessing().elapsedNanos() + result.metrics().dfsMining().elapsedNanos());
            assertThrows(UnsupportedOperationException.class, () -> result.supports().clear());
        }
    }
}
