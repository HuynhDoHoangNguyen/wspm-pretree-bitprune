package vn.edu.wspm.backend.services.run.engine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.bitprune.measurement.BitPruneAlgorithmMetrics;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.mining.BitPruneRunMetadata;
import vn.edu.wspm.bitprune.mining.MeasuredBitPruneRun;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.measurement.WspmAlgorithmMetrics;
import vn.edu.wspm.mining.MeasuredMiningRun;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmMiningResult;
import vn.edu.wspm.mining.WspmMiningStatistics;
import vn.edu.wspm.mining.WspmPreTreeMiner;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;

/** Mocked orchestration/resource tests; actual mining is verified separately. */
class CoreMiningEngineAdapterTest {
    private static final PhaseExecutionMetrics PREP = new PhaseExecutionMetrics(2, 10, 12, 13);
    private static final PhaseExecutionMetrics DFS = new PhaseExecutionMetrics(3, 12, 14, 15);
    private static final Pattern PATTERN = Pattern.singleton(new Item(1));
    private final WspmPreTreeMiner preTree = mock(WspmPreTreeMiner.class);
    private final BitPruneMiner bitPrune = mock(BitPruneMiner.class);
    private final ExecutionMemorySampler sampler = mock(ExecutionMemorySampler.class);
    private final AtomicInteger preCreated = new AtomicInteger(), bitCreated = new AtomicInteger(), samplerCreated = new AtomicInteger();

    private CoreMiningEngineAdapter adapter() {
        return new CoreMiningEngineAdapter(new RunConfigurationValidityGate(),
                () -> { preCreated.incrementAndGet(); return preTree; },
                () -> { bitCreated.incrementAndGet(); return bitPrune; },
                () -> { samplerCreated.incrementAndGet(); return sampler; });
    }

    private MiningEngineRequest request(MiningAlgorithm algorithm, MinimumSupport config) throws Exception {
        return new MiningEngineRequest(algorithm, new SpmfSequenceParser().parse(
                new StringReader("1 2 -1 -2\n1 -1 -2\n2 -1 -2\n")), config);
    }

    private ResolvedMinimumSupport resolve(MiningEngineRequest request) {
        return new MinimumSupportResolver().resolve(request.minimumSupport(), request.database().size());
    }

    private WspmMiningResult stubPreTree(MiningEngineRequest request, ResolvedMinimumSupport metadata) {
        var raw = mock(WspmMiningResult.class);
        when(raw.minimumSupport()).thenReturn(metadata);
        when(raw.globalMeanWeight()).thenReturn(OptionalDouble.of(.5));
        when(raw.distinctItemCount()).thenReturn(2);
        when(raw.supports()).thenReturn(Map.of(PATTERN, 2));
        when(raw.statistics()).thenReturn(new WspmMiningStatistics(3, 1, 0, 0, 0, 0, 0, 0, 1, 987));
        when(preTree.mineMeasured(same(request.database()), same(request.minimumSupport()), same(sampler),
                eq(ResultRetentionPolicy.SUPPORT_ONLY))).thenReturn(new MeasuredMiningRun(raw,
                        new WspmAlgorithmMetrics(PREP, DFS, 8, 14, 16)));
        return raw;
    }

