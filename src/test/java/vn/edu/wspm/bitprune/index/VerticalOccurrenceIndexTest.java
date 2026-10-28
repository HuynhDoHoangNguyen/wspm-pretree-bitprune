package vn.edu.wspm.bitprune.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Correctness tests for {@link VerticalOccurrenceIndex}.
 *
 * <p>Each test constructs the expected positions using a <strong>direct-scan oracle</strong> that
 * is completely independent of the production index:
 *
 * <pre>
 *   for each SequenceRecord in the database (in record order):
 *       for itemsetPosition from 1 to sequence.size():
 *           inspect itemset.contains(item) directly
 * </pre>
 *
 * The oracle does NOT:
 * <ul>
 *   <li>call any {@link VerticalOccurrenceIndex} helper or share builder logic,
 *   <li>use {@link vn.edu.wspm.bitprune.bitmap.SidBitmap},
 *   <li>use PRISM encoding,
 *   <li>infer positions from any implementation-internal collection.
 * </ul>
 *
 * <p>All array comparisons are exact element-by-element, not just length-based.
 */
class VerticalOccurrenceIndexTest {

    // =========================================================================
    // Oracle: independent direct-scan of the database
    // =========================================================================

    /**
     * Direct-scan oracle: returns the sorted 1-based positions of {@code item} in the sequence
     * identified by {@code targetSid}, by iterating over every itemset of that sequence in order.
     *
     * <p>This method is intentionally self-contained and must not reuse any production helper.
     */
    private static int[] oraclePositions(SequenceDatabase db, int targetSid, Item item) {
        for (SequenceRecord record : db.records()) {
            if (record.sid() != targetSid) {
                continue;
            }
            List<Integer> positions = new ArrayList<>();
            List<Itemset> itemsets = record.sequence().itemsets();
            for (int pos = 1; pos <= itemsets.size(); pos++) {
                if (itemsets.get(pos - 1).contains(item)) {
                    positions.add(pos);
                }
            }
            return positions.stream().mapToInt(Integer::intValue).toArray();
        }
        // targetSid not found — should not be called on unknown SID in correctness tests
        throw new AssertionError("Oracle: SID " + targetSid + " not found in database");
    }

    // =========================================================================
    // Shared builders
    // =========================================================================

    private static Item item(int id) {
        return new Item(id);
    }

    private static Itemset itemset(Item... items) {
        return Itemset.of(items);
    }

    private static Sequence seq(Itemset... itemsets) {
        return Sequence.of(itemsets);
    }

    private static SequenceRecord rec(int sid, Sequence sequence) {
        return new SequenceRecord(sid, sequence);
    }

    private static SequenceDatabase db(SequenceRecord... records) {
        return new SequenceDatabase(Arrays.asList(records));
    }

    // =========================================================================
    // Test 1: Reject null database
    // =========================================================================

    @Test
    void rejectsNullDatabase() {
        var db = db(rec(1, seq(itemset(item(1)))));
        var mapping = SidMapping.of(db);
        assertThrows(NullPointerException.class, () -> VerticalOccurrenceIndex.build(null, mapping));
    }

    // =========================================================================
    // Test 2: Reject null mapping
    // =========================================================================

    @Test
    void rejectsNullMapping() {
        var db = db(rec(1, seq(itemset(item(1)))));
        assertThrows(NullPointerException.class, () -> VerticalOccurrenceIndex.build(db, null));
    }

    // =========================================================================
    // Test 3: Empty database
    // =========================================================================

    @Test
    void handlesEmptyDatabase() {
        var db = new SequenceDatabase(List.of());
        var mapping = SidMapping.of(db);
        // Must not throw
        var idx = VerticalOccurrenceIndex.build(db, mapping);
        assertNotNull(idx);
    }

    // =========================================================================
    // Test 4: Single sequence, single itemset
    // =========================================================================

    @Test
    void handlesSingleSequenceSingleItemset() {
        Item a = item(1);
        Item b = item(2);
        var db = db(rec(10, seq(itemset(a, b))));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // Oracle: position 1 only
        assertArrayEquals(oraclePositions(db, 10, a), idx.positions(a, 10));
        assertArrayEquals(new int[]{1}, idx.positions(a, 10));
        assertArrayEquals(new int[]{1}, idx.positions(b, 10));
    }

