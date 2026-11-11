package vn.edu.wspm.prism;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * Immutable compact PRISM encoding {@code E(X) = (S_X, P_X)} for one pattern.
 *
 * <p>Only supporting SIDs and non-empty position ranks are retained. Absolute block indexes keep
 * interior empty blocks unambiguous. Four parallel primitive arrays encode the compact positions:
 * supporting SIDs, per-SID segment offsets, absolute block indexes, and non-zero ranks. The
 * coordinate system is immutable and may be shared by every pattern from one database.</p>
 */
public final class PatternEncoding {

    private final SequenceBlock sequenceBlock;
    private final PositionCoordinateSystem coordinates;
    private final int[] supportingSids;
    private final int[] positionOffsets;
    private final int[] positionBlockIndexes;
    private final int[] positionRanks;

    /** Compatibility constructor that compacts full per-SID position objects at the API boundary. */
    public PatternEncoding(
            SequenceBlock sequenceBlock, Collection<PositionBlock> positionBlocks) {
        this(sequenceBlock, pack(sequenceBlock, positionBlocks));
    }

    private PatternEncoding(SequenceBlock sequenceBlock, PackedPositions packed) {
        this(sequenceBlock, packed.coordinates(), packed.supportingSids(), packed.positionOffsets(),
                packed.positionBlockIndexes(), packed.positionRanks(), true, false);
    }

    /**
     * Builds a compact encoding from public/untrusted arrays. Every array is validated and copied.
     */
    public static PatternEncoding fromCompact(
            SequenceBlock sequenceBlock,
            PositionCoordinateSystem coordinates,
            int[] supportingSids,
            int[] positionOffsets,
            int[] positionBlockIndexes,
            int[] positionRanks) {
        return new PatternEncoding(
                sequenceBlock, coordinates, supportingSids, positionOffsets,
                positionBlockIndexes, positionRanks, true, true);
    }

    /** Ownership-transfer boundary for fresh arrays built entirely by the join implementation. */
    static PatternEncoding fromTrustedCompact(
            SequenceBlock sequenceBlock,
            PositionCoordinateSystem coordinates,
            int[] supportingSids,
            int[] positionOffsets,
            int[] positionBlockIndexes,
            int[] positionRanks) {
        return new PatternEncoding(
                sequenceBlock, coordinates, supportingSids, positionOffsets,
                positionBlockIndexes, positionRanks, false, false);
    }

    private PatternEncoding(
            SequenceBlock sequenceBlock,
            PositionCoordinateSystem coordinates,
            int[] supportingSids,
            int[] positionOffsets,
            int[] positionBlockIndexes,
            int[] positionRanks,
            boolean validate,
            boolean copyArrays) {
        this.sequenceBlock = Objects.requireNonNull(
                sequenceBlock, "sequenceBlock must not be null");
        this.coordinates = Objects.requireNonNull(
                coordinates, "coordinates must not be null");
        Objects.requireNonNull(supportingSids, "supportingSids must not be null");
        Objects.requireNonNull(positionOffsets, "positionOffsets must not be null");
        Objects.requireNonNull(positionBlockIndexes, "positionBlockIndexes must not be null");
        Objects.requireNonNull(positionRanks, "positionRanks must not be null");
        if (validate) {
            validateCompact(
                    sequenceBlock, coordinates, supportingSids, positionOffsets,
                    positionBlockIndexes, positionRanks);
        }
        this.supportingSids = copyArrays
                ? Arrays.copyOf(supportingSids, supportingSids.length) : supportingSids;
        this.positionOffsets = copyArrays
                ? Arrays.copyOf(positionOffsets, positionOffsets.length) : positionOffsets;
        this.positionBlockIndexes = copyArrays
                ? Arrays.copyOf(positionBlockIndexes, positionBlockIndexes.length)
                : positionBlockIndexes;
        this.positionRanks = copyArrays
                ? Arrays.copyOf(positionRanks, positionRanks.length) : positionRanks;
    }

    public SequenceBlock sequenceBlock() {
        return sequenceBlock;
    }

    public PositionCoordinateSystem coordinates() {
        return coordinates;
    }

    /**
     * Read-only lazy compatibility view. It creates one PositionBlock only when an element is
     * requested and never stores per-SID objects inside this encoding.
     */
    public List<PositionBlock> positionBlocks() {
        return new PositionBlockView(this);
    }

