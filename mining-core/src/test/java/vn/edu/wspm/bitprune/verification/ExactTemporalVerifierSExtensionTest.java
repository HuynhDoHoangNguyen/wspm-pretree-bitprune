package vn.edu.wspm.bitprune.verification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * Deterministic correctness and oracle differential tests for
 * {@link ExactTemporalVerifier}
 * focusing strictly on S-Extension operations (C08.1).
 *
 * <p>
 * <strong>INDEPENDENT CORRECTNESS ORACLE:</strong>
 * Expected results are derived by exhaustive direct scanning of raw
 * {@link SequenceDatabase}
 * and {@link Pattern} structures. The oracle uses recursive backtracking over
 * sequence itemsets
 * without relying on:
 * <ul>
 * <li>{@link ExactTemporalVerifier} production methods or private helpers,
 * <li>{@link VerticalOccurrenceIndex} queries,
 * <li>{@link PatternEndpoints} helper logic,
 * <li>Bitmap, CMAP, or PRISM data structures.
 * </ul>
 *
 * <p>
 * The test matrix covers all required cases specified by the C08.1 contract.
 */
class ExactTemporalVerifierSExtensionTest {

    private ExactTemporalVerifier verifier;
    private static final PatternExtensions SHARED_EXTENSIONS = new PatternExtensions();

    @BeforeEach
    void setUp() {
        verifier = new ExactTemporalVerifier();
    }

    // =========================================================================
    // Independent Direct-Scan Oracle
    // =========================================================================

