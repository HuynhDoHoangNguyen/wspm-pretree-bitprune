package vn.edu.wspm.backend.services.run.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.measurement.PhaseExecutionMetrics;
import vn.edu.wspm.mining.WspmMiningStatistics;
import vn.edu.wspm.model.Pattern;

/**
 * Completed application result. Not a public JSON DTO or a persistence entity.
 * Only structural patterns/supports and immutable observations cross this boundary.
 * This carrier checks consistency, not completeness against the input database;
 * adapter differential tests must establish the latter.
 */
public record MiningEngineResult(
        MiningAlgorithm algorithm,
        Map<Pattern, Integer> supports,
        ResolvedMinimumSupport minimumSupport,
        int distinctItemCount,
        double globalMeanWeight,
        Statistics statistics,
        Metrics metrics) {

    public MiningEngineResult {
        Objects.requireNonNull(algorithm, "algorithm is required");
        Objects.requireNonNull(supports, "supports are required");
        Objects.requireNonNull(minimumSupport, "resolved minimum support is required");
        Objects.requireNonNull(statistics, "statistics are required");
        Objects.requireNonNull(metrics, "metrics are required");
        int sequenceCount = minimumSupport.numberOfSequences();
        if (sequenceCount <= 0 || !Double.isFinite(minimumSupport.effectiveThreshold())
                || minimumSupport.effectiveThreshold() <= 0.0) {
            throw new IllegalArgumentException("result requires positive effective threshold and sequence count");
        }
        if (distinctItemCount <= 0 || !Double.isFinite(globalMeanWeight) || globalMeanWeight <= 0.0) {
            throw new IllegalArgumentException("non-empty database requires item metadata and positive finite MeanW");
        }
        if (statistics.algorithm() != algorithm || statistics.sequenceCount() != sequenceCount
                || statistics.patternCount() != supports.size()) {
            throw new IllegalArgumentException("algorithm, counts and statistics must agree");
        }
        var snapshot = new LinkedHashMap<Pattern, Integer>();
        supports.forEach((pattern, support) -> {
            Objects.requireNonNull(pattern, "pattern is required");
            Objects.requireNonNull(support, "support is required");
            if (pattern.isEmpty() || support <= 0 || support > sequenceCount) {
                throw new IllegalArgumentException("only non-root patterns with exact positive sequence supports are allowed");
            }
            snapshot.put(pattern, support);
        });
        supports = Collections.unmodifiableMap(snapshot);
    }

    public long patternCount() { return statistics.patternCount(); }

    /** Original counters are retained; no unsupported counters are invented. */
    public sealed interface Statistics permits PreTreeStatistics, BitPruneStatisticsView {
        MiningAlgorithm algorithm();
        long sequenceCount();
        long patternCount();
        long candidateCount();
    }

    public record PreTreeStatistics(WspmMiningStatistics original) implements Statistics {
        public PreTreeStatistics { Objects.requireNonNull(original, "statistics are required"); }
        public MiningAlgorithm algorithm() { return MiningAlgorithm.WSPM_PRETREE; }
        public long sequenceCount() { return original.sequenceCount(); }
        public long patternCount() { return original.patternCount(); }
        public long candidateCount() { return original.candidateCount(); }
    }

    public record BitPruneStatisticsView(BitPruneStatistics original) implements Statistics {
        public BitPruneStatisticsView { Objects.requireNonNull(original, "statistics are required"); }
        public MiningAlgorithm algorithm() { return MiningAlgorithm.WSPM_BITPRUNE; }
        public long sequenceCount() { return original.sequenceCount(); }
        public long patternCount() { return original.patternCount(); }
        public long candidateCount() { return original.generatedExtensions(); }
    }

    /**
     * Algorithm observations in nanoseconds/bytes, including finalization but excluding
     * dataset I/O, output I/O, queue wait and application projection/copying.
     * Heap is sampled JVM-wide used heap during this algorithm, not run-private RAM.
     */
    public record Metrics(
            PhaseExecutionMetrics preprocessing,
            PhaseExecutionMetrics dfsMining,
            long completeAlgorithmNanos,
            long heapAfterMiningBytes,
            long observedPeakHeapAlgorithmBytes) {
        public Metrics {
            Objects.requireNonNull(preprocessing, "preprocessing metrics are required");
            Objects.requireNonNull(dfsMining, "DFS metrics are required");
            if (completeAlgorithmNanos < Math.addExact(preprocessing.elapsedNanos(), dfsMining.elapsedNanos())
                    || heapAfterMiningBytes < 0
                    || observedPeakHeapAlgorithmBytes < Math.max(heapAfterMiningBytes,
                            Math.max(preprocessing.observedPeakHeapBytes(), dfsMining.observedPeakHeapBytes()))) {
                throw new IllegalArgumentException("algorithm metrics must contain phase times and heap observations");
            }
        }
    }
}
