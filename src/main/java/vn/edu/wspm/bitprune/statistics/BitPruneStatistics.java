package vn.edu.wspm.bitprune.statistics;

/**
 * Immutable execution statistics for one completed WSPM_BitPrune mining run.
 *
 * <p>
 * Maintains exactly 12 primary non-negative counters of type {@code long} and
 * provides
 * one derived statistic {@link #rejectedExtensions()}. Strict candidate
 * accounting invariants
 * are validated at construction time using checked arithmetic.
 *
 * @param sequenceCount        actual number of sequence records in the input
 *                             database (|SD|);
 *                             never derived from maximum SID
 * @param f1Count              number of weighted singleton patterns accepted
 *                             directly under the PrefixTree
 *                             root (|F1|); F1 patterns are not part of
 *                             {@code generatedExtensions}
 * @param generatedExtensions  total structural extension candidates generated
 *                             to evaluate in
 *                             DFS; does not include F1, ungenerated potential
 *                             descendants,
 *                             duplicate retries, or internal verifier
 *                             operations
 * @param generatedIExtensions portion of {@code generatedExtensions} produced
 *                             via I-Extension
 * @param generatedSExtensions portion of {@code generatedExtensions} produced
 *                             via S-Extension
 * @param cmapRejected         candidate extensions receiving terminal outcome
 *                             CMAP_REJECTED (Tier 1 filter)
 * @param bitmapRejected       candidate extensions passing Tier 1 and receiving
 *                             terminal outcome
 *                             BITMAP_REJECTED (Tier 2 weighted upper-bound
 *                             filter)
 * @param exactRejected        candidate extensions passing Tier 2, completing
 *                             exact temporal verification,
 *                             and receiving terminal outcome EXACT_REJECTED
 *                             (Tier 3)
 * @param acceptedExtensions   candidate extensions completing exact temporal
 *                             verification and
 *                             receiving terminal outcome ACCEPTED (Tier 3)
 * @param expandedNodes        number of accepted non-root pattern nodes chosen
 *                             as DFS parents
 * @param patternCount         total weighted sequential patterns in the
 *                             completed result (excluding root)
 * @param exactVerifications   number of extension candidates that actually
 *                             completed exact temporal
 *                             verification (Tier 3); does not count examined
 *                             SIDs, embeddings,
 *                             endpoints, positional comparisons, or verifier
 *                             loops
 */
public record BitPruneStatistics(
        long sequenceCount,
        long f1Count,
        long generatedExtensions,
        long generatedIExtensions,
        long generatedSExtensions,
        long cmapRejected,
        long bitmapRejected,
        long exactRejected,
        long acceptedExtensions,
        long expandedNodes,
        long patternCount,
        long exactVerifications) {

    public BitPruneStatistics {
        requireNonNegative(sequenceCount, "sequenceCount");
        requireNonNegative(f1Count, "f1Count");
        requireNonNegative(generatedExtensions, "generatedExtensions");
        requireNonNegative(generatedIExtensions, "generatedIExtensions");
        requireNonNegative(generatedSExtensions, "generatedSExtensions");
        requireNonNegative(cmapRejected, "cmapRejected");
        requireNonNegative(bitmapRejected, "bitmapRejected");
        requireNonNegative(exactRejected, "exactRejected");
        requireNonNegative(acceptedExtensions, "acceptedExtensions");
        requireNonNegative(expandedNodes, "expandedNodes");
        requireNonNegative(patternCount, "patternCount");
        requireNonNegative(exactVerifications, "exactVerifications");

        // Invariant 1: generatedExtensions = generatedIExtensions +
        // generatedSExtensions
        long expectedGeneratedFromExtensions = Math.addExact(generatedIExtensions, generatedSExtensions);
        if (generatedExtensions != expectedGeneratedFromExtensions) {
            throw new IllegalStateException(
                    "Invariant violation: generatedExtensions (" + generatedExtensions
                            + ") must equal generatedIExtensions (" + generatedIExtensions
                            + ") + generatedSExtensions (" + generatedSExtensions + ")");
        }

        // Invariant 2: generatedExtensions = cmapRejected + bitmapRejected +
        // exactRejected + acceptedExtensions
        long terminalSumTier12 = Math.addExact(cmapRejected, bitmapRejected);
        long terminalSumTier3 = Math.addExact(exactRejected, acceptedExtensions);
        long expectedGeneratedFromOutcomes = Math.addExact(terminalSumTier12, terminalSumTier3);
        if (generatedExtensions != expectedGeneratedFromOutcomes) {
            throw new IllegalStateException(
                    "Invariant violation: generatedExtensions (" + generatedExtensions
                            + ") must equal cmapRejected (" + cmapRejected
                            + ") + bitmapRejected (" + bitmapRejected
                            + ") + exactRejected (" + exactRejected
                            + ") + acceptedExtensions (" + acceptedExtensions + ")");
        }

        // Invariant 3: exactVerifications = exactRejected + acceptedExtensions
        long expectedExactVerifications = Math.addExact(exactRejected, acceptedExtensions);
        if (exactVerifications != expectedExactVerifications) {
            throw new IllegalStateException(
                    "Invariant violation: exactVerifications (" + exactVerifications
                            + ") must equal exactRejected (" + exactRejected
                            + ") + acceptedExtensions (" + acceptedExtensions + ")");
        }

        // Invariant 4: patternCount = f1Count + acceptedExtensions
        long expectedPatternCount = Math.addExact(f1Count, acceptedExtensions);
        if (patternCount != expectedPatternCount) {
            throw new IllegalStateException(
                    "Invariant violation: patternCount (" + patternCount
                            + ") must equal f1Count (" + f1Count
                            + ") + acceptedExtensions (" + acceptedExtensions + ")");
        }
    }

    /**
     * Returns the total number of candidate extensions rejected across all three
     * pruning tiers.
     *
     * <p>
     * Derived convenience statistic:
     * {@code rejectedExtensions = cmapRejected + bitmapRejected + exactRejected}.
     *
     * @return total candidate extensions rejected by CMAP, Bitmap Upper Bound, or
     *         Exact Verification
     */
    public long rejectedExtensions() {
        return Math.addExact(Math.addExact(cmapRejected, bitmapRejected), exactRejected);
    }

    private static void requireNonNegative(long value, String counterName) {
        if (value < 0L) {
            throw new IllegalArgumentException(counterName + " must not be negative: " + value);
        }
    }
}
