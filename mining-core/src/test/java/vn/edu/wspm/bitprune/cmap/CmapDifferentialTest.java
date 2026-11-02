package vn.edu.wspm.bitprune.cmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Integrated differential safety test suite for CMAP-I and CMAP-S.
 *
 * <p>
 * Validates CMAP structures against independent direct-scan oracles:
 * <ul>
 * <li>Deterministic directional relation catalogue (I vs S, self-pair,
 * same-itemset, cross-SID).
 * <li>Caller-supplied F1 universe edge cases (empty universe, empty database,
 * absent items, outside items).
 * <li>Boundary value testing with {@link Integer#MAX_VALUE} to prove
 * collision-free pair encoding.
 * <li>Fixed-seed ({@code 20261028L}) randomized differential testing across
 * exactly 1,200 trials.
 * </ul>
 */
public class CmapDifferentialTest {

    // =========================================================================
    // Independent Direct-Scan Oracles
    // =========================================================================

    /**
     * Independent direct-scan oracle for CMAP-I:
     * returns true iff at least one itemset in the database contains both a and b.
     */
    static boolean directIOracle(SequenceDatabase database, Item a, Item b) {
        for (SequenceRecord record : database.records()) {
            for (Itemset itemset : record.sequence().itemsets()) {
                if (itemset.contains(a) && itemset.contains(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Independent direct-scan oracle for CMAP-S:
     * returns true iff at least one sequence record contains itemset positions t <
     * u
     * such that itemset[t] contains a and itemset[u] contains b.
     */
    static boolean directSOracle(SequenceDatabase database, Item a, Item b) {
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

    // =========================================================================
    // 7.1 Deterministic Directional Catalogue
    // =========================================================================

    @Test
    void testSameItemsetOnlyCreatesIButNotS() {
        Item a = new Item(1);
        Item b = new Item(2);
        SequenceDatabase db = database(record(1, itemset(a, b)));
        List<Item> f1 = List.of(a, b);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertTrue(cmapI.contains(a, b), "I(a,b) must be true for <(ab)>");
        assertFalse(cmapS.contains(a, b), "S(a,b) must be false for <(ab)>");
        assertFalse(cmapS.contains(b, a), "S(b,a) must be false for <(ab)>");
    }

    @Test
    void testSequencePrecedenceCreatesForwardSNotReverseSAndNotI() {
        Item a = new Item(1);
        Item b = new Item(2);
        SequenceDatabase db = database(record(1, itemset(a), itemset(b)));
        List<Item> f1 = List.of(a, b);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertFalse(cmapI.contains(a, b), "I(a,b) must be false for <(a)(b)>");
        assertTrue(cmapS.contains(a, b), "S(a,b) must be true for <(a)(b)>");
        assertFalse(cmapS.contains(b, a), "S(b,a) must be false for <(a)(b)>");
    }

    @Test
    void testSequenceReversePrecedenceCreatesReverseSNotForwardSAndNotI() {
        Item a = new Item(1);
        Item b = new Item(2);
        SequenceDatabase db = database(record(1, itemset(b), itemset(a)));
        List<Item> f1 = List.of(a, b);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertFalse(cmapI.contains(a, b), "I(a,b) must be false for <(b)(a)>");
        assertFalse(cmapS.contains(a, b), "S(a,b) must be false for <(b)(a)>");
        assertTrue(cmapS.contains(b, a), "S(b,a) must be true for <(b)(a)>");
    }

    @Test
    void testBothDirectionsTrueFromDistinctOccurrencesOrSequences() {
        Item a = new Item(1);
        Item b = new Item(2);
        SequenceDatabase db = database(
                record(1, itemset(a), itemset(b)),
                record(2, itemset(b), itemset(a)));
        List<Item> f1 = List.of(a, b);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertFalse(cmapI.contains(a, b), "I(a,b) must be false when co-occurrence is only cross-itemset");
        assertTrue(cmapS.contains(a, b), "S(a,b) must be true from sequence 1");
        assertTrue(cmapS.contains(b, a), "S(b,a) must be true from sequence 2");
    }

    @Test
    void testSelfPairTrueForMultipleItemsets() {
        Item a = new Item(1);
        SequenceDatabase db = database(record(1, itemset(a), itemset(a)));
        List<Item> f1 = List.of(a);

        CmapS cmapS = new CmapBuilder().buildS(db, f1);
        assertTrue(cmapS.contains(a, a), "S(a,a) must be true for <(a)(a)>");
    }

    @Test
    void testSelfPairFalseForSingleItemset() {
        Item a = new Item(1);
        SequenceDatabase db = database(record(1, itemset(a)));
        List<Item> f1 = List.of(a);

        CmapS cmapS = new CmapBuilder().buildS(db, f1);
        assertFalse(cmapS.contains(a, a), "S(a,a) must be false for <(a)>");
    }

    @Test
    void testSameItemsetTrapDoesNotCreateS() {
        Item a = new Item(1);
        Item b = new Item(2);
        Item c = new Item(3);
        // a and b appear only together in itemset 0; c appears in itemset 1
        SequenceDatabase db = database(record(1, itemset(a, b), itemset(c)));
        List<Item> f1 = List.of(a, b, c);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertTrue(cmapI.contains(a, b), "I(a,b) must be true");
        assertFalse(cmapS.contains(a, b), "S(a,b) must be false — same itemset does not satisfy t < u");
        assertFalse(cmapS.contains(b, a), "S(b,a) must be false — same itemset does not satisfy t < u");
        assertTrue(cmapS.contains(a, c), "S(a,c) must be true");
        assertTrue(cmapS.contains(b, c), "S(b,c) must be true");
    }

    @Test
    void testCrossSidEvidenceDoesNotCreateIOrS() {
        Item a = new Item(1);
        Item b = new Item(2);
        SequenceDatabase db = database(
                record(10, itemset(a)),
                record(20, itemset(b)));
        List<Item> f1 = List.of(a, b);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        assertFalse(cmapI.contains(a, b), "Cross-SID items must not satisfy CMAP-I");
        assertFalse(cmapS.contains(a, b), "Cross-SID items must not satisfy CMAP-S");
        assertFalse(cmapS.contains(b, a), "Cross-SID items must not satisfy CMAP-S");
    }

    // =========================================================================
    // 7.2 Caller-Supplied F1 Universe Edge Cases
    // =========================================================================

    @Test
    void testCallerSuppliedF1EdgeCases() {
        Item a = new Item(1);
        Item b = new Item(2);
        Item zAbsent = new Item(99);
        Item outsideDb = new Item(50);

        SequenceDatabase db = database(
                record(1, itemset(a, outsideDb), itemset(b)));
        List<Item> f1 = List.of(a, b, zAbsent);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        // F1 item with no database occurrence returns false
        assertFalse(cmapI.contains(a, zAbsent));
        assertFalse(cmapI.contains(b, zAbsent));
        assertFalse(cmapS.contains(a, zAbsent));
        assertFalse(cmapS.contains(zAbsent, a));
        assertFalse(cmapS.contains(zAbsent, zAbsent));

        // Database item outside F1 cannot be queried (throws IllegalArgumentException)
        assertThrows(IllegalArgumentException.class, () -> cmapI.contains(a, outsideDb));
        assertThrows(IllegalArgumentException.class, () -> cmapS.contains(a, outsideDb));
        assertThrows(IllegalArgumentException.class, () -> cmapS.contains(outsideDb, a));

        // Canonical constraint for CMAP-I
        assertThrows(IllegalArgumentException.class, () -> cmapI.contains(b, a));
        assertThrows(IllegalArgumentException.class, () -> cmapI.contains(a, a));

        // Null queries rejected
        assertThrows(NullPointerException.class, () -> cmapI.contains(null, b));
        assertThrows(NullPointerException.class, () -> cmapI.contains(a, null));
        assertThrows(NullPointerException.class, () -> cmapS.contains(null, b));
        assertThrows(NullPointerException.class, () -> cmapS.contains(a, null));
    }

    @Test
    void testEmptyF1AndEmptyDatabase() {
        CmapBuilder builder = new CmapBuilder();
        Item a = new Item(1);
        Item b = new Item(2);

        // Empty database with non-empty F1
        SequenceDatabase emptyDb = emptyDatabase();
        CmapI cmapIEmptyDb = builder.buildI(emptyDb, List.of(a, b));
        CmapS cmapSEmptyDb = builder.buildS(emptyDb, List.of(a, b));
        assertFalse(cmapIEmptyDb.contains(a, b));
        assertFalse(cmapSEmptyDb.contains(a, b));
        assertFalse(cmapSEmptyDb.contains(b, a));
        assertFalse(cmapSEmptyDb.contains(a, a));

        // Empty F1
        CmapI cmapIEmptyF1 = builder.buildI(database(record(1, itemset(a, b))), List.of());
        CmapS cmapSEmptyF1 = builder.buildS(database(record(1, itemset(a, b))), List.of());
        assertThrows(IllegalArgumentException.class, () -> cmapIEmptyF1.contains(a, b));
        assertThrows(IllegalArgumentException.class, () -> cmapSEmptyF1.contains(a, b));
    }

    // =========================================================================
    // 7.3 Integer.MAX_VALUE Boundary Value Testing
    // =========================================================================

    @Test
    void testIntegerMaxValueBoundaryValueNoCollision() {
        Item item0 = new Item(0);
        Item item1 = new Item(1);
        Item itemMaxMinus1 = new Item(Integer.MAX_VALUE - 1);
        Item itemMax = new Item(Integer.MAX_VALUE);
        Item itemAbsent = new Item(Integer.MAX_VALUE - 2);

        // Record 1: <(0)(MAX_VALUE)(MAX_VALUE)>
        // Record 2: <(MAX_VALUE - 1, MAX_VALUE)>
        SequenceDatabase db = database(
                record(1, itemset(item0), itemset(itemMax), itemset(itemMax)),
                record(2, itemset(itemMaxMinus1, itemMax)));
        List<Item> f1 = List.of(item0, item1, itemMaxMinus1, itemMax, itemAbsent);

        CmapBuilder builder = new CmapBuilder();
        CmapI cmapI = builder.buildI(db, f1);
        CmapS cmapS = builder.buildS(db, f1);

        // CMAP-I: (MAX_VALUE - 1, MAX_VALUE) co-occur in same itemset in rec 2
        assertTrue(cmapI.contains(itemMaxMinus1, itemMax),
                "I(MAX_VALUE - 1, MAX_VALUE) must be true from record 2");
        assertFalse(cmapI.contains(item0, itemMax),
                "I(0, MAX_VALUE) must be false");
        assertFalse(cmapI.contains(itemAbsent, itemMax),
                "I(absent, MAX_VALUE) must be false — no collision with present pairs");

        // CMAP-S: (0, MAX_VALUE) has t < u in rec 1
        assertTrue(cmapS.contains(item0, itemMax),
                "S(0, MAX_VALUE) must be true from record 1");
        assertFalse(cmapS.contains(itemMax, item0),
                "S(MAX_VALUE, 0) must be false — directional independence preserved");

        // Self-pair for MAX_VALUE: occurs in pos 1 and pos 2 in rec 1
        assertTrue(cmapS.contains(itemMax, itemMax),
                "S(MAX_VALUE, MAX_VALUE) must be true from record 1");

        // Same-itemset pair in rec 2 must not create S relation
        assertFalse(cmapS.contains(itemMaxMinus1, itemMax),
                "S(MAX_VALUE - 1, MAX_VALUE) must be false — same-itemset does not satisfy t < u");
        assertFalse(cmapS.contains(itemMax, itemMaxMinus1),
                "S(MAX_VALUE, MAX_VALUE - 1) must be false");

        // Absent boundary item
        assertFalse(cmapS.contains(itemAbsent, itemMax),
                "S(absent, MAX_VALUE) must be false");
        assertFalse(cmapS.contains(itemMax, itemAbsent),
                "S(MAX_VALUE, absent) must be false");
        assertFalse(cmapS.contains(itemAbsent, itemAbsent),
                "S(absent, absent) must be false");
    }

    // =========================================================================
    // 8. Fixed-Seed Randomized Differential Testing (1,200 Trials)
    // =========================================================================

    @Test
    void testFixedSeedRandomizedDifferential1200Trials() {
        long seed = 20261028L;
        int trials = 1_200;
        Random rand = new Random(seed);

        CmapBuilder builder = new CmapBuilder();

        // Universe pool: mixing standard IDs, small IDs, zero, and boundary IDs
        int[] poolIds = {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                100, 1_000, 1_000_000,
                Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };

        long totalIComparisons = 0;
        long totalSComparisons = 0;
        long falsePositivesI = 0;
        long falseNegativesI = 0;
        long falsePositivesS = 0;
        long falseNegativesS = 0;

        for (int trial = 0; trial < trials; trial++) {
            // Pick a random subset of pool items as available items for this trial (1..8
            // items)
            int universeSize = 1 + rand.nextInt(8);
            List<Integer> shuffledPool = new ArrayList<>();
            for (int id : poolIds) {
                shuffledPool.add(id);
            }
            Collections.shuffle(shuffledPool, rand);

            List<Item> trialItems = new ArrayList<>();
            for (int i = 0; i < universeSize; i++) {
                trialItems.add(new Item(shuffledPool.get(i)));
            }

            // Decide supplied F1: random subset of trial items (0..trialItems.size())
            int f1Size = rand.nextInt(trialItems.size() + 1);
            List<Item> shuffledForF1 = new ArrayList<>(trialItems);
            Collections.shuffle(shuffledForF1, rand);
            List<Item> f1 = new ArrayList<>(shuffledForF1.subList(0, f1Size));
            // Ensure canonical sorting and distinctness for F1
            Set<Item> f1Set = new HashSet<>(f1);
            List<Item> f1List = new ArrayList<>(f1Set);
            Collections.sort(f1List);

            // Generate sequence database: 0..6 sequences
            int seqCount = rand.nextInt(7);
            List<SequenceRecord> records = new ArrayList<>();
            Set<Integer> usedSids = new HashSet<>();

            for (int s = 0; s < seqCount; s++) {
                // Generate noncontiguous SID (negative, zero, positive)
                int sid;
                do {
                    sid = rand.nextInt(2001) - 1000;
                } while (!usedSids.add(sid));

                int itemsetCount = 1 + rand.nextInt(6);
                List<Itemset> itemsets = new ArrayList<>();

                for (int it = 0; it < itemsetCount; it++) {
                    // Pick non-empty distinct subset of trialItems
                    int itemCount = 1 + rand.nextInt(trialItems.size());
                    List<Item> shuffledItems = new ArrayList<>(trialItems);
                    Collections.shuffle(shuffledItems, rand);
                    Set<Item> itemsetItems = new HashSet<>(shuffledItems.subList(0, itemCount));
                    itemsets.add(new Itemset(itemsetItems));
                }

                records.add(new SequenceRecord(sid, new Sequence(itemsets)));
            }

            SequenceDatabase db = new SequenceDatabase(records);

            // Build CMAP-I and CMAP-S
            CmapI cmapI = builder.buildI(db, f1List);
            CmapS cmapS = builder.buildS(db, f1List);

            // Differential comparison for CMAP-I: all canonical pairs a < b in F1
            for (int i = 0; i < f1List.size(); i++) {
                Item a = f1List.get(i);
                for (int j = i + 1; j < f1List.size(); j++) {
                    Item b = f1List.get(j);
                    totalIComparisons++;

                    boolean expected = directIOracle(db, a, b);
                    boolean actual = cmapI.contains(a, b);

                    if (actual != expected) {
                        if (actual) {
                            falsePositivesI++;
                        } else {
                            falseNegativesI++;
                        }
                        assertEquals(expected, actual, String.format(
                                "CMAP-I mismatch at trial=%d, seed=%d, pair=(%d, %d), expected=%b, actual=%b, F1=%s",
                                trial, seed, a.id(), b.id(), expected, actual, f1List));
                    }
                }
            }

            // Differential comparison for CMAP-S: all ordered pairs (a, b) in F1, including
            // a == b
            for (Item a : f1List) {
                for (Item b : f1List) {
                    totalSComparisons++;

                    boolean expected = directSOracle(db, a, b);
                    boolean actual = cmapS.contains(a, b);

                    if (actual != expected) {
                        if (actual) {
                            falsePositivesS++;
                        } else {
                            falseNegativesS++;
                        }
                        assertEquals(expected, actual, String.format(
                                "CMAP-S mismatch at trial=%d, seed=%d, pair=(%d, %d), expected=%b, actual=%b, F1=%s",
                                trial, seed, a.id(), b.id(), expected, actual, f1List));
                    }
                }
            }
        }

        System.out.println("=== CMAP Integrated Differential Safety Report ===");
        System.out.println("Randomized Trials: " + trials);
        System.out.println("Seed: " + seed);
        System.out.println("Total CMAP-I Comparisons: " + totalIComparisons);
        System.out.println("Total CMAP-S Comparisons: " + totalSComparisons);
        System.out.println("CMAP-I False Positives: " + falsePositivesI);
        System.out.println("CMAP-I False Negatives: " + falseNegativesI);
        System.out.println("CMAP-S False Positives: " + falsePositivesS);
        System.out.println("CMAP-S False Negatives: " + falseNegativesS);

        assertEquals(0, falsePositivesI, "CMAP-I False Positives must be 0");
        assertEquals(0, falseNegativesI, "CMAP-I False Negatives must be 0");
        assertEquals(0, falsePositivesS, "CMAP-S False Positives must be 0");
        assertEquals(0, falseNegativesS, "CMAP-S False Negatives must be 0");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

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
