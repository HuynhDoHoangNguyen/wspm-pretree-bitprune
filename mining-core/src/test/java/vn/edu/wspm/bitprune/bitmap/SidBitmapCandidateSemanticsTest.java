package vn.edu.wspm.bitprune.bitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Correctness and differential evidence for singleton and candidate bitmap
 * semantics (C03.2).
 *
 * <p>
 * Verifies that candidate bitmaps act strictly as necessary-condition upper
 * bounds:
 * {@code SupportingSIDs(P) ⊆ CandidateBitmapSIDs(P)} and
 * {@code support(P) <= cardinality(BM(P))}.
 * Bitmap cardinality is never equated with exact temporal support.
 * </p>
 */
class SidBitmapCandidateSemanticsTest {

    private static final Item ITEM_A = new Item(1);
    private static final Item ITEM_B = new Item(2);
    private static final Item ITEM_C = new Item(3);
    private static final Item ITEM_D = new Item(4);

    // =========================================================================
    // Test-Local Helper Fixture Constructors & Oracles
    // =========================================================================

    private static Itemset is(Item... items) {
        return Itemset.of(items);
    }

    private static Sequence seq(Itemset... itemsets) {
        return Sequence.of(itemsets);
    }

    private static SequenceRecord rec(int sid, Itemset... itemsets) {
        return new SequenceRecord(sid, seq(itemsets));
    }

    private static SequenceDatabase db(SequenceRecord... records) {
        return new SequenceDatabase(Arrays.asList(records));
    }

