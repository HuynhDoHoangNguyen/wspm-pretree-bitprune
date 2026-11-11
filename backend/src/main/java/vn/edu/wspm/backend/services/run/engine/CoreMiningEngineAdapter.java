package vn.edu.wspm.backend.services.run.engine;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.measurement.ExecutionMemorySampler;
import vn.edu.wspm.mining.ResultRetentionPolicy;
import vn.edu.wspm.mining.WspmPreTreeMiner;

/** Synchronous core adapter. No run lifecycle, I/O, queue or result cache is owned here. */
@Component
@Slf4j
public final class CoreMiningEngineAdapter implements MiningEngine {
    private final RunConfigurationValidityGate gate;
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();
    private final Supplier<WspmPreTreeMiner> preTrees;
    private final Supplier<BitPruneMiner> bitPrunes;
    private final Supplier<ExecutionMemorySampler> samplers;

    @Autowired
    public CoreMiningEngineAdapter(RunConfigurationValidityGate gate) {
        this(gate, WspmPreTreeMiner::new, BitPruneMiner::new, ExecutionMemorySampler::new);
    }

    /** Package-local resource factories allow lifecycle/dispatch verification without public hooks. */
    CoreMiningEngineAdapter(RunConfigurationValidityGate gate, Supplier<WspmPreTreeMiner> preTrees,
            Supplier<BitPruneMiner> bitPrunes, Supplier<ExecutionMemorySampler> samplers) {
        this.gate = Objects.requireNonNull(gate, "gate is required");
        this.preTrees = Objects.requireNonNull(preTrees, "PreTree factory is required");
        this.bitPrunes = Objects.requireNonNull(bitPrunes, "BitPrune factory is required");
        this.samplers = Objects.requireNonNull(samplers, "sampler factory is required");
    }

    @Override
    public MiningEngineResult execute(MiningEngineRequest request) {
        Objects.requireNonNull(request, "request is required");
        var resolved = gate.requireSupported(resolver.resolve(request.minimumSupport(), request.database().size()));
        log.debug("Core mining started: algorithm={}, mode={}, sequenceCount={}",
                request.algorithm(), resolved.mode(), resolved.numberOfSequences());
        MiningEngineResult result;
        try (var sampler = Objects.requireNonNull(samplers.get(), "sampler is required")) {
            result = switch (request.algorithm()) {
                case WSPM_PRETREE -> {
                    var measured = preTrees.get().mineMeasured(request.database(), request.minimumSupport(),
                            sampler, ResultRetentionPolicy.SUPPORT_ONLY);
                    var raw = measured.result();
                    requireSameResolution(resolved, raw.minimumSupport());
                    var m = measured.metrics();
                    yield new MiningEngineResult(request.algorithm(), raw.supports(), raw.minimumSupport(),
                            raw.distinctItemCount(), raw.globalMeanWeight().orElseThrow(),
                            new MiningEngineResult.PreTreeStatistics(raw.statistics()),
                            new MiningEngineResult.Metrics(m.preprocessing(), m.dfsMining(), m.algorithmNanos(),
                                    m.heapAfterMiningBytes(), m.observedPeakHeapAlgorithmBytes()));
                }
                case WSPM_BITPRUNE -> {
                    var measured = bitPrunes.get().mineMeasured(request.database(), request.minimumSupport(), sampler);
                    var raw = measured.result();
                    var metadata = measured.metadata();
                    requireSameResolution(resolved, metadata.minimumSupport());
                    var m = measured.metrics();
                    yield new MiningEngineResult(request.algorithm(), raw.supports(), metadata.minimumSupport(),
                            metadata.distinctItemCount(), metadata.globalMeanWeight().orElseThrow(),
                            new MiningEngineResult.BitPruneStatisticsView(raw.statistics()),
                            new MiningEngineResult.Metrics(m.preprocessing(), m.dfsMining(), m.completeAlgorithmNanos(),
                                    m.heapAfterMiningBytes(), m.observedPeakHeapAlgorithmBytes()));
                }
            };
        }
        // The future run service owns run-ID/error logging; do not duplicate exception logs here.
        log.debug("Core mining completed: algorithm={}, sequenceCount={}, patternCount={}, candidateCount={}",
                result.algorithm(), resolved.numberOfSequences(), result.patternCount(), result.statistics().candidateCount());
        return result;
    }

    private static void requireSameResolution(ResolvedMinimumSupport expected, ResolvedMinimumSupport actual) {
        if (actual == null || actual.mode() != expected.mode()
                || actual.numberOfSequences() != expected.numberOfSequences()
                || !sameBits(actual.configuredValue(), expected.configuredValue())
                || !sameOptionalBits(actual.relativeMinimumSupport(), expected.relativeMinimumSupport())
                || !sameBits(actual.effectiveThreshold(), expected.effectiveThreshold())) {
            throw new IllegalStateException("Core resolved metadata differs from execution preflight");
        }
    }

    private static boolean sameOptionalBits(OptionalDouble actual, OptionalDouble expected) {
        return actual != null && actual.isPresent() == expected.isPresent()
                && (actual.isEmpty() || sameBits(actual.getAsDouble(), expected.getAsDouble()));
    }

    private static boolean sameBits(double actual, double expected) {
        return Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected);
    }
}
