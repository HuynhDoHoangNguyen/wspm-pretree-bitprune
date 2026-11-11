package vn.edu.wspm.bitprune.cmap;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import vn.edu.wspm.model.Item;

/**
 * Immutable distinct-SID pair support for canonical same-itemset I-extension pairs.
 *
 * <p>The distinct-SID count extension is specified in
 * docs/research/wspm-bitprune-cmap-distinct-sid-contract.md. The historical
 * boolean relation below is retained by contains as the count > 0 projection.
 *
 * <p><strong>C01/C02-ACCEPTED CONTRACT:</strong> {@code CmapI(a, b)} is true exactly when
 * at least one database itemset contains both {@code a} and {@code b}, with the canonical
 * I-extension constraint {@code a < b}. A true result is only a necessary condition and does not
 * replace exact temporal verification.
 *
 * <p><strong>IMPLEMENTATION INFERENCE — OWNER APPROVED FOR C05.1:</strong> queries fail fast when
 * either item is outside the caller-supplied F1 universe or when the pair is not canonical. The
 * query never swaps, normalizes, or silently treats invalid input as a negative relation.
 */
public final class CmapI {

    private final NavigableSet<Item> f1Universe;
    private final Map<Long, Integer> supportCounts;

    CmapI(Set<Item> f1Universe, Map<Long, Integer> supportCounts) {
        Objects.requireNonNull(f1Universe, "F1 universe must not be null");
        Objects.requireNonNull(supportCounts, "support counts must not be null");
        if (supportCounts.values().stream().anyMatch(count -> count == null || count <= 0)) {
            throw new IllegalArgumentException("stored support counts must be positive");
        }

        this.f1Universe = Collections.unmodifiableNavigableSet(new TreeSet<>(f1Universe));
        this.supportCounts = Map.copyOf(supportCounts);
    }

    /**
     * Reports whether the canonical pair has same-itemset evidence in the source database.
     *
     * @param left  canonical left item, which must belong to the supplied F1 universe
     * @param right canonical right item, which must belong to the supplied F1 universe
     * @return {@code true} only when at least one scanned itemset contained both items
     * @throws NullPointerException if either item is null
     * @throws IllegalArgumentException if an item is outside F1 or {@code left >= right}
     */
    public boolean contains(Item left, Item right) {
        return supportCount(left, right) > 0;
    }

    /**
     * Returns the number of distinct source SIDs supporting this pair, or zero.
     * Uses the same universe and pair validation as {@link #contains(Item, Item)}.
     * This is a pair-support upper bound, not exact support of a longer pattern.
     */
    public int supportCount(Item left, Item right) {
        Objects.requireNonNull(left, "left item must not be null");
        Objects.requireNonNull(right, "right item must not be null");

        if (!f1Universe.contains(left)) {
            throw new IllegalArgumentException("left item is outside the caller-supplied F1 universe: " + left.id());
        }
        if (!f1Universe.contains(right)) {
            throw new IllegalArgumentException("right item is outside the caller-supplied F1 universe: " + right.id());
        }
        if (left.compareTo(right) >= 0) {
            throw new IllegalArgumentException(
                    "CMAP-I query must be canonical (left < right): " + left.id() + ", " + right.id());
        }

        return supportCounts.getOrDefault(pairKey(left, right), 0);
    }

    /** Collision-free encoding for two non-negative 32-bit item identifiers. */
    static long pairKey(Item left, Item right) {
        return ((long) left.id() << 32) | (right.id() & 0xffff_ffffL);
    }
}
