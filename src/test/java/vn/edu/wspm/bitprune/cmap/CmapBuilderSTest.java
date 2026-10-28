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

/**
 * Differential and boundary tests for
 * {@link CmapBuilder#buildS(SequenceDatabase, java.util.Collection)}
 * using an independent direct-scan oracle.
 */
class CmapBuilderSTest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item D = new Item(4);
    private static final Item Z = new Item(99);

    /**
     * Independent direct database-scan oracle for CMAP-S:
     * returns true iff exists sequence record with itemset positions t < u such
     * that
     * itemset[t] contains a and itemset[u] contains b.
     */
    private static boolean directScanOracleS(SequenceDatabase database, Item a, Item b) {
        for (SequenceRecord record : database.records()) {
            List<Itemset> itemsets = record.sequence().itemsets();
            for (int t = 0; t < itemsets.size(); t++) {
                for (int u = t + 1; u < itemsets.size(); u++) {
                    if (itemsets.get(t).contains(a) && itemsets.get(u).contains(b)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- 1. a trước b ---
    @Test
    void aPrecedesBCreatesForwardRelation() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
    }

    // --- 2. Reverse-only b trước a ---
    @Test
    void bPrecedesACreatesReverseOnlyRelation() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(B), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(B, A));
        assertFalse(cmap.contains(A, B));
    }

    // --- 3. Cả hai chiều true từ các occurrence khác nhau ---
    @Test
    void bothDirectionsTrueFromDistinctOccurrences() {
        CmapS cmap = new CmapBuilder().buildS(
                database(
                        record(1, itemset(A), itemset(B)),
                        record(2, itemset(B), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertTrue(cmap.contains(B, A));
    }

    // --- 4. Same-itemset-only -> false ---
    @Test
    void sameItemsetOnlyDoesNotCreateSrelation() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A, B))),
                List.of(A, B));

        assertFalse(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
    }

    // --- 5. Cross-SID -> false ---
    @Test
    void crossSidOccurrencesDoNotCreateRelation() {
        CmapS cmap = new CmapBuilder().buildS(
                database(
                        record(1, itemset(A)),
                        record(2, itemset(B))),
                List.of(A, B));

        assertFalse(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
    }

    // --- 6. (a,a) true ở hai itemset ---
    @Test
    void selfPairTrueWhenOccurringInMultipleItemsetsOfSameSequence() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B), itemset(A))),
                List.of(A, B));

        assertTrue(cmap.contains(A, A));
        assertFalse(cmap.contains(B, B));
    }

    // --- 7. (a,a) false ở một itemset ---
    @Test
    void selfPairFalseWhenOccurringInOnlyOneItemset() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B));

        assertFalse(cmap.contains(A, A));
        assertFalse(cmap.contains(B, B));
    }

    // --- 8. Multiple evidence / duplicate suppression ---
    @Test
    void multipleEvidenceDoesNotCorruptRelation() {
        // A appears at t=0, B appears at u=1 and u=2 => multiple evidence for A->B
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B), itemset(B))),
                List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
        assertTrue(cmap.contains(B, B)); // B is at u=1 and u=2
    }

    // --- 9. Negative/zero/noncontiguous SID ---
    @Test
    void handlesNegativeZeroAndNoncontiguousSids() {
        CmapS cmap = new CmapBuilder().buildS(
                database(
                        record(-100, itemset(A), itemset(B)),
                        record(0, itemset(B), itemset(C)),
                        record(9999, itemset(C), itemset(A))),
                List.of(A, B, C));

        assertTrue(cmap.contains(A, B));
        assertTrue(cmap.contains(B, C));
        assertTrue(cmap.contains(C, A));
    }

    // --- 10. F1 item không occurrence ---
    @Test
    void f1ItemWithNoOccurrencesRemainsInUniverseWithFalseRelations() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of(A, B, Z));

        assertTrue(cmap.contains(A, B));
        assertFalse(cmap.contains(A, Z));
        assertFalse(cmap.contains(Z, A));
        assertFalse(cmap.contains(Z, Z));
    }

    // --- 11. Database item ngoài F1 ---
    @Test
    void databaseItemOutsideF1IsIgnored() {
        Item outside = new Item(50);
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(outside), itemset(A), itemset(outside), itemset(B))),
                List.of(A, B));

        assertTrue(cmap.contains(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(outside, A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, outside));
    }

    // --- 12. Empty database ---
    @Test
    void handlesEmptyDatabase() {
        CmapS cmap = new CmapBuilder().buildS(emptyDatabase(), List.of(A, B));
        assertFalse(cmap.contains(A, B));
        assertFalse(cmap.contains(B, A));
        assertFalse(cmap.contains(A, A));
    }

    // --- 13. Empty F1 ---
    @Test
    void handlesEmptyF1Universe() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, B));
    }

    // --- 14. Single-item F1 ---
    @Test
    void handlesSingleItemF1Universe() {
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(A))),
                List.of(A));

        assertTrue(cmap.contains(A, A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, B));
    }

    // --- 15. Null database / F1 / F1 element ---
    @Test
    void rejectsNullInputs() {
        CmapBuilder builder = new CmapBuilder();
        assertThrows(NullPointerException.class, () -> builder.buildS(null, List.of(A)));
        assertThrows(NullPointerException.class, () -> builder.buildS(emptyDatabase(), null));

        var f1WithNull = new ArrayList<Item>();
        f1WithNull.add(A);
        f1WithNull.add(null);
        assertThrows(NullPointerException.class, () -> builder.buildS(emptyDatabase(), f1WithNull));
    }

    // --- 16. Duplicate F1 item ---
    @Test
    void rejectsDuplicateF1Items() {
        assertThrows(IllegalArgumentException.class,
                () -> new CmapBuilder().buildS(emptyDatabase(), List.of(A, B, A)));
    }

    // --- 17. Null query ---
    @Test
    void rejectsNullQueryItems() {
        CmapS cmap = new CmapBuilder().buildS(emptyDatabase(), List.of(A, B));
        assertThrows(NullPointerException.class, () -> cmap.contains(null, A));
        assertThrows(NullPointerException.class, () -> cmap.contains(A, null));
    }

    // --- 18. Outside-universe query ---
    @Test
    void rejectsOutsideUniverseQuery() {
        CmapS cmap = new CmapBuilder().buildS(emptyDatabase(), List.of(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(Z, A));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, Z));
    }

    // --- 19. Input mutation isolation ---
    @Test
    void inputMutationAfterBuildDoesNotAffectCmap() {
        List<Item> f1 = new ArrayList<>(List.of(A, B));
        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(A), itemset(B))),
                f1);

        f1.add(C);
        assertTrue(cmap.contains(A, B));
        assertThrows(IllegalArgumentException.class, () -> cmap.contains(A, C));
    }

    // --- 20. Repeated-build determinism ---
    @Test
    void repeatedBuildProducesIdenticalResults() {
        SequenceDatabase db = database(
                record(1, itemset(A), itemset(B)),
                record(2, itemset(B, C), itemset(A)));
        List<Item> f1 = List.of(A, B, C);

        CmapBuilder builder = new CmapBuilder();
        CmapS first = builder.buildS(db, f1);
        CmapS second = builder.buildS(db, f1);

        for (Item left : f1) {
            for (Item right : f1) {
                assertEquals(first.contains(left, right), second.contains(left, right),
                        "Mismatch for pair (" + left.id() + ", " + right.id() + ")");
            }
        }
    }

    // --- 21. Integer ID boundary/collision test ---
    @Test
    void pairEncodingDoesNotCollideAtIdBoundaries() {
        Item high1 = new Item(1_000_000);
        Item high2 = new Item(2_000_000);
        Item zero = new Item(0);

        CmapS cmap = new CmapBuilder().buildS(
                database(record(1, itemset(high1), itemset(high2))),
                List.of(zero, high1, high2));

        assertTrue(cmap.contains(high1, high2));
        assertFalse(cmap.contains(high2, high1));
        assertFalse(cmap.contains(zero, high1));
        assertFalse(cmap.contains(zero, high2));
        assertFalse(cmap.contains(high1, zero));
        assertFalse(cmap.contains(high2, zero));
    }

    // --- 22. Exhaustive direct-scan comparison across |F1|^2 ordered pairs ---
    @Test
    void exhaustiveDifferentialOracleComparison() {
        // Multi-sequence fixture with:
        // - Asymmetric directions: A -> B, but not B -> A
        // - Bidirectional: B -> C and C -> B
        // - Self-pair true: B occurs in two itemsets in SID 10
        // - Self-pair false: A occurs only once in SID 10, C occurs only once in SID 20
        // - F1 item without occurrence: Z
        // - Same-itemset trap: (C, D) in SID 20 (C and D are in same itemset; neither
        // precedes the other in SID 20)
        // - Cross-SID trap: A is in SID 10, D is only in SID 20 (A does not precede D)
        SequenceDatabase db = database(
                record(10, itemset(A), itemset(B), itemset(B, C)),
                record(20, itemset(C, D), itemset(B)));
        List<Item> f1 = List.of(A, B, C, D, Z);

        CmapS cmap = new CmapBuilder().buildS(db, f1);

        for (Item left : f1) {
            for (Item right : f1) {
                boolean expected = directScanOracleS(db, left, right);
                boolean actual = cmap.contains(left, right);
                assertEquals(expected, actual,
                        "Oracle mismatch for pair (" + left.id() + ", " + right.id() + ")");
            }
        }

        // Explicit trap verification
        assertFalse(cmap.contains(C, D), "Same-itemset trap: C and D are only co-present in SID 20 at position 0");
        assertFalse(cmap.contains(D, C), "Same-itemset trap: D and C are only co-present in SID 20 at position 0");
        assertFalse(cmap.contains(A, D), "Cross-SID trap: A in SID 10 and D in SID 20 must not form relation");
        assertFalse(cmap.contains(D, A), "Cross-SID trap: D in SID 20 and A in SID 10 must not form relation");
        assertTrue(cmap.contains(B, B), "Self-pair: B is at pos 1 and pos 2 in SID 10");
        assertFalse(cmap.contains(A, A), "Self-pair false: A occurs only once in SID 10");
        assertFalse(cmap.contains(D, D), "Self-pair false: D occurs only once in SID 20");
        assertFalse(cmap.contains(Z, Z), "Absent item: Z has no occurrences");
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

    private static SequenceDatabase emptyDatabase() {
        return new SequenceDatabase(List.of());
    }
}
