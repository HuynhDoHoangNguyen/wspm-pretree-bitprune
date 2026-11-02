package vn.edu.wspm.config;

import java.util.OptionalDouble;

/** Auditable minimum-support values after resolution against one database. */
public final class ResolvedMinimumSupport {

    private final MinimumSupport configuration;
    private final int numberOfSequences;
    private final Double relativeMinimumSupport;
    private final double effectiveThreshold;

    private ResolvedMinimumSupport(
            MinimumSupport configuration,
            int numberOfSequences,
            Double relativeMinimumSupport,
            double effectiveThreshold) {
        this.configuration = configuration;
        this.numberOfSequences = numberOfSequences;
        this.relativeMinimumSupport = relativeMinimumSupport;
        this.effectiveThreshold = effectiveThreshold;
    }

    static ResolvedMinimumSupport percentage(
            MinimumSupport configuration,
            int numberOfSequences,
            double relativeMinimumSupport,
            double effectiveThreshold) {
        return new ResolvedMinimumSupport(
                configuration,
                numberOfSequences,
                relativeMinimumSupport,
                effectiveThreshold);
    }

    static ResolvedMinimumSupport absolute(
            MinimumSupport configuration,
            int numberOfSequences,
            double effectiveThreshold) {
        return new ResolvedMinimumSupport(
                configuration, numberOfSequences, null, effectiveThreshold);
    }

    public double configuredValue() {
        return configuration.configuredValue();
    }

    public MinimumSupportMode mode() {
        return configuration.mode();
    }

    /** Present only for percentage mode; represented as a fraction in [0, 1]. */
    public OptionalDouble relativeMinimumSupport() {
        return relativeMinimumSupport == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(relativeMinimumSupport);
    }

    public int numberOfSequences() {
        return numberOfSequences;
    }

    /** Returns the real-valued threshold used by support * MeanW >= threshold. */
    public double effectiveThreshold() {
        return effectiveThreshold;
    }

    /** Applies the paper's inclusive weighted acceptance condition without an epsilon. */
    public boolean meetsThreshold(int support, double meanWeight) {
        if (support < 0 || support > numberOfSequences) {
            throw new IllegalArgumentException(
                    "support must be between 0 and the number of sequences inclusive");
        }
        if (!Double.isFinite(meanWeight) || meanWeight < 0.0) {
            throw new IllegalArgumentException("MeanW must be finite and non-negative");
        }

        double weightedSupport = support * meanWeight;
        if (!Double.isFinite(weightedSupport)) {
            throw new IllegalArgumentException("weighted support must be finite");
        }
        return weightedSupport >= effectiveThreshold;
    }
}
