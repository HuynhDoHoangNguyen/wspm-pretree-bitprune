package vn.edu.wspm.mining;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/** Orchestrates the source-defined DFS construction of the baseline WSPM Prefix Tree. */
public final class WspmPreTreeMiner {

    private final ItemStatisticsCalculator statisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator weightCalculator = new ItemWeightCalculator();
    private final MinimumSupportResolver minimumSupportResolver = new MinimumSupportResolver();
    private final F1Builder f1Builder = new F1Builder();
    private final SingletonEncodingBuilder singletonEncodingBuilder =
            new SingletonEncodingBuilder();
    private final CandidateEngine candidateEngine = new CandidateEngine();

    /** Mines all weighted sequential patterns accepted by the existing baseline components. */
    public WspmMiningResult mine(
            SequenceDatabase database, MinimumSupport minimumSupportConfiguration) {
        Objects.requireNonNull(database, "sequence database must not be null");
        Objects.requireNonNull(
                minimumSupportConfiguration, "minimum support configuration must not be null");
        long startedAt = System.nanoTime();

        var statistics = statisticsCalculator.calculate(database);
        var weights = weightCalculator.calculate(statistics);
        var minimumSupport = minimumSupportResolver.resolve(
                minimumSupportConfiguration, statistics.numberOfSequences());
        var f1 = f1Builder.build(statistics, weights, minimumSupport);
        var singletonEncodings = singletonEncodingBuilder.build(database, f1);
        var miningStatistics = new MiningStatisticsAccumulator(database.size(), f1.size());

        var tree = new PrefixTree();
        var encodings = new LinkedHashMap<Pattern, PatternEncoding>();
        for (var onePattern : f1) {
            var pattern = Pattern.singleton(new Item(onePattern.itemId()));
            tree.insert(Pattern.empty(), pattern, ExtensionType.S_EXTENSION);
            encodings.put(pattern, singletonEncodings.get(onePattern.itemId()));
        }

        if (!f1.isEmpty()) {
            double meanWeight = weights.meanWeight().orElseThrow();
            int maximumSupportedPatternLength = maximumSequenceLength(database);
            for (var onePattern : f1) {
                var pattern = Pattern.singleton(new Item(onePattern.itemId()));
                depthFirstExpand(
                        pattern,
                        encodings.get(pattern),
                        maximumSupportedPatternLength,
                        tree,
                        encodings,
                        f1,
                        singletonEncodings,
                        meanWeight,
                        minimumSupport,
                        miningStatistics);
            }
        }

        tree.freeze();
        int patternCount = tree.nodeCount() - 1;
        long elapsedNanos = Math.max(0L, System.nanoTime() - startedAt);
        return new WspmMiningResult(
                tree,
                encodings,
                weights.meanWeight(),
                minimumSupport,
                miningStatistics.finish(patternCount, elapsedNanos));
    }

    private void depthFirstExpand(
            Pattern parent,
            PatternEncoding parentEncoding,
            int maximumSupportedPatternLength,
            PrefixTree tree,
            Map<Pattern, PatternEncoding> encodings,
            java.util.List<WeightedOnePattern> f1,
            Map<Integer, PatternEncoding> singletonEncodings,
            double meanWeight,
            vn.edu.wspm.config.ResolvedMinimumSupport minimumSupport,
            MiningStatisticsAccumulator statistics) {
        statistics.recordExpandedNode();
        // A subsequence cannot contain more item occurrences than its supporting sequence.
        // This data-derived structural bound makes zero-threshold runs finite without imposing an
        // arbitrary experimental maximum pattern length.
        if (parent.length() >= maximumSupportedPatternLength) {
            return;
        }

        var candidates = candidateEngine.generateOneStep(
                parent,
                parentEncoding,
                f1,
                singletonEncodings,
                meanWeight,
                minimumSupport);
        for (var candidate : candidates) {
            statistics.recordCandidate(candidate);
            if (!candidate.accepted()) {
                continue;
            }
            if (encodings.containsKey(candidate.pattern())) {
                throw new IllegalStateException(
                        "duplicate pattern reached by DFS: " + candidate.pattern().serialize());
            }

            tree.insert(parent, candidate.pattern(), candidate.extensionType());
            encodings.put(candidate.pattern(), candidate.encoding());
            depthFirstExpand(
                    candidate.pattern(),
                    candidate.encoding(),
                    maximumSupportedPatternLength,
                    tree,
                    encodings,
                    f1,
                    singletonEncodings,
                    meanWeight,
                    minimumSupport,
                    statistics);
        }
    }

    /** Per-run mutable instrumentation; it never crosses the public result boundary. */
    private static final class MiningStatisticsAccumulator {

        private final int sequenceCount;
        private final int f1Count;
        private long candidateCount;
        private long acceptedCandidateCount;
        private long rejectedCandidateCount;
        private long iCandidateCount;
        private long sCandidateCount;
        private long expandedNodeCount;

        private MiningStatisticsAccumulator(int sequenceCount, int f1Count) {
            this.sequenceCount = sequenceCount;
            this.f1Count = f1Count;
        }

        private void recordExpandedNode() {
            expandedNodeCount = Math.addExact(expandedNodeCount, 1L);
        }

        private void recordCandidate(CandidateResult candidate) {
            candidateCount = Math.addExact(candidateCount, 1L);
            if (candidate.accepted()) {
                acceptedCandidateCount = Math.addExact(acceptedCandidateCount, 1L);
            } else {
                rejectedCandidateCount = Math.addExact(rejectedCandidateCount, 1L);
            }
            switch (candidate.extensionType()) {
                case I_EXTENSION -> iCandidateCount = Math.addExact(iCandidateCount, 1L);
                case S_EXTENSION -> sCandidateCount = Math.addExact(sCandidateCount, 1L);
            }
        }

        private WspmMiningStatistics finish(int patternCount, long elapsedNanos) {
            return new WspmMiningStatistics(
                    sequenceCount,
                    f1Count,
                    candidateCount,
                    acceptedCandidateCount,
                    rejectedCandidateCount,
                    iCandidateCount,
                    sCandidateCount,
                    expandedNodeCount,
                    patternCount,
                    elapsedNanos);
        }
    }

    private static int maximumSequenceLength(SequenceDatabase database) {
        int maximum = 0;
        for (var record : database.records()) {
            int sequenceLength = 0;
            for (var itemset : record.sequence().itemsets()) {
                sequenceLength = Math.addExact(sequenceLength, itemset.size());
            }
            maximum = Math.max(maximum, sequenceLength);
        }
        return maximum;
    }
}
