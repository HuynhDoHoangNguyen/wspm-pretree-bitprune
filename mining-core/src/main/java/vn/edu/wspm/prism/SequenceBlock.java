package vn.edu.wspm.prism;

import java.util.Objects;

/**
 * Sequence block {@code S_X} for a pattern X: an encoded vector of SID membership bits.
 *
 * <h2>Concept — SOURCE-CONFIRMED (PRISM Sec. 3.2, Theorem 3.1)</h2>
 * <p>For pattern X, the sequence block encodes a global bit vector whose bit {@code i} is 1
 * exactly when database sequence {@code i} contains X. The bit vector is right-padded to a
 * multiple of {@link PrimalBlockCodec#BLOCK_WIDTH} and encoded blockwise.</p>
 *
 * <p>Support of pattern X is the factor cardinality sum over all sequence blocks:
 * <pre>support(X) = sum(||v||_G  for v in S_X)</pre>
 * This counts supporting SIDs; multiple occurrences of X in one SID contribute exactly 1.</p>
 *
 * <h2>Indexing — IMPLEMENTATION POLICY</h2>
 * <p>SIDs are 1-based in the PRISM paper. Internally they map to 0-based bit positions.
 * <pre>paper SID i → internal bit index (i - 1)</pre>
 * Factory methods document this conversion explicitly.</p>
 *
 * <h2>Immutability — IMPLEMENTATION POLICY</h2>
 * <p>All state is delegated to the immutable {@link EncodedVector}. No mutable buffer is exposed.</p>
 */
public final class SequenceBlock {

    /**
     * Total number of SIDs in the database universe that this sequence block covers.
     * This equals the logical length of the underlying encoded vector.
     */
    private final int sidCount;

    /** The encoded SID membership vector. */
    private final EncodedVector vector;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    private SequenceBlock(int sidCount, EncodedVector vector) {
        if (sidCount < 0) {
            throw new IllegalArgumentException("sidCount must be >= 0, got: " + sidCount);
        }
        if (vector.logicalLength() != sidCount) {
            throw new IllegalArgumentException(
                    "EncodedVector logicalLength " + vector.logicalLength()
                    + " must equal sidCount " + sidCount);
        }
        this.sidCount = sidCount;
        this.vector = Objects.requireNonNull(vector);
    }

    /**
     * Creates a SequenceBlock for a database of {@code sidCount} sequences where the given
     * 1-based SIDs are supporting.
     *
     * <p><b>Indexing contract:</b> paper SID {@code i} maps to bit index {@code i-1}.</p>
     *
     * @param sidCount       total number of sequences in the database universe (>= 0)
     * @param supportingSids 1-based SIDs that support the pattern; must be in [1, sidCount]
     * @return an immutable SequenceBlock
     * @throws IllegalArgumentException if any SID is outside [1, sidCount]
     */
    public static SequenceBlock of(int sidCount, int... supportingSids) {
        if (sidCount < 0) {
            throw new IllegalArgumentException("sidCount must be >= 0, got: " + sidCount);
        }
        return new SequenceBlock(sidCount,
                EncodedVector.fromPaperPositions(sidCount, supportingSids));
    }

    /**
     * Creates an empty SequenceBlock for a database of {@code sidCount} sequences where no SID
     * supports the pattern.
     *
     * @param sidCount total number of sequences; >= 0
     * @return an immutable SequenceBlock with all bits zero
     */
    public static SequenceBlock empty(int sidCount) {
        if (sidCount < 0) {
            throw new IllegalArgumentException("sidCount must be >= 0, got: " + sidCount);
        }
        return new SequenceBlock(sidCount, EncodedVector.fromPaperPositions(sidCount));
    }

    /**
     * Creates a SequenceBlock from an already-constructed EncodedVector.
     *
     * @param sidCount number of sequences; must equal {@code vector.logicalLength()}
     * @param vector   the encoded membership vector; not null
     */
    public static SequenceBlock fromVector(int sidCount, EncodedVector vector) {
        return new SequenceBlock(sidCount, Objects.requireNonNull(vector));
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the total number of sequences in the database universe covered by this block.
     */
    public int sidCount() {
        return sidCount;
    }

    /**
     * Returns the number of encoded 8-bit blocks in the sequence vector.
     */
    public int blockCount() {
        return vector.blockCount();
    }

    /**
     * Returns the encoded product for the given 0-based block index.
     *
     * @param blockIndex 0-based in [0, blockCount())
     */
    public int blockProduct(int blockIndex) {
        return vector.blockProduct(blockIndex);
    }

    /**
     * Returns the rank for the given 0-based block index.
     *
     * @param blockIndex 0-based in [0, blockCount())
     */
    public int blockRank(int blockIndex) {
        return vector.blockRank(blockIndex);
    }

    /**
     * Returns {@code true} if the given 1-based SID is recorded as supporting the pattern.
     *
     * <p><b>Indexing:</b> paper SID {@code i} → bit index {@code i-1}.</p>
     *
     * @param sid 1-based SID in [1, sidCount]
     */
    public boolean isSupporting(int sid) {
        if (sid < 1 || sid > sidCount) {
            throw new IllegalArgumentException(
                    "SID " + sid + " is outside [1, " + sidCount + "]");
        }
        return vector.isSet(sid - 1); // convert 1-based → 0-based
    }

    /**
     * Returns the support of the pattern as the total factor cardinality over all sequence blocks.
     *
     * <p>SOURCE-CONFIRMED: PRISM Theorem 3.1. Each supporting SID contributes exactly 1 to support
     * regardless of how many times the pattern occurs in that sequence.</p>
     *
     * @return non-negative integer count of supporting sequences
     */
    public int support() {
        return vector.totalCardinality();
    }

    /**
     * Returns {@code true} if no SID supports the pattern (support = 0).
     */
    public boolean isEmpty() {
        return vector.isEmpty();
    }

    /**
     * Returns the underlying encoded vector.
     */
    public EncodedVector vector() {
        return vector;
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SequenceBlock that)) return false;
        return sidCount == that.sidCount && vector.equals(that.vector);
    }

    @Override
    public int hashCode() {
        return 31 * sidCount + vector.hashCode();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("SequenceBlock{sids=").append(sidCount)
                .append(", support=").append(support())
                .append(", blocks=[");
        for (int i = 0; i < blockCount(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(blockProduct(i));
        }
        return sb.append("]}").toString();
    }
}
