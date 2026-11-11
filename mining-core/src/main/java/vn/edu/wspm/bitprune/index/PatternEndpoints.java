package vn.edu.wspm.bitprune.index;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable All-Endpoints value object representing the complete set of feasible ending
 * positions for a pattern in a single actual SID.
 *
 * <p>A {@code PatternEndpoints} instance encodes:
 *
 * <pre>
 *   (actualSid, E(P, s))
 * </pre>
 *
 * where {@code E(P, s)} is the set of all feasible ending positions of pattern {@code P} in
 * sequence {@code s} identified by {@code actualSid}. The endpoints are stored as a sorted,
 * distinct, 1-based, strictly increasing {@code int[]} array.
 *
 * <p>This class is a <em>pure value object</em>: it validates and stores the state it is given.
 * It does not compute {@code E(P, s)} from a database, verify embeddings, or perform temporal
 * joins. Those responsibilities belong to temporal verifiers in later tasks (C07/C08).
 *
 * <hr>
 *
 * <p><strong>SOURCE-CONFIRMED:</strong>
 *
 * <ul>
 *   <li><em>Full All-Endpoints semantics:</em> OBL-07 (C01.2, §11) proves that preserving the
 *       complete set of feasible endpoints {@code E(P, s)} is necessary and sufficient for correct
 *       temporal extension. Missing any endpoint can cause false negatives in I- or S-extension.
 *   <li><em>Earliest-only is not safe:</em> Counterexample CE-01 (C01.2, §11/CE-01) demonstrates
 *       that keeping only the earliest endpoint causes false negatives for I-extension.
 *   <li><em>Latest-only is not safe:</em> Counterexample CE-02 (C01.2, §11/CE-02) demonstrates
 *       that keeping only the latest endpoint causes false negatives for S-extension.
 *   <li><em>Endpoint positions are 1-based:</em> Consistent with the sequence-position convention
 *       established in C01.1 and implemented by {@link VerticalOccurrenceIndex}.
 *   <li><em>Endpoints must be distinct and strictly increasing:</em> The set {@code E(P, s)} is
 *       mathematically a set of positions; duplicates are structurally invalid.
 * </ul>
 *
 * <p><strong>C02-ACCEPTED:</strong>
 *
 * <ul>
 *   <li>{@code PatternEndpoints.of(int sid, int[] endpoints)} as the factory method (C02, §10.2,
 *       Table 8.1: {@code of(int sid, int[] endpoints)} — IllegalArgumentException if unsorted).
 *   <li>Non-empty endpoint state: an empty endpoint array means {@code E(P, s) = ∅}, which implies
 *       the sequence does not support the pattern. Such a state must not be stored (C02 §10.2).
 *   <li>Immutable DFS-frame ownership: instances of this class are owned by DFS stack frames and
 *       must not be mutated after creation (C02, §11: lifecycle contract).
 * </ul>
 *
 * <p><strong>IMPLEMENTATION POLICY / REASONABLE INFERENCE:</strong>
 *
 * <ul>
 *   <li>{@code actualSid()} returns the raw {@code int} actual SID. Any {@code int} value is a
 *       valid SID identity (negative, zero, positive, noncontiguous). This class does not hold a
 *       {@link vn.edu.wspm.bitprune.bitmap.SidMapping} and therefore cannot validate SID
 *       membership; that validation is the caller's responsibility at construction time.
 *   <li>{@code endpoints()} returns a defensive copy to prevent external mutation of the stored
 *       state.
 *   <li>The factory defensively copies the input array before validation and storage to prevent
 *       post-construction mutation of the stored state through the caller's original reference.
 *   <li>Exception types: {@link NullPointerException} for {@code null} endpoints,
 *       {@link IllegalArgumentException} for structural violations (empty, non-positive, duplicate,
 *       unsorted). These specific types are inferred from the domain convention established in
 *       {@link VerticalOccurrenceIndex} and the C02 failure-policy notes.
 *   <li>{@code equals}/{@code hashCode} use value semantics over {@code (actualSid, endpoints[])}.
 *       Two instances with the same SID and the same endpoint contents compare equal regardless of
 *       object identity. This is required for deterministic test comparisons.
 * </ul>
 *
 * <p><strong>UNSPECIFIED / UNRESOLVED:</strong>
 *
 * <ul>
 *   <li>Whether an endpoint is feasible relative to the database (i.e., does not exceed the
 *       sequence size for {@code actualSid}). This class does not receive a database or sequence,
 *       so feasibility relative to sequence length is intentionally not validated here.
 *   <li>The upper bound of valid endpoint values relative to sequence size. This class accepts any
 *       positive endpoint, including {@code Integer.MAX_VALUE}, as structurally valid.
 *   <li>Memory-compression strategies for large All-Endpoints sets (noted as UNRESOLVED / DEFERRED
 *       in C01.2 §14 and C02 §11).
 * </ul>
 */
public final class PatternEndpoints {

    /** The actual sequence identifier (may be negative, zero, or positive). */
    private final int actualSid;

