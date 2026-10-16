package vn.edu.wspm.prism;

import java.util.Arrays;
import java.util.Objects;

/** Exact PRISM Prime-block I-Join and S-Join operations used by WSPM_PreTree. */
public final class PrimeBlockJoins {

    private PrimeBlockJoins() {}

    /**
     * Performs the PRISM itemset join by retaining equal endpoint positions.
     *
     * <p>The caller remains responsible for the structural sibling/extension precondition. This
     * method operates only on the encodings and cannot determine pattern ancestry.</p>
     */
    public static PatternEncoding iJoin(PatternEncoding left, PatternEncoding right) {
        validateCompatible(left, right);
        SequenceBlock candidates = intersectSequenceBlocks(
                left.sequenceBlock(), right.sequenceBlock());
        return positionalJoin(left, right, candidates, false);
    }

    /**
     * Performs the PRISM sequence join by retaining right endpoints strictly after the earliest
     * left endpoint in each candidate SID.
     */
    public static PatternEncoding sJoin(PatternEncoding left, PatternEncoding right) {
        validateCompatible(left, right);
        SequenceBlock candidates = intersectSequenceBlocks(
                left.sequenceBlock(), right.sequenceBlock());
        return positionalJoin(left, right, candidates, true);
    }

    /**
     * Builds PRISM's strict-after mask for one complete per-SID position vector.
     *
     * <p>All valid positions after the first set position are set, including positions in later
     * blocks. Positions through the first endpoint and padding positions remain clear. An empty
     * input has no valid endpoint and therefore yields an empty mask.</p>
     */
    public static PositionBlock strictAfter(PositionBlock positions) {
        Objects.requireNonNull(positions, "positions must not be null");
        int[] mask = strictAfterRanks(positions);
        return PositionBlock.fromVector(
                positions.sid(), positions.sequenceSize(),
                EncodedVector.fromTrustedOwnedRanks(positions.sequenceSize(), mask));
    }

    private static PatternEncoding positionalJoin(
            PatternEncoding left,
            PatternEncoding right,
            SequenceBlock candidates,
            boolean temporal) {
        int sidCount = candidates.sidCount();
        int[] supportingRanks = new int[EncodedVector.blocksNeeded(sidCount)];
        int[] resultSids = new int[candidates.support()];
        int[] resultOffsets = new int[Math.addExact(resultSids.length, 1)];
        int maximumResultRanks = temporal
                ? right.compactPositionRankCount()
                : Math.min(left.compactPositionRankCount(), right.compactPositionRankCount());
        int[] resultBlockIndexes = new int[maximumResultRanks];
        int[] resultPositionRanks = new int[maximumResultRanks];
        int resultSupport = 0;
        int resultRankCount = 0;

        for (int sid = 1; sid <= sidCount; sid++) {
            if (!candidates.isSupporting(sid)) continue;
            int leftSupportIndex = left.supportIndexForSid(sid);
            int rightSupportIndex = right.supportIndexForSid(sid);
            if (leftSupportIndex < 0 || rightSupportIndex < 0) {
                throw new IllegalStateException(
                        "sequence-block candidate has no compact position segment for SID " + sid);
            }
            int before = resultRankCount;
            resultRankCount = temporal
                    ? appendTemporalIntersection(
                            left, leftSupportIndex, right, rightSupportIndex,
                            resultBlockIndexes, resultPositionRanks, resultRankCount)
                    : appendSameEndpointIntersection(
                            left, leftSupportIndex, right, rightSupportIndex,
                            resultBlockIndexes, resultPositionRanks, resultRankCount);
            if (resultRankCount == before) continue;

            resultSids[resultSupport] = sid;
            resultSupport++;
            resultOffsets[resultSupport] = resultRankCount;
            int sidIndex = sid - 1;
            supportingRanks[sidIndex / PrimalBlockCodec.BLOCK_WIDTH]
                    |= 1 << (sidIndex % PrimalBlockCodec.BLOCK_WIDTH);
        }

        SequenceBlock resultSequenceBlock = SequenceBlock.fromVector(
                sidCount, EncodedVector.fromTrustedOwnedRanks(sidCount, supportingRanks));
        return PatternEncoding.fromTrustedCompact(
                resultSequenceBlock,
                left.coordinates(),
                Arrays.copyOf(resultSids, resultSupport),
                Arrays.copyOf(resultOffsets, Math.addExact(resultSupport, 1)),
                Arrays.copyOf(resultBlockIndexes, resultRankCount),
                Arrays.copyOf(resultPositionRanks, resultRankCount));
    }

