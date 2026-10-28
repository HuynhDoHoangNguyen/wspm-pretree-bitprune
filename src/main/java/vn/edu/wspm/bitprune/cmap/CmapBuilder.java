package vn.edu.wspm.bitprune.cmap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.SequenceDatabase;

/** Builds immutable CMAP preprocessing structures from caller-supplied mining universes. */
public final class CmapBuilder {

    /**
     * Builds the CMAP-I same-itemset relation over exactly the supplied F1 universe.
     *
     * <p><strong>IMPLEMENTATION INFERENCE — OWNER APPROVED FOR C05.1:</strong> this method does not
     * derive F1 from the database and does not calculate MeanW, WSUP, or minimum support. An F1 item
     * with no database occurrence remains in the query universe and simply has no true relation.
     * Duplicate F1 items are rejected rather than silently collapsed.
     *
     * @param database source sequence database
     * @param f1Items caller-supplied F1 universe
     * @return an immutable CMAP-I relation
     * @throws NullPointerException if an input or F1 element is null
     * @throws IllegalArgumentException if {@code f1Items} contains a duplicate
     */
    public CmapI buildI(SequenceDatabase database, Collection<Item> f1Items) {
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(f1Items, "F1 items must not be null");

        var universe = copyAndValidateUniverse(f1Items);
        Set<Long> relations = new HashSet<>();

        for (var record : database.records()) {
            for (var itemset : record.sequence().itemsets()) {
                var eligibleItems = itemset.items().stream()
                        .filter(universe::contains)
                        .toList();
                for (int leftIndex = 0; leftIndex < eligibleItems.size(); leftIndex++) {
                    Item left = eligibleItems.get(leftIndex);
                    for (int rightIndex = leftIndex + 1; rightIndex < eligibleItems.size(); rightIndex++) {
                        Item right = eligibleItems.get(rightIndex);
                        relations.add(CmapI.pairKey(left, right));
                    }
                }
            }
        }

        return new CmapI(universe, relations);
    }

    /**
     * Builds the CMAP-S sequence co-occurrence relation over exactly the supplied F1 universe.
     *
     * <p><strong>IMPLEMENTATION INFERENCE — OWNER APPROVED FOR C05.2:</strong> this method does not
     * derive F1 from the database and does not calculate MeanW, WSUP, or minimum support. An F1 item
     * with no database occurrence remains in the query universe and simply has no true relation.
     * Duplicate F1 items are rejected rather than silently collapsed.
     *
     * @param database source sequence database
     * @param f1Items caller-supplied F1 universe
     * @return an immutable CMAP-S relation
     * @throws NullPointerException if an input or F1 element is null
     * @throws IllegalArgumentException if {@code f1Items} contains a duplicate
     */
    public CmapS buildS(SequenceDatabase database, Collection<Item> f1Items) {
        Objects.requireNonNull(database, "database must not be null");
        Objects.requireNonNull(f1Items, "F1 items must not be null");

        var universe = copyAndValidateUniverse(f1Items);
        Set<Long> relations = new HashSet<>();

        for (var record : database.records()) {
            Set<Item> seenItems = new HashSet<>();
            for (var itemset : record.sequence().itemsets()) {
                var eligibleItems = itemset.items().stream()
                        .filter(universe::contains)
                        .toList();
                for (Item current : eligibleItems) {
                    for (Item prior : seenItems) {
                        relations.add(CmapS.pairKey(prior, current));
                    }
                }
                seenItems.addAll(eligibleItems);
            }
        }

        return new CmapS(universe, relations);
    }

    private static Set<Item> copyAndValidateUniverse(Collection<Item> f1Items) {
        var universe = new TreeSet<Item>();
        for (Item item : f1Items) {
            Objects.requireNonNull(item, "F1 items must not contain null");
            if (!universe.add(item)) {
                throw new IllegalArgumentException("duplicate item in caller-supplied F1 universe: " + item.id());
            }
        }
        return universe;
    }
}
