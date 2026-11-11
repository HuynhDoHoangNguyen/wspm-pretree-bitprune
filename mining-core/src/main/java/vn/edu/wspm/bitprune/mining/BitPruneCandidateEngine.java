package vn.edu.wspm.bitprune.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.tree.ExtensionType;

/**
 * Deterministic generator core for structural candidate patterns in
 * WSPM_BitPrune.
 *
 * <p>
 * Stateless and independent of PrefixTree mutations, bitmaps, support counting,
 * and PRISM joins.
 * For a non-empty parent pattern and a set of frequent 1-patterns F1:
 * <ol>
 * <li>Validates parent is non-null and non-empty.</li>
 * <li>Validates F1 is non-null, contains no nulls, and has unique item IDs
 * (fails fast on duplicates).</li>
 * <li>Creates a defensive snapshot of F1 sorted in ascending numeric item ID
 * order.</li>
 * <li>Emits all eligible I-Extensions first (where itemId &gt;
 * parent.lastItem().id()).</li>
 * <li>Emits all S-Extensions second (for every item in F1).</li>
 * <li>Enforces candidate pattern identity uniqueness (fails fast with
 * IllegalStateException on duplicates).</li>
 * </ol>
 * </p>
 */
public final class BitPruneCandidateEngine {

    private final PatternExtensions extensions = new PatternExtensions();

    /**
     * Generates all structural candidate extensions for the given parent pattern
     * from F1.
     *
     * @param parent non-null and non-empty sequential pattern to extend
     * @param f1     non-null list of weighted 1-patterns without nulls or duplicate
     *               item IDs
     * @return immutable list of candidate results with I-Extensions preceding
     *         S-Extensions
     * @throws NullPointerException     if {@code parent}, {@code f1}, or any
     *                                  element of {@code f1} is null
     * @throws IllegalArgumentException if {@code parent} is empty or {@code f1}
     *                                  contains duplicate item IDs
     * @throws IllegalStateException    if an internal duplicate candidate pattern
     *                                  is produced
     */
    public List<BitPruneCandidateResult> generateCandidates(
            Pattern parent,
            List<WeightedOnePattern> f1) {
        Objects.requireNonNull(parent, "parent pattern must not be null");
        if (parent.isEmpty()) {
            throw new IllegalArgumentException("parent pattern must not be empty");
        }
        Objects.requireNonNull(f1, "f1 must not be null");

        if (f1.isEmpty()) {
            return List.of();
        }

        var seenItemIds = new HashSet<Integer>(f1.size());
        for (var onePattern : f1) {
            Objects.requireNonNull(onePattern, "f1 element must not be null");
            if (!seenItemIds.add(onePattern.itemId())) {
                throw new IllegalArgumentException(
                        "duplicate item id in F1: " + onePattern.itemId());
            }
        }

        var sortedF1 = new ArrayList<>(f1);
        sortedF1.sort(Comparator.comparingInt(WeightedOnePattern::itemId));

        int lastItemId = parent.lastItem().orElseThrow().id();
        var results = new ArrayList<BitPruneCandidateResult>(sortedF1.size() * 2);
        var seenPatterns = new HashSet<Pattern>();

        // 1. Emit all eligible I-extensions first (itemId > parent.lastItem().id())
        for (var onePattern : sortedF1) {
            int itemId = onePattern.itemId();
            if (itemId > lastItemId) {
                var extensionItem = new Item(itemId);
                var candidatePattern = extensions.iExtend(parent, extensionItem);
                if (!seenPatterns.add(candidatePattern)) {
                    throw new IllegalStateException(
                            "duplicate candidate pattern generated: " + candidatePattern.serialize());
                }
                results.add(new BitPruneCandidateResult(
                        candidatePattern, ExtensionType.I_EXTENSION, extensionItem));
            }
        }

        // 2. Emit all S-extensions second
        for (var onePattern : sortedF1) {
            int itemId = onePattern.itemId();
            var extensionItem = new Item(itemId);
            var candidatePattern = extensions.sExtend(parent, extensionItem);
            if (!seenPatterns.add(candidatePattern)) {
                throw new IllegalStateException(
                        "duplicate candidate pattern generated: " + candidatePattern.serialize());
            }
            results.add(new BitPruneCandidateResult(
                    candidatePattern, ExtensionType.S_EXTENSION, extensionItem));
        }

        return List.copyOf(results);
    }
}