    /** Materializes one full logical position vector at the explicit compatibility boundary. */
    public PositionBlock positionBlock(int sid) {
        int sequenceSize = coordinates.sequenceSize(sid);
        int[] ranks = new int[EncodedVector.blocksNeeded(sequenceSize)];
        int supportIndex = Arrays.binarySearch(supportingSids, sid);
        if (supportIndex >= 0) {
            for (int index = positionOffsets[supportIndex];
                    index < positionOffsets[supportIndex + 1]; index++) {
                ranks[positionBlockIndexes[index]] = positionRanks[index];
            }
        }
        return PositionBlock.fromVector(
                sid, sequenceSize, EncodedVector.fromTrustedOwnedRanks(sequenceSize, ranks));
    }

    public int support() {
        return sequenceBlock.support();
    }

    public int compactSupportingSidCount() {
        return supportingSids.length;
    }

    public int compactPositionRankCount() {
        return positionRanks.length;
    }

    public int[] supportingSids() {
        return Arrays.copyOf(supportingSids, supportingSids.length);
    }

    public int[] positionOffsets() {
        return Arrays.copyOf(positionOffsets, positionOffsets.length);
    }

    public int[] positionBlockIndexes() {
        return Arrays.copyOf(positionBlockIndexes, positionBlockIndexes.length);
    }

    public int[] positionRanks() {
        return Arrays.copyOf(positionRanks, positionRanks.length);
    }

    int supportingSidAt(int supportIndex) {
        return supportingSids[supportIndex];
    }

    int positionStart(int supportIndex) {
        return positionOffsets[supportIndex];
    }

    int positionEnd(int supportIndex) {
        return positionOffsets[supportIndex + 1];
    }

    int positionBlockIndexAt(int positionIndex) {
        return positionBlockIndexes[positionIndex];
    }

    int positionRankAt(int positionIndex) {
        return positionRanks[positionIndex];
    }

    int supportIndexForSid(int sid) {
        return Arrays.binarySearch(supportingSids, sid);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof PatternEncoding that
                && sequenceBlock.equals(that.sequenceBlock)
                && coordinates.equals(that.coordinates)
                && Arrays.equals(supportingSids, that.supportingSids)
                && Arrays.equals(positionOffsets, that.positionOffsets)
                && Arrays.equals(positionBlockIndexes, that.positionBlockIndexes)
                && Arrays.equals(positionRanks, that.positionRanks);
    }

    @Override
    public int hashCode() {
        int result = 31 * sequenceBlock.hashCode() + coordinates.hashCode();
        result = 31 * result + Arrays.hashCode(supportingSids);
        result = 31 * result + Arrays.hashCode(positionOffsets);
        result = 31 * result + Arrays.hashCode(positionBlockIndexes);
        return 31 * result + Arrays.hashCode(positionRanks);
    }

    @Override
    public String toString() {
        return "PatternEncoding{sequenceBlock=" + sequenceBlock
                + ", supportingSids=" + Arrays.toString(supportingSids)
                + ", nonEmptyPositionRanks=" + positionRanks.length + '}';
    }

    private static PackedPositions pack(
            SequenceBlock sequenceBlock, Collection<PositionBlock> positionBlocks) {
        Objects.requireNonNull(sequenceBlock, "sequenceBlock must not be null");
        Objects.requireNonNull(positionBlocks, "positionBlocks must not be null");
        List<PositionBlock> positions = List.copyOf(positionBlocks);
        int sidCount = sequenceBlock.sidCount();
        if (positions.size() != sidCount) {
            throw new IllegalArgumentException(
                    "Full encoding requires one PositionBlock per SID: expected "
                            + sidCount + ", got " + positions.size());
        }

        int[] sequenceSizes = new int[sidCount];
        int supportCount = 0;
        int rankCount = 0;
        for (int index = 0; index < sidCount; index++) {
            PositionBlock block = Objects.requireNonNull(
                    positions.get(index), "positionBlocks must not contain null");
            int sid = index + 1;
            if (block.sid() != sid) {
                throw new IllegalArgumentException(
                        "PositionBlock at index " + index + " must belong to SID "
                                + sid + ", got SID " + block.sid());
            }
            sequenceSizes[index] = block.sequenceSize();
            boolean empty = block.isEmpty();
            if (sequenceBlock.isSupporting(sid) == empty) {
                throw new IllegalArgumentException(
                        "SID membership and position-vector emptiness disagree for SID " + sid);
            }
            if (!empty) {
                supportCount = Math.addExact(supportCount, 1);
                for (int blockIndex = 0; blockIndex < block.blockCount(); blockIndex++) {
                    if (block.blockRank(blockIndex) != 0) {
                        rankCount = Math.addExact(rankCount, 1);
                    }
                }
            }
        }

        int[] sids = new int[supportCount];
        int[] offsets = new int[Math.addExact(supportCount, 1)];
        int[] blockIndexes = new int[rankCount];
        int[] ranks = new int[rankCount];
        int supportIndex = 0;
        int rankIndex = 0;
        for (PositionBlock block : positions) {
            if (block.isEmpty()) continue;
            sids[supportIndex] = block.sid();
            offsets[supportIndex] = rankIndex;
            for (int blockIndex = 0; blockIndex < block.blockCount(); blockIndex++) {
                int rank = block.blockRank(blockIndex);
                if (rank != 0) {
                    blockIndexes[rankIndex] = blockIndex;
                    ranks[rankIndex] = rank;
                    rankIndex++;
                }
            }
            supportIndex++;
        }
        offsets[supportCount] = rankIndex;
        return new PackedPositions(
                PositionCoordinateSystem.fromTrustedOwnedSizes(sequenceSizes),
                sids, offsets, blockIndexes, ranks);
    }

