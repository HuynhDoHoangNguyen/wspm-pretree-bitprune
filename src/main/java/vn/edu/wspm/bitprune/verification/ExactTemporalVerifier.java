package vn.edu.wspm.bitprune.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;

/**
 * Exact temporal verifier for pattern extensions in WSPM_BitPrune.
 *
 * <p>
 * Under tasks C07.1 and C08.1, this verifier provides the exact temporal join core for
 * <strong>I-Extension</strong> and <strong>S-Extension</strong> operations.
 *
 * <h2>I-Extension Temporal Semantics</h2>
 * <p>
 * Given a non-empty parent pattern:
 * 
 * <pre>
 *   P = &lt;p_1, ..., p_k&gt;, k &gt;= 1
 * </pre>
 * 
 * and an extension item {@code x} satisfying the canonical ordering constraint:
 * 
 * <pre>
 * x &gt; lastItem(p_k)
 * </pre>
 * 
 * the itemset extension creates child pattern:
 * 
 * <pre>
 *   P' = &lt;p_1, ..., p_k ∪ {x}&gt;
 * </pre>
 *
 * <p>
 * For a sequence {@code s} with actual SID and parent feasible endpoint set
 * {@code E(P, s)},
 * an itemset position {@code e ∈ E(P, s)} is a feasible endpoint for {@code P'}
 * if and only if
 * {@code x} appears at the <em>exact same 1-based itemset position</em>
 * {@code e} in {@code s}:
 * 
 * <pre>
 *   E(P', s) = { e ∈ E(P, s) | x appears at position e in sequence s }
 *            = E(P, s) ∩ Index(x, s)
 * </pre>
 *
 * <h2>S-Extension Temporal Semantics</h2>
 * <p>
 * Given a non-empty parent pattern:
 *
 * <pre>
 *   P = &lt;p_1, ..., p_k&gt;, k &gt;= 1
 * </pre>
 *
 * and an extension item {@code x} (repetition of existing items is legal;
 * canonical ordering does not apply across itemsets), the sequence extension
 * creates child pattern:
 *
 * <pre>
 *   P' = P ⊕S x = &lt;p_1, ..., p_k, {x}&gt;
 * </pre>
 *
 * <p>
 * For a sequence {@code s} with actual SID and parent feasible endpoint set
 * {@code E(P, s)}, an itemset position {@code p ∈ Index(x, s)} is a feasible
 * endpoint for {@code P'} if and only if there exists at least one parent
 * endpoint {@code e ∈ E(P, s)} strictly preceding {@code p}:
 *
 * <pre>
 *   E(P ⊕S x, s) = { p ∈ Index(x, s) | ∃ e ∈ E(P, s) : e &lt; p }
 * </pre>
 *
 * Same-itemset occurrences ({@code p == e}) and reverse-order occurrences
 * ({@code p &lt; e}) are strictly rejected.
 *
 * <p>
 * <strong>SOURCE-CONFIRMED:</strong>
 * <ul>
 * <li><em>I-extension structure:</em> item {@code x} is appended to the final
 * itemset {@code p_k}
 * under strict natural/lexicographic item ordering (C01.1 §5.2).
 * <li><em>Same-itemset endpoint matching:</em> the temporal position of
 * {@code x} must be
 * identical to the ending position {@code e} of parent pattern {@code P}.
 * Occurrences of
 * {@code x} before or after {@code e} do not satisfy I-extension.
 * <li><em>S-extension structure and strict temporal order:</em> item {@code x}
 * is appended as a new singleton itemset; occurrences must be strictly later
 * than at least one parent endpoint ({@code e < p}).
 * <li><em>Repeated items in S-extension:</em> repetition across itemset
 * boundaries is structurally and semantically legal.
 * <li><em>Distinct SID support:</em> each sequence contributing at least one
 * matching endpoint
 * contributes exactly 1 to the candidate's exact support count (C01.1 §7.3).
 * Multiple
 * embeddings within the same sequence do not inflate support.
 * </ul>
 *
 * <p>
 * <strong>C01/C02-ACCEPTED:</strong>
 * <ul>
 * <li><em>All-Endpoints (OBL-07):</em> every feasible endpoint must be tracked.
 * Pruning or
 * compressing endpoints via earliest-only (refuted by CE-01) or latest-only
 * (refuted by CE-02)
 * is strictly prohibited.
 * <li><em>Index and endpoint encapsulation:</em> temporal positions are
 * retrieved exclusively
 * from {@link VerticalOccurrenceIndex}; endpoint states are received and
 * emitted as
 * immutable {@link PatternEndpoints} instances (C02 §8, §10).
 * </ul>
 *
 * <p>
 * <strong>IMPLEMENTATION POLICY / REASONABLE INFERENCE:</strong>
 * <ul>
 * <li>Two-pointer intersection on sorted arrays achieves optimal
 * {@code O(|E(P, s)| + |Occurrences(x, s)|)} complexity per SID without heap
 * allocation
 * or set conversions.
 * <li>Strictly-later S-extension join runs in linear time
 * {@code O(|Occurrences(x, s)|)} by identifying the suffix of positions strictly
 * greater than {@code min(E(P, s))} while fully preserving all child endpoints.
 * <li>Canonical I-extension constraints are validated by delegating to
 * {@link PatternExtensions#iExtend(Pattern, Item)}.
 * <li>Structural S-extension constraints are validated by delegating to
 * {@link PatternExtensions#sExtend(Pattern, Item)}.
 * <li>Fail-fast empty parent policy: temporal extension requires non-empty parent
 * endpoint states; singleton generation from empty root belongs to C09.
 * <li>Input sequence ordering from {@code parentEndpointStates} is strictly
 * preserved.
 * <li>Sequences where the join is empty are omitted from the resulting
 * {@link VerificationResult}; empty {@link PatternEndpoints} are never
 * constructed.
 * <li>Duplicate actual SIDs in {@code parentEndpointStates} are rejected with
 * {@link IllegalArgumentException}.
 * <li>Unknown actual SIDs (not registered in {@code occurrenceIndex}) are
 * rejected with
 * {@link IllegalArgumentException} via
 * {@link VerticalOccurrenceIndex#positions(Item, int)}.
 * <li>This class is completely stateless and thread-safe.
 * </ul>
 *
 * <p>
 * <strong>UNSPECIFIED / UNRESOLVED:</strong>
 * <ul>
 * <li>C08.2 exhaustive and randomized differential testing is deferred to the
 * next execution unit.
 * <li>C09 miner and candidate generation layer are deferred.
 * <li>R6 (minsup = 0 enumeration semantics) remains open at the research level.
 * This verifier
 * computes exact support and endpoints only; operational candidate pruning
 * remains the
 * responsibility of the caller.
 * </ul>
 */
