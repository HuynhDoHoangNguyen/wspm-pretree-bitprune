package vn.edu.wspm.bitprune.mining;

import java.util.Objects;
import java.util.Optional;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation.Outcome;
import vn.edu.wspm.bitprune.verification.BitmapUpperBoundFilter;
import vn.edu.wspm.bitprune.verification.ExactTemporalVerifier;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.tree.ExtensionType;

/**
 * Stateless coordinator that routes one structural candidate extension through
 * the three-tier pruning architecture in WSPM_BitPrune:
 * <ol>
 * <li>CMAP necessary-condition check;</li>
 * <li>Candidate bitmap intersection and upper-bound filter;</li>
 * <li>Exact temporal verification and weighted support acceptance.</li>
 * </ol>
 */
public final class BitPruneCandidateEvaluator {

    private final PatternExtensions patternExtensions = new PatternExtensions();
    private final BitmapUpperBoundFilter upperBoundFilter = new BitmapUpperBoundFilter();
    private final ExactTemporalVerifier exactTemporalVerifier = new ExactTemporalVerifier();

    public BitPruneCandidateEvaluator() {
    }

    /**
     * Evaluates a single structural candidate extension.
     *
     * @param parent                the non-empty parent sequential pattern
     * @param candidate             the declared candidate extension of parent
     * @param parentCandidateBitmap the bitmap upper bound of the parent pattern
     * @param extensionItemBitmap   the bitmap of the extension item
     * @param parentVerification    the exact verification result of the parent
     *                              pattern
     * @param cmapI                 the CMAP-I co-occurrence relation
     * @param cmapS                 the CMAP-S sequence co-occurrence relation
     * @param occurrenceIndex       the vertical occurrence index
     * @param globalMeanWeight      the finite non-negative global MeanW
     * @param minimumSupport        the resolved minimum support threshold
     * @return an immutable evaluation result
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if inputs violate structural or universe
     *                                  invariants
     * @throws IllegalStateException    if internal upper-bound inclusion is broken
     */
    public BitPruneCandidateEvaluation evaluate(
            Pattern parent,
            BitPruneCandidateResult candidate,
            SidBitmap parentCandidateBitmap,
            SidBitmap extensionItemBitmap,
            VerificationResult parentVerification,
            CmapI cmapI,
            CmapS cmapS,
            VerticalOccurrenceIndex occurrenceIndex,
            double globalMeanWeight,
            ResolvedMinimumSupport minimumSupport) {

        // --- 1. Validation Before Routing ---
        Objects.requireNonNull(parent, "parent pattern must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        Objects.requireNonNull(parentCandidateBitmap, "parent candidate bitmap must not be null");
        Objects.requireNonNull(extensionItemBitmap, "extension item bitmap must not be null");
        Objects.requireNonNull(parentVerification, "parent verification result must not be null");
        Objects.requireNonNull(cmapI, "cmapI must not be null");
        Objects.requireNonNull(cmapS, "cmapS must not be null");
        Objects.requireNonNull(occurrenceIndex, "occurrence index must not be null");
        Objects.requireNonNull(minimumSupport, "minimum support must not be null");

        if (parent.isEmpty()) {
            throw new IllegalArgumentException("parent pattern must not be empty");
        }

        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "globalMeanWeight must be finite and non-negative; got: " + globalMeanWeight);
        }

        // Structural consistency validation: candidate must be the immediate valid
        // extension of parent
        Item extensionItem = candidate.extensionItem();
        ExtensionType extensionType = candidate.extensionType();
        Pattern candidatePattern = candidate.pattern();

        Pattern expectedPattern;
        switch (extensionType) {
            case I_EXTENSION -> {
                try {
                    expectedPattern = patternExtensions.iExtend(parent, extensionItem);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Candidate is not a valid I-extension of parent: " + e.getMessage(), e);
                }
                if (!expectedPattern.equals(candidatePattern)) {
                    throw new IllegalArgumentException(
                            "Candidate pattern does not match expected I-extension of parent: expected "
                                    + expectedPattern + ", but got " + candidatePattern);
                }
            }
            case S_EXTENSION -> {
                try {
                    expectedPattern = patternExtensions.sExtend(parent, extensionItem);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Candidate is not a valid S-extension of parent: " + e.getMessage(), e);
                }
                if (!expectedPattern.equals(candidatePattern)) {
                    throw new IllegalArgumentException(
                            "Candidate pattern does not match expected S-extension of parent: expected "
                                    + expectedPattern + ", but got " + candidatePattern);
                }
            }
        }