    /**
     * The stored endpoint array: sorted, distinct, strictly increasing, all positive (>= 1).
     * This array is never exposed directly; {@link #endpoints()} returns a defensive copy.
     */
    private final int[] endpoints;

    private PatternEndpoints(int actualSid, int[] endpoints) {
        this.actualSid = actualSid;
        this.endpoints = endpoints;
    }

    /**
     * Creates an immutable {@link PatternEndpoints} value object.
     *
     * <p>The supplied {@code endpoints} array is defensively copied immediately. The caller's
     * original array may be freely mutated after this call without affecting the stored state.
     *
     * <p>Validation rules (applied to the copied array):
     *
     * <ul>
     *   <li>{@code endpoints} must not be {@code null}.
     *   <li>{@code endpoints} must not be empty ({@code length == 0}).
     *   <li>Every element must be strictly positive ({@code > 0}); endpoint 0 or negative is
     *       structurally invalid because positions are 1-based.
     *   <li>Elements must be strictly increasing (no duplicates, no descending pairs).
     * </ul>
     *
     * <p>Invalid input is never silently repaired (no sorting, no deduplication).
     *
     * @param actualSid the actual sequence identifier (any {@code int} is accepted)
     * @param endpoints the array of feasible endpoints (must not be null, empty, contain
     *                  non-positive values, duplicates, or unsorted values)
     * @return an immutable {@link PatternEndpoints} instance
     * @throws NullPointerException     if {@code endpoints} is null
     * @throws IllegalArgumentException if {@code endpoints} is empty, contains a non-positive
     *                                  value, a duplicate, or is not strictly increasing
     */
    public static PatternEndpoints of(int actualSid, int[] endpoints) {
        Objects.requireNonNull(endpoints, "endpoints must not be null");

        // Defensive copy before any validation so the stored array is fully isolated
        int[] copy = endpoints.clone();

        if (copy.length == 0) {
            throw new IllegalArgumentException(
                    "endpoints must not be empty — an empty endpoint set means the sequence does not"
                            + " support the pattern and must not be stored as PatternEndpoints");
        }

        // Validate each element and the strictly-increasing invariant in a single pass
        if (copy[0] <= 0) {
            throw new IllegalArgumentException(
                    "endpoint at index 0 must be positive (>= 1), but was " + copy[0]
                            + " — positions are 1-based");
        }
        for (int i = 1; i < copy.length; i++) {
            if (copy[i] <= 0) {
                throw new IllegalArgumentException(
                        "endpoint at index " + i + " must be positive (>= 1), but was " + copy[i]
                                + " — positions are 1-based");
            }
            if (copy[i] <= copy[i - 1]) {
                if (copy[i] == copy[i - 1]) {
                    throw new IllegalArgumentException(
                            "duplicate endpoint at index " + i + ": value " + copy[i]
                                    + " — endpoints must be strictly increasing");
                } else {
                    throw new IllegalArgumentException(
                            "unsorted endpoints at index " + i + ": " + copy[i - 1] + " followed by "
                                    + copy[i] + " — endpoints must be strictly increasing");
                }
            }
        }

        return new PatternEndpoints(actualSid, copy);
    }

    /**
     * Returns the actual sequence identifier associated with this endpoint state.
     *
     * <p><strong>IMPLEMENTATION POLICY:</strong> This accessor returns the raw {@code int} actual
     * SID exactly as supplied to {@link #of}. It does not remap through a
     * {@link vn.edu.wspm.bitprune.bitmap.SidMapping} and does not validate SID membership in any
     * database.
     *
     * @return the actual SID
     */
    public int actualSid() {
        return actualSid;
    }

    /**
     * Returns a defensive copy of the sorted, strictly increasing, 1-based endpoint array.
     *
     * <p>Mutating the returned array does not affect the state of this instance.
     *
     * @return a defensive copy of the endpoint array; never null; always non-empty; always
     *         strictly increasing; all elements &gt;= 1
     */
    public int[] endpoints() {
        return endpoints.clone();
    }

    /**
     * Value equality: two instances are equal if and only if they have the same actual SID and
     * the same endpoint array contents.
     *
     * <p><strong>IMPLEMENTATION POLICY:</strong> Array identity is not used; contents are
     * compared element-by-element via {@link Arrays#equals}.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PatternEndpoints that)) {
            return false;
        }
        return this.actualSid == that.actualSid && Arrays.equals(this.endpoints, that.endpoints);
    }

    /** Hash code consistent with {@link #equals}: based on {@code actualSid} and endpoint contents. */
    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(actualSid) + Arrays.hashCode(endpoints);
    }

    /**
     * Returns a deterministic string representation for debugging.
     *
     * <p>Output format: {@code PatternEndpoints[sid=<sid>, endpoints=<[e1, e2, ...]>]}.
     * The format is stable and does not expose a mutable reference.
     */
    @Override
    public String toString() {
        return "PatternEndpoints[sid=" + actualSid + ", endpoints=" + Arrays.toString(endpoints) + "]";
    }
}