public final class ExactTemporalVerifier {

    private static final int[] EMPTY_POSITIONS = new int[0];
    private static final PatternExtensions PATTERN_EXTENSIONS = new PatternExtensions();

    public ExactTemporalVerifier() {
    }

    /**
     * Verifies an I-Extension of {@code parent} with {@code extensionItem} using
     * exact temporal
     * endpoint intersection.
     *
     * <p>
     * Canonical extension preconditions (non-empty parent, strictly greater
     * extension item,
     * no duplicate in final itemset) are validated fail-fast. For each parent
     * endpoint state,
     * the feasible child endpoints are computed via two-pointer intersection with
     * the extension
     * item's occurrence positions in that sequence.
     *
     * @param parent               the parent sequential pattern (must not be null
     *                             or empty)
     * @param parentEndpointStates the list of parent sequence endpoint states (must
     *                             not be null
     *                             or contain null; duplicate actual SIDs rejected)
     * @param extensionItem        the item to append to the final itemset (must not
     *                             be null)
     * @param occurrenceIndex      the vertical occurrence index (must not be null)
     * @return an immutable {@link VerificationResult} containing surviving endpoint
     *         states
     * @throws NullPointerException     if any parameter is null or
     *                                  {@code parentEndpointStates}
     *                                  contains a null element
     * @throws IllegalArgumentException if {@code parent} is empty,
     *                                  {@code extensionItem} violates
     *                                  canonical ordering,
     *                                  {@code parentEndpointStates} contains
     *                                  duplicate actual SIDs, or an actual SID is
     *                                  unknown to
     *                                  {@code occurrenceIndex}
     */
    public VerificationResult verifyIExtension(
            Pattern parent,
            List<PatternEndpoints> parentEndpointStates,
            Item extensionItem,
            VerticalOccurrenceIndex occurrenceIndex) {

        Objects.requireNonNull(parent, "parent must not be null");
        Objects.requireNonNull(parentEndpointStates, "parentEndpointStates must not be null");
        Objects.requireNonNull(extensionItem, "extensionItem must not be null");
        Objects.requireNonNull(occurrenceIndex, "occurrenceIndex must not be null");

        // Canonical validation: delegates to shared PatternExtensions policy
        PATTERN_EXTENSIONS.iExtend(parent, extensionItem);

        if (parentEndpointStates.isEmpty()) {
            return VerificationResult.of(List.of());
        }

        List<PatternEndpoints> childEndpointStates = new ArrayList<>();
        HashSet<Integer> seenSids = new HashSet<>(parentEndpointStates.size());

        for (PatternEndpoints parentEp : parentEndpointStates) {
            Objects.requireNonNull(parentEp, "parent endpoint state element must not be null");
            int sid = parentEp.actualSid();
            if (!seenSids.add(sid)) {
                throw new IllegalArgumentException(
                        "duplicate actual SID in parentEndpointStates: " + sid);
            }

            // Unknown actual SID check is performed inside occurrenceIndex.positions(...)
            int[] occPositions = occurrenceIndex.positions(extensionItem, sid);
            int[] childEndpoints = intersect(parentEp.endpoints(), occPositions);

            if (childEndpoints.length > 0) {
                childEndpointStates.add(PatternEndpoints.of(sid, childEndpoints));
            }
        }

        return VerificationResult.of(childEndpointStates);
    }

