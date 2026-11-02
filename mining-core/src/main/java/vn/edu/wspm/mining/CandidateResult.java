package vn.edu.wspm.mining;

import java.util.Objects;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.prism.PatternEncoding;
import vn.edu.wspm.tree.ExtensionType;

/** Immutable outcome of evaluating one structurally valid one-step candidate. */
public record CandidateResult(
        Pattern pattern,
        ExtensionType extensionType,
        PatternEncoding encoding,
        double globalMeanWeight,
        double effectiveMinimumSupport,
        boolean accepted) {

    public CandidateResult {
        Objects.requireNonNull(pattern, "candidate pattern must not be null");
        Objects.requireNonNull(extensionType, "extension type must not be null");
        Objects.requireNonNull(encoding, "candidate encoding must not be null");
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("candidate pattern must not be empty");
        }
        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "global MeanW must be finite and non-negative");
        }
        if (!Double.isFinite(effectiveMinimumSupport) || effectiveMinimumSupport < 0.0) {
            throw new IllegalArgumentException(
                    "effective minimum support must be finite and non-negative");
        }

        double weightedSupport = encoding.sequenceBlock().support() * globalMeanWeight;
        if (!Double.isFinite(weightedSupport)) {
            throw new IllegalArgumentException("weighted support must be finite");
        }
        if (accepted != (weightedSupport >= effectiveMinimumSupport)) {
            throw new IllegalArgumentException(
                    "accepted flag disagrees with the inclusive weighted-support comparison");
        }
    }

    /** Support is always derived from the post-join result SequenceBlock. */
    public int support() {
        return encoding.sequenceBlock().support();
    }

    public double weightedSupport() {
        return support() * globalMeanWeight;
    }
}