        // SID universe consistency
        if (!parentCandidateBitmap.mapping().equals(extensionItemBitmap.mapping())) {
            throw new IllegalArgumentException(
                    "parentCandidateBitmap and extensionItemBitmap must have equal SID mappings");
        }
        int universeSize = parentCandidateBitmap.sequenceCount();
        if (universeSize != minimumSupport.numberOfSequences()) {
            throw new IllegalArgumentException(
                    "bitmap universe size (" + universeSize
                            + ") must equal minimumSupport.numberOfSequences() ("
                            + minimumSupport.numberOfSequences() + ")");
        }

        // Validate parent verification SIDs against bitmap universe and parent bitmap
        SidMapping mapping = parentCandidateBitmap.mapping();
        for (PatternEndpoints ep : parentVerification.endpointStates()) {
            int sid = ep.actualSid();
            int bitIndex;
            try {
                bitIndex = mapping.sidToIndex(sid);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "SID " + sid + " in parentVerification does not exist in bitmap universe", e);
            }
            if (!parentCandidateBitmap.isSet(bitIndex)) {
                throw new IllegalArgumentException(
                        "SID " + sid + " in parentVerification is not present in parentCandidateBitmap");
            }
        }

        // --- 2. First-Failure Routing ---
        Item parentLastItem = parent.lastItem().orElseThrow();
        double effectiveThreshold = minimumSupport.effectiveThreshold();

        // Tier 1: CMAP necessary-condition check (bypassed when effectiveThreshold ==
        // 0.0)
        if (effectiveThreshold > 0.0) {
            boolean cmapPass = switch (extensionType) {
                case I_EXTENSION -> cmapI.contains(parentLastItem, extensionItem);
                case S_EXTENSION -> cmapS.contains(parentLastItem, extensionItem);
            };
            if (!cmapPass) {
                return BitPruneCandidateEvaluation.cmapRejected(candidate);
            }
        }

        // Tier 2: Candidate Bitmap Upper Bound (intersection computed when CMAP passed
        // or threshold == 0)
        SidBitmap candidateBitmap = parentCandidateBitmap.and(extensionItemBitmap);

        if (upperBoundFilter.canPrune(candidateBitmap, globalMeanWeight, minimumSupport)) {
            return BitPruneCandidateEvaluation.bitmapRejected(candidate, candidateBitmap);
        }

        // Tier 3: Exact Temporal Verification
        VerificationResult verificationResult = switch (extensionType) {
            case I_EXTENSION -> exactTemporalVerifier.verifyIExtension(
                    parent,
                    parentVerification.endpointStates(),
                    extensionItem,
                    occurrenceIndex);
            case S_EXTENSION -> exactTemporalVerifier.verifySExtension(
                    parent,
                    parentVerification.endpointStates(),
                    extensionItem,
                    occurrenceIndex);
        };

        // Assert upper-bound inclusion invariant
        SidMapping candidateMapping = candidateBitmap.mapping();
        for (PatternEndpoints ep : verificationResult.endpointStates()) {
            int sid = ep.actualSid();
            int bitIndex;
            try {
                bitIndex = candidateMapping.sidToIndex(sid);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Upper-bound inclusion invariant broken: supporting SID " + sid
                                + " is not in candidateBitmap universe",
                        e);
            }
            if (!candidateBitmap.isSet(bitIndex)) {
                throw new IllegalStateException(
                        "Upper-bound inclusion invariant broken: supporting SID " + sid
                                + " is not contained in candidateBitmap");
            }
        }

        // Final weighted acceptance predicate
        boolean meets = minimumSupport.meetsThreshold(verificationResult.exactSupport(), globalMeanWeight);
        Outcome outcome = meets ? Outcome.ACCEPTED : Outcome.EXACT_REJECTED;

        return new BitPruneCandidateEvaluation(
                candidate,
                outcome,
                Optional.of(candidateBitmap),
                Optional.of(verificationResult));
    }
}
