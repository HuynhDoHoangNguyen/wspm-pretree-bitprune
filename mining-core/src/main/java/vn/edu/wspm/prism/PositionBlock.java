package vn.edu.wspm.prism;

import java.util.Objects;

/**
 * Position block {@code P_X^i} for one SID: an encoded vector of itemset-index end positions.
 *
 * <h2>Concept — SOURCE-CONFIRMED (PRISM Sec. 3.2, Theorems 3.2–3.3)</h2>
 * <p>For pattern X and database sequence {@code i}, {@code P_X^i} encodes the itemset indexes at
 * which an embedding/occurrence of X ends. For a singleton item, these are exactly the itemset
 * indexes containing that item. For a derived pattern, PRISM retains the positions of the last
 * item in an embedding.</p>
 *
 * <p>Positions are <strong>itemset indexes</strong>, not flat item offsets. Multiple occurrences
 * of X in the same SID set multiple bits in this per-SID vector. The vector is right-padded to a
 * multiple of {@link PrimalBlockCodec#BLOCK_WIDTH} before blockwise encoding.</p>
 *
 * <h2>Full representation — SOURCE-CONFIRMED (PRISM Sec. 3.2, B12 Contract Sec. 15)</h2>
 * <p>The full encoding retains all blocks including trailing empty blocks. This avoids alignment
 * ambiguity during joins. Compact forms are deferred until B14 correctness passes.</p>
 *
 * <h2>Indexing — IMPLEMENTATION POLICY</h2>
 * <p>Itemset positions are 1-based in the PRISM paper. Internally they map to 0-based bit indexes.
 * <pre>paper position t → internal bit index (t - 1)</pre></p>
 *
 * <h2>SID association — IMPLEMENTATION POLICY</h2>
 * <p>A PositionBlock is always associated with a specific 1-based SID. This prevents misalignment
 * during joins and makes the invariant from B12 Contract Section 15 explicit:
 * "a SID bit is set iff its position vector contains a non-empty block."</p>
 */
public final class PositionBlock {

    /**
     * The 1-based SID this position block belongs to.
     * SOURCE-CONFIRMED: SIDs are 1-based in the paper (PRISM Sec. 3.2).
     */
    private final int sid;

    /**
     * The number of itemsets in the associated database sequence (logical length).
     * This is the "sequence size" in paper terms.
     */
    private final int sequenceSize;

    /** The encoded itemset-position vector for this SID. */
    private final EncodedVector vector;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    private PositionBlock(int sid, int sequenceSize, EncodedVector vector) {
        if (sid < 1) {
            throw new IllegalArgumentException("SID must be >= 1, got: " + sid);
        }
        if (sequenceSize < 0) {
            throw new IllegalArgumentException("sequenceSize must be >= 0, got: " + sequenceSize);
        }
        if (vector.logicalLength() != sequenceSize) {
            throw new IllegalArgumentException(
                    "EncodedVector logicalLength " + vector.logicalLength()
                    + " must equal sequenceSize " + sequenceSize);
        }
        this.sid = sid;
        this.sequenceSize = sequenceSize;
        this.vector = Objects.requireNonNull(vector);
    }

    /**
     * Creates a PositionBlock for a given SID with specific 1-based end positions set.
     *
     * <p><b>Indexing contract:</b> paper position {@code t} maps to bit index {@code t-1}.</p>
     *
     * @param sid            1-based SID; >= 1
     * @param sequenceSize   total number of itemsets in this sequence; >= 0
     * @param oneBasedPositions 1-based end positions that are set; must be in [1, sequenceSize]
     * @return an immutable PositionBlock
     * @throws IllegalArgumentException if any position is outside [1, sequenceSize]
     */
    public static PositionBlock of(int sid, int sequenceSize, int... oneBasedPositions) {
        if (sid < 1) {
            throw new IllegalArgumentException("SID must be >= 1, got: " + sid);
        }
        if (sequenceSize < 0) {
            throw new IllegalArgumentException("sequenceSize must be >= 0, got: " + sequenceSize);
        }
        return new PositionBlock(sid, sequenceSize,
                EncodedVector.fromPaperPositions(sequenceSize, oneBasedPositions));
    }

