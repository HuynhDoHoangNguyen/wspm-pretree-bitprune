package vn.edu.wspm.bitprune.cmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class CmapSTest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item Z = new Item(99);

    @Test
    void reportsTrueAndFalseForDirectedQueries() {
        // Sequence: (A)(B) => A precedes B, but B does not precede A
        CmapS cmap = new CmapBuilder().buildS(
                database(record(10, itemset(A), itemset(B))),
                List.of(A, B, C));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
        assertFalse(cmap.contains(A, C));
        assertFalse(cmap.contains(C, A));
        assertFalse(cmap.contains(B, C));
        assertFalse(cmap.contains(C, B));
    }

    @Test
    void supportsSelfPairWhenEvidenceExists() {
        // Sequence: (A)(A) => A precedes A in distinct itemsets
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(A, A));
        assertFalse(cmap.contains(B, B));
    }

    @Test
    void supportsBothDirectionsIndependently() {
        // Sequence 1: (A)(B), Sequence 2: (B)(A) => both A->B and B->A are true
        CmapS cmap = new CmapBuilder().buildS(
                database(
                        record(1, itemset(A), itemset(B)),
                        record(2, itemset(B), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertTrue(cmap.contains(B, A));
    }

    @Test
    void doesNotRequireCanonicalOrdering() {
        // Unlike CMAP-I, query (B, A) with B > A is perfectly valid and does not throw
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(B), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(B, A));
        assertFalse(cmap.contains(A, B));
    }

    @Test
    void rejectsNullQueryItems() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));

        assertThrows(NullPointerException.class, () -> cmap.contains(null, B));
        assertThrows(NullPointerException.class, () -> cmap.contains(A, null));
    }

    @Test
    void rejectsLeftItemOutsideF1() {
        Item outside = new Item(0);
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));

        assertThrows(IllegalArgumentException.class, () -> cmap.contains(outside, A));
    }

    @Test
    void rejectsRightItemOutsideF1() {
        Item outside = new Item(50);
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));

        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, outside));
    }

    @Test
    void keepsAbsentF1ItemInQueryUniverseWithFalseRelations() {
        // Z is in F1 but has no occurrences in database
        CmapS cmap = new CmapBuilder().buildS(
                database(record(-5, itemset(A), itemset(B))),
                List.of(A, B, Z));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(A, Z));
        assertFalse(cmap.contains(Z, A));
        assertFalse(cmap.contains(B, Z));
        assertFalse(cmap.contains(Z, B));
        assertFalse(cmap.contains(Z, Z));
    }

    @Test
    void immutabilityAndDefensiveCopyingInConstructor() {
        Set<Item> f1 = new HashSet<>();
        f1.add(A);
        f1.add(B);

        Set<Long> relations = new HashSet<>();
        relations.add(CmapS.pairKey(A, B));

        CmapS cmap = new CmapS(f1, relations);
        assertTrue(cmap.contains(A, B));

        // Mutate original sets
        f1.add(C);
        relations.add(CmapS.pairKey(B, A));

        // CmapS must remain unchanged
        assertFalse(cmap.contains(B, A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, C));
    }

    // --- Helpers ---

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
