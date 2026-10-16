package vn.edu.wspm.config;

import java.util.Objects;

/** Converts configured minimum support into the unit used by the weighted comparison. */
public final class MinimumSupportResolver {

    /**
     * Resolves a threshold without integer rounding.
     *
     * <p>B7 research decision: percentage mode uses
     * {@code (configuredValue / 100.0) * numberOfSequences}. The paper does not state this
     * conversion explicitly; it is a strongly supported inference that reproduces its Table 1
     * oracle. Absolute mode is not scaled.</p>
     */
    public ResolvedMinimumSupport resolve(
            MinimumSupport minimumSupport, int numberOfSequences) {
        Objects.requireNonNull(minimumSupport, "minimum support must not be null");
        if (numberOfSequences < 0) {
            throw new IllegalArgumentException("number of sequences must not be negative");
        }

        if (minimumSupport.mode() == MinimumSupportMode.PERCENT) {
            if (numberOfSequences == 0) {
                throw new IllegalArgumentException(
                        "percentage minimum support cannot be resolved for an empty database");
            }
            double relativeMinimumSupport = minimumSupport.configuredValue() / 100.0;
            double effectiveThreshold = relativeMinimumSupport * numberOfSequences;
            return ResolvedMinimumSupport.percentage(
                    minimumSupport,
                    numberOfSequences,
                    relativeMinimumSupport,
                    effectiveThreshold);
        }

        return ResolvedMinimumSupport.absolute(
                minimumSupport, numberOfSequences, minimumSupport.configuredValue());
    }
}
