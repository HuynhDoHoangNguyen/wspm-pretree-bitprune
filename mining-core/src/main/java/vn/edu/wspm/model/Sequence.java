package vn.edu.wspm.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** An immutable, ordered, non-empty list of itemsets. */
public final class Sequence {

    private final List<Itemset> itemsets;

    public Sequence(Collection<Itemset> itemsets) {
        Objects.requireNonNull(itemsets, "itemsets must not be null");
        if (itemsets.isEmpty()) {
            throw new IllegalArgumentException("a sequence must contain at least one itemset");
        }
        this.itemsets = List.copyOf(itemsets);
    }

    public static Sequence of(Itemset... itemsets) {
        Objects.requireNonNull(itemsets, "itemsets must not be null");
        return new Sequence(Arrays.asList(itemsets));
    }

    public List<Itemset> itemsets() {
        return itemsets;
    }

    /** Returns the number of itemsets, which the paper calls sequence size. */
    public int size() {
        return itemsets.size();
    }

    /** Returns the total number of items across all itemsets, which the paper calls sequence length. */
    public int length() {
        return itemsets.stream().mapToInt(Itemset::size).sum();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Sequence sequence && itemsets.equals(sequence.itemsets);
    }

    @Override
    public int hashCode() {
        return itemsets.hashCode();
    }
}
