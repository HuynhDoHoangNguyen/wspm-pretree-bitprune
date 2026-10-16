package vn.edu.wspm.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable structural identity of a sequential pattern.
 *
 * <p>A pattern contains only ordered itemsets. Support, weights, encodings, tree relationships,
 * and other mining state deliberately remain outside this value object.</p>
 */
public final class Pattern {

    private static final Pattern EMPTY = new Pattern(List.of());

    private final List<Itemset> itemsets;
    private final int length;

    public Pattern(Collection<Itemset> itemsets) {
        Objects.requireNonNull(itemsets, "pattern itemsets must not be null");
        for (var itemset : itemsets) {
            Objects.requireNonNull(itemset, "a pattern must not contain a null itemset");
        }
        this.itemsets = List.copyOf(itemsets);
        this.length = this.itemsets.stream().mapToInt(Itemset::size).sum();
    }

    /** Returns the structural empty pattern used for the future Prefix Tree root. */
    public static Pattern empty() {
        return EMPTY;
    }

    public static Pattern of(Itemset... itemsets) {
        Objects.requireNonNull(itemsets, "pattern itemsets must not be null");
        return itemsets.length == 0 ? empty() : new Pattern(Arrays.asList(itemsets));
    }

    public static Pattern singleton(Item item) {
        return of(Itemset.of(Objects.requireNonNull(item, "item must not be null")));
    }

    /** Returns itemsets in temporal order through an immutable list. */
    public List<Itemset> itemsets() {
        return itemsets;
    }

    public boolean isEmpty() {
        return itemsets.isEmpty();
    }

    /** Returns the number of itemsets, which the paper calls sequence size. */
    public int size() {
        return itemsets.size();
    }

    /** Returns the total number of items, which the paper calls sequence length. */
    public int length() {
        return length;
    }

    public Optional<Itemset> lastItemset() {
        return isEmpty() ? Optional.empty() : Optional.of(itemsets.getLast());
    }

    /** Returns the numerically greatest item in the canonical final itemset. */
    public Optional<Item> lastItem() {
        return lastItemset().map(itemset -> itemset.items().getLast());
    }

    /** Returns the stable numeric representation used by tests, logs, and later export. */
    public String serialize() {
        var result = new StringBuilder("<");
        for (var itemset : itemsets) {
            result.append('(');
            for (int index = 0; index < itemset.items().size(); index++) {
                if (index > 0) {
                    result.append(' ');
                }
                result.append(itemset.items().get(index).id());
            }
            result.append(')');
        }
        return result.append('>').toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Pattern pattern && itemsets.equals(pattern.itemsets);
    }

    @Override
    public int hashCode() {
        return itemsets.hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }
}
