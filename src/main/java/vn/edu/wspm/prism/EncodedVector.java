package vn.edu.wspm.prism;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable logical bit-vector represented as an ordered sequence of 8-bit encoded blocks.
 *
 * <h2>Concept — SOURCE-CONFIRMED (PRISM Sec. 2.2)</h2>
 * <p>PRISM encodes a logical binary vector {@code B} of arbitrary length by splitting it into
 * consecutive {@code g}-bit blocks {@code B0, B1, ...} and encoding each block with
 * {@code nu(Bi, G)} — the product of primes at set-bit positions. This class stores those
 * encoded products together with the original logical length so that padding zeros can be
 * distinguished from real positions.</p>
 *
 * <h2>Full representation — SOURCE-CONFIRMED (PRISM Sec. 3.2)</h2>
 * <p>The full encoding retains every block including trailing empty blocks. This is the canonical,
 * join-safe representation for B13. Compact forms that drop empty blocks require separate
 * alignment metadata (per the B12 contract, Section 15) and are deferred to later tasks.</p>
 *
 * <h2>Padding — SOURCE-CONFIRMED (PRISM Sec. 3.2)</h2>
 * <p>The logical vector is right-padded with zeros until its length is a multiple of
 * {@link PrimalBlockCodec#BLOCK_WIDTH}. Padding bits are always zero and can never become set
 * positions. The {@link #logicalLength} field excludes padding.</p>
 *
 * <h2>Indexing — IMPLEMENTATION POLICY</h2>
 * <p>The PRISM paper numbers SIDs and positions from 1 (1-based). This class uses 0-based
 * internal arrays. Factory methods document the conversion:
 * <pre>
 *   paper position p  →  internal index (p - 1)
 *   paper SID i       →  internal index (i - 1)
 * </pre>
 * Public factory methods that accept 1-based paper positions convert them explicitly.</p>
 */
public final class EncodedVector {

    /** Number of bits per block, equal to {@link PrimalBlockCodec#BLOCK_WIDTH}. */
    static final int BLOCK_WIDTH = PrimalBlockCodec.BLOCK_WIDTH;

    /** Encoded block products in block order; length = ceil(logicalLength / BLOCK_WIDTH). */
    private final int[] blockProducts;

    /**
     * Number of logical (non-padding) bit positions represented.
     * When 0, the vector has no positions and there are 0 blocks.
     */
    private final int logicalLength;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates an EncodedVector from an explicit array of block products and the logical length.
     *
     * @param blockProducts encoded block products in block order; must not be null
     * @param logicalLength the number of logical bit positions, excluding padding; must be ≥ 0
     * @throws IllegalArgumentException if arguments are inconsistent or blocks are invalid
     */
    private EncodedVector(int[] blockProducts, int logicalLength) {
        Objects.requireNonNull(blockProducts, "blockProducts must not be null");
        if (logicalLength < 0) {
            throw new IllegalArgumentException("logicalLength must be >= 0, got: " + logicalLength);
        }
        int expectedBlocks = blocksNeeded(logicalLength);
        if (blockProducts.length != expectedBlocks) {
            throw new IllegalArgumentException(
                    "logicalLength " + logicalLength + " requires " + expectedBlocks
                    + " blocks, got " + blockProducts.length);
        }
        for (int i = 0; i < blockProducts.length; i++) {
            PrimalBlockCodec.validateProduct(blockProducts[i]);
        }
        // Validate padding: bits beyond logicalLength in the last block must be zero.
        // The last block's padding is positions (logicalLength % BLOCK_WIDTH) through 7.
        if (blockProducts.length > 0) {
            int lastBlockIdx = blockProducts.length - 1;
            int usedBitsInLastBlock = (logicalLength == 0) ? 0
                    : ((logicalLength % BLOCK_WIDTH == 0) ? BLOCK_WIDTH : logicalLength % BLOCK_WIDTH);
            int lastRank = PrimalBlockCodec.productToRank(blockProducts[lastBlockIdx]);
            // Create a mask that covers only the valid positions in the last block
            int validMask = (usedBitsInLastBlock == BLOCK_WIDTH) ? 0xFF : ((1 << usedBitsInLastBlock) - 1);
            if ((lastRank & ~validMask) != 0) {
                throw new IllegalArgumentException(
                        "Last block has set bits in padding positions. "
                        + "logicalLength=" + logicalLength + ", lastRank=" + lastRank
                        + ", validMask=" + Integer.toBinaryString(validMask));
            }
        }
        this.blockProducts = Arrays.copyOf(blockProducts, blockProducts.length);
        this.logicalLength = logicalLength;
    }

    /**
     * Creates an empty EncodedVector with zero logical positions and zero blocks.
     *
     * @return the canonical empty vector
     */
    public static EncodedVector empty() {
        return new EncodedVector(new int[0], 0);
    }

    /**
     * Creates an EncodedVector by encoding a boolean bit array where {@code bits[i]} is true
     * when logical position {@code i+1} (1-based) is set.
     *
     * <p>The input uses 0-based indexing in Java; logical positions are 1-based in the paper.
     * The logical length equals {@code bits.length}.</p>
     *
     * @param bits the logical positions to encode; 0-based Java array; not null
     * @return an encoded vector with the same set-bit structure
     */
    public static EncodedVector fromBits(boolean[] bits) {
        Objects.requireNonNull(bits, "bits must not be null");
        int logLen = bits.length;
        int numBlocks = blocksNeeded(logLen);
        int[] products = new int[numBlocks];
        for (int blockIdx = 0; blockIdx < numBlocks; blockIdx++) {
            int rank = 0;
            for (int offset = 0; offset < BLOCK_WIDTH; offset++) {
                int bitIdx = blockIdx * BLOCK_WIDTH + offset; // 0-based
                if (bitIdx < logLen && bits[bitIdx]) {
                    rank |= (1 << offset);
                }
            }
            products[blockIdx] = PrimalBlockCodec.rankToProduct(rank);
        }
        return new EncodedVector(products, logLen);
    }

    /**
     * Creates an EncodedVector from a set of 1-based paper positions within a universe of
     * {@code logicalLength} positions.
     *
     * <p><b>Indexing contract:</b> paper positions are 1-based. This method converts them to
     * 0-based internal bit indexes by subtracting 1.</p>
     *
     * @param logicalLength total number of valid positions (≥ 0); 0 means no positions exist
     * @param oneBasedPositions 1-based paper positions that are set; must be in [1, logicalLength]
     * @return an encoded vector
     * @throws IllegalArgumentException if any position is outside [1, logicalLength]
     */
    public static EncodedVector fromPaperPositions(int logicalLength, int... oneBasedPositions) {
        if (logicalLength < 0) {
            throw new IllegalArgumentException("logicalLength must be >= 0, got: " + logicalLength);
        }
        boolean[] bits = new boolean[logicalLength];
        for (int pos : oneBasedPositions) {
            if (pos < 1 || pos > logicalLength) {
                throw new IllegalArgumentException(
                        "Paper position " + pos + " is outside [1, " + logicalLength + "]");
            }
            bits[pos - 1] = true; // convert 1-based → 0-based
        }
        return fromBits(bits);
    }

    /**
     * Creates an EncodedVector from explicit block products and logical length.
     * Used when product values are directly known (e.g. from paper examples).
     *
     * @param logicalLength the number of logical bit positions
     * @param blockProducts encoded products in block order
     * @return an encoded vector
     */
    public static EncodedVector fromProducts(int logicalLength, int... blockProducts) {
        return new EncodedVector(blockProducts, logicalLength);
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the number of logical (non-padding) bit positions.
     * Padding positions are always zero and do not represent real occurrences.
     */
    public int logicalLength() {
        return logicalLength;
    }

    /** Returns the number of encoded blocks. */
    public int blockCount() {
        return blockProducts.length;
    }

    /**
     * Returns the encoded block product at the given 0-based block index.
     *
     * @param blockIndex 0-based block index in [0, blockCount())
     * @return the prime product for that block
     */
    public int blockProduct(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= blockProducts.length) {
            throw new IndexOutOfBoundsException(
                    "Block index " + blockIndex + " out of range [0, " + blockProducts.length + ")");
        }
        return blockProducts[blockIndex];
    }

    /**
     * Returns the rank (unsigned 8-bit value in [0,255]) of the block at the given index.
     *
     * @param blockIndex 0-based block index
     */
    public int blockRank(int blockIndex) {
        return PrimalBlockCodec.productToRank(blockProduct(blockIndex));
    }

    /**
     * Returns {@code true} if the logical bit at 0-based Java index {@code bitIndex} is set.
     * Use {@link #isSetPaperPosition(int)} for 1-based paper positions.
     *
     * @param bitIndex 0-based; must be in [0, logicalLength)
     */
    public boolean isSet(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= logicalLength) {
            throw new IndexOutOfBoundsException(
                    "Bit index " + bitIndex + " out of range [0, " + logicalLength + ")");
        }
        int blockIdx = bitIndex / BLOCK_WIDTH;
        int offset   = bitIndex % BLOCK_WIDTH;
        return PrimalBlockCodec.isBitSet(blockRank(blockIdx), offset);
    }

    /**
     * Returns {@code true} if the 1-based paper position {@code position} is set.
     *
     * <p><b>Indexing contract:</b> paper position {@code p} maps to 0-based index {@code p-1}.</p>
     *
     * @param position 1-based position in [1, logicalLength]
     */
    public boolean isSetPaperPosition(int position) {
        if (position < 1 || position > logicalLength) {
            throw new IndexOutOfBoundsException(
                    "Paper position " + position + " out of range [1, " + logicalLength + "]");
        }
        return isSet(position - 1);
    }

    /**
     * Returns {@code true} if every block in this vector is empty (product = 1, rank = 0).
     * Equivalent to no positions being set.
     */
    public boolean isEmpty() {
        for (int product : blockProducts) {
            if (product != PrimalBlockCodec.EMPTY_PRODUCT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the total factor cardinality summed across all blocks.
     *
     * <p>When this vector is a sequence block ({@code S_X}), this equals the support of pattern X:
     * the number of supporting database sequences. SOURCE-CONFIRMED: PRISM Theorem 3.1.</p>
     *
     * @return non-negative integer
     */
    public int totalCardinality() {
        int total = 0;
        for (int product : blockProducts) {
            total += PrimalBlockCodec.factorCardinalityFromProduct(product);
        }
        return total;
    }

    /**
     * Decodes this vector back to a boolean bit array of length {@link #logicalLength()}.
     * Padding bits are excluded.
     *
     * @return 0-based boolean array; element at index {@code i} is true iff logical position
     *         {@code i+1} (1-based paper position) is set
     */
    public boolean[] toBits() {
        boolean[] bits = new boolean[logicalLength];
        for (int i = 0; i < logicalLength; i++) {
            bits[i] = isSet(i);
        }
        return bits;
    }

    /**
     * Returns a copy of the encoded block products array.
     *
     * @return array of length {@link #blockCount()}
     */
    public int[] blockProducts() {
        return Arrays.copyOf(blockProducts, blockProducts.length);
    }

    // -----------------------------------------------------------------------
    // equals / hashCode / toString
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EncodedVector that)) {
            return false;
        }
        return logicalLength == that.logicalLength && Arrays.equals(blockProducts, that.blockProducts);
    }

    @Override
    public int hashCode() {
        return 31 * logicalLength + Arrays.hashCode(blockProducts);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("EncodedVector{len=").append(logicalLength).append(", blocks=[");
        for (int i = 0; i < blockProducts.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(blockProducts[i]);
        }
        return sb.append("]}").toString();
    }

    // -----------------------------------------------------------------------
    // Internal utility
    // -----------------------------------------------------------------------

    static int blocksNeeded(int logicalLength) {
        if (logicalLength <= 0) {
            return 0;
        }
        return (logicalLength + BLOCK_WIDTH - 1) / BLOCK_WIDTH;
    }
}
