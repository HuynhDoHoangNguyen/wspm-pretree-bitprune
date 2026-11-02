package vn.edu.wspm.bitprune.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Correctness tests for {@link PatternEndpoints}.
 *
 * <p>This test class is an <em>independent correctness oracle</em>: it constructs expected endpoint
 * arrays directly from the test specification and compares them element-by-element against
 * production output. It does not use any {@link PatternEndpoints} helper logic to derive expected
 * values.
 *
 * <p>The test matrix covers all 25 required cases from the C04.2 contract:
 *
 * <ol>
 *   <li>Valid single endpoint.
 *   <li>Valid multiple endpoints.
 *   <li>Negative actual SID.
 *   <li>Zero actual SID.
 *   <li>Positive/noncontiguous actual SID.
 *   <li>Null endpoints.
 *   <li>Empty endpoints.
 *   <li>Endpoint equal to 0.
 *   <li>Negative endpoint.
 *   <li>Unsorted endpoints.
 *   <li>Adjacent duplicate.
 *   <li>Duplicate in longer array.
 *   <li>Input mutation after construction.
 *   <li>Output mutation after retrieval.
 *   <li>Exact preservation of all supplied endpoints.
 *   <li>CE-01-style state {1,2} preserved fully.
 *   <li>CE-02-style state {1,3} preserved fully.
 *   <li>Same-value instances are equal.
 *   <li>Same-value instances have equal hashCode.
 *   <li>Different SID is not equal.
 *   <li>Different endpoints are not equal.
 *   <li>Integer.MAX_VALUE endpoint accepted.
 *   <li>Deterministic endpoint iteration.
 *   <li>No earliest-only/latest-only compression.
 *   <li>No temporal join or database feasibility logic (structural check).
 * </ol>
 */
class PatternEndpointsTest {

    // =========================================================================
    // Test 1: Valid single endpoint
    // =========================================================================

    @Test
    void validSingleEndpoint() {
        var pe = PatternEndpoints.of(5, new int[]{3});
        assertEquals(5, pe.actualSid());
        assertArrayEquals(new int[]{3}, pe.endpoints());
    }

    // =========================================================================
    // Test 2: Valid multiple endpoints
    // =========================================================================

    @Test
    void validMultipleEndpoints() {
        var pe = PatternEndpoints.of(10, new int[]{1, 3, 7, 12});
        assertEquals(10, pe.actualSid());
        assertArrayEquals(new int[]{1, 3, 7, 12}, pe.endpoints());
    }

    // =========================================================================
    // Test 3: Negative actual SID
    // =========================================================================

    @Test
    void negativeActualSid() {
        var pe = PatternEndpoints.of(-99, new int[]{2});
        assertEquals(-99, pe.actualSid());
        assertArrayEquals(new int[]{2}, pe.endpoints());
    }

    // =========================================================================
    // Test 4: Zero actual SID
    // =========================================================================

    @Test
    void zeroActualSid() {
        var pe = PatternEndpoints.of(0, new int[]{1});
        assertEquals(0, pe.actualSid());
        assertArrayEquals(new int[]{1}, pe.endpoints());
    }

    // =========================================================================
    // Test 5: Positive and noncontiguous actual SID
    // =========================================================================

    @Test
    void positiveAndNoncontiguousActualSid() {
        // Noncontiguous SIDs are valid — SID identity is arbitrary int
        var pe1 = PatternEndpoints.of(1, new int[]{5});
        var pe2 = PatternEndpoints.of(1000, new int[]{5});
        var pe3 = PatternEndpoints.of(Integer.MAX_VALUE, new int[]{5});

        assertEquals(1, pe1.actualSid());
        assertEquals(1000, pe2.actualSid());
        assertEquals(Integer.MAX_VALUE, pe3.actualSid());
    }

    // =========================================================================
    // Test 6: Null endpoints
    // =========================================================================

    @Test
    void rejectsNullEndpoints() {
        assertThrows(NullPointerException.class, () -> PatternEndpoints.of(1, null));
    }

    // =========================================================================
    // Test 7: Empty endpoints
    // =========================================================================