    /**
     * Performs a two-pointer intersection between two sorted, strictly increasing,
     * 1-based
     * position arrays.
     *
     * @param parentEndpoints     the feasible endpoints of the parent pattern in
     *                            this sequence
     * @param occurrencePositions the 1-based positions where the extension item
     *                            appears
     * @return a sorted, strictly increasing array of matching positions; empty
     *         array if none match
     */
    private static int[] intersect(int[] parentEndpoints, int[] occurrencePositions) {
        if (parentEndpoints.length == 0 || occurrencePositions.length == 0) {
            return EMPTY_POSITIONS;
        }

        int maxPossible = Math.min(parentEndpoints.length, occurrencePositions.length);
        int[] buffer = new int[maxPossible];
        int i = 0;
        int j = 0;
        int matchCount = 0;

        while (i < parentEndpoints.length && j < occurrencePositions.length) {
            int ep = parentEndpoints[i];
            int pos = occurrencePositions[j];
            if (ep == pos) {
                buffer[matchCount++] = ep;
                i++;
                j++;
            } else if (ep < pos) {
                i++;
            } else {
                j++;
            }
        }

        if (matchCount == 0) {
            return EMPTY_POSITIONS;
        }
        if (matchCount == maxPossible) {
            return buffer;
        }
        return Arrays.copyOf(buffer, matchCount);
    }

