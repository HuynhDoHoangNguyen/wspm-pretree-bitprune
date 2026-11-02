package vn.edu.wspm.bitprune.result;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Minimal immutable completed-run result carrier for WSPM_BitPrune (C12.3).
 *
 * <p>
 * Encapsulates the frozen PrefixTree, unmodifiable accepted patterns in tree
 * pre-order,
 * an unmodifiable snapshot of exact supports preserving the miner's iteration
 * order,
 * and immutable execution statistics.
 * </p>
 */
public final class BitPruneMiningResult {

    private final PrefixTree prefixTree;
    private final List<Pattern> acceptedPatterns;
    private final Map<Pattern, Integer> supports;
    private final BitPruneStatistics statistics;

    public BitPruneMiningResult(
            PrefixTree prefixTree,
            Map<Pattern, Integer> supports,
            BitPruneStatistics statistics) {

        this.prefixTree = Objects.requireNonNull(prefixTree, "prefixTree must not be null");
        Objects.requireNonNull(supports, "supports must not be null");
        this.statistics = Objects.requireNonNull(statistics, "statistics must not be null");

        if (!prefixTree.isFrozen()) {
            throw new IllegalArgumentException("prefixTree must be frozen");
        }

        this.acceptedPatterns = prefixTree.preOrderPatterns().stream()
                .filter(pattern -> !pattern.isEmpty())
                .toList();

        Set<Pattern> nonRootTreePatterns = new LinkedHashSet<>(this.acceptedPatterns);
        if (!nonRootTreePatterns.equals(supports.keySet())) {
            throw new IllegalArgumentException(
                    "support keys must exactly match non-root Prefix Tree patterns");
        }

        Map<Pattern, Integer> supportSnapshot = new LinkedHashMap<>(supports.size());
        for (Map.Entry<Pattern, Integer> entry : supports.entrySet()) {
            Pattern pattern = Objects.requireNonNull(entry.getKey(), "support key must not be null");
            Integer support = Objects.requireNonNull(
                    entry.getValue(), "support value must not be null for pattern: " + pattern);
            if (support < 0) {
                throw new IllegalArgumentException(
                        "support value must not be negative for pattern: " + pattern + ", got: " + support);
            }
            supportSnapshot.put(pattern, support);
        }
        this.supports = Collections.unmodifiableMap(supportSnapshot);

        long f1Count = statistics.f1Count();
        long rootChildCount = (long) prefixTree.root().children().size();
        if (f1Count != rootChildCount) {
            throw new IllegalArgumentException(
                    "statistics.f1Count (" + f1Count
                            + ") must equal prefixTree root children count ("
                            + rootChildCount + ")");
        }

        long patternCount = statistics.patternCount();
        long acceptedSize = (long) this.acceptedPatterns.size();
        long supportSize = (long) this.supports.size();
        long treeNonRootCount = (long) prefixTree.nodeCount() - 1L;

        if (patternCount != acceptedSize || patternCount != supportSize || patternCount != treeNonRootCount) {
            throw new IllegalArgumentException(
                    "statistics.patternCount (" + patternCount
                            + ") must equal acceptedPatterns size (" + acceptedSize
                            + "), supports size (" + supportSize
                            + "), and tree non-root node count (" + treeNonRootCount + ")");
        }
    }

    public PrefixTree prefixTree() {
        return prefixTree;
    }

    public List<Pattern> acceptedPatterns() {
        return acceptedPatterns;
    }

    public Map<Pattern, Integer> supports() {
        return supports;
    }

    public int support(Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Integer support = supports.get(pattern);
        if (support == null) {
            throw new IllegalArgumentException("pattern is not an accepted result: " + pattern);
        }
        return support;
    }

    public BitPruneStatistics statistics() {
        return statistics;
    }
}