    @Test
    void rejectsEmptyEndpoints() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{}));
        assertTrue(ex.getMessage().toLowerCase().contains("empty"),
                "Exception message should mention 'empty': " + ex.getMessage());
    }

    // =========================================================================
    // Test 8: Endpoint equal to zero
    // =========================================================================

    @Test
    void rejectsEndpointEqualToZero() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{0}));
        assertTrue(ex.getMessage().contains("0") || ex.getMessage().toLowerCase().contains("positive"),
                "Exception message should reference the invalid value: " + ex.getMessage());
    }

    @Test
    void rejectsZeroEndpointInMultiElementArray() {
        // Zero at index 1 (first element is valid)
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{1, 0, 3}));
    }

    // =========================================================================
    // Test 9: Negative endpoint
    // =========================================================================

    @Test
    void rejectsNegativeEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{-1}));
    }

    @Test
    void rejectsNegativeEndpointAtFirstPosition() {
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{-5, 1, 2}));
    }

    @Test
    void rejectsNegativeEndpointInMiddle() {
        // First element positive, then negative
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{2, -3}));
    }

    // =========================================================================
    // Test 10: Unsorted endpoints
    // =========================================================================

    @Test
    void rejectsUnsortedEndpoints() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{3, 1, 5}));
        // Message must reference the violation, not silently fix it
        assertTrue(ex.getMessage() != null && !ex.getMessage().isEmpty(),
                "Exception must have a descriptive message");
    }

    @Test
    void rejectsDescendingEndpoints() {
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{5, 4, 3}));
    }

    // =========================================================================
    // Test 11: Adjacent duplicate
    // =========================================================================

    @Test
    void rejectsAdjacentDuplicate() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{2, 2, 4}));
        assertTrue(ex.getMessage().toLowerCase().contains("duplicate")
                        || ex.getMessage().contains("2"),
                "Exception message should reference the duplicate: " + ex.getMessage());
    }

    // =========================================================================
    // Test 12: Duplicate in longer array (non-adjacent)
    // =========================================================================

    @Test
    void rejectsDuplicateInLongerArray() {
        // [1, 3, 5, 3, 7] — 3 repeated; also unsorted after 5
        assertThrows(IllegalArgumentException.class,
                () -> PatternEndpoints.of(1, new int[]{1, 3, 5, 3, 7}));
    }

    // =========================================================================
    // Test 13: Input mutation after construction does not affect stored state
    // =========================================================================

    @Test
    void inputMutationAfterConstructionDoesNotAffectStoredState() {
        int[] input = {1, 2, 5};
        var pe = PatternEndpoints.of(42, input);

        // Mutate the original input array
        input[0] = 999;
        input[1] = 888;
        input[2] = 777;

        // Stored endpoints must be the original values
        assertArrayEquals(new int[]{1, 2, 5}, pe.endpoints(),
                "Input mutation after construction must not alter stored state");
    }

    // =========================================================================
    // Test 14: Output mutation after retrieval does not affect stored state
    // =========================================================================

    @Test
    void outputMutationAfterRetrievalDoesNotAffectStoredState() {
        var pe = PatternEndpoints.of(7, new int[]{3, 6, 9});

        // Get the endpoints and mutate them
        int[] result = pe.endpoints();
        result[0] = 999;
        result[1] = 888;
        result[2] = 777;

        // A second retrieval must return the original values
        assertArrayEquals(new int[]{3, 6, 9}, pe.endpoints(),
                "Output mutation must not affect stored state");
    }

    // =========================================================================
    // Test 15: Exact preservation of all supplied endpoints
    // =========================================================================

    @Test
    void exactPreservationOfAllSuppliedEndpoints() {
        int[] supplied = {1, 2, 4, 7, 11, 16, 22};
        var pe = PatternEndpoints.of(55, supplied);

        int[] retrieved = pe.endpoints();
        assertArrayEquals(supplied, retrieved,
                "Every supplied endpoint must be preserved exactly");
        assertEquals(supplied.length, retrieved.length,
                "No endpoint may be added or removed");
    }

    // =========================================================================
    // Test 16: CE-01-style state {1, 2} fully preserved
    //
    // CE-01 (C01.2 §11): For I-extension to succeed, endpoint 2 must be
    // present. Earliest-only (storing only endpoint 1) would miss it.
    // =========================================================================

    @Test
    void ce01StyleStateWithEndpoints1And2IsFullyPreserved() {
        // Pattern P, SID s1: embeddings end at positions 1 and 2
        var pe = PatternEndpoints.of(1, new int[]{1, 2});

        assertArrayEquals(new int[]{1, 2}, pe.endpoints(),
                "CE-01: Both endpoints {1, 2} must be preserved; earliest-only would lose endpoint 2");

        // Confirm neither endpoint is suppressed
        int[] eps = pe.endpoints();
        assertEquals(2, eps.length, "CE-01: Must have exactly 2 endpoints, not 1 (earliest-only)");
        assertEquals(1, eps[0]);
        assertEquals(2, eps[1]);
    }

    // =========================================================================
    // Test 17: CE-02-style state {1, 3} fully preserved
    //
    // CE-02 (C01.2 §11): For S-extension to succeed, endpoint 1 must be
    // present. Latest-only (storing only endpoint 3) would miss it.
    // =========================================================================

    @Test
    void ce02StyleStateWithEndpoints1And3IsFullyPreserved() {
        // Pattern P, SID s1: embeddings end at positions 1 and 3
        var pe = PatternEndpoints.of(1, new int[]{1, 3});

        assertArrayEquals(new int[]{1, 3}, pe.endpoints(),
                "CE-02: Both endpoints {1, 3} must be preserved; latest-only would lose endpoint 1");

        int[] eps = pe.endpoints();
        assertEquals(2, eps.length, "CE-02: Must have exactly 2 endpoints, not 1 (latest-only)");
        assertEquals(1, eps[0]);
        assertEquals(3, eps[1]);
    }

    // =========================================================================
    // Test 18: Same-value instances are equal
    // =========================================================================

    @Test
    void sameValueInstancesAreEqual() {
        var pe1 = PatternEndpoints.of(10, new int[]{2, 4, 6});
        var pe2 = PatternEndpoints.of(10, new int[]{2, 4, 6});

        assertEquals(pe1, pe2, "Two instances with same SID and endpoints must be equal");
    }

    // =========================================================================
    // Test 19: Same-value instances have equal hashCode
    // =========================================================================

    @Test
    void sameValueInstancesHaveEqualHashCode() {
        var pe1 = PatternEndpoints.of(10, new int[]{2, 4, 6});
        var pe2 = PatternEndpoints.of(10, new int[]{2, 4, 6});

        assertEquals(pe1.hashCode(), pe2.hashCode(),
                "Equal instances must have equal hashCode");
    }

    // =========================================================================
    // Test 20: Different SID is not equal
    // =========================================================================

    @Test
    void differentSidIsNotEqual() {
        var pe1 = PatternEndpoints.of(1, new int[]{5});
        var pe2 = PatternEndpoints.of(2, new int[]{5});

        assertNotEquals(pe1, pe2, "Instances with different SID must not be equal");
    }

    // =========================================================================
    // Test 21: Different endpoints are not equal
    // =========================================================================

    @Test
    void differentEndpointsAreNotEqual() {
        var pe1 = PatternEndpoints.of(1, new int[]{1, 3});
        var pe2 = PatternEndpoints.of(1, new int[]{1, 4});

        assertNotEquals(pe1, pe2, "Instances with different endpoint contents must not be equal");
    }

    // =========================================================================
    // Test 22: Integer.MAX_VALUE endpoint is accepted as structurally valid
    // =========================================================================

    @Test
    void integerMaxValueEndpointIsAccepted() {
        // Integer.MAX_VALUE is a valid positive int; no sequence-size check is performed
        var pe = PatternEndpoints.of(1, new int[]{Integer.MAX_VALUE});
        assertArrayEquals(new int[]{Integer.MAX_VALUE}, pe.endpoints());
    }

    @Test
    void integerMaxValueEndpointInArrayIsAccepted() {
        var pe = PatternEndpoints.of(1, new int[]{1, 3, Integer.MAX_VALUE});
        assertArrayEquals(new int[]{1, 3, Integer.MAX_VALUE}, pe.endpoints());
    }

    // =========================================================================
    // Test 23: Deterministic endpoint iteration
    // =========================================================================

    @Test
    void endpointIterationIsDeterministic() {
        var pe = PatternEndpoints.of(5, new int[]{2, 5, 8, 13});

        // Multiple retrievals must return identical content in the same order
        int[] first = pe.endpoints();
        int[] second = pe.endpoints();
        int[] third = pe.endpoints();

        assertArrayEquals(first, second, "Repeated endpoint() calls must return identical content");
        assertArrayEquals(second, third, "Repeated endpoint() calls must return identical content");
        assertArrayEquals(new int[]{2, 5, 8, 13}, first);
    }

    // =========================================================================
    // Test 24: No earliest-only / latest-only compression occurs
    // =========================================================================

    @Test
    void noEarliestOnlyCompression() {
        // If earliest-only compression were applied, only endpoint 1 would be stored
        var pe = PatternEndpoints.of(1, new int[]{1, 2, 3, 4, 5});
        int[] eps = pe.endpoints();

        assertEquals(5, eps.length,
                "All 5 endpoints must be preserved; earliest-only would produce length=1");
        assertArrayEquals(new int[]{1, 2, 3, 4, 5}, eps);
    }

    @Test
    void noLatestOnlyCompression() {
        // If latest-only compression were applied, only endpoint 5 would be stored
        var pe = PatternEndpoints.of(1, new int[]{1, 2, 3, 4, 5});
        int[] eps = pe.endpoints();

        assertEquals(5, eps.length,
                "All 5 endpoints must be preserved; latest-only would produce length=1");
        assertTrue(eps[0] == 1, "Earliest endpoint must be preserved, not just the latest");
    }

    // =========================================================================
    // Test 25: No temporal join or database feasibility logic in the class
    //          (structural/API check — class must be a pure value object)
    // =========================================================================

    @Test
    void classIsAPureValueObjectWithNoTemporalJoinOrDatabaseLogic() {
        // This test verifies the structural contract by confirming:
        // - Construction succeeds without any database or SidMapping argument
        // - No exception is thrown even for a logically "impossible" endpoint (e.g., 9999
        //   when the sequence has only 3 itemsets), because feasibility is not checked here
        var pe = PatternEndpoints.of(42, new int[]{1, 9999}); // structurally valid
        assertArrayEquals(new int[]{1, 9999}, pe.endpoints(),
                "PatternEndpoints must not validate endpoint feasibility against any database — it is a pure value object");
    }

    // =========================================================================
    // Additional edge cases
    // =========================================================================

    @Test
    void equalsIsSymmetricAndTransitive() {
        var pe1 = PatternEndpoints.of(3, new int[]{1, 4, 9});
        var pe2 = PatternEndpoints.of(3, new int[]{1, 4, 9});
        var pe3 = PatternEndpoints.of(3, new int[]{1, 4, 9});

        // Symmetric
        assertEquals(pe1, pe2);
        assertEquals(pe2, pe1);
        // Transitive
        assertEquals(pe2, pe3);
        assertEquals(pe1, pe3);
    }

    @Test
    void equalsReturnsFalseForNullAndWrongType() {
        var pe = PatternEndpoints.of(1, new int[]{5});
        assertNotEquals(null, pe);
        assertNotEquals("not a PatternEndpoints", pe);
    }

    @Test
    void negativeSidEdgeCasesAllAccepted() {
        PatternEndpoints.of(Integer.MIN_VALUE, new int[]{1});
        PatternEndpoints.of(-1, new int[]{1});
        PatternEndpoints.of(-100, new int[]{2, 4});
        // No assertion needed — must not throw
    }

    @Test
    void singleElementEndpointAtPositionOneIsValid() {
        var pe = PatternEndpoints.of(1, new int[]{1});
        assertArrayEquals(new int[]{1}, pe.endpoints());
    }

    @Test
    void largeStrictlyIncreasingArrayIsAccepted() {
        int n = 100;
        int[] large = new int[n];
        for (int i = 0; i < n; i++) large[i] = i + 1; // 1..100
        var pe = PatternEndpoints.of(77, large);
        assertArrayEquals(large, pe.endpoints(),
                "Large strictly increasing endpoint array must be accepted and preserved");
    }

    @Test
    void toStringIsDeterministicAndContainsSidAndEndpoints() {
        var pe = PatternEndpoints.of(42, new int[]{1, 3, 5});
        String s1 = pe.toString();
        String s2 = pe.toString();
        assertEquals(s1, s2, "toString must be deterministic");
        assertTrue(s1.contains("42"), "toString should contain the SID");
        assertTrue(s1.contains("1") && s1.contains("3") && s1.contains("5"),
                "toString should contain the endpoint values");
    }
}