    /**
     * Creates an empty PositionBlock for a given SID (pattern has no occurrence in this SID).
     *
     * @param sid          1-based SID; >= 1
     * @param sequenceSize total number of itemsets in this sequence; >= 0
     * @return an immutable empty PositionBlock
     */
    public static PositionBlock empty(int sid, int sequenceSize) {
        if (sid < 1) {
            throw new IllegalArgumentException("SID must be >= 1, got: " + sid);
        }
        if (sequenceSize < 0) {
            throw new IllegalArgumentException("sequenceSize must be >= 0, got: " + sequenceSize);
        }
        return new PositionBlock(sid, sequenceSize,
                EncodedVector.fromPaperPositions(sequenceSize));
    }

    /**
     * Creates a PositionBlock from an already-constructed EncodedVector.
     *
     * @param sid          1-based SID; >= 1
     * @param sequenceSize must equal {@code vector.logicalLength()}
     * @param vector       the encoded position vector; not null
     */
    public static PositionBlock fromVector(int sid, int sequenceSize, EncodedVector vector) {
        return new PositionBlock(sid, sequenceSize, Objects.requireNonNull(vector));
    }

    /**
     * Creates a PositionBlock from explicit block products and the sequence size.
     * Useful when product values are directly known from paper examples.
     *
     * @param sid          1-based SID
     * @param sequenceSize total number of itemsets
     * @param blockProducts encoded block products in block order
     */
    public static PositionBlock fromProducts(int sid, int sequenceSize, int... blockProducts) {
        return new PositionBlock(sid, sequenceSize,
                EncodedVector.fromProducts(sequenceSize, blockProducts));
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the 1-based SID this position block is associated with.
     */
    public int sid() {
        return sid;
    }

    /**
     * Returns the total number of itemsets in the associated database sequence.
     */
    public int sequenceSize() {
        return sequenceSize;
    }

    /**
     * Returns the number of encoded blocks.
     */
    public int blockCount() {
        return vector.blockCount();
    }

    /**
     * Returns the encoded block product at the given 0-based block index.
     *
     * @param blockIndex 0-based in [0, blockCount())
     */
    public int blockProduct(int blockIndex) {
        return vector.blockProduct(blockIndex);
    }

    /**
     * Returns the rank at the given 0-based block index.
     *
     * @param blockIndex 0-based in [0, blockCount())
     */
    public int blockRank(int blockIndex) {
        return vector.blockRank(blockIndex);
    }

    /**
     * Returns {@code true} if the given 1-based itemset position is set (X ends there).
     *
     * <p><b>Indexing:</b> paper position {@code t} → bit index {@code t-1}.</p>
     *
     * @param position 1-based itemset position in [1, sequenceSize]
     */
    public boolean isSetAt(int position) {
        if (position < 1 || position > sequenceSize) {
            throw new IllegalArgumentException(
                    "Position " + position + " is outside [1, " + sequenceSize + "]");
        }
        return vector.isSet(position - 1); // convert 1-based → 0-based
    }

    /**
     * Returns {@code true} if this position block has no set positions.
     * A SID whose position block is empty is not a supporting SID for the pattern.
     * SOURCE-CONFIRMED: B12 Contract Section 7.
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
        if (!(other instanceof PositionBlock that)) return false;
        return sid == that.sid && sequenceSize == that.sequenceSize && vector.equals(that.vector);
    }

    @Override
    public int hashCode() {
        int h = 31 * sid + sequenceSize;
        return 31 * h + vector.hashCode();
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("PositionBlock{sid=").append(sid)
                .append(", seqSize=").append(sequenceSize)
                .append(", blocks=[");
        for (int i = 0; i < blockCount(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(blockProduct(i));
        }
        return sb.append("]}").toString();
    }
}