    /**
     * Direct-scan oracle: computes the sorted, distinct, 1-based ending itemset
     * positions
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
     * Oracle helper: builds the expected {@link VerificationResult} for a pattern
     * across
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

    private static VerticalOccurrenceIndex buildIndex(SequenceDatabase db) {
        SidMapping mapping = SidMapping.of(db);
        return VerticalOccurrenceIndex.build(db, mapping);
    }

    // =========================================================================
    // Deterministic Tests 1-26
    // =========================================================================

    /**
     * 1. Basic strictly-later match: parent endpoint at 1, extension at 2 -> child
     * endpoint at 2.
     */
    @Test
    void test01_basicStrictlyLaterMatch() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 1 }, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertEquals(PatternEndpoints.of(1, new int[] { 2 }), result.endpointStates().get(0));

        // Differential verification with direct-scan oracle
        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 2. Same-itemset-only occurrence rejected: extension item appears only in the
     * same
     * itemset as parent endpoint (pos == ep). S-extension requires strict ep < pos.
     */
    @Test
    void test02_sameItemsetOnlyOccurrenceRejected() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a, b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(0, result.exactSupport());
        assertArrayEquals(new int[0], result.supportingSids());
        assertTrue(result.endpointStates().isEmpty());

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 3. Extension occurrence before parent endpoint rejected: extension item
     * appears at pos 1,
     * parent endpoint is at pos 2. ep < pos is violated (2 < 1 is false).
     */
    @Test
    void test03_extensionOccurrenceBeforeParentEndpointRejected() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(b), itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 4. Reverse-order match rejected: sequence has <(b), (a)>, candidate is
     * <(a)(b)>.
     */
    @Test
    void test04_reverseOrderMatchRejected() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(10, seq(itemset(b), itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(10, new int[] { 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    /**
     * 5. Repeated item: <(a)> ⊕S a = <(a)(a)>. Repetition across itemset boundaries
     * is valid for S-Extension.
     */
    @Test
    void test05_repeatedItemExtension() {
        Item a = item(1);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        // Parent <(a)> appears at positions 1 and 2
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1, 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, a, index);

        // Child <(a)(a)> can only end at pos 2 (from parent at pos 1)
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 1 }, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertEquals(PatternEndpoints.of(1, new int[] { 2 }), result.endpointStates().get(0));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, a);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 6. Extension item smaller than parent last item is allowed in S-Extension
     * (unlike I-Extension, S-Extension has no canonical item ordering constraint).
     */
    @Test
    void test06_extensionItemSmallerThanParentLastItemAllowed() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(b), itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        // Parent is <(b)> where b has id 2. Extension is a (id 1 < 2).
        Pattern parent = Pattern.of(itemset(b));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, a, index);

        assertEquals(1, result.exactSupport());
        assertEquals(PatternEndpoints.of(1, new int[] { 2 }), result.endpointStates().get(0));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, a);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 7. Named counterexample CE-02 (C01.2 §11/CE-02):
     * S = <(a), (b), (a)>
     * P = <(a)>
     * E(P, S) = {1, 3}
     * P ⊕S b = <(a)(b)>
     * Expected child endpoints = {2}
     * exactSupport = 1
     *
     * <p>
     * If an implementation compressed parent state to latest-only {3}, endpoint 2
     * would be
     * falsely rejected (2 < 3), creating a false negative. This test demonstrates
     * that
     * All-Endpoints preservation correctly survives endpoint 2.
     */
    @Test
    void test07_namedCounterexampleCE02() {
        Item a = item(1);
        Item b = item(2);
        Sequence seq = seq(itemset(a), itemset(b), itemset(a));
        SequenceDatabase db = new SequenceDatabase(List.of(rec(1, seq)));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        // Full All-Endpoints state for parent <(a)> in S is {1, 3}
        List<PatternEndpoints> allEndpointsState = List.of(PatternEndpoints.of(1, new int[] { 1, 3 }));

        VerificationResult result = verifier.verifySExtension(parent, allEndpointsState, b, index);

        // Child endpoint at 2 must survive!
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 1 }, result.supportingSids());
        assertEquals(PatternEndpoints.of(1, new int[] { 2 }), result.endpointStates().get(0));

        // Demonstrating that latest-only compression produces a FALSE NEGATIVE:
        List<PatternEndpoints> flawedLatestOnlyState = List.of(PatternEndpoints.of(1, new int[] { 3 }));
        VerificationResult flawedResult = verifier.verifySExtension(parent, flawedLatestOnlyState, b, index);
        assertEquals(0, flawedResult.exactSupport(), "Latest-only compression causes false negative (support 0)");

        // Differential verification against direct-scan oracle
        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 8. Multiple parent endpoints, only the early endpoint allows a child.
     * Parent endpoints: {2, 5}. Extension occurs at pos 3.
     * 2 < 3 (valid), 5 < 3 (invalid). Child endpoint 3 must be preserved.
     */
    @Test
    void test08_multipleParentEndpointsOnlyEarlyAllowsChild() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(b), itemset(a), itemset(b), itemset(b), itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        // Parent endpoints at {2, 5}
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 2, 5 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        // Extension item b appears at positions 1, 3, 4, 6.
        // Positions > minParent(2) are: 3, 4, 6.
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 3, 4, 6 }, result.endpointStates().get(0).endpoints());
    }

    /**
     * 9. Multiple extension positions after parent are all preserved.
     * Parent endpoint at 1. Extension item at positions 2, 4, 6.
     * Child endpoints must be {2, 4, 6}.
     */
    @Test
    void test09_multipleExtensionPositionsAfterParentAllPreserved() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b), itemset(a), itemset(b), itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 2, 4, 6 }, result.endpointStates().get(0).endpoints());
    }

    /**
     * 10. A single child position reachable from multiple parent endpoints is
     * recorded
     * exactly once (no duplicates).
     * Parent endpoints: {1, 2}. Extension item at pos 3. Both 1 < 3 and 2 < 3 hold.
     * Child endpoint must be {3}.
     */
    @Test
    void test10_singleChildPositionReachableFromMultipleParentEndpointsRecordedOnce() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1, 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 3 }, result.endpointStates().get(0).endpoints());
    }

    /**
     * 11. Multiple embeddings within the same SID: exact support remains 1.
     * Distinct SID support invariant (C01.1 §7.3).
     */
    @Test
    void test11_multipleEmbeddingsInSameSidExactSupportRemainsOne() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b), itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1, 3 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        // Two embeddings: (pos 1 -> pos 2, pos 1 -> pos 4, pos 3 -> pos 4).
        // Surviving child endpoints: {2, 4}.
        // But exact support is exactly 1 (one distinct SID).
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 2, 4 }, result.endpointStates().get(0).endpoints());

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 12. Two SIDs: one supporting, one reverse/same-itemset non-matching.
     */
    @Test
    void test12_twoSidsOneSupportingOneNonMatching() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(100, seq(itemset(a), itemset(b))), // matches <(a)(b)>
                rec(200, seq(itemset(b), itemset(a))) // reverse order, does not match <(a)(b)>
        ));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(
                PatternEndpoints.of(100, new int[] { 1 }),
                PatternEndpoints.of(200, new int[] { 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[] { 100 }, result.supportingSids());
        assertEquals(1, result.endpointStates().size());
        assertEquals(PatternEndpoints.of(100, new int[] { 2 }), result.endpointStates().get(0));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, b);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 13. Actual SID negative, 0, positive, and non-contiguous.
     */
    @Test
    void test13_actualSidNegativeZeroPositiveNonContiguous() {
        Item a = item(1);
        Item c = item(3);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(-50, seq(itemset(a), itemset(c))), // matches <(a)(c)> at pos 2
                rec(0, seq(itemset(c), itemset(a))), // reverse, no match
                rec(9999, seq(itemset(a), itemset(item(2)), itemset(c))) // matches at pos 3
        ));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(
                PatternEndpoints.of(-50, new int[] { 1 }),
                PatternEndpoints.of(0, new int[] { 2 }),
                PatternEndpoints.of(9999, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, c, index);

        assertEquals(2, result.exactSupport());
        assertArrayEquals(new int[] { -50, 9999 }, result.supportingSids());
        assertEquals(2, result.endpointStates().size());
        assertEquals(PatternEndpoints.of(-50, new int[] { 2 }), result.endpointStates().get(0));
        assertEquals(PatternEndpoints.of(9999, new int[] { 3 }), result.endpointStates().get(1));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, c);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 14. Extension item absent from the entire database.
     */
    @Test
    void test14_extensionItemAbsentFromDatabase() {
        Item a = item(1);
        Item absent = item(99);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, absent, index);

        assertEquals(0, result.exactSupport());
        assertArrayEquals(new int[0], result.supportingSids());
        assertTrue(result.endpointStates().isEmpty());
    }

    /**
     * 15. Empty parent-state list returns an empty VerificationResult.
     */
    @Test
    void test15_emptyParentStateListReturnsEmptyResult() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        VerificationResult result = verifier.verifySExtension(parent, List.of(), b, index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    /**
     * 16. No occurrence strictly later: extension item occurs in sequence, but only
     * at positions
     * less than or equal to parent endpoints.
     */
    @Test
    void test16_noOccurrenceStrictlyLater() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(b), itemset(b), itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 3 }));

        // b appears at 1 and 2, but parent endpoint is 3. None strictly later.
        VerificationResult result = verifier.verifySExtension(parent, parentStates, b, index);

        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    /**
     * 17. Multi-itemset parent pattern: <(a), (b)> ⊕S c = <(a), (b), (c)>.
     */
    @Test
    void test17_multiItemsetParentPattern() {
        Item a = item(1);
        Item b = item(2);
        Item c = item(3);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b), itemset(c)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a), itemset(b));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, c, index);

        assertEquals(1, result.exactSupport());
        assertEquals(PatternEndpoints.of(1, new int[] { 3 }), result.endpointStates().get(0));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, c);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 18. Parent final itemset has multiple items: <(a), (b, c)> ⊕S d = <(a), (b,
     * c), (d)>.
     */
    @Test
    void test18_parentFinalItemsetHasMultipleItems() {
        Item a = item(1);
        Item b = item(2);
        Item c = item(3);
        Item d = item(4);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b, c), itemset(d)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a), itemset(b, c));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 2 }));

        VerificationResult result = verifier.verifySExtension(parent, parentStates, d, index);

        assertEquals(1, result.exactSupport());
        assertEquals(PatternEndpoints.of(1, new int[] { 3 }), result.endpointStates().get(0));

        Pattern child = SHARED_EXTENSIONS.sExtend(parent, d);
        VerificationResult expected = oracleExpectedResult(db, child);
        assertEquals(expected, result);
    }

    /**
     * 19. Duplicate parent actual SID rejected with IllegalArgumentException.
     */
    @Test
    void test19_duplicateParentSidRejected() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> duplicateStates = List.of(
                PatternEndpoints.of(1, new int[] { 1 }),
                PatternEndpoints.of(1, new int[] { 1 }));

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifySExtension(parent, duplicateStates, b, index));
    }

    /**
     * 20. Unknown actual SID (not in occurrence index universe) rejected with
     * IllegalArgumentException.
     */
    @Test
    void test20_unknownActualSidRejected() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> unknownSidState = List.of(PatternEndpoints.of(999, new int[] { 1 }));

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifySExtension(parent, unknownSidState, b, index));
    }

    /**
     * 21. Null parameter validation: fail-fast with NullPointerException.
     */
    @Test
    void test21_nullValidation() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);
        Pattern parent = Pattern.of(itemset(a));
        List<PatternEndpoints> parentStates = List.of(PatternEndpoints.of(1, new int[] { 1 }));

        assertThrows(NullPointerException.class, () -> verifier.verifySExtension(null, parentStates, b, index));
        assertThrows(NullPointerException.class, () -> verifier.verifySExtension(parent, null, b, index));
        assertThrows(NullPointerException.class, () -> verifier.verifySExtension(parent, parentStates, null, index));
        assertThrows(NullPointerException.class, () -> verifier.verifySExtension(parent, parentStates, b, null));

        // Null element in parentEndpointStates list
        List<PatternEndpoints> listWithNull = Collections.singletonList(null);
        assertThrows(NullPointerException.class, () -> verifier.verifySExtension(parent, listWithNull, b, index));
    }

    /**
     * 22. Empty parent pattern rejected according to ROOT POLICY.
     * Extension from an empty parent belongs to C09 generation layer.
     */
    @Test
    void test22_emptyParentPatternRejectedByRootPolicy() {
        Item a = item(1);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        assertThrows(IllegalArgumentException.class,
                () -> verifier.verifySExtension(Pattern.empty(), List.of(), a, index));
    }

    /**
     * 23. Input lists and arrays are not mutated by verifySExtension.
     */
    @Test
    void test23_inputListsAndArraysNotMutated() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        int[] callerEndpoints = new int[] { 1 };
        PatternEndpoints ep = PatternEndpoints.of(1, callerEndpoints);
        List<PatternEndpoints> mutableList = new ArrayList<>(List.of(ep));

        VerificationResult result = verifier.verifySExtension(parent, mutableList, b, index);

        // Mutable list size and content unchanged
        assertEquals(1, mutableList.size());
        assertEquals(ep, mutableList.get(0));

        // Mutating callerEndpoints does not affect ep or result
        callerEndpoints[0] = 999;
        assertArrayEquals(new int[] { 1 }, ep.endpoints());
        assertEquals(1, result.exactSupport());
    }

    /**
     * 24. Deterministic actual SID order: output strictly preserves input
     * parent-state order.
     */
    @Test
    void test24_deterministicSidOrderPreserved() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(10, seq(itemset(a), itemset(b))),
                rec(20, seq(itemset(a), itemset(b))),
                rec(30, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        // Supply parent states in non-ascending order: 30, 10, 20
        List<PatternEndpoints> arbitraryOrder = List.of(
                PatternEndpoints.of(30, new int[] { 1 }),
                PatternEndpoints.of(10, new int[] { 1 }),
                PatternEndpoints.of(20, new int[] { 1 }));

        VerificationResult result = verifier.verifySExtension(parent, arbitraryOrder, b, index);

        assertEquals(3, result.exactSupport());
        assertArrayEquals(new int[] { 30, 10, 20 }, result.supportingSids());
    }

    /**
     * 25. VerificationResult post-construction immutability.
     */
    @Test
    void test25_verificationResultImmutability() {
        Item a = item(1);
        Item b = item(2);
        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b)))));
        VerticalOccurrenceIndex index = buildIndex(db);

        Pattern parent = Pattern.of(itemset(a));
        VerificationResult result = verifier.verifySExtension(
                parent, List.of(PatternEndpoints.of(1, new int[] { 1 })), b, index);

        // endpointStates() must be unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> result.endpointStates().add(PatternEndpoints.of(2, new int[] { 2 })));

        // Mutating returned supportingSids array must not alter internal state
        int[] sids = result.supportingSids();
        sids[0] = 999;
        assertArrayEquals(new int[] { 1 }, result.supportingSids());
    }

    /**
     * 26. Differential verification against direct-scan oracle on a non-trivial
     * database
     * with repeated items, multiple embeddings, mixed orderings, and gaps.
     */
    @Test
    void test26_directScanOracleComparisonOnRepeatedItemsAndMultipleEmbeddings() {
        Item a = item(1);
        Item b = item(2);
        Item c = item(3);
        Item d = item(4);

        SequenceDatabase db = new SequenceDatabase(List.of(
                rec(1, seq(itemset(a), itemset(b), itemset(a), itemset(b))), // repeated a, b
                rec(2, seq(itemset(b), itemset(a), itemset(c))), // reverse a then b
                rec(3, seq(itemset(a, b), itemset(b), itemset(c))), // same-itemset a,b then b, c
                rec(4, seq(itemset(c), itemset(d))), // disjoint items
                rec(5, seq(itemset(a), itemset(a), itemset(a), itemset(b), itemset(b))) // dense repetitions
        ));
        VerticalOccurrenceIndex index = buildIndex(db);

        // Test multiple S-extensions from various parent patterns
        // Case A: <(a)> ⊕S b = <(a)(b)>
        Pattern parentA = Pattern.of(itemset(a));
        Pattern childA = SHARED_EXTENSIONS.sExtend(parentA, b);
        List<PatternEndpoints> parentStatesA = oracleExpectedResult(db, parentA).endpointStates();
        VerificationResult resultA = verifier.verifySExtension(parentA, parentStatesA, b, index);
        VerificationResult expectedA = oracleExpectedResult(db, childA);

        assertEquals(expectedA.exactSupport(), resultA.exactSupport());
        assertArrayEquals(expectedA.supportingSids(), resultA.supportingSids());
        assertEquals(expectedA.endpointStates(), resultA.endpointStates());
        assertEquals(expectedA, resultA);

        // Case B: <(a)> ⊕S a = <(a)(a)> (repeated item)
        Pattern childB = SHARED_EXTENSIONS.sExtend(parentA, a);
        VerificationResult resultB = verifier.verifySExtension(parentA, parentStatesA, a, index);
        VerificationResult expectedB = oracleExpectedResult(db, childB);

        assertEquals(expectedB.exactSupport(), resultB.exactSupport());
        assertArrayEquals(expectedB.supportingSids(), resultB.supportingSids());
        assertEquals(expectedB.endpointStates(), resultB.endpointStates());
        assertEquals(expectedB, resultB);

        // Case C: <(a)(b)> ⊕S c = <(a)(b)(c)>
        Pattern parentC = childA;
        Pattern childC = SHARED_EXTENSIONS.sExtend(parentC, c);
        List<PatternEndpoints> parentStatesC = resultA.endpointStates();
        VerificationResult resultC = verifier.verifySExtension(parentC, parentStatesC, c, index);
        VerificationResult expectedC = oracleExpectedResult(db, childC);

        assertEquals(expectedC.exactSupport(), resultC.exactSupport());
        assertArrayEquals(expectedC.supportingSids(), resultC.supportingSids());
        assertEquals(expectedC.endpointStates(), resultC.endpointStates());
        assertEquals(expectedC, resultC);

        // Case D: <(b)> ⊕S a = <(b)(a)> (extension item id 1 smaller than parent id 2)
        Pattern parentD = Pattern.of(itemset(b));
        Pattern childD = SHARED_EXTENSIONS.sExtend(parentD, a);
        List<PatternEndpoints> parentStatesD = oracleExpectedResult(db, parentD).endpointStates();
        VerificationResult resultD = verifier.verifySExtension(parentD, parentStatesD, a, index);
        VerificationResult expectedD = oracleExpectedResult(db, childD);

        assertEquals(expectedD.exactSupport(), resultD.exactSupport());
        assertArrayEquals(expectedD.supportingSids(), resultD.supportingSids());
        assertEquals(expectedD.endpointStates(), resultD.endpointStates());
        assertEquals(expectedD, resultD);
    }
}