    private static int appendSameEndpointIntersection(
            PatternEncoding left,
            int leftSupportIndex,
            PatternEncoding right,
            int rightSupportIndex,
            int[] resultBlocks,
            int[] resultRanks,
            int resultSize) {
        int leftIndex = left.positionStart(leftSupportIndex);
        int leftEnd = left.positionEnd(leftSupportIndex);
        int rightIndex = right.positionStart(rightSupportIndex);
        int rightEnd = right.positionEnd(rightSupportIndex);
        while (leftIndex < leftEnd && rightIndex < rightEnd) {
            int leftBlock = left.positionBlockIndexAt(leftIndex);
            int rightBlock = right.positionBlockIndexAt(rightIndex);
            if (leftBlock < rightBlock) {
                leftIndex++;
            } else if (rightBlock < leftBlock) {
                rightIndex++;
            } else {
                int rank = PrimalBlockCodec.gcdRank(
                        left.positionRankAt(leftIndex), right.positionRankAt(rightIndex));
                if (rank != 0) {
                    resultBlocks[resultSize] = leftBlock;
                    resultRanks[resultSize] = rank;
                    resultSize = Math.addExact(resultSize, 1);
                }
                leftIndex++;
                rightIndex++;
            }
        }
        return resultSize;
    }

    private static int appendTemporalIntersection(
            PatternEncoding left,
            int leftSupportIndex,
            PatternEncoding right,
            int rightSupportIndex,
            int[] resultBlocks,
            int[] resultRanks,
            int resultSize) {
        int firstLeftIndex = left.positionStart(leftSupportIndex);
        int firstLeftBlock = left.positionBlockIndexAt(firstLeftIndex);
        int firstMask = PrimalBlockCodec.strictAfterMaskRank(
                left.positionRankAt(firstLeftIndex));
        for (int rightIndex = right.positionStart(rightSupportIndex);
                rightIndex < right.positionEnd(rightSupportIndex); rightIndex++) {
            int block = right.positionBlockIndexAt(rightIndex);
            if (block < firstLeftBlock) continue;
            int rank = right.positionRankAt(rightIndex);
            if (block == firstLeftBlock) {
                rank = PrimalBlockCodec.gcdRank(firstMask, rank);
            }
            if (rank != 0) {
                resultBlocks[resultSize] = block;
                resultRanks[resultSize] = rank;
                resultSize = Math.addExact(resultSize, 1);
            }
        }
        return resultSize;
    }

    private static SequenceBlock intersectSequenceBlocks(SequenceBlock left, SequenceBlock right) {
        int[] ranks = new int[left.blockCount()];
        for (int block = 0; block < ranks.length; block++) {
            ranks[block] = PrimalBlockCodec.gcdRank(
                    left.blockRank(block), right.blockRank(block));
        }
        return SequenceBlock.fromVector(
                left.sidCount(), EncodedVector.fromTrustedOwnedRanks(left.sidCount(), ranks));
    }

    private static int[] strictAfterRanks(PositionBlock positions) {
        int[] ranks = new int[positions.blockCount()];
        boolean endpointFound = false;
        for (int block = 0; block < ranks.length; block++) {
            if (endpointFound) {
                ranks[block] = EncodedVector.validRankMask(positions.sequenceSize(), block);
                continue;
            }
            int rank = positions.blockRank(block);
            if (rank != 0) {
                endpointFound = true;
                ranks[block] = PrimalBlockCodec.strictAfterMaskRank(rank)
                        & EncodedVector.validRankMask(positions.sequenceSize(), block);
            }
        }
        return ranks;
    }

    private static void validateCompatible(PatternEncoding left, PatternEncoding right) {
        Objects.requireNonNull(left, "left encoding must not be null");
        Objects.requireNonNull(right, "right encoding must not be null");
        if (left.sequenceBlock().sidCount() != right.sequenceBlock().sidCount()) {
            throw new IllegalArgumentException(
                    "Join operands must use the same SID universe: "
                            + left.sequenceBlock().sidCount() + " != "
                            + right.sequenceBlock().sidCount());
        }
        if (!left.coordinates().equals(right.coordinates())) {
            throw new IllegalArgumentException(
                    "Join operands use different position coordinate systems");
        }
    }
}
