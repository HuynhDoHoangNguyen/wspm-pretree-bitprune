package vn.edu.wspm.config;

import java.util.Objects;

/** Immutable, validated minimum-support configuration before database-size resolution. */
public record MinimumSupport(double configuredValue, MinimumSupportMode mode) {

    public MinimumSupport {
        Objects.requireNonNull(mode, "minimum-support mode must not be null");
        if (!Double.isFinite(configuredValue)) {
            throw new IllegalArgumentException("minimum support must be finite");
        }
        if (mode == MinimumSupportMode.PERCENT
                && (configuredValue < 0.0 || configuredValue > 100.0)) {
            throw new IllegalArgumentException(
                    "percentage minimum support must be between 0 and 100 inclusive");
        }
        if (mode == MinimumSupportMode.ABSOLUTE && configuredValue < 0.0) {
            throw new IllegalArgumentException(
                    "absolute minimum support must not be negative");
        }
    }

    /** Maps the planned MIN_SUP and MIN_SUP_IS_PERCENT configuration pair. */
    public static MinimumSupport of(double minSup, boolean minSupIsPercent) {
        return new MinimumSupport(
                minSup,
                minSupIsPercent ? MinimumSupportMode.PERCENT : MinimumSupportMode.ABSOLUTE);
    }

    public static MinimumSupport percentage(double percentage) {
        return new MinimumSupport(percentage, MinimumSupportMode.PERCENT);
    }

    public static MinimumSupport absolute(double threshold) {
        return new MinimumSupport(threshold, MinimumSupportMode.ABSOLUTE);
    }
}
