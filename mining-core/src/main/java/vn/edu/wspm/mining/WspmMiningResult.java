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
    private final ResultRetentionPolicy retentionPolicy;
    private final OptionalDouble globalMeanWeight;
    private final int distinctItemCount;
    private final ResolvedMinimumSupport minimumSupport;
    private final WspmMiningStatistics statistics;

    WspmMiningResult(
            PrefixTree prefixTree,
            Map<Pattern, PatternEncoding> encodings,
            OptionalDouble globalMeanWeight,
            int distinctItemCount,
            ResolvedMinimumSupport minimumSupport,
            WspmMiningStatistics statistics) {
        this(
                prefixTree,
                extractSupports(encodings),
                encodings,
                ResultRetentionPolicy.FULL_ENCODING,
                globalMeanWeight,
                distinctItemCount,
                minimumSupport,
                statistics);
    }

    WspmMiningResult(
            PrefixTree prefixTree,
            Map<Pattern, Integer> supports,
            Map<Pattern, PatternEncoding> encodings,
            ResultRetentionPolicy retentionPolicy,
            OptionalDouble globalMeanWeight,
            int distinctItemCount,
            ResolvedMinimumSupport minimumSupport,
            WspmMiningStatistics statistics) {
        this.prefixTree = Objects.requireNonNull(prefixTree, "prefix tree must not be null");
        Objects.requireNonNull(supports, "supports must not be null");
        this.retentionPolicy = Objects.requireNonNull(
                retentionPolicy, "result retention policy must not be null");
        Objects.requireNonNull(globalMeanWeight, "global MeanW optional must not be null");
        if (distinctItemCount < 0) {
            throw new IllegalArgumentException("distinct item count must not be negative");
        }
        this.distinctItemCount = distinctItemCount;
        this.minimumSupport = Objects.requireNonNull(
                minimumSupport, "resolved minimum support must not be null");
        this.statistics = Objects.requireNonNull(statistics, "mining statistics must not be null");

        this.acceptedPatterns = prefixTree.preOrderPatterns().stream()
                .filter(pattern -> !pattern.isEmpty())
                .toList();
        var treePatterns = new LinkedHashSet<>(acceptedPatterns);
        if (!treePatterns.equals(supports.keySet())) {
            throw new IllegalArgumentException(
                    "support metadata must exactly match non-root Prefix Tree patterns");
        }

        var supportSnapshot = new LinkedHashMap<Pattern, Integer>();
        for (var pattern : acceptedPatterns) {
            var support = Objects.requireNonNull(
                    supports.get(pattern), "support must not contain null");
            if (support < 0) {
                throw new IllegalArgumentException("support must not be negative");
            }
            supportSnapshot.put(pattern, support);
        }
        this.supports = Collections.unmodifiableMap(supportSnapshot);

        if (retentionPolicy == ResultRetentionPolicy.FULL_ENCODING) {
            Objects.requireNonNull(encodings, "encodings must not be null in FULL_ENCODING mode");
            if (!treePatterns.equals(encodings.keySet())) {
                throw new IllegalArgumentException(
                        "encoding metadata must exactly match non-root Prefix Tree patterns");
            }
            var encodingSnapshot = new LinkedHashMap<Pattern, PatternEncoding>();
            for (var pattern : acceptedPatterns) {
                var encoding = Objects.requireNonNull(
                        encodings.get(pattern), "encoding metadata must not contain null");
                if (encoding.sequenceBlock().sidCount() != minimumSupport.numberOfSequences()) {
                    throw new IllegalArgumentException(
                            "encoding and minimum support use different SID universes for "
                                    + pattern.serialize());
                }
                if (encoding.sequenceBlock().support() != supportSnapshot.get(pattern)) {
                    throw new IllegalArgumentException(
                            "encoding support disagrees with declared support for "
                                    + pattern.serialize());
                }
                encodingSnapshot.put(pattern, encoding);
            }
            this.encodings = Collections.unmodifiableMap(encodingSnapshot);
        } else {
            this.encodings = null;
        }

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

    private static Map<Pattern, Integer> extractSupports(Map<Pattern, PatternEncoding> encodings) {
        Objects.requireNonNull(encodings, "encodings must not be null");
        var map = new LinkedHashMap<Pattern, Integer>();
        for (var entry : encodings.entrySet()) {
            map.put(entry.getKey(), entry.getValue().sequenceBlock().support());
        }
        return map;
    }

    public PrefixTree prefixTree() {
        return prefixTree;
    }

    /** Returns the result retention policy used during mining. */
    public ResultRetentionPolicy retentionPolicy() {
        return retentionPolicy;
    }

    /** True only if full compact Prime-block encodings are retained in this result. */
    public boolean hasEncodings() {
        return encodings != null;
    }

    /** Returns accepted patterns in the Prefix Tree's deterministic structural pre-order. */
    public List<Pattern> acceptedPatterns() {
        return acceptedPatterns;
    }

    public Map<Pattern, Integer> supports() {
        return supports;
    }

    /**
     * Returns full Prime-block encodings for all accepted patterns.
     *
     * @throws UnsupportedOperationException if retention policy is {@link ResultRetentionPolicy#SUPPORT_ONLY}
     */
    public Map<Pattern, PatternEncoding> encodings() {
        if (encodings == null) {
            throw new UnsupportedOperationException(
                    "Pattern encodings are not retained under " + retentionPolicy + " policy");
        }
        return encodings;
    }

    public int support(Pattern pattern) {
        Integer support = supports.get(Objects.requireNonNull(pattern, "pattern must not be null"));
        if (support == null) {
            throw new IllegalArgumentException("pattern is not an accepted result: " + pattern);
        }
        return support;
    }

    /**
     * Returns the full Prime-block encoding for {@code pattern}.
     *
     * @throws UnsupportedOperationException if retention policy is {@link ResultRetentionPolicy#SUPPORT_ONLY}
     */
    public PatternEncoding encoding(Pattern pattern) {
        if (encodings == null) {
            throw new UnsupportedOperationException(
                    "Pattern encodings are not retained under " + retentionPolicy + " policy");
        }
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

    public int distinctItemCount() {
        return distinctItemCount;
    }

    public ResolvedMinimumSupport minimumSupport() {
        return minimumSupport;
    }

    public WspmMiningStatistics statistics() {
        return statistics;
    }
}
