package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation.Outcome;
import vn.edu.wspm.bitprune.verification.VerificationResult;

/** Allocation-minimal internal terminal result used only by the production DFS hot path. */
final class BitPruneHotEvaluation {

    private static final BitPruneHotEvaluation CMAP_REJECTED =
            new BitPruneHotEvaluation(Outcome.CMAP_REJECTED, null, null);
    private static final BitPruneHotEvaluation BITMAP_REJECTED =
            new BitPruneHotEvaluation(Outcome.BITMAP_REJECTED, null, null);
    private static final BitPruneHotEvaluation EXACT_REJECTED =
            new BitPruneHotEvaluation(Outcome.EXACT_REJECTED, null, null);

    private final Outcome outcome;
    private final SidBitmap acceptedBitmap;
    private final VerificationResult acceptedVerification;

    private BitPruneHotEvaluation(
            Outcome outcome,
            SidBitmap acceptedBitmap,
            VerificationResult acceptedVerification) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.acceptedBitmap = acceptedBitmap;
        this.acceptedVerification = acceptedVerification;
    }

    static BitPruneHotEvaluation cmapRejected() {
        return CMAP_REJECTED;
    }

    static BitPruneHotEvaluation bitmapRejected() {
        return BITMAP_REJECTED;
    }

    static BitPruneHotEvaluation exactRejected() {
        return EXACT_REJECTED;
    }

    static BitPruneHotEvaluation accepted(
            SidBitmap bitmap, VerificationResult verification) {
        return new BitPruneHotEvaluation(
                Outcome.ACCEPTED,
                Objects.requireNonNull(bitmap, "accepted bitmap must not be null"),
                Objects.requireNonNull(verification, "accepted verification must not be null"));
    }

    Outcome outcome() {
        return outcome;
    }

    SidBitmap acceptedBitmap() {
        if (outcome != Outcome.ACCEPTED) {
            throw new IllegalStateException("bitmap is available only for accepted extensions");
        }
        return acceptedBitmap;
    }

    VerificationResult acceptedVerification() {
        if (outcome != Outcome.ACCEPTED) {
            throw new IllegalStateException("verification is available only for accepted extensions");
        }
        return acceptedVerification;
    }
}
