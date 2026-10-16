package vn.edu.wspm.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * An immutable, non-empty, unordered collection of distinct items.
 *
 * <p>B2 implementation inference: duplicate input is rejected instead of silently collapsed so
 * malformed data remains observable. Items are stored in canonical natural order for deterministic
 * equality and traversal; that storage choice does not define later mining-extension semantics.</p>
 */
public final class Itemset {

    private final List<Item> items;

    public Itemset(Collection<Item> items) {
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty()) {
            throw new IllegalArgumentException("an itemset must contain at least one item");
        }

        var canonicalItems = new TreeSet<Item>();
        for (var item : items) {
            Objects.requireNonNull(item, "an itemset must not contain null");
            if (!canonicalItems.add(item)) {
                throw new IllegalArgumentException("duplicate item in itemset: " + item.id());
            }
        }
        this.items = List.copyOf(canonicalItems);
    }

    public static Itemset of(Item... items) {
        Objects.requireNonNull(items, "items must not be null");
        return new Itemset(Arrays.asList(items));
    }

    public List<Item> items() {
        return items;
    }

    public int size() {
        return items.size();
    }

    public boolean contains(Item item) {
        return items.contains(Objects.requireNonNull(item, "item must not be null"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Itemset itemset && items.equals(itemset.items);
    }

    @Override
    public int hashCode() {
        return items.hashCode();
    }
}
