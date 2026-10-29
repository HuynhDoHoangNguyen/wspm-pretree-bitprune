package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.verification.VerificationResult;

/**
 * Immutable evaluation result for a structural candidate in WSPM_BitPrune.
 *
 * <p>
 * Represents the outcome of routing a single candidate through the three-tier
 * filtering architecture: CMAP, Bitmap Upper Bound, and Exact Temporal
 * Verification.
 * </p>
 */
public record BitPruneCandidateEvaluation(
        BitPruneCandidateResult candidate,
        Outcome outcome,
        Optional<SidBitmap> candidateBitmap,
        Optional<VerificationResult> verificationResult) {

    /**
     * Terminal outcome of evaluating a candidate extension.
     */
    public enum Outcome {
        /** Rejected by CMAP co-occurrence necessary condition. */
        CMAP_REJECTED,

        /** Rejected by Bitmap cardinality upper-bound filter. */
        BITMAP_REJECTED,

        /**
         * Exact temporal verification completed but exact weighted support is below
         * threshold.
         */
        EXACT_REJECTED,

        /**
         * Passed all three tiers; accepted as a valid weighted sequential pattern
         * extension.
         */
        ACCEPTED
    }

    public BitPruneCandidateEvaluation {
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(candidateBitmap, "candidateBitmap container must not be null");
        Objects.requireNonNull(verificationResult, "verificationResult container must not be null");

        switch (outcome) {
            case CMAP_REJECTED -> {
                if (candidateBitmap.isPresent()) {
                    throw new IllegalArgumentException("CMAP_REJECTED requires absent candidate bitmap");
                }
                if (verificationResult.isPresent()) {
                    throw new IllegalArgumentException("CMAP_REJECTED requires absent verification result");
                }
            }
            case BITMAP_REJECTED -> {
                if (candidateBitmap.isEmpty()) {
                    throw new IllegalArgumentException("BITMAP_REJECTED requires present candidate bitmap");
                }
                if (verificationResult.isPresent()) {
                    throw new IllegalArgumentException("BITMAP_REJECTED requires absent verification result");
                }
            }
            case EXACT_REJECTED, ACCEPTED -> {
                if (candidateBitmap.isEmpty()) {
                    throw new IllegalArgumentException(outcome + " requires present candidate bitmap");
                }
                if (verificationResult.isEmpty()) {
                    throw new IllegalArgumentException(outcome + " requires present verification result");
                }
            }
        }
    }

    public static BitPruneCandidateEvaluation cmapRejected(BitPruneCandidateResult candidate) {
        return new BitPruneCandidateEvaluation(
                candidate, Outcome.CMAP_REJECTED, Optional.empty(), Optional.empty());
    }

    public static BitPruneCandidateEvaluation bitmapRejected(
            BitPruneCandidateResult candidate, SidBitmap candidateBitmap) {
        return new BitPruneCandidateEvaluation(
                candidate, Outcome.BITMAP_REJECTED, Optional.of(candidateBitmap), Optional.empty());
    }

    public static BitPruneCandidateEvaluation exactRejected(
            BitPruneCandidateResult candidate,
            SidBitmap candidateBitmap,
            VerificationResult verificationResult) {
        return new BitPruneCandidateEvaluation(
                candidate, Outcome.EXACT_REJECTED, Optional.of(candidateBitmap), Optional.of(verificationResult));
    }

    public static BitPruneCandidateEvaluation accepted(
            BitPruneCandidateResult candidate,
            SidBitmap candidateBitmap,
            VerificationResult verificationResult) {
        return new BitPruneCandidateEvaluation(
                candidate, Outcome.ACCEPTED, Optional.of(candidateBitmap), Optional.of(verificationResult));
    }
}
