package vn.edu.wspm.bitprune.verification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Deterministic correctness and oracle differential tests for {@link ExactTemporalVerifier}
 * focusing strictly on I-Extension operations (C07.1).
 *
 * <p><strong>INDEPENDENT CORRECTNESS ORACLE:</strong>
 * Expected results are derived by exhaustive direct scanning of raw {@link SequenceDatabase}
 * and {@link Pattern} structures. The oracle uses recursive backtracking over sequence itemsets
 * without relying on:
 * <ul>
 *   <li>{@link ExactTemporalVerifier} production methods,
 *   <li>{@link VerticalOccurrenceIndex} queries,
 *   <li>{@link PatternEndpoints} helper logic,
 *   <li>Bitmap, CMAP, or PRISM data structures.
 * </ul>
 *
 * <p>The test matrix covers all 22 required cases specified by the C07.1 contract.
 */
class ExactTemporalVerifierIExtensionTest {

    private ExactTemporalVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new ExactTemporalVerifier();
    }

    // =========================================================================
    // Independent Direct-Scan Oracle
    // =========================================================================

    /**
     * Direct-scan oracle: computes the sorted, distinct, 1-based ending itemset positions
     * of all feasible embeddings of {@code pattern} in {@code sequence}.
     */
    private static int[] oracleFeasibleEndpoints(Sequence sequence, Pattern pattern) {
        if (pattern.isEmpty()) {
            return new int[0];
        }
        List<Itemset> patternItemsets = pattern.itemsets();
        List<Itemset> seqItemsets = sequence.itemsets();
        TreeSet<Integer> endpoints = new TreeSet<>();
        findEmbeddings(seqItemsets, patternItemsets, 0, 0, endpoints);

        int[] result = new int[endpoints.size()];
        int idx = 0;
        for (int ep : endpoints) {
            result[idx++] = ep;
        }
        return result;
    }

    private static void findEmbeddings(
            List<Itemset> seqItemsets,
            List<Itemset> patternItemsets,
            int patternIdx,
            int minSeqIdx,
            TreeSet<Integer> endpoints) {

        Itemset targetItemset = patternItemsets.get(patternIdx);
        boolean isLast = (patternIdx == patternItemsets.size() - 1);

        for (int i = minSeqIdx; i < seqItemsets.size(); i++) {
            if (containsAllItems(seqItemsets.get(i), targetItemset)) {
                if (isLast) {
                    endpoints.add(i + 1); // 1-based position
                } else {
                    findEmbeddings(seqItemsets, patternItemsets, patternIdx + 1, i + 1, endpoints);
                }
            }
        }
    }

    private static boolean containsAllItems(Itemset seqItemset, Itemset patternItemset) {
        for (Item item : patternItemset.items()) {
            if (!seqItemset.contains(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Oracle helper: builds the expected {@link VerificationResult} for a pattern across
     * all records in {@code database} in record order.
     */
    private static VerificationResult oracleExpectedResult(SequenceDatabase db, Pattern pattern) {
        List<PatternEndpoints> expectedStates = new ArrayList<>();
        for (SequenceRecord record : db.records()) {
            int[] endpoints = oracleFeasibleEndpoints(record.sequence(), pattern);
            if (endpoints.length > 0) {
                expectedStates.add(PatternEndpoints.of(record.sid(), endpoints));
            }
        }
        return VerificationResult.of(expectedStates);
    }

    // =========================================================================
    // Shared Builders
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

    private static VerticalOccurrenceIndex buildIndex(SequenceDatabase db) {
        return VerticalOccurrenceIndex.build(db, SidMapping.of(db));
    }

    // =========================================================================
    // Test 1: Basic same-itemset match
    // =========================================================================

    @Test
    void test01_basicSameItemsetMatch() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertEquals(1, result.endpointStates().get(0).actualSid());
        assertArrayEquals(new int[]{1}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 2: Same SID but extension item only in a different itemset => nonmatch
    // =========================================================================

    @Test
    void test02_sameSidExtensionItemInDifferentItemsetNonmatch() {
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(0, result.exactSupport());
        assertArrayEquals(new int[0], result.supportingSids());
        assertTrue(result.endpointStates().isEmpty());
    }

    // =========================================================================
    // Test 3: Extension occurrence before parent endpoint => nonmatch
    // =========================================================================

    @Test
    void test03_extensionOccurrenceBeforeParentEndpointNonmatch() {
        // Sequence: <(3)(1)>, pos 1 has item 3, pos 2 has item 1
        var database = db(rec(1, seq(itemset(item(3)), itemset(item(1)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        // Parent endpoint is at position 2
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{2}));

        // Extension item 3 is only at position 1 (< 2)
        var result = verifier.verifyIExtension(parent, parentStates, item(3), index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    // =========================================================================
    // Test 4: Extension occurrence after parent endpoint => nonmatch
    // =========================================================================

    @Test
    void test04_extensionOccurrenceAfterParentEndpointNonmatch() {
        // Sequence: <(1)(2)>, pos 1 has item 1, pos 2 has item 2
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        // Extension item 2 is only at position 2 (> 1) -> does not match I-extension
        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    // =========================================================================
    // Test 5: Multiple parent endpoints, only one endpoint matches
    // =========================================================================

    @Test
    void test05_multipleParentEndpointsOnlyOneEndpointMatches() {
        // Sequence: <(1)(4)(1 2)>, pos 1 has item 1, pos 2 has item 4, pos 3 has items 1 and 2
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(4)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 6: Multiple parent endpoints, multiple endpoints match and are all kept
    // =========================================================================

    @Test
    void test06_multipleParentEndpointsMultipleEndpointsMatchAndAllKept() {
        // Sequence: <(1 2)(1)(1 2)>, positions 1, 2, 3
        var database = db(rec(1, seq(itemset(item(1), item(2)), itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertArrayEquals(new int[]{1, 3}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 7: Counterexample CE-01
    // =========================================================================

    @Test
    void test07_counterexampleCE01() {
        // CE-01 from C01.2 §14: Sequence S_1 = <(a)(ab)> at pos 1, 2. Let a=1, b=2.
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 8: No earliest-only compression
    // =========================================================================

    @Test
    void test08_noEarliestOnlyCompression() {
        // If earliest-only were used on CE-01, endpoints {1, 2} would be compressed to {1}.
        // Then at pos 1, item 2 is absent, yielding false negative support 0.
        // Full All-Endpoints correctly yields support 1 and endpoint {2}.
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertNotEquals(0, result.exactSupport(), "Earliest-only would falsely report support 0");
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 9: No latest-only compression
    // =========================================================================

    @Test
    void test09_noLatestOnlyCompression() {
        // Sequence: <(1 2)(3)(1)> -> pos 1 has (1 2), pos 2 has (3), pos 3 has (1)
        // Parent <(1)> has endpoints {1, 3}.
        // If latest-only were used, parent endpoints would be compressed to {3}.
        // Then extending with item 2 at pos 3 would fail, yielding false negative support 0.
        var database = db(rec(1, seq(itemset(item(1), item(2)), itemset(item(3)), itemset(item(1)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertNotEquals(0, result.exactSupport(), "Latest-only would falsely report support 0");
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 10: Multiple prefix embeddings in the same SID, support is still 1
    // =========================================================================

    @Test
    void test10_multiplePrefixEmbeddingsSameSidSupportIsOne() {
        // Sequence: <(1)(1)(1 2)> at pos 1, 2, 3
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        // Support must be 1, not inflated by multiple prefix embeddings
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 11: Two or more SIDs: one SID matches, other SID does not match
    // =========================================================================

    @Test
    void test11_twoSidsOneMatchOneNonmatch() {
        var database = db(
                rec(10, seq(itemset(item(1), item(2)))),
                rec(20, seq(itemset(item(1)), itemset(item(2))))
        );
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(
                PatternEndpoints.of(10, new int[]{1}),
                PatternEndpoints.of(20, new int[]{1})
        );

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{10}, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertEquals(10, result.endpointStates().get(0).actualSid());
        assertArrayEquals(new int[]{1}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Test 12: Actual SIDs negative, zero, positive and noncontiguous
    // =========================================================================

    @Test
    void test12_actualSidsNegativeZeroPositiveAndNoncontiguous() {
        var database = db(
                rec(-50, seq(itemset(item(1), item(2)))),
                rec(0, seq(itemset(item(1), item(2)))),
                rec(400, seq(itemset(item(1), item(2))))
        );
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(
                PatternEndpoints.of(-50, new int[]{1}),
                PatternEndpoints.of(0, new int[]{1}),
                PatternEndpoints.of(400, new int[]{1})
        );

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);

        assertEquals(3, result.exactSupport());
        assertArrayEquals(new int[]{-50, 0, 400}, result.supportingSids());
        assertEquals(-50, result.endpointStates().get(0).actualSid());
        assertEquals(0, result.endpointStates().get(1).actualSid());
        assertEquals(400, result.endpointStates().get(2).actualSid());
    }

    // =========================================================================
    // Test 13: Empty parent state list => empty result / support 0
    // =========================================================================

    @Test
    void test13_emptyParentStateListReturnsEmptyResult() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var result = verifier.verifyIExtension(parent, List.of(), item(2), index);

        assertEquals(0, result.exactSupport());
        assertArrayEquals(new int[0], result.supportingSids());
        assertTrue(result.endpointStates().isEmpty());
    }

    // =========================================================================
    // Test 14: Extension item absent => empty result
    // =========================================================================

    @Test
    void test14_extensionItemAbsentReturnsEmptyResult() {
        var database = db(rec(1, seq(itemset(item(1)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        // Item 99 is never present in the database
        var result = verifier.verifyIExtension(parent, parentStates, item(99), index);

        assertEquals(0, result.exactSupport());
        assertArrayEquals(new int[0], result.supportingSids());
        assertTrue(result.endpointStates().isEmpty());
    }

    // =========================================================================
    // Test 15: Invalid canonical extension rejected
    // =========================================================================

    @Test
    void test15_invalidCanonicalExtensionRejected() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);
        var states = List.of(PatternEndpoints.of(1, new int[]{1}));

        // Equal item: parent <(1)>, extension 1
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(Pattern.singleton(item(1)), states, item(1), index));

        // Lower item: parent <(2)>, extension 1
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(Pattern.singleton(item(2)), states, item(1), index));

        // Already present item: parent <(1 2)>, extension 2
        var pattern12 = new PatternExtensions().iExtend(Pattern.singleton(item(1)), item(2));
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(pattern12, states, item(2), index));

        // Empty parent
        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(Pattern.empty(), states, item(1), index));
    }

    // =========================================================================
    // Test 16: Duplicate parent SID state rejected
    // =========================================================================

    @Test
    void test16_duplicateParentSidStateRejected() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var duplicateStates = List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(1, new int[]{1})
        );

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(parent, duplicateStates, item(2), index));
    }

    // =========================================================================
    // Test 17: Unknown actual SID rejected
    // =========================================================================

    @Test
    void test17_unknownActualSidRejected() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        // SID 999 is unknown to the index built from database containing only SID 1
        var unknownSidStates = List.of(PatternEndpoints.of(999, new int[]{1}));

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifyIExtension(parent, unknownSidStates, item(2), index));
    }

    // =========================================================================
    // Test 18: Null validation
    // =========================================================================

    @Test
    void test18_nullValidation() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.singleton(item(1));
        var states = List.of(PatternEndpoints.of(1, new int[]{1}));

        assertThrows(NullPointerException.class,
                () -> verifier.verifyIExtension(null, states, item(2), index));
        assertThrows(NullPointerException.class,
                () -> verifier.verifyIExtension(parent, null, item(2), index));
        assertThrows(NullPointerException.class,
                () -> verifier.verifyIExtension(parent, states, null, index));
        assertThrows(NullPointerException.class,
                () -> verifier.verifyIExtension(parent, states, item(2), null));

        var listWithNull = new ArrayList<PatternEndpoints>();
        listWithNull.add(null);
        assertThrows(NullPointerException.class,
                () -> verifier.verifyIExtension(parent, listWithNull, item(2), index));

        assertThrows(NullPointerException.class, () -> VerificationResult.of(null));
        assertThrows(NullPointerException.class, () -> VerificationResult.of(listWithNull));
    }

    // =========================================================================
    // Test 19: Input lists and arrays not mutated
    // =========================================================================

    @Test
    void test19_inputListsAndArraysNotMutated() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var originalEp = PatternEndpoints.of(1, new int[]{1});
        var mutableStates = new ArrayList<PatternEndpoints>();
        mutableStates.add(originalEp);

        verifier.verifyIExtension(parent, mutableStates, item(2), index);

        assertEquals(1, mutableStates.size());
        assertEquals(originalEp, mutableStates.get(0));
        assertArrayEquals(new int[]{1}, originalEp.endpoints());
    }

    // =========================================================================
    // Test 20: VerificationResult defensive-copy and immutability
    // =========================================================================

    @Test
    void test20_verificationResultDefensiveCopyAndImmutability() {
        var ep1 = PatternEndpoints.of(1, new int[]{1, 2});
        var ep2 = PatternEndpoints.of(2, new int[]{3});
        var mutableList = new ArrayList<>(List.of(ep1, ep2));

        var result = VerificationResult.of(mutableList);

        // Mutating caller list does not affect result
        mutableList.clear();
        assertEquals(2, result.endpointStates().size());

        // endpointStates list is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> result.endpointStates().add(ep1));

        // supportingSids defensive copying
        int[] sids1 = result.supportingSids();
        int[] sids2 = result.supportingSids();
        assertArrayEquals(new int[]{1, 2}, sids1);
        sids1[0] = 999;
        assertArrayEquals(new int[]{1, 2}, sids2, "Mutating sids1 must not affect sids2");

        // Value equality and hashCode
        var equalResult = VerificationResult.of(List.of(ep1, ep2));
        assertEquals(result, equalResult);
        assertEquals(result.hashCode(), equalResult.hashCode());

        // Duplicate SIDs rejected
        assertThrows(IllegalArgumentException.class,
                () -> VerificationResult.of(List.of(ep1, PatternEndpoints.of(1, new int[]{5}))));
    }

    // =========================================================================
    // Test 21: Direct-scan oracle differential comparison
    // =========================================================================

    @Test
    void test21_directScanOracleDifferentialComparison() {
        // Multi-sequence rich test database
        var database = db(
                rec(1, seq(itemset(item(1)), itemset(item(1), item(2), item(3)))),
                rec(2, seq(itemset(item(1), item(2)), itemset(item(3)), itemset(item(1), item(2)))),
                rec(3, seq(itemset(item(1)), itemset(item(2)), itemset(item(3)))),
                rec(4, seq(itemset(item(1), item(2), item(3)))),
                rec(5, seq(itemset(item(4)), itemset(item(5))))
        );
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var child = new PatternExtensions().iExtend(parent, item(2));

        // Obtain parent endpoint states from oracle
        var parentExpected = oracleExpectedResult(database, parent);

        // Execute production verifier
        var actualResult = verifier.verifyIExtension(
                parent, parentExpected.endpointStates(), item(2), index);

        // Obtain child expected result directly from oracle
        var expectedChildResult = oracleExpectedResult(database, child);

        // Differential verification: exactSupport, supportingSids, endpointStates
        assertEquals(expectedChildResult.exactSupport(), actualResult.exactSupport());
        assertArrayEquals(expectedChildResult.supportingSids(), actualResult.supportingSids());
        assertEquals(expectedChildResult.endpointStates(), actualResult.endpointStates());
        assertEquals(expectedChildResult, actualResult);
    }

    // =========================================================================
    // Test 22: Pattern with multiple itemsets and final itemset with multiple items
    // =========================================================================

    @Test
    void test22_multiItemsetPatternWithMultiItemFinalItemset() {
        // Parent: <(1 2)(3 4)>, Extension: 5 => Child: <(1 2)(3 4 5)>
        var database = db(
                // SID 10: pos 1 = (1 2), pos 2 = (3 4 5) -> Match at endpoint 2
                rec(10, seq(itemset(item(1), item(2)), itemset(item(3), item(4), item(5)))),
                // SID 20: pos 1 = (1 2), pos 2 = (3 4), pos 3 = (3 4 5) -> Match at endpoint 3
                rec(20, seq(itemset(item(1), item(2)), itemset(item(3), item(4)), itemset(item(3), item(4), item(5)))),
                // SID 30: pos 1 = (1 2), pos 2 = (3 4), pos 3 = (5) -> 5 is in separate itemset -> Nonmatch
                rec(30, seq(itemset(item(1), item(2)), itemset(item(3), item(4)), itemset(item(5))))
        );
        var index = buildIndex(database);

        var parent = Pattern.of(itemset(item(1), item(2)), itemset(item(3), item(4)));
        var child = new PatternExtensions().iExtend(parent, item(5));

        var parentExpected = oracleExpectedResult(database, parent);
        var actualResult = verifier.verifyIExtension(
                parent, parentExpected.endpointStates(), item(5), index);

        var expectedChildResult = oracleExpectedResult(database, child);

        assertEquals(2, actualResult.exactSupport());
        assertArrayEquals(new int[]{10, 20}, actualResult.supportingSids());
        assertEquals(expectedChildResult.endpointStates(), actualResult.endpointStates());
        assertEquals(expectedChildResult, actualResult);
    }
}
