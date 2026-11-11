package vn.edu.wspm.model;

import java.util.Objects;

/**
 * An immutable item identity.
 *
 * <p>B2.1 implementation inference: non-negative integer identifiers match the numeric identifiers
 * used by SPMF datasets. Numeric order is used only to give itemsets a deterministic canonical
 * order. B9/B10 must separately confirm the ordering used for mining extensions.</p>
 */
public record Item(int id) implements Comparable<Item> {

    public Item {
        if (id < 0) {
            throw new IllegalArgumentException("item id must not be negative: " + id);
        }
    }

    @Override
    public int compareTo(Item other) {
        Objects.requireNonNull(other, "other item must not be null");
        return Integer.compare(id, other.id);
    }
}
