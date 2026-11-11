package vn.edu.wspm.bitprune.cmap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import vn.edu.wspm.model.Item;

/**
 * Immutable distinct-SID pair support for directed S-extension pairs.
 *
 * <p>The distinct-SID count extension is specified in
 * docs/research/wspm-bitprune-cmap-distinct-sid-contract.md. The historical
 * boolean relation below is retained by contains as the count > 0 projection.
 *
 * <p>
 * <strong>C01/C02-ACCEPTED CONTRACT:</strong> {@code CmapS(a, b)} is true if
 * and only if
 * there exists at least one sequence {@code s} in the scanned database with
 * itemset positions
 * {@code t < u} such that {@code a ∈ s[t]} and {@code b ∈ s[u]}.
 * A true result is only a necessary condition and does not replace exact
 * temporal verification.
 *
 * <p>
 * <strong>MATHEMATICAL INVARIANTS:</strong>
 * <ul>
 * <li>Directed relation: {@code (a, b)} does not imply {@code (b, a)}.
 * <li>Strict temporal precedence: occurrences in the same itemset
 * ({@code t == u}) do not satisfy {@code t < u}.
 * <li>Same-sequence boundary: occurrences across different sequences (different
 * SIDs) do not satisfy the relation.
 * <li>Self-pair validity: {@code (a, a)} is valid if item {@code a} occurs in
 * two distinct itemsets {@code t < u} within the same sequence.
 * </ul>
 *
 * <p>
 * <strong>IMPLEMENTATION INFERENCE — OWNER APPROVED FOR C05.2:</strong>
 * <ul>
 * <li>{@code contains(left, right)} accepts all ordered pairs in the
 * caller-supplied F1 universe,
 * including {@code left < right}, {@code left > right}, and
 * {@code left == right}.
 * No canonical ordering or swapping is applied.
 * <li>Queries fail fast with {@link NullPointerException} if either item is
 * null.
 * <li>Queries fail fast with {@link IllegalArgumentException} if either item is
 * outside the F1 universe.
 * <li>Valid pairs without temporal evidence return {@code false}.
 * </ul>
 */
public final class CmapS {

    private final NavigableSet<Item> f1Universe;
    private final Map<Long, Integer> supportCounts;

    CmapS(Set<Item> f1Universe, Map<Long, Integer> supportCounts) {
        Objects.requireNonNull(f1Universe, "F1 universe must not be null");
        Objects.requireNonNull(supportCounts, "support counts must not be null");
        if (supportCounts.values().stream().anyMatch(count -> count == null || count <= 0)) {
            throw new IllegalArgumentException("stored support counts must be positive");
        }

        this.f1Universe = Collections.unmodifiableNavigableSet(new TreeSet<>(f1Universe));
        // Packed pair keys can share Long hashes. HashMap handles these collisions
        // without the linear probing used by Map.copyOf; retain a defensive snapshot.
        this.supportCounts = Collections.unmodifiableMap(new HashMap<>(supportCounts));
    }

    /**
     * Reports whether the directed pair {@code (left, right)} has strict temporal
     * precedence evidence
     * ({@code t < u}) in the source database.
     *
     * @param left  source item, which must belong to the supplied F1 universe
     * @param right target item, which must belong to the supplied F1 universe
     * @return {@code true} only when at least one scanned sequence contained
     *         {@code left} at position {@code t}
     *         and {@code right} at position {@code u} with {@code t < u}
     * @throws NullPointerException     if either item is null
     * @throws IllegalArgumentException if an item is outside the caller-supplied F1
     *                                  universe
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

        return supportCounts.getOrDefault(pairKey(left, right), 0);
    }

    /**
     * Collision-free directed encoding for two non-negative 32-bit item
     * identifiers.
     */
    static long pairKey(Item left, Item right) {
        return ((long) left.id() << 32) | (right.id() & 0xffff_ffffL);
    }
}
