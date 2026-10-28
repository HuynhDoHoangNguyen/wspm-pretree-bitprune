package vn.edu.wspm.bitprune.cmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/** Differential tests using a direct database-scan oracle independent of production CMAP logic. */
class CmapBuilderITest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item D = new Item(4);
    private static final Item Z = new Item(99);

    @Test
    void rejectsNullDatabase() {
        assertThrows(NullPointerException.class, () -> new CmapBuilder().buildI(null, List.of(A)));
    }

    @Test
    void rejectsNullF1Collection() {
        assertThrows(NullPointerException.class, () -> new CmapBuilder().buildI(emptyDatabase(), null));
    }

    @Test
    void rejectsNullF1Element() {
        var f1 = new ArrayList<Item>();
        f1.add(A);
        f1.add(null);
        assertThrows(NullPointerException.class, () -> new CmapBuilder().buildI(emptyDatabase(), f1));
    }

    @Test
    void rejectsDuplicateF1ItemsInsteadOfSilentlyCollapsingThem() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CmapBuilder().buildI(emptyDatabase(), List.of(A, B, A)));
    }

    @Test
    void handlesEmptyDatabaseAndEmptyF1Universe() {
        CmapI cmap = new CmapBuilder().buildI(emptyDatabase(), List.of());
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, B));
    }

    @Test
    void handlesSingleItemF1UniverseWithoutInventingRelations() {
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A))), List.of(A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, B));
    }

    @Test
    void sameItemsetCreatesRelation() {
        CmapI cmap = new CmapBuilder().buildI(database(record(1, itemset(A, B))), List.of(A, B));
        assertTrue(cmap.contains(A, B));
    }

    @Test
    void differentItemsetsDoNotCreateRelation() {
        CmapI cmap = new CmapBuilder().buildI(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));
        assertFalse(cmap.contains(A, B));
    }

    @Test
    void differentSidsDoNotCreateRelation() {
        CmapI cmap = new CmapBuilder().buildI(
                database(record(-10, itemset(A)), record(250, itemset(B))),
                List.of(A, B));
        assertFalse(cmap.contains(A, B));
    }

    @Test
    void repeatedEvidenceRepresentsOneLogicalRelation() {
        SequenceDatabase database = database(
                record(-5, itemset(A, B), itemset(A, B)),
                record(0, itemset(A, B)),
                record(100, itemset(A, B, C)));
        CmapI cmap = new CmapBuilder().buildI(database, List.of(A, B, C));

        assertTrue(cmap.contains(A, B));
        assertTrue(cmap.contains(A, C));
        assertTrue(cmap.contains(B, C));
    }

    @Test
    void negativeZeroAndNoncontiguousSidsDoNotAffectSameItemsetSemantics() {
        SequenceDatabase database = database(
                record(-7, itemset(A, C)),
                record(0, itemset(B, D)),
                record(500, itemset(A), itemset(D)));
        CmapI cmap = new CmapBuilder().buildI(database, List.of(A, B, C, D));

        assertTrue(cmap.contains(A, C));
        assertTrue(cmap.contains(B, D));
        assertFalse(cmap.contains(A, D));
    }

    @Test
    void exhaustiveFixtureMatchesIndependentDirectScanOracleForEveryCanonicalPair() {
        SequenceDatabase database = database(
                record(-8, itemset(A, B), itemset(C)),
                record(0, itemset(A, C, D)),
                record(17, itemset(B), itemset(C, D)),
                record(900, itemset(A), itemset(B, D)));
        List<Item> f1 = List.of(A, B, C, D, Z);
        CmapI cmap = new CmapBuilder().buildI(database, f1);

        int comparisons = 0;
        for (int leftIndex = 0; leftIndex < f1.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < f1.size(); rightIndex++) {
                Item left = f1.get(leftIndex);
                Item right = f1.get(rightIndex);
                assertEquals(
                        oracleSameItemset(database, left, right),
                        cmap.contains(left, right),
                        () -> "Mismatch for canonical pair (" + left.id() + ", " + right.id() + ")");
                comparisons++;
            }
        }

        assertEquals(10, comparisons);
    }

    @Test
    void repeatedBuildsAreDeterministicForEveryCanonicalPair() {
        SequenceDatabase database = database(
                record(3, itemset(A, D), itemset(B, C)),
                record(9, itemset(A, B, C)));
        List<Item> f1 = List.of(A, B, C, D, Z);

        CmapI first = new CmapBuilder().buildI(database, f1);
        CmapI second = new CmapBuilder().buildI(database, f1);

        for (int leftIndex = 0; leftIndex < f1.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < f1.size(); rightIndex++) {
                assertEquals(
                        first.contains(f1.get(leftIndex), f1.get(rightIndex)),
                        second.contains(f1.get(leftIndex), f1.get(rightIndex)));
            }
        }
    }

    @Test
    void pairEncodingRemainsDistinctAtIntegerIdentifierBoundaries() {
        Item zero = new Item(0);
        Item one = new Item(1);
        Item almostMax = new Item(Integer.MAX_VALUE - 1);
        Item max = new Item(Integer.MAX_VALUE);
        List<Item> f1 = List.of(zero, one, almostMax, max);
        SequenceDatabase database = database(
                record(1, itemset(zero, max), itemset(one, almostMax)));

        CmapI cmap = new CmapBuilder().buildI(database, f1);

        assertTrue(cmap.contains(zero, max));
        assertTrue(cmap.contains(one, almostMax));
        assertFalse(cmap.contains(zero, almostMax));
        assertFalse(cmap.contains(one, max));
    }

    /** Independent oracle: no production CMAP type, helper, or pair encoding is reused. */
    private static boolean oracleSameItemset(SequenceDatabase database, Item left, Item right) {
        for (SequenceRecord record : database.records()) {
            for (Itemset itemset : record.sequence().itemsets()) {
                if (itemset.contains(left) && itemset.contains(right)) {
                    return true;
                }
            }
        }
        return false;
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

    private static SequenceDatabase emptyDatabase() {
        return new SequenceDatabase(List.of());
    }
}
