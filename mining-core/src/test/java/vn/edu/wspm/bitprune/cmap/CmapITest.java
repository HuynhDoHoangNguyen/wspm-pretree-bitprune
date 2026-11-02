package vn.edu.wspm.bitprune.cmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class CmapITest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item Z = new Item(99);

    @Test
    void reportsTrueAndFalseForValidCanonicalQueries() {
        CmapI cmap = new CmapBuilder().buildI(
                database(record(10, itemset(A, B), itemset(C))),
                List.of(A, B, C));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(A, C));
        assertFalse(cmap.contains(B, C));
    }

    @Test
    void rejectsEqualItemsInsteadOfReturningFalse() {
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A))), List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, A));
    }

    @Test
    void rejectsReverseOrderInsteadOfSwappingOrNormalizing() {
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(B, A));
    }

    @Test
    void rejectsLeftItemOutsideF1EvenWhenNumericOrderWouldBeCanonical() {
        Item outside = new Item(0);
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(outside, A));
    }

    @Test
    void rejectsRightItemOutsideF1InsteadOfReturningFalse() {
        Item outside = new Item(50);
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, outside));
    }

    @Test
    void rejectsNullQueryItems() {
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), List.of(A, B));

        assertThrows(NullPointerException.class, () -> cmap.contains(null, B));
        assertThrows(NullPointerException.class, () -> cmap.contains(A, null));
    }

    @Test
    void keepsAbsentF1ItemInQueryUniverseWithFalseRelations() {
        CmapI cmap = new CmapBuilder().buildI(
                database(record(-5, itemset(A, B)), record(20, itemset(A))),
                List.of(A, B, Z));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(A, Z));
        assertFalse(cmap.contains(B, Z));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(Z, A));
    }

    @Test
    void inputCollectionMutationDoesNotChangeQueryUniverse() {
        var supplied = new ArrayList<>(List.of(A, B));
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), supplied);
        supplied.clear();
        supplied.add(C);

        assertTrue(cmap.contains(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, C));
    }

    @Test
    void emptyDatabaseStillPreservesSuppliedUniverse() {
        CmapI cmap = new CmapBuilder().buildI(new SequenceDatabase(List.of()), List.of(A, B, Z));

        assertFalse(cmap.contains(A, B));
        assertFalse(cmap.contains(A, Z));
        assertFalse(cmap.contains(B, Z));
    }

    @Test
    void databaseItemsAreNotAutomaticallyAddedToF1Universe() {
        CmapI cmap = new CmapBuilder().buildI(
                database(record(1, itemset(A, B, C))),
                List.of(A, Z));

        assertFalse(cmap.contains(A, Z));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, C));
    }

    private static Itemset itemset(Item... items) {
        return Itemset.of(items);
    }

    private static SequenceRecord record(int sid, Itemset... itemsets) {
        return new SequenceRecord(sid, Sequence.of(itemsets));
    }

    private static SequenceDatabase database(SequenceRecord... records) {
        return new SequenceDatabase(List.of(records));
    }
}