    // =========================================================================
    // Test 5: Negative, zero, positive and noncontiguous actual SIDs
    // =========================================================================

    @Test
    void handlesNegativeZeroPositiveAndNoncontiguousSids() {
        Item x = item(5);
        var db = db(
                rec(-3, seq(itemset(x))),
                rec(0, seq(itemset(item(99)))),       // x absent
                rec(100, seq(itemset(x, item(1))))    // x present
        );
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // x in SID -3: position 1
        assertArrayEquals(oraclePositions(db, -3, x), idx.positions(x, -3));
        assertArrayEquals(new int[]{1}, idx.positions(x, -3));

        // x absent in SID 0: empty
        assertArrayEquals(oraclePositions(db, 0, x), idx.positions(x, 0));
        assertArrayEquals(new int[]{}, idx.positions(x, 0));

        // x in SID 100: position 1
        assertArrayEquals(oraclePositions(db, 100, x), idx.positions(x, 100));
        assertArrayEquals(new int[]{1}, idx.positions(x, 100));
    }

    // =========================================================================
    // Test 6: Database record order different from natural SID order
    // =========================================================================

    @Test
    void preservesRecordOrderNotNaturalSidOrder() {
        // Record order: SID 300, SID 1, SID 150 — deliberately not sorted
        Item q = item(7);
        var db = db(
                rec(300, seq(itemset(q))),
                rec(1, seq(itemset(item(2)))),       // q absent
                rec(150, seq(itemset(q, item(3))))   // q present
        );
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // bit index 0 → SID 300, bit index 1 → SID 1, bit index 2 → SID 150
        // The index must use actual SIDs, not bit indices
        assertArrayEquals(new int[]{1}, idx.positions(q, 300));
        assertArrayEquals(new int[]{}, idx.positions(q, 1));
        assertArrayEquals(new int[]{1}, idx.positions(q, 150));

        // Confirm oracle agrees
        assertArrayEquals(oraclePositions(db, 300, q), idx.positions(q, 300));
        assertArrayEquals(oraclePositions(db, 1, q), idx.positions(q, 1));
        assertArrayEquals(oraclePositions(db, 150, q), idx.positions(q, 150));
    }

    // =========================================================================
    // Test 7: Item at first itemset position
    // =========================================================================

