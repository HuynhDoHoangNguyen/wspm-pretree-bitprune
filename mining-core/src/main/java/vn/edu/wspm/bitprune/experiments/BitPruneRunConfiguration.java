package vn.edu.wspm.bitprune.experiments;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Objects;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;

/**
 * Immutable value-configuration model for the standalone WSPM_BitPrune runner.
 *
 * <p>This model validates in-memory structural constraints and minimum support ranges.
 * Filesystem/path execution validation is decoupled and handled by C15.2.</p>
 */
public record BitPruneRunConfiguration(
        Path datasetPath,
        SequenceDatabaseFormat inputFormat,
        boolean minimumSupportIsRelative,
        double minimumSupport,
        boolean printPatterns,
        boolean printStatistics,
        boolean exportPatterns,
        Path outputPath) {

    public BitPruneRunConfiguration {
        Objects.requireNonNull(datasetPath, "datasetPath must not be null");
        Objects.requireNonNull(inputFormat, "inputFormat must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");

        if (!Double.isFinite(minimumSupport)) {
            throw new IllegalArgumentException("minimumSupport must be finite");
        }

        if (minimumSupportIsRelative) {
            if (minimumSupport < 0.0 || minimumSupport > 1.0) {
                throw new IllegalArgumentException(
                        "relative minimumSupport must be between 0.0 and 1.0 inclusive");
            }
        } else {
            if (minimumSupport < 0.0) {
                throw new IllegalArgumentException(
                        "absolute minimumSupport must not be negative");
            }
        }
    }

    /**
     * Converts the configured minimum support into the shared {@link MinimumSupport} model.
     *
     * <p>Relative fractions in {@code [0.0, 1.0]} are converted to percentage values in {@code [0.0, 100.0]}
     * via {@link BigDecimal#movePointRight(int)} to avoid binary floating-point multiplication artifacts.
     * Absolute thresholds are preserved directly without integer rounding.</p>
     *
     * @return the configured shared {@link MinimumSupport} carrier
     */
    public MinimumSupport toMinimumSupport() {
        if (minimumSupportIsRelative) {
            double percentage = BigDecimal.valueOf(minimumSupport)
                    .movePointRight(2)
                    .doubleValue();
            return MinimumSupport.percentage(percentage);
        }
        return MinimumSupport.absolute(minimumSupport);
    }
}