    private static boolean sequenceContainsItem(Sequence sequence, Item item) {
        for (var itemset : sequence.itemsets()) {
            if (itemset.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private static Set<Item> distinctItems(Pattern pattern) {
        var distinct = new LinkedHashSet<Item>();
        for (var itemset : pattern.itemsets()) {
            distinct.addAll(itemset.items());
        }
        return distinct;
    }

    /**
     * Builds a singleton bitmap for an item by directly scanning the database.
     */
    private static SidBitmap scanSingleton(SequenceDatabase db, SidMapping mapping, Item item) {
        var builder = SidBitmap.builder(mapping);
        for (var record : db.records()) {
            if (sequenceContainsItem(record.sequence(), item)) {
                builder.setSid(record.sid());
            }
        }
        return builder.build();
    }

    /**
     * Builds a candidate bitmap by intersecting the singleton bitmaps of all
     * distinct items in the pattern.
     */
    private static SidBitmap candidateBitmap(SequenceDatabase db, SidMapping mapping, Pattern pattern) {
        var distinct = distinctItems(pattern);
        if (distinct.isEmpty()) {
            return SidBitmap.empty(mapping);
        }
        SidBitmap candidate = null;
        for (var item : distinct) {
            var singleton = scanSingleton(db, mapping, item);
            candidate = (candidate == null) ? singleton : candidate.and(singleton);
        }
        return candidate;
    }

    /**
     * Independent Set-based oracle for singleton item supporting SIDs.
     */
    private static Set<Integer> expectedSingletonSids(SequenceDatabase db, Item item) {
        var sids = new HashSet<Integer>();
        for (var record : db.records()) {
            if (sequenceContainsItem(record.sequence(), item)) {
                sids.add(record.sid());
            }
        }
        return sids;
    }

    /**
     * Independent Set-based oracle for candidate bitmap supporting SIDs via Set
     * intersection.
     */
    private static Set<Integer> expectedCandidateSids(SequenceDatabase db, Pattern pattern) {
        var distinct = distinctItems(pattern);
        if (distinct.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> result = null;
        for (var item : distinct) {
            var sids = expectedSingletonSids(db, item);
            if (result == null) {
                result = new HashSet<>(sids);
            } else {
                result.retainAll(sids);
            }
        }
        return result;
    }

    /**
     * Test-local structural sequence matcher (pure test oracle; not a production
     * verifier).
     */
    private static boolean sequenceMatchesPattern(Sequence sequence, Pattern pattern) {
        if (pattern.isEmpty()) {
            return true;
        }
        var patternItemsets = pattern.itemsets();
        var sequenceItemsets = sequence.itemsets();
        int pIdx = 0;
        for (int sIdx = 0; sIdx < sequenceItemsets.size() && pIdx < patternItemsets.size(); sIdx++) {
            var pItemset = patternItemsets.get(pIdx);
            var sItemset = sequenceItemsets.get(sIdx);
            if (sItemset.items().containsAll(pItemset.items())) {
                pIdx++;
            }
        }
        return pIdx == patternItemsets.size();
    }

    /**
     * Independent exact temporal supporting SIDs oracle.
     */
    private static Set<Integer> exactSupportingSids(SequenceDatabase db, Pattern pattern) {
        var sids = new HashSet<Integer>();
        for (var record : db.records()) {
            if (sequenceMatchesPattern(record.sequence(), pattern)) {
                sids.add(record.sid());
            }
        }
        return sids;
    }

    private static Set<Integer> toSet(int[] array) {
        var set = new HashSet<Integer>(array.length);
        for (int val : array) {
            set.add(val);
        }
        return set;
    }

    // =========================================================================
    // Deterministic Tests (Section 11)
    // =========================================================================

    @Test
    void tvC03201_singletonDistinctSid() {
        // TV-C03.2-01: Fixture with actual SIDs {-5, 0, 12, 100}
        // Item A appears multiple times in SID -5, once in SID 12, absent in 0 and 100
        var database = db(
                rec(-5, is(ITEM_A), is(ITEM_A, ITEM_B)),
                rec(0, is(ITEM_B), is(ITEM_C)),
                rec(12, is(ITEM_A, ITEM_C)),
                rec(100, is(ITEM_D)));
        var mapping = SidMapping.of(database);
        var bitmapA = scanSingleton(database, mapping, ITEM_A);

        assertEquals(2, bitmapA.cardinality());
        assertArrayEquals(new int[] { -5, 12 }, bitmapA.toSupportingSids(),
                "Must return actual SIDs in bit-index/record order, not compact indices or natural sort");

        // Verify independent Set oracle
        assertEquals(Set.of(-5, 12), expectedSingletonSids(database, ITEM_A));
        assertEquals(expectedSingletonSids(database, ITEM_A), toSet(bitmapA.toSupportingSids()));

        assertTrue(bitmapA.containsSid(-5));
        assertTrue(bitmapA.containsSid(12));
        assertFalse(bitmapA.containsSid(0));
        assertFalse(bitmapA.containsSid(100));
    }

    @Test
    void tvC03202_multipleOccurrencesDoNotInflateCardinality() {
        // TV-C03.2-02: A single SID containing item A in multiple itemsets and
        // positions
        // contributes at most one bit toward cardinality
        var database = db(
                rec(10, is(ITEM_A), is(ITEM_A), is(ITEM_A)),
                rec(20, is(ITEM_A, ITEM_B), is(ITEM_A, ITEM_C)),
                rec(30, is(ITEM_B), is(ITEM_C)));
        var mapping = SidMapping.of(database);
        var bitmapA = scanSingleton(database, mapping, ITEM_A);

        // Item A occurs 5 times across 2 distinct sequences
        assertEquals(2, bitmapA.cardinality(), "Cardinality must equal distinct supporting SIDs, not item occurrences");
        assertEquals(Set.of(10, 20), toSet(bitmapA.toSupportingSids()));
    }

    @Test
    void tvC03203_candidateAndVersusSetOracle() {
        // TV-C03.2-03: Intersection across three singleton items on negative, 0,
        // noncontiguous SIDs
        var database = db(
                rec(-10, is(ITEM_A, ITEM_B, ITEM_C)), // Has A, B, C
                rec(-5, is(ITEM_A, ITEM_B)), // Has A, B
                rec(0, is(ITEM_A, ITEM_C)), // Has A, C
                rec(7, is(ITEM_B, ITEM_C)), // Has B, C
                rec(25, is(ITEM_A, ITEM_B), is(ITEM_C)) // Has A, B, C
        );
        var mapping = SidMapping.of(database);

        var pattern = Pattern.of(is(ITEM_A), is(ITEM_B), is(ITEM_C));
        var candidateBm = candidateBitmap(database, mapping, pattern);

        Set<Integer> expectedSet = expectedCandidateSids(database, pattern);
        assertEquals(Set.of(-10, 25), expectedSet);

        assertEquals(expectedSet.size(), candidateBm.cardinality());
        assertEquals(expectedSet, toSet(candidateBm.toSupportingSids()));
    }

    @Test
    void tvC03204_sameItemsetAndSequentialPatternsShareLooseCandidateBitmap() {
        // TV-C03.2-04: <(a b)> and <(a)(b)> share identical candidate bitmap,
        // while exact supporting SID sets differ
        var database = db(
                rec(1, is(ITEM_A), is(ITEM_B)), // Sequential <(a)(b)>
                rec(2, is(ITEM_A, ITEM_B)) // Same-itemset <(a b)>
        );
        var mapping = SidMapping.of(database);

        var pSameItemset = Pattern.of(is(ITEM_A, ITEM_B));
        var pSequential = Pattern.of(is(ITEM_A), is(ITEM_B));

        var bmSameItemset = candidateBitmap(database, mapping, pSameItemset);
        var bmSequential = candidateBitmap(database, mapping, pSequential);

        // Candidate bitmaps are identical because distinct item sets are both {A, B}
        assertEquals(bmSameItemset, bmSequential);
        assertEquals(2, bmSameItemset.cardinality());
        assertEquals(Set.of(1, 2), toSet(bmSameItemset.toSupportingSids()));

        // Exact temporal supporting SIDs differ
        var exactSameItemset = exactSupportingSids(database, pSameItemset);
        var exactSequential = exactSupportingSids(database, pSequential);

        assertEquals(Set.of(2), exactSameItemset, "<(a b)> is only supported by SID 2");
        assertEquals(Set.of(1), exactSequential, "<(a)(b)> is only supported by SID 1");

        // Verify inclusion for both patterns
        assertTrue(toSet(bmSameItemset.toSupportingSids()).containsAll(exactSameItemset));
        assertTrue(toSet(bmSequential.toSupportingSids()).containsAll(exactSequential));
        assertTrue(exactSameItemset.size() <= bmSameItemset.cardinality());
        assertTrue(exactSequential.size() <= bmSequential.cardinality());
    }

    @Test
    void tvC03205_repeatedItemPattern() {
        // TV-C03.2-05: Pattern <(a)(a)> has distinct items {a}, so BM(P) = BM(a)
        var database = db(
                rec(10, is(ITEM_A), is(ITEM_A)), // Contains two a's
                rec(20, is(ITEM_A)), // Contains only one a
                rec(30, is(ITEM_B)) // Contains no a
        );
        var mapping = SidMapping.of(database);

        var pRepeated = Pattern.of(is(ITEM_A), is(ITEM_A));
        var bmA = scanSingleton(database, mapping, ITEM_A);
        var bmCandidate = candidateBitmap(database, mapping, pRepeated);

        // BM(P) = BM(a) AND BM(a) = BM(a)
        assertEquals(bmA, bmCandidate);
        assertEquals(bmA, bmA.and(bmA));
        assertEquals(2, bmCandidate.cardinality());
        assertEquals(Set.of(10, 20), toSet(bmCandidate.toSupportingSids()));

        // Exact temporal support excludes SID 20 because it contains only one 'a'
        var exactSids = exactSupportingSids(database, pRepeated);
        assertEquals(Set.of(10), exactSids);

        // Inclusion holds: {10} ⊆ {10, 20} and 1 <= 2
        assertTrue(toSet(bmCandidate.toSupportingSids()).containsAll(exactSids));
        assertTrue(exactSids.size() <= bmCandidate.cardinality());
    }

    @Test
    void tvC03206_ce03TemporalFalsePositive() {
        // TV-C03.2-06: Mandatory counter-example CE-03
        // SID 1: <(b)(a)>
        // Candidate: <(a)(b)>
        var database = db(
                rec(1, is(ITEM_B), is(ITEM_A)));
        var mapping = SidMapping.of(database);

        var pattern = Pattern.of(is(ITEM_A), is(ITEM_B));
        var bmA = scanSingleton(database, mapping, ITEM_A);
        var bmB = scanSingleton(database, mapping, ITEM_B);
        var candidateBm = bmA.and(bmB);

        assertEquals(1, candidateBm.cardinality(), "Both items A and B are present in SID 1");
        assertEquals(Set.of(1), toSet(candidateBm.toSupportingSids()));

        var exactSids = exactSupportingSids(database, pattern);
        assertEquals(Collections.emptySet(), exactSids, "<(a)(b)> does not appear in <(b)(a)>");
        assertEquals(0, exactSids.size());

        // Inclusion holds strictly: ∅ ⊆ {1} and 0 <= 1
        assertTrue(toSet(candidateBm.toSupportingSids()).containsAll(exactSids));
        assertTrue(exactSids.size() <= candidateBm.cardinality());
    }

    @Test
    void tvC03207_inclusionAndUpperBoundCatalogue() {
        // TV-C03.2-07: Diverse deterministic database catalogue
        var database = db(
                rec(10, is(ITEM_A, ITEM_B), is(ITEM_C), is(ITEM_A)),
                rec(20, is(ITEM_B), is(ITEM_A), is(ITEM_C)),
                rec(30, is(ITEM_A), is(ITEM_B), is(ITEM_C), is(ITEM_D)),
                rec(40, is(ITEM_C), is(ITEM_B), is(ITEM_A)),
                rec(50, is(ITEM_A, ITEM_C), is(ITEM_B, ITEM_D)),
                rec(60, is(ITEM_D), is(ITEM_D)));
        var mapping = SidMapping.of(database);

        List<Pattern> catalogue = List.of(
                Pattern.singleton(ITEM_A), // 1. Singleton
                Pattern.singleton(ITEM_D), // 2. Singleton
                Pattern.of(is(ITEM_A, ITEM_B)), // 3. I-style same-itemset
                Pattern.of(is(ITEM_A), is(ITEM_C)), // 4. S-style sequential
                Pattern.of(is(ITEM_A), is(ITEM_D)), // 5. Gapped pattern
                Pattern.of(is(ITEM_A), is(ITEM_A)), // 6. Repeated-item pattern
                Pattern.of(is(ITEM_A), is(ITEM_B), is(ITEM_A)), // 7. Multi-itemset repeated
                Pattern.of(is(ITEM_B), is(ITEM_C)), // 8. Multiple embeddings
                Pattern.of(is(ITEM_C), is(ITEM_A)), // 9. Temporal false positive
                Pattern.of(is(ITEM_A, ITEM_B), is(ITEM_C)), // 10. Multi-itemset combo
                Pattern.of(is(ITEM_A), is(ITEM_B), is(ITEM_C), is(ITEM_D)) // 11. Long sequential
        );

        for (var pattern : catalogue) {
            var candidateBm = candidateBitmap(database, mapping, pattern);
            var candidateSids = toSet(candidateBm.toSupportingSids());
            var exactSids = exactSupportingSids(database, pattern);

            String diagnostic = String.format(
                    "Failure on pattern: %s%nExact SIDs: %s (size %d)%nCandidate SIDs: %s (cardinality %d)",
                    pattern.serialize(), exactSids, exactSids.size(), candidateSids, candidateBm.cardinality());

            // Invariant 1: SupportingSIDs(P) ⊆ CandidateBitmapSIDs(P)
            assertTrue(candidateSids.containsAll(exactSids), "Inclusion violation: " + diagnostic);

            // Invariant 2: support(P) <= cardinality(candidateBitmap(P))
            assertTrue(exactSids.size() <= candidateBm.cardinality(), "Upper bound violation: " + diagnostic);
        }
    }

    // =========================================================================
    // Fixed-Seed Randomized Differential Test (Section 12)
    // =========================================================================

    @Test
    void fixedSeedRandomizedDifferentialAndVersusSetOracle() {
        // Section 12: Fixed-seed randomized AND differential test
        final long fixedSeed = 20261028L;
        final int iterations = 1200; // >= 1000
        var random = new Random(fixedSeed);

        // Predefined universe sizes covering boundary and general cases
        int[] universeSizes = {
                0, 1, 2, 10, 31, 32, 63, 64, 65, 127, 128, 129, 150, 192, 200
        };

        for (int i = 0; i < iterations; i++) {
            int universeSize = universeSizes[i % universeSizes.length];

            // Generate arbitrary SIDs (negative, zero, positive, noncontiguous)
            int[] actualSids = new int[universeSize];
            int currentSid = -500 + random.nextInt(100);
            for (int k = 0; k < universeSize; k++) {
                currentSid += 1 + random.nextInt(15); // strictly increasing, noncontiguous
                actualSids[k] = currentSid;
            }

            // Shuffle SID order to ensure record-order mapping rather than natural sort
            // (Fisher-Yates shuffle)
            for (int k = actualSids.length - 1; k > 0; k--) {
                int swapIdx = random.nextInt(k + 1);
                int temp = actualSids[k];
                actualSids[k] = actualSids[swapIdx];
                actualSids[swapIdx] = temp;
            }

            // Build SequenceDatabase and SidMapping
            var records = new ArrayList<SequenceRecord>(universeSize);
            for (int sid : actualSids) {
                records.add(rec(sid, is(ITEM_A)));
            }
            var db = new SequenceDatabase(records);
            var mapping = SidMapping.of(db);

            // Generate two random subsets of SIDs
            var setA = new HashSet<Integer>();
            var setB = new HashSet<Integer>();

            var builderA = SidBitmap.builder(mapping);
            var builderB = SidBitmap.builder(mapping);

            for (int sid : actualSids) {
                if (random.nextBoolean()) {
                    setA.add(sid);
                    builderA.setSid(sid);
                }
                if (random.nextBoolean()) {
                    setB.add(sid);
                    builderB.setSid(sid);
                }
            }

            var bitmapA = builderA.build();
            var bitmapB = builderB.build();

            // Independent Oracle: Set intersection
            var expectedIntersection = new HashSet<>(setA);
            expectedIntersection.retainAll(setB);

            // Production operation: Bitwise AND
            var actualBitmap = bitmapA.and(bitmapB);

            String diagnostic = String.format(
                    "Iteration: %d, Seed: %d, UniverseSize: %d, ExpectedSize: %d, ActualCard: %d",
                    i, fixedSeed, universeSize, expectedIntersection.size(), actualBitmap.cardinality());

            // Cardinality equality
            assertEquals(expectedIntersection.size(), actualBitmap.cardinality(), diagnostic);

            // Exact set equality
            assertEquals(expectedIntersection, toSet(actualBitmap.toSupportingSids()), diagnostic);

            // Immuntability check: neither input was mutated
            assertEquals(setA.size(), bitmapA.cardinality(), "bitmapA mutated: " + diagnostic);
            assertEquals(setB.size(), bitmapB.cardinality(), "bitmapB mutated: " + diagnostic);
        }
    }
}
