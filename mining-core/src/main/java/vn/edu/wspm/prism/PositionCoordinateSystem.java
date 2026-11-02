package vn.edu.wspm.prism;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable sequence-size metadata shared by compact position encodings from one SID universe.
 * SID {@code i} maps to array index {@code i - 1}; sizes count itemsets, not flattened items.
 */
public final class PositionCoordinateSystem {

    private final int[] sequenceSizes;

    private PositionCoordinateSystem(int[] sequenceSizes, boolean trustedOwned) {
        Objects.requireNonNull(sequenceSizes, "sequenceSizes must not be null");
        for (int sidIndex = 0; sidIndex < sequenceSizes.length; sidIndex++) {
            if (sequenceSizes[sidIndex] < 0) {
                throw new IllegalArgumentException(
                        "sequence size for SID " + (sidIndex + 1)
                                + " must be non-negative, got: " + sequenceSizes[sidIndex]);
            }
        }
        this.sequenceSizes = trustedOwned
                ? sequenceSizes
                : Arrays.copyOf(sequenceSizes, sequenceSizes.length);
    }

    public static PositionCoordinateSystem of(int... sequenceSizes) {
        return new PositionCoordinateSystem(sequenceSizes, false);
    }

    static PositionCoordinateSystem fromTrustedOwnedSizes(int[] sequenceSizes) {
        return new PositionCoordinateSystem(sequenceSizes, true);
    }

    public int sidCount() {
        return sequenceSizes.length;
    }

    public int sequenceSize(int sid) {
        if (sid < 1 || sid > sequenceSizes.length) {
            throw new IllegalArgumentException(
                    "SID " + sid + " is outside [1, " + sequenceSizes.length + "]");
        }
        return sequenceSizes[sid - 1];
    }

    public int[] sequenceSizes() {
        return Arrays.copyOf(sequenceSizes, sequenceSizes.length);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PositionCoordinateSystem that
                && Arrays.equals(sequenceSizes, that.sequenceSizes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sequenceSizes);
    }
}