    /**
     * Verifies an S-Extension of {@code parent} with {@code extensionItem} using
     * exact temporal strictly-later position matching.
     *
     * <p>
     * Under the C08.1 contract and Root Policy, {@code parent} must be a non-empty
     * sequential pattern. Singleton/F1 generation from an empty root belongs to the
     * generation layer (C09) and is rejected fail-fast here.
     *
     * <p>
     * For each sequence {@code s} with actual SID and parent feasible endpoint set
     * {@code E(P, s)}, an itemset position {@code p} where {@code extensionItem} appears
     * is a feasible child endpoint for {@code P' = P ⊕S x} if and only if:
     * <pre>
     *   ∃ e ∈ E(P, s) : e &lt; p
     * </pre>
     * Positions with {@code p == e} (same itemset) or {@code p &lt; e} (reverse order)
     * are strictly rejected. All surviving child endpoints are retained.
     *
     * @param parent               the parent sequential pattern (must not be null or empty)
     * @param parentEndpointStates the list of parent sequence endpoint states (must not be null
     *                             or contain null; duplicate actual SIDs rejected)
     * @param extensionItem        the item to append as a new singleton itemset (must not be null)
     * @param occurrenceIndex      the vertical occurrence index (must not be null)
     * @return an immutable {@link VerificationResult} containing surviving endpoint states
     * @throws NullPointerException     if any parameter is null or {@code parentEndpointStates}
     *                                  contains a null element
     * @throws IllegalArgumentException if {@code parent} is empty, {@code parentEndpointStates}
     *                                  contains duplicate actual SIDs, or an actual SID is
     *                                  unknown to {@code occurrenceIndex}
     */
    public VerificationResult verifySExtension(
            Pattern parent,
            List<PatternEndpoints> parentEndpointStates,
            Item extensionItem,
            VerticalOccurrenceIndex occurrenceIndex) {

        Objects.requireNonNull(parent, "parent must not be null");
        Objects.requireNonNull(parentEndpointStates, "parentEndpointStates must not be null");
        Objects.requireNonNull(extensionItem, "extensionItem must not be null");
        Objects.requireNonNull(occurrenceIndex, "occurrenceIndex must not be null");

        if (parent.isEmpty()) {
            throw new IllegalArgumentException("parent pattern must not be empty");
        }

        // Structural validation: delegates to shared PatternExtensions policy
        PATTERN_EXTENSIONS.sExtend(parent, extensionItem);

        if (parentEndpointStates.isEmpty()) {
            return VerificationResult.of(List.of());
        }

        List<PatternEndpoints> childEndpointStates = new ArrayList<>();
        HashSet<Integer> seenSids = new HashSet<>(parentEndpointStates.size());

        for (PatternEndpoints parentEp : parentEndpointStates) {
            Objects.requireNonNull(parentEp, "parent endpoint state element must not be null");
            int sid = parentEp.actualSid();
            if (!seenSids.add(sid)) {
                throw new IllegalArgumentException(
                        "duplicate actual SID in parentEndpointStates: " + sid);
            }

            // Unknown actual SID check is performed inside occurrenceIndex.positions(...)
            int[] occPositions = occurrenceIndex.positions(extensionItem, sid);
            int[] childEndpoints = strictlyLaterEndpoints(parentEp.endpoints(), occPositions);

            if (childEndpoints.length > 0) {
                childEndpointStates.add(PatternEndpoints.of(sid, childEndpoints));
            }
        }

        return VerificationResult.of(childEndpointStates);
    }

    /**
     * Computes strictly-later child endpoints for S-Extension from sorted, strictly increasing,
     * 1-based position arrays.
     *
     * <p>
     * A position {@code p ∈ occurrencePositions} is a feasible child endpoint if and only if
     * there exists {@code e ∈ parentEndpoints} such that {@code e < p}.
     * Since {@code parentEndpoints} is non-empty, sorted, and strictly increasing, this is
     * mathematically equivalent to {@code p > parentEndpoints[0]}.
     *
     * <p>
     * All matching positions {@code p} are preserved in the returned array without compression
     * to earliest-only or latest-only representation.
     *
     * @param parentEndpoints     the feasible endpoints of the parent pattern in this sequence
     * @param occurrencePositions the 1-based positions where the extension item appears
     * @return a sorted, strictly increasing array of matching positions; empty array if none match
     */
    private static int[] strictlyLaterEndpoints(int[] parentEndpoints, int[] occurrencePositions) {
        if (parentEndpoints.length == 0 || occurrencePositions.length == 0) {
            return EMPTY_POSITIONS;
        }

        int minParent = parentEndpoints[0];
        int start = 0;
        while (start < occurrencePositions.length && occurrencePositions[start] <= minParent) {
            start++;
        }

        if (start == occurrencePositions.length) {
            return EMPTY_POSITIONS;
        }

        return Arrays.copyOfRange(occurrencePositions, start, occurrencePositions.length);
    }
}