    @Test
    void itemAtFirstItemsetPosition() {
        Item a = item(1);
        var db = db(rec(42, seq(
                itemset(a),          // position 1
                itemset(item(9)),    // position 2
                itemset(item(8))     // position 3
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        assertArrayEquals(oraclePositions(db, 42, a), idx.positions(a, 42));
        assertArrayEquals(new int[]{1}, idx.positions(a, 42));
    }

    // =========================================================================
    // Test 8: Item at last itemset position
    // =========================================================================

    @Test
    void itemAtLastItemsetPosition() {
        Item z = item(99);
        var db = db(rec(7, seq(
                itemset(item(1)),    // position 1
                itemset(item(2)),    // position 2
                itemset(z)           // position 3 (last)
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        assertArrayEquals(oraclePositions(db, 7, z), idx.positions(z, 7));
        assertArrayEquals(new int[]{3}, idx.positions(z, 7));
    }

    // =========================================================================
    // Test 9: Repeated item across multiple itemsets — all occurrences preserved
    // =========================================================================

    @Test
    void repeatedItemAcrossItemsetsPreservesAllOccurrences() {
        Item r = item(3);
        Item other = item(10);
        // Sequence: (r)(other)(r)(r)(other)
        // r appears at positions 1, 3, 4
        var db = db(rec(55, seq(
                itemset(r),           // position 1
                itemset(other),       // position 2
                itemset(r),           // position 3
                itemset(r),           // position 4
                itemset(other)        // position 5
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // Direct-scan oracle
        int[] expected = oraclePositions(db, 55, r);
        int[] actual = idx.positions(r, 55);

        assertArrayEquals(expected, actual,
                "All occurrences of repeated item must be preserved; expected="
                        + Arrays.toString(expected) + " actual=" + Arrays.toString(actual));
        // Verify exact content
        assertArrayEquals(new int[]{1, 3, 4}, actual);
    }

    // =========================================================================
    // Test 10: Dense itemset containing many items
    // =========================================================================

    @Test
    void denseItemsetContainingManyItems() {
        Item[] items = new Item[]{item(1), item(2), item(3), item(4), item(5)};
        var db = db(rec(20, seq(
                Itemset.of(items[0], items[1], items[2], items[3], items[4])  // all 5 at position 1
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        for (Item it : items) {
            assertArrayEquals(oraclePositions(db, 20, it), idx.positions(it, 20));
            assertArrayEquals(new int[]{1}, idx.positions(it, 20));
        }
    }

    // =========================================================================
    // Test 11: Sparse sequence — item appears only in one of many itemsets
    // =========================================================================

    @Test
    void sparseSequenceItemInOnlyOneItemset() {
        Item rare = item(77);
        Item absent = item(999); // never appears in the database at all
        var db = db(rec(9, seq(
                itemset(item(1)),    // position 1
                itemset(item(2)),    // position 2
                itemset(rare),       // position 3
                itemset(item(4)),    // position 4
                itemset(item(5))     // position 5
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // Oracle confirms rare is only at position 3
        assertArrayEquals(oraclePositions(db, 9, rare), idx.positions(rare, 9));
        assertArrayEquals(new int[]{3}, idx.positions(rare, 9));

        // item(1) appears at position 1 in SID 9 — oracle confirms
        assertArrayEquals(oraclePositions(db, 9, item(1)), idx.positions(item(1), 9));
        assertArrayEquals(new int[]{1}, idx.positions(item(1), 9));

        // A truly absent item returns empty
        assertArrayEquals(oraclePositions(db, 9, absent), idx.positions(absent, 9));
        assertArrayEquals(new int[]{}, idx.positions(absent, 9));
    }

    // =========================================================================
    // Test 12: Valid SID with item absent — returns empty array
    // =========================================================================

    @Test
    void validSidWithAbsentItemReturnsEmptyArray() {
        Item present = item(1);
        Item absent = item(999);
        var db = db(rec(5, seq(itemset(present))));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        assertArrayEquals(oraclePositions(db, 5, absent), idx.positions(absent, 5));
        assertArrayEquals(new int[]{}, idx.positions(absent, 5));
    }

    // =========================================================================
    // Test 13: Unknown actual SID — must be rejected
    // =========================================================================

    @Test
    void unknownActualSidIsRejected() {
        var db = db(rec(10, seq(itemset(item(1)))));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // SID 99 does not exist in the database
        assertThrows(IllegalArgumentException.class, () -> idx.positions(item(1), 99));
    }

    // =========================================================================
    // Test 14: Mapping with same size but different SID set — must be rejected
    // =========================================================================

    @Test
    void mismatchedSidSetInMappingIsRejected() {
        // Database has SID {10, 20}
        var db = db(
                rec(10, seq(itemset(item(1)))),
                rec(20, seq(itemset(item(2))))
        );
        // Mapping was built from a different database with SID {10, 30}
        var differentDb = db(
                rec(10, seq(itemset(item(1)))),
                rec(30, seq(itemset(item(2))))
        );
        var mismatchedMapping = SidMapping.of(differentDb);

        assertThrows(IllegalArgumentException.class,
                () -> VerticalOccurrenceIndex.build(db, mismatchedMapping));
    }

    // =========================================================================
    // Test 15: Mapping with same SID set but different record order — must be rejected
    // =========================================================================

    @Test
    void mismatchedRecordOrderInMappingIsRejected() {
        // Database record order: SID 10, SID 20
        var db = db(
                rec(10, seq(itemset(item(1)))),
                rec(20, seq(itemset(item(2))))
        );
        // Mapping built from reversed order: SID 20, SID 10
        var reversedDb = db(
                rec(20, seq(itemset(item(2)))),
                rec(10, seq(itemset(item(1))))
        );
        var reversedMapping = SidMapping.of(reversedDb);

        assertThrows(IllegalArgumentException.class,
                () -> VerticalOccurrenceIndex.build(db, reversedMapping));
    }

    // =========================================================================
    // Test 16: Input database is not mutated by build()
    // =========================================================================

    @Test
    void buildDoesNotMutateDatabaseInput() {
        Item a = item(1);
        var records = new ArrayList<SequenceRecord>();
        records.add(rec(1, seq(itemset(a))));
        var db = new SequenceDatabase(records);
        int sizeBefore = db.size();
        var mapping = SidMapping.of(db);

        VerticalOccurrenceIndex.build(db, mapping);

        // Database size must be unchanged
        assertEquals(sizeBefore, db.size());
        // First record must be the same SID
        assertEquals(1, db.records().get(0).sid());
    }

    // =========================================================================
    // Test 17: Returned positions array is a defensive copy
    // =========================================================================

    @Test
    void positionsReturnsDefensiveCopy() {
        Item a = item(1);
        Item b = item(2);
        var db = db(rec(5, seq(
                itemset(a),    // position 1
                itemset(a, b), // position 2
                itemset(b)     // position 3
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        // Get positions and mutate the returned array
        int[] first = idx.positions(a, 5);
        assertArrayEquals(new int[]{1, 2}, first);

        first[0] = 999; // Mutate the returned copy

        // A second query must return the original unmodified values
        int[] second = idx.positions(a, 5);
        assertArrayEquals(new int[]{1, 2}, second,
                "Mutating the returned array must not affect the stored index state");
    }

    // =========================================================================
    // Test 18: All returned position arrays are strictly increasing
    // =========================================================================

    @Test
    void allPositionsAreStrictlyIncreasing() {
        Item a = item(1);
        Item b = item(2);
        // a appears at positions 1, 3, 5
        // b appears at positions 2, 4
        var db = db(rec(1, seq(
                itemset(a),    // 1
                itemset(b),    // 2
                itemset(a),    // 3
                itemset(b),    // 4
                itemset(a)     // 5
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        int[] posA = idx.positions(a, 1);
        int[] posB = idx.positions(b, 1);

        assertStrictlyIncreasing(posA, "positions of item a");
        assertStrictlyIncreasing(posB, "positions of item b");
        assertArrayEquals(new int[]{1, 3, 5}, posA);
        assertArrayEquals(new int[]{2, 4}, posB);
    }

    // =========================================================================
    // Test 19: Full differential direct-scan catalogue for all item/SID pairs
    // =========================================================================

    @Test
    void differentialDirectScanCatalogueForAllItemSidPairs() {
        // Multi-sequence, multi-item fixture
        Item a = item(1);
        Item b = item(2);
        Item c = item(3);

        // SID 10: <(a,b)(c)(a)(b,c)>   a@{1,3}  b@{1,4}  c@{2,4}
        // SID 20: <(c)(a,b,c)>          a@{2}    b@{2}    c@{1,2}
        // SID 30: <(a)(a)(a)>           a@{1,2,3} b@{}    c@{}
        var db = db(
                rec(10, seq(itemset(a, b), itemset(c), itemset(a), itemset(b, c))),
                rec(20, seq(itemset(c), itemset(a, b, c))),
                rec(30, seq(itemset(a), itemset(a), itemset(a)))
        );
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        int[] sids = {10, 20, 30};
        Item[] items = {a, b, c};

        for (int sid : sids) {
            for (Item it : items) {
                int[] expected = oraclePositions(db, sid, it);
                int[] actual = idx.positions(it, sid);
                assertArrayEquals(expected, actual,
                        "Mismatch for item=" + it.id() + " sid=" + sid
                                + " expected=" + Arrays.toString(expected)
                                + " actual=" + Arrays.toString(actual));
            }
        }
    }

    // =========================================================================
    // Test 20: No cross-SID contamination
    // =========================================================================

    @Test
    void noCrossSidContamination() {
        Item a = item(1);
        // SID 100: a at position 1
        // SID 200: a absent
        // SID 300: a at positions 2 and 4
        var db = db(
                rec(100, seq(itemset(a))),
                rec(200, seq(itemset(item(9)), itemset(item(8)))),
                rec(300, seq(itemset(item(7)), itemset(a), itemset(item(6)), itemset(a)))
        );
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        assertArrayEquals(oraclePositions(db, 100, a), idx.positions(a, 100));
        assertArrayEquals(new int[]{1}, idx.positions(a, 100));

        assertArrayEquals(oraclePositions(db, 200, a), idx.positions(a, 200));
        assertArrayEquals(new int[]{}, idx.positions(a, 200));

        assertArrayEquals(oraclePositions(db, 300, a), idx.positions(a, 300));
        assertArrayEquals(new int[]{2, 4}, idx.positions(a, 300));
    }

    // =========================================================================
    // Test: Reject null item in positions()
    // =========================================================================

    @Test
    void rejectsNullItemInPositions() {
        var db = db(rec(1, seq(itemset(item(1)))));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        assertThrows(NullPointerException.class, () -> idx.positions(null, 1));
    }

    // =========================================================================
    // Test: Mapping size mismatch (database larger than mapping)
    // =========================================================================

    @Test
    void mappingSizeMismatchIsRejected() {
        var db = db(
                rec(10, seq(itemset(item(1)))),
                rec(20, seq(itemset(item(2))))
        );
        // Mapping built from single-record database
        var smallDb = db(rec(10, seq(itemset(item(1)))));
        var tooSmallMapping = SidMapping.of(smallDb);

        assertThrows(IllegalArgumentException.class,
                () -> VerticalOccurrenceIndex.build(db, tooSmallMapping));
    }

    // =========================================================================
    // Test: Item appears in every itemset of a long sequence
    // =========================================================================

    @Test
    void itemAppearingInEveryItemsetHasAllPositions() {
        Item a = item(1);
        int seqLength = 10;
        // Build a sequence where 'a' appears in every itemset
        Itemset[] itemsets = new Itemset[seqLength];
        for (int i = 0; i < seqLength; i++) {
            itemsets[i] = itemset(a);
        }
        var db = db(rec(7, Sequence.of(itemsets)));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        int[] expected = oraclePositions(db, 7, a);
        int[] actual = idx.positions(a, 7);
        assertArrayEquals(expected, actual);

        // Expect positions 1 through 10
        int[] allPositions = new int[seqLength];
        for (int i = 0; i < seqLength; i++) allPositions[i] = i + 1;
        assertArrayEquals(allPositions, actual);
    }

    // =========================================================================
    // Test: Large multi-SID database — differential scan for item across all SIDs
    // =========================================================================

    @Test
    void multiSidDatabaseDifferentialScanForSingleItem() {
        Item target = item(42);
        Item noise = item(99);

        // Build database: target appears in every other SID
        List<SequenceRecord> records = new ArrayList<>();
        int[] sids = {-10, 0, 5, 7, 11, 50};
        for (int i = 0; i < sids.length; i++) {
            if (i % 2 == 0) {
                // target appears at position 1
                records.add(rec(sids[i], seq(itemset(target))));
            } else {
                // target absent
                records.add(rec(sids[i], seq(itemset(noise))));
            }
        }
        var db = new SequenceDatabase(records);
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        for (int sid : sids) {
            int[] expected = oraclePositions(db, sid, target);
            int[] actual = idx.positions(target, sid);
            assertArrayEquals(expected, actual,
                    "Differential mismatch at sid=" + sid);
        }
    }

    // =========================================================================
    // Test: Positions are 1-based, never 0-based
    // =========================================================================

    @Test
    void positionsAreOneBased() {
        Item a = item(1);
        var db = db(rec(1, seq(
                itemset(item(99)),  // position 1 — a absent
                itemset(a)          // position 2 — a present
        )));
        var mapping = SidMapping.of(db);
        var idx = VerticalOccurrenceIndex.build(db, mapping);

        int[] positions = idx.positions(a, 1);
        assertArrayEquals(new int[]{2}, positions,
                "First itemset is at position 1, second at position 2 (1-based, not 0-based)");
        // Explicitly confirm no zero entry
        for (int p : positions) {
            assertTrue(p >= 1, "All positions must be >= 1 (1-based), found: " + p);
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static void assertStrictlyIncreasing(int[] array, String label) {
        for (int i = 1; i < array.length; i++) {
            assertTrue(array[i] > array[i - 1],
                    label + ": positions must be strictly increasing at index " + i
                            + " — prev=" + array[i - 1] + " curr=" + array[i]);
        }
    }
}
