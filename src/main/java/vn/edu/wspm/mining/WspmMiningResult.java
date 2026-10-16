package vn.edu.wspm.mining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.tree.PrefixTree;

/** Read-only metadata boundary for one completed WSPM_PreTree mining run. */
public final class WspmMiningResult {

    private final PrefixTree prefixTree;
    private final List<Pattern> acceptedPatterns;
    private final Map<Pattern, Integer> supports;
    private final Map<Pattern, PatternEncoding> encodings;
    private final OptionalDouble globalMeanWeight;
    private final ResolvedMinimumSupport minimumSupport;
    private final WspmMiningStatistics statistics;

    WspmMiningResult(
            PrefixTree prefixTree,
            Map<Pattern, PatternEncoding> encodings,
            OptionalDouble globalMeanWeight,
            ResolvedMinimumSupport minimumSupport,
            WspmMiningStatistics statistics) {
        this.prefixTree = Objects.requireNonNull(prefixTree, "prefix tree must not be null");
        Objects.requireNonNull(encodings, "encodings must not be null");
        Objects.requireNonNull(globalMeanWeight, "global MeanW optional must not be null");
        this.minimumSupport = Objects.requireNonNull(
                minimumSupport, "resolved minimum support must not be null");
        this.statistics = Objects.requireNonNull(statistics, "mining statistics must not be null");

        this.acceptedPatterns = prefixTree.preOrderPatterns().stream()
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        var treePatterns = new LinkedHashSet<>(acceptedPatterns);
        if (!treePatterns.equals(encodings.keySet())) {
            throw new IllegalArgumentException(
                    "encoding metadata must exactly match non-root Prefix Tree patterns");
        }

        var encodingSnapshot = new LinkedHashMap<Pattern, PatternEncoding>();
        var supportSnapshot = new LinkedHashMap<Pattern, Integer>();
        for (var pattern : acceptedPatterns) {
            var encoding = Objects.requireNonNull(
                    encodings.get(pattern), "encoding metadata must not contain null");
            if (encoding.sequenceBlock().sidCount() != minimumSupport.numberOfSequences()) {
                throw new IllegalArgumentException(
                        "encoding and minimum support use different SID universes for "
                                + pattern.serialize());
            }
            encodingSnapshot.put(pattern, encoding);
            supportSnapshot.put(pattern, encoding.sequenceBlock().support());
        }
        this.encodings = Collections.unmodifiableMap(encodingSnapshot);
        this.supports = Collections.unmodifiableMap(supportSnapshot);
        this.globalMeanWeight = globalMeanWeight.isPresent()
                ? OptionalDouble.of(globalMeanWeight.getAsDouble())
                : OptionalDouble.empty();

        if (statistics.sequenceCount() != minimumSupport.numberOfSequences()) {
            throw new IllegalArgumentException(
                    "statistics and minimum support use different sequence counts");
        }
        if (statistics.f1Count() != prefixTree.root().children().size()) {
            throw new IllegalArgumentException(
                    "F1 statistics must match the Prefix Tree root-child count");
        }
        if (statistics.patternCount() != acceptedPatterns.size()
                || statistics.patternCount() != prefixTree.nodeCount() - 1) {
            throw new IllegalArgumentException(
                    "pattern statistics must match the non-root Prefix Tree nodes");
        }
    }

    public PrefixTree prefixTree() {
        return prefixTree;
    }

    /** Returns accepted patterns in the Prefix Tree's deterministic structural pre-order. */
    public List<Pattern> acceptedPatterns() {
        return acceptedPatterns;
    }

    public Map<Pattern, Integer> supports() {
        return supports;
    }

    public Map<Pattern, PatternEncoding> encodings() {
        return encodings;
    }

    public int support(Pattern pattern) {
        Integer support = supports.get(Objects.requireNonNull(pattern, "pattern must not be null"));
        if (support == null) {
            throw new IllegalArgumentException("pattern is not an accepted result: " + pattern);
        }
        return support;
    }

    public PatternEncoding encoding(Pattern pattern) {
        var encoding = encodings.get(Objects.requireNonNull(pattern, "pattern must not be null"));
        if (encoding == null) {
            throw new IllegalArgumentException("pattern is not an accepted result: " + pattern);
        }
        return encoding;
    }

    /** Empty only when the input database has no items and B6 defines no MeanW. */
    public OptionalDouble globalMeanWeight() {
        return globalMeanWeight.isPresent()
                ? OptionalDouble.of(globalMeanWeight.getAsDouble())
                : OptionalDouble.empty();
    }

    public ResolvedMinimumSupport minimumSupport() {
        return minimumSupport;
    }

    public WspmMiningStatistics statistics() {
        return statistics;
    }
}