    private static void validateCompact(
            SequenceBlock sequenceBlock,
            PositionCoordinateSystem coordinates,
            int[] sids,
            int[] offsets,
            int[] blockIndexes,
            int[] ranks) {
        if (coordinates.sidCount() != sequenceBlock.sidCount()) {
            throw new IllegalArgumentException(
                    "coordinate SID count must equal sequence-block SID count");
        }
        if (offsets.length != Math.addExact(sids.length, 1)
                || offsets[0] != 0
                || offsets[offsets.length - 1] != ranks.length
                || blockIndexes.length != ranks.length) {
            throw new IllegalArgumentException("compact position array lengths/offsets disagree");
        }
        int priorSid = 0;
        for (int supportIndex = 0; supportIndex < sids.length; supportIndex++) {
            int sid = sids[supportIndex];
            if (sid <= priorSid || sid > sequenceBlock.sidCount()) {
                throw new IllegalArgumentException(
                        "supporting SIDs must be strictly increasing and in range: " + sid);
            }
            if (!sequenceBlock.isSupporting(sid)) {
                throw new IllegalArgumentException(
                        "compact positions contain non-supporting SID " + sid);
            }
            if (offsets[supportIndex] >= offsets[supportIndex + 1]) {
                throw new IllegalArgumentException(
                        "supporting SID must have at least one position rank: " + sid);
            }
            int maximumBlock = EncodedVector.blocksNeeded(coordinates.sequenceSize(sid));
            int priorBlock = -1;
            for (int index = offsets[supportIndex]; index < offsets[supportIndex + 1]; index++) {
                int block = blockIndexes[index];
                int rank = ranks[index];
                if (block <= priorBlock || block >= maximumBlock) {
                    throw new IllegalArgumentException(
                            "position block indexes must be strictly increasing and in range");
                }
                PrimalBlockCodec.validateRank(rank);
                if (rank == 0) {
                    throw new IllegalArgumentException("compact position ranks must be non-zero");
                }
                if ((rank & ~EncodedVector.validRankMask(
                        coordinates.sequenceSize(sid), block)) != 0) {
                    throw new IllegalArgumentException("position rank sets a padding bit");
                }
                priorBlock = block;
            }
            priorSid = sid;
        }
        if (sequenceBlock.support() != sids.length) {
            throw new IllegalArgumentException(
                    "sequence-block support must equal compact supporting SID count");
        }
    }

    private record PackedPositions(
            PositionCoordinateSystem coordinates,
            int[] supportingSids,
            int[] positionOffsets,
            int[] positionBlockIndexes,
            int[] positionRanks) {}

    private static final class PositionBlockView extends AbstractList<PositionBlock>
            implements RandomAccess {
        private final PatternEncoding encoding;

        private PositionBlockView(PatternEncoding encoding) {
            this.encoding = encoding;
        }

        @Override
        public PositionBlock get(int index) {
            if (index < 0 || index >= size()) {
                throw new IndexOutOfBoundsException(index);
            }
            return encoding.positionBlock(index + 1);
        }

        @Override
        public int size() {
            return encoding.sequenceBlock.sidCount();
        }
    }
}
