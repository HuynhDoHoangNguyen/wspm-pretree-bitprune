package vn.edu.wspm.prism;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable full PRISM vector split into source-defined eight-position blocks.
 *
 * <p>PRISM Sec. 3.4 defines a bijection between each prime product and a rank in
 * {@code [0,255]}, plus GCD/CARD/MASK lookups over those ranks. B22.1 stores the equivalent ranks
 * internally so joins do not repeatedly factor products. Public product APIs remain exact views
 * through {@link PrimalBlockCodec#rankToProduct(int)}. This is an implementation policy; it does
 * not introduce Bitmap/BitSet mining or PRISM's compact position-block layout.</p>
 *
 * <p>All logical blocks, including empty and trailing blocks, remain present. The logical length
 * excludes right-padding, whose bits are required to remain zero.</p>
 */
public final class EncodedVector {

    static final int BLOCK_WIDTH = PrimalBlockCodec.BLOCK_WIDTH;

    private final int[] blockRanks;
    private final int logicalLength;

    private EncodedVector(int[] blockRanks, int logicalLength, boolean trustedOwnedRanks) {
        Objects.requireNonNull(blockRanks, "blockRanks must not be null");
        if (logicalLength < 0) {
            throw new IllegalArgumentException("logicalLength must be >= 0, got: " + logicalLength);
        }
        int expectedBlocks = blocksNeeded(logicalLength);
        if (blockRanks.length != expectedBlocks) {
            throw new IllegalArgumentException(
                    "logicalLength " + logicalLength + " requires " + expectedBlocks
                            + " blocks, got " + blockRanks.length);
        }
        if (!trustedOwnedRanks) {
            for (int rank : blockRanks) {
                PrimalBlockCodec.validateRank(rank);
            }
        }
        if (blockRanks.length > 0) {
            int lastBlock = blockRanks.length - 1;
            int validMask = validRankMask(logicalLength, lastBlock);
            if ((blockRanks[lastBlock] & ~validMask) != 0) {
                throw new IllegalArgumentException(
                        "Last block has set bits in padding positions. logicalLength="
                                + logicalLength + ", lastRank=" + blockRanks[lastBlock]
                                + ", validMask=" + Integer.toBinaryString(validMask));
            }
        }
        this.blockRanks = trustedOwnedRanks
                ? blockRanks
                : Arrays.copyOf(blockRanks, blockRanks.length);
        this.logicalLength = logicalLength;
    }

    /**
     * Package-private ownership transfer for a fresh array produced by trusted codec operations.
     * The caller must never retain or mutate the array after this call.
     */
    static EncodedVector fromTrustedOwnedRanks(int logicalLength, int[] blockRanks) {
        return new EncodedVector(blockRanks, logicalLength, true);
    }

    public static EncodedVector empty() {
        return fromTrustedOwnedRanks(0, new int[0]);
    }

    public static EncodedVector fromBits(boolean[] bits) {
        Objects.requireNonNull(bits, "bits must not be null");
        int[] ranks = new int[blocksNeeded(bits.length)];
        for (int bitIndex = 0; bitIndex < bits.length; bitIndex++) {
            if (bits[bitIndex]) {
                ranks[bitIndex / BLOCK_WIDTH] |= 1 << (bitIndex % BLOCK_WIDTH);
            }
        }
        return fromTrustedOwnedRanks(bits.length, ranks);
    }

    /** Accepts 1-based paper positions and validates the public/untrusted boundary. */
    public static EncodedVector fromPaperPositions(
            int logicalLength, int... oneBasedPositions) {
        if (logicalLength < 0) {
            throw new IllegalArgumentException("logicalLength must be >= 0, got: " + logicalLength);
        }
        Objects.requireNonNull(oneBasedPositions, "oneBasedPositions must not be null");
        int[] ranks = new int[blocksNeeded(logicalLength)];
        for (int position : oneBasedPositions) {
            if (position < 1 || position > logicalLength) {
                throw new IllegalArgumentException(
                        "Paper position " + position + " is outside [1, " + logicalLength + "]");
            }
            int bitIndex = position - 1;
            ranks[bitIndex / BLOCK_WIDTH] |= 1 << (bitIndex % BLOCK_WIDTH);
        }
        return fromTrustedOwnedRanks(logicalLength, ranks);
    }

    /** Accepts source-format prime products and validates every public/untrusted value. */
    public static EncodedVector fromProducts(int logicalLength, int... blockProducts) {
        Objects.requireNonNull(blockProducts, "blockProducts must not be null");
        int[] ranks = new int[blockProducts.length];
        for (int index = 0; index < blockProducts.length; index++) {
            ranks[index] = PrimalBlockCodec.productToRank(blockProducts[index]);
        }
        return new EncodedVector(ranks, logicalLength, true);
    }

    public int logicalLength() {
        return logicalLength;
    }

    public int blockCount() {
        return blockRanks.length;
    }

    public int blockProduct(int blockIndex) {
        return PrimalBlockCodec.rankToProduct(blockRank(blockIndex));
    }

    public int blockRank(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= blockRanks.length) {
            throw new IndexOutOfBoundsException(
                    "Block index " + blockIndex + " out of range [0, " + blockRanks.length + ")");
        }
        return blockRanks[blockIndex];
    }

    public boolean isSet(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= logicalLength) {
            throw new IndexOutOfBoundsException(
                    "Bit index " + bitIndex + " out of range [0, " + logicalLength + ")");
        }
        return (blockRanks[bitIndex / BLOCK_WIDTH] & (1 << (bitIndex % BLOCK_WIDTH))) != 0;
    }

    public boolean isSetPaperPosition(int position) {
        if (position < 1 || position > logicalLength) {
            throw new IndexOutOfBoundsException(
                    "Paper position " + position + " out of range [1, " + logicalLength + "]");
        }
        return isSet(position - 1);
    }

    public boolean isEmpty() {
        for (int rank : blockRanks) {
            if (rank != 0) {
                return false;
            }
        }
        return true;
    }

    /** SOURCE-CONFIRMED: PRISM Theorem 3.1 and Sec. 3.4 CARD lookup. */
    public int totalCardinality() {
        int total = 0;
        for (int rank : blockRanks) {
            total += PrimalBlockCodec.factorCardinality(rank);
        }
        return total;
    }

    public boolean[] toBits() {
        boolean[] bits = new boolean[logicalLength];
        for (int block = 0; block < blockRanks.length; block++) {
            int base = block * BLOCK_WIDTH;
            for (int offset = 0; offset < BLOCK_WIDTH && base + offset < logicalLength; offset++) {
                bits[base + offset] = (blockRanks[block] & (1 << offset)) != 0;
            }
        }
        return bits;
    }

    public int[] blockProducts() {
        int[] products = new int[blockRanks.length];
        for (int index = 0; index < blockRanks.length; index++) {
            products[index] = PrimalBlockCodec.rankToProduct(blockRanks[index]);
        }
        return products;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EncodedVector that
                && logicalLength == that.logicalLength
                && Arrays.equals(blockRanks, that.blockRanks);
    }

    @Override
    public int hashCode() {
        return 31 * logicalLength + Arrays.hashCode(blockRanks);
    }

    @Override
    public String toString() {
        var result = new StringBuilder("EncodedVector{len=")
                .append(logicalLength).append(", blocks=[");
        for (int index = 0; index < blockRanks.length; index++) {
            if (index > 0) result.append(", ");
            result.append(PrimalBlockCodec.rankToProduct(blockRanks[index]));
        }
        return result.append("]}").toString();
    }

    static int blocksNeeded(int logicalLength) {
        return logicalLength <= 0 ? 0 : (logicalLength + BLOCK_WIDTH - 1) / BLOCK_WIDTH;
    }

    /** Returns a rank with exactly this block's valid, non-padding positions set. */
    static int validRankMask(int logicalLength, int blockIndex) {
        int blockCount = blocksNeeded(logicalLength);
        if (blockIndex < 0 || blockIndex >= blockCount) {
            throw new IndexOutOfBoundsException(
                    "Block index " + blockIndex + " out of range [0, " + blockCount + ")");
        }
        if (blockIndex < blockCount - 1 || logicalLength % BLOCK_WIDTH == 0) {
            return 0xFF;
        }
        return (1 << (logicalLength % BLOCK_WIDTH)) - 1;
    }
}
