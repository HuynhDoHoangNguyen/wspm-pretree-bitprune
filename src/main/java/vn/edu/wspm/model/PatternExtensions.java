package vn.edu.wspm.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/** Performs one structural I-Extension or S-Extension without support computation. */
public final class PatternExtensions {

    /**
     * B10 implementation policy: numeric item identifiers realize the paper's lexicographic order.
     * This is the same natural order used by Itemset canonical storage.
     */
    private static final Comparator<Item> ITEM_ORDER = Comparator.naturalOrder();

    /** Adds one item to the final itemset under the paper's strict ordering constraint. */
    public Pattern iExtend(Pattern pattern, Item extensionItem) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(extensionItem, "extension item must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException(
                    "I-Extension requires a non-empty pattern with a final itemset");
        }

        var lastItemset = pattern.lastItemset().orElseThrow();
        if (lastItemset.contains(extensionItem)) {
            throw new IllegalArgumentException(
                    "I-Extension item already exists in the final itemset: "
                            + extensionItem.id());
        }

        var lastItem = pattern.lastItem().orElseThrow();
        if (ITEM_ORDER.compare(extensionItem, lastItem) <= 0) {
            throw new IllegalArgumentException(
                    "I-Extension item must be greater than the final itemset's last item: "
                            + extensionItem.id() + " <= " + lastItem.id());
        }

        var extendedItems = new ArrayList<>(lastItemset.items());
        extendedItems.add(extensionItem);
        var extendedItemsets = new ArrayList<>(pattern.itemsets());
        extendedItemsets.set(extendedItemsets.size() - 1, new Itemset(extendedItems));
        return new Pattern(extendedItemsets);
    }

    /** Appends one item as a new singleton itemset; repetition across itemsets is legal. */
    public Pattern sExtend(Pattern pattern, Item extensionItem) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        Objects.requireNonNull(extensionItem, "extension item must not be null");

        var extendedItemsets = new ArrayList<>(pattern.itemsets());
        extendedItemsets.add(Itemset.of(extensionItem));
        return new Pattern(extendedItemsets);
    }
}