    private BitPruneMiningResult stubBitPrune(MiningEngineRequest request, ResolvedMinimumSupport metadata) {
        var raw = mock(BitPruneMiningResult.class);
        when(raw.supports()).thenReturn(Map.of(PATTERN, 2));
        var statistics = new BitPruneStatistics(metadata.numberOfSequences(), 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
        when(raw.statistics()).thenReturn(statistics);
        var measured = new MeasuredBitPruneRun(raw, new BitPruneAlgorithmMetrics(PREP, DFS, 9, 14, 17),
                new BitPruneRunMetadata(2, OptionalDouble.of(.5), metadata));
        when(bitPrune.mineMeasured(same(request.database()), same(request.minimumSupport()), same(sampler))).thenReturn(measured);
        return raw;
    }

    @ParameterizedTest @EnumSource(MiningAlgorithm.class)
    void invokesOnlyRequestedEngineWithExactArgumentsAndOriginalObservations(MiningAlgorithm algorithm) throws Exception {
        var request = request(algorithm, MinimumSupport.percentage(14));
        var metadata = resolve(request);
        Object original;
        if (algorithm == MiningAlgorithm.WSPM_PRETREE) original = stubPreTree(request, metadata).statistics();
        else original = stubBitPrune(request, metadata).statistics();
        var result = adapter().execute(request);
        assertEquals(algorithm, result.algorithm()); assertSame(metadata, result.minimumSupport());
        assertEquals(Map.of(PATTERN, 2), result.supports()); assertEquals(2, result.distinctItemCount());
        assertEquals(.5, result.globalMeanWeight()); assertEquals(1, result.patternCount());
        assertSame(PREP, result.metrics().preprocessing()); assertSame(DFS, result.metrics().dfsMining());
        assertEquals(14, result.metrics().heapAfterMiningBytes());
        if (algorithm == MiningAlgorithm.WSPM_PRETREE) {
            assertSame(original, ((MiningEngineResult.PreTreeStatistics) result.statistics()).original());
            assertEquals(8, result.metrics().completeAlgorithmNanos());
            assertEquals(16, result.metrics().observedPeakHeapAlgorithmBytes());
            verify(preTree).mineMeasured(request.database(), request.minimumSupport(), sampler, ResultRetentionPolicy.SUPPORT_ONLY);
            verifyNoInteractions(bitPrune); assertEquals(0, bitCreated.get()); assertEquals(1, preCreated.get());
        } else {
            assertSame(original, ((MiningEngineResult.BitPruneStatisticsView) result.statistics()).original());
            assertEquals(9, result.metrics().completeAlgorithmNanos());
            assertEquals(17, result.metrics().observedPeakHeapAlgorithmBytes());
            verify(bitPrune).mineMeasured(request.database(), request.minimumSupport(), sampler);
            verifyNoInteractions(preTree); assertEquals(0, preCreated.get()); assertEquals(1, bitCreated.get());
        }
        assertEquals(1, samplerCreated.get()); verify(sampler).close();
    }

    static Stream<MinimumSupport> unsupported() { return Stream.of(MinimumSupport.absolute(0), MinimumSupport.absolute(-0.0),
            MinimumSupport.percentage(0), MinimumSupport.percentage(-0.0), MinimumSupport.percentage(Double.MIN_VALUE)); }

    @ParameterizedTest @MethodSource("unsupported")
    void rejectsBeforeAnySamplerOrMinerIsCreated(MinimumSupport config) throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var request = request(algorithm, config);
            var failure = assertThrows(ApplicationException.class, () -> adapter().execute(request));
            assertEquals(422, failure.status().value());
            assertEquals("ZERO_EFFECTIVE_THRESHOLD", failure.details().getFirst().code());
        }
        assertEquals(0, preCreated.get()); assertEquals(0, bitCreated.get()); assertEquals(0, samplerCreated.get());
        verifyNoInteractions(preTree, bitPrune, sampler);
    }

    @Test void nullInputFailsBeforeResourceCreation() {
        assertThrows(NullPointerException.class, () -> adapter().execute(null));
        assertEquals(0, samplerCreated.get()); assertEquals(0, preCreated.get()); assertEquals(0, bitCreated.get());
    }

    @ParameterizedTest @EnumSource(MiningAlgorithm.class)
    void propagatesEngineFailureClosesSamplerAndPreservesSuppressedCloseFailure(MiningAlgorithm algorithm) throws Exception {
        var request = request(algorithm, MinimumSupport.absolute(.1));
        var failure = new IllegalStateException("controlled miner failure");
        var closeFailure = new IllegalStateException("controlled close failure");
        if (algorithm == MiningAlgorithm.WSPM_PRETREE) {
            when(preTree.mineMeasured(any(), any(), any(), any())).thenThrow(failure);
        } else when(bitPrune.mineMeasured(any(), any(), any())).thenThrow(failure);
        doThrow(closeFailure).when(sampler).close();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter().execute(request)));
        assertArrayEquals(new Throwable[]{closeFailure}, failure.getSuppressed());
        verify(sampler).close(); assertEquals(1, preCreated.get() + bitCreated.get());
        assertEquals(algorithm == MiningAlgorithm.WSPM_PRETREE ? 0 : 1, bitCreated.get());
    }

    static Stream<String> metadataDefects() { return Stream.of("mode", "configured", "relative", "presence", "count", "threshold", "null"); }

    @ParameterizedTest @MethodSource("metadataDefects")
    void rejectsMetadataDriftForBothEnginesAndClosesSampler(String defect) throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var request = request(algorithm, MinimumSupport.percentage(14));
            var expected = resolve(request); var wrong = mock(ResolvedMinimumSupport.class);
            when(wrong.mode()).thenReturn(defect.equals("mode") ? MinimumSupportMode.ABSOLUTE : expected.mode());
            when(wrong.configuredValue()).thenReturn(defect.equals("configured") ? Math.nextUp(expected.configuredValue()) : expected.configuredValue());
            when(wrong.relativeMinimumSupport()).thenReturn(defect.equals("presence") ? OptionalDouble.empty()
                    : OptionalDouble.of(defect.equals("relative") ? Math.nextUp(.14) : .14));
            when(wrong.numberOfSequences()).thenReturn(defect.equals("count") ? 2 : 3);
            when(wrong.effectiveThreshold()).thenReturn(defect.equals("threshold") ? Math.nextUp(expected.effectiveThreshold()) : expected.effectiveThreshold());
            // BitPrune's public metadata already rejects null; null is tested at the PreTree boundary.
            if (algorithm == MiningAlgorithm.WSPM_BITPRUNE && defect.equals("null")) continue;
            if (algorithm == MiningAlgorithm.WSPM_PRETREE) stubPreTree(request, defect.equals("null") ? null : wrong);
            else stubBitPrune(request, wrong);
            assertThrows(IllegalStateException.class, () -> adapter().execute(request));
        }
        verify(sampler, times(defect.equals("null") ? 1 : 2)).close();
    }

    @ParameterizedTest @EnumSource(MiningAlgorithm.class)
    void projectionFailureDoesNotReturnSuccessAndClosesSampler(MiningAlgorithm algorithm) throws Exception {
        var request = request(algorithm, MinimumSupport.absolute(.1));
        if (algorithm == MiningAlgorithm.WSPM_PRETREE) {
            var raw = stubPreTree(request, resolve(request)); when(raw.supports()).thenReturn(Map.of(Pattern.empty(), 1));
        } else { var raw = stubBitPrune(request, resolve(request)); when(raw.supports()).thenReturn(Map.of(Pattern.empty(), 1)); }
        assertThrows(IllegalArgumentException.class, () -> adapter().execute(request)); verify(sampler).close();
    }

    @ParameterizedTest @EnumSource(MiningAlgorithm.class)
    void closeFailureAfterMiningIsNotReportedAsSuccess(MiningAlgorithm algorithm) throws Exception {
        var request = request(algorithm, MinimumSupport.absolute(.1));
        if (algorithm == MiningAlgorithm.WSPM_PRETREE) stubPreTree(request, resolve(request)); else stubBitPrune(request, resolve(request));
        var failure = new IllegalStateException("controlled close failure"); doThrow(failure).when(sampler).close();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter().execute(request)));
    }

    @Test void samplerFactoryFailureNeverInvokesMiner() throws Exception {
        var request = request(MiningAlgorithm.WSPM_PRETREE, MinimumSupport.absolute(.1));
        var failure = new IllegalStateException("controlled sampler failure");
        var adapter = new CoreMiningEngineAdapter(new RunConfigurationValidityGate(), () -> preTree, () -> bitPrune, () -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> adapter.execute(request)));
        verifyNoInteractions(preTree, bitPrune, sampler);
    }

    @Test void repeatedCallsCreateFreshMinersAndSamplersWithoutCachingResults() throws Exception {
        var request = request(MiningAlgorithm.WSPM_PRETREE, MinimumSupport.absolute(.1));
        var miners = new ArrayList<WspmPreTreeMiner>(); var samplers = new ArrayList<ExecutionMemorySampler>();
        Supplier<WspmPreTreeMiner> factory = () -> { var miner = spy(new WspmPreTreeMiner()); miners.add(miner); return miner; };
        var adapter = new CoreMiningEngineAdapter(new RunConfigurationValidityGate(), factory, () -> bitPrune,
                () -> { var sampler = spy(new ExecutionMemorySampler()); samplers.add(sampler); return sampler; });
        try {
            var first = adapter.execute(request); var second = adapter.execute(request);
            assertEquals(first.supports(), second.supports()); assertNotSame(first.supports(), second.supports());
            assertNotSame(miners.get(0), miners.get(1)); assertNotSame(samplers.get(0), samplers.get(1));
            for (var resource : samplers) verify(resource).close(); verifyNoInteractions(bitPrune);
        } finally { for (var resource : samplers) resource.close(); }
    }
}
