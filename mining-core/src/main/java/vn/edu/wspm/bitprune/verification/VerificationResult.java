package vn.edu.wspm.bitprune.verification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.bitprune.index.PatternEndpoints;

/**
 * Immutable result of exact temporal verification for a pattern extension.
 *
 * <p>
 * A {@code VerificationResult} records the set of sequence endpoint states
 * ({@link PatternEndpoints}) that survived exact temporal join verification.
 * Each entry in {@link #endpointStates()} represents one sequence (by its
 * actual SID)
 * that contains at least one valid temporal embedding of the candidate pattern,
 * along with the complete set of feasible 1-based ending itemset positions
 * (All-Endpoints state, per OBL-07).
 *
 * <p>
 * <strong>SOURCE-CONFIRMED:</strong>
 * <ul>
 * <li><em>Distinct SID support:</em> Each sequence in which a pattern has at
 * least one valid
 * embedding contributes exactly 1 to the pattern's support (C01.1 §7.3).
 * Multiple embeddings
 * within the same sequence do not inflate support.
 * <li><em>All-Endpoints preservation:</em> OBL-07 (C01.2 §11) mandates
 * preserving all feasible
 * endpoints {@code E(P, s)} for downstream I- and S-extensions.
 * </ul>
 *
 * <p>
 * <strong>C01/C02-ACCEPTED:</strong>
 * <ul>
 * <li>Verification output carries only surviving endpoint states; sequences
 * where the pattern
 * has no feasible embedding (empty endpoint set) are not represented (C02
 * §10.2).
 * <li>The result object is immutable and owned by the DFS stack frame (C02
 * §11).
 * <li>Actual SIDs are preserved directly; no compact bit index translation is
 * performed.
 * </ul>
 *
 * <p>
 * <strong>IMPLEMENTATION POLICY / REASONABLE INFERENCE:</strong>
 * <ul>
 * <li>{@code of(List<PatternEndpoints>)} defensively copies the input list and
 * wraps it in an
 * unmodifiable view to guarantee post-construction immutability.
 * <li>Duplicate actual SIDs in the input list are rejected with
 * {@link IllegalArgumentException}
 * to prevent corrupted or double-counted support.
 * <li>The input ordering of endpoint states is strictly preserved (no
 * re-sorting).
 * <li>{@link #supportingSids()} returns a newly allocated {@code int[]} array
 * on each invocation
 * so caller mutations cannot alter internal state.
 * <li>{@link #exactSupport()} equals {@code endpointStates().size()}.
 * <li>Value equality ({@link #equals}/{@link #hashCode}) is defined over the
 * endpoint state list.
 * </ul>
 *
 * <p>
 * <strong>UNSPECIFIED / UNRESOLVED:</strong>
 * <ul>
 * <li>R6 (minsup = 0 enumeration semantics) is not evaluated here. An empty
 * verification result
 * represents only the exact temporal fact that zero sequences support the
 * candidate;
 * operational pruning decisions remain outside this class.
 * </ul>
 */
public final class VerificationResult {

    private static final VerificationResult EMPTY = new VerificationResult(List.of());

    private final List<PatternEndpoints> endpointStates;

    private VerificationResult(List<PatternEndpoints> endpointStates) {
        this.endpointStates = endpointStates;
    }

    /**
     * Creates an immutable {@link VerificationResult} from the given list of
     * endpoint states.
     *
     * <p>
     * The input list is defensively copied. Null elements and duplicate actual SIDs
     * are rejected.
     * Empty input is allowed and produces an empty result with
     * {@code exactSupport() == 0}.
     *
     * @param endpointStates the list of surviving endpoint states (must not be null
     *                       or contain null)
     * @return an immutable verification result
     * @throws NullPointerException     if {@code endpointStates} is null or
     *                                  contains a null element
     * @throws IllegalArgumentException if {@code endpointStates} contains duplicate
     *                                  actual SIDs
     */
    public static VerificationResult of(List<PatternEndpoints> endpointStates) {
        Objects.requireNonNull(endpointStates, "endpointStates must not be null");
        if (endpointStates.isEmpty()) {
            return EMPTY;
        }

        List<PatternEndpoints> copy = new ArrayList<>(endpointStates.size());
        HashSet<Integer> seenSids = new HashSet<>(endpointStates.size());
        for (PatternEndpoints ep : endpointStates) {
            Objects.requireNonNull(ep, "endpoint state element must not be null");
            if (!seenSids.add(ep.actualSid())) {
                throw new IllegalArgumentException(
                        "duplicate actual SID in endpointStates: " + ep.actualSid());
            }
            copy.add(ep);
        }

        return new VerificationResult(Collections.unmodifiableList(copy));
    }

    /**
     * Returns an immutable list of surviving sequence endpoint states in input
     * order.
     *
     * @return an immutable list of endpoint states; never null; may be empty
     */
    public List<PatternEndpoints> endpointStates() {
        return endpointStates;
    }

    /**
     * Returns an array of actual SIDs supporting this candidate, in input order.
     *
     * <p>
     * A defensive {@code int[]} array is allocated and returned on each invocation.
     *
     * @return a newly allocated array of actual SIDs; never null; empty if support
     *         is 0
     */
    public int[] supportingSids() {
        int size = endpointStates.size();
        int[] sids = new int[size];
        for (int i = 0; i < size; i++) {
            sids[i] = endpointStates.get(i).actualSid();
        }
        return sids;
    }

    /**
     * Returns the exact support count, defined as the number of distinct sequences
     * containing at least one feasible embedding of the pattern.
     *
     * @return exact support (>= 0)
     */
    public int exactSupport() {
        return endpointStates.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VerificationResult that)) {
            return false;
        }
        return this.endpointStates.equals(that.endpointStates);
    }

    @Override
    public int hashCode() {
        return endpointStates.hashCode();
    }

    @Override
    public String toString() {
        return "VerificationResult[exactSupport=" + exactSupport() + ", endpointStates=" + endpointStates + "]";
    }
}
