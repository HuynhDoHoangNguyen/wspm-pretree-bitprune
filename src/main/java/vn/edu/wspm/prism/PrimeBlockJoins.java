package vn.edu.wspm.prism;

import java.util.ArrayList;
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
        boolean[] mask = new boolean[positions.sequenceSize()];
        int firstSetIndex = -1;
        for (int index = 0; index < positions.sequenceSize(); index++) {
            if (positions.vector().isSet(index)) {
                firstSetIndex = index;
                break;
            }
        }
        if (firstSetIndex >= 0) {
            for (int index = firstSetIndex + 1; index < mask.length; index++) {
                mask[index] = true;
            }
        }
        return PositionBlock.fromVector(
                positions.sid(), positions.sequenceSize(), EncodedVector.fromBits(mask));
    }

    private static PatternEncoding positionalJoin(
            PatternEncoding left,
            PatternEncoding right,
            SequenceBlock candidates,
            boolean temporal) {
        int sidCount = candidates.sidCount();
        var resultPositions = new ArrayList<PositionBlock>(sidCount);
        var supportingSids = new int[candidates.support()];
        int supportIndex = 0;

        for (int sid = 1; sid <= sidCount; sid++) {
            PositionBlock leftPositions = left.positionBlock(sid);
            PositionBlock rightPositions = right.positionBlock(sid);
            PositionBlock joined;
            if (!candidates.isSupporting(sid)) {
                joined = PositionBlock.empty(sid, leftPositions.sequenceSize());
            } else {
                PositionBlock leftOperand = temporal ? strictAfter(leftPositions) : leftPositions;
                joined = intersectPositionBlocks(leftOperand, rightPositions);
                if (!joined.isEmpty()) {
                    supportingSids[supportIndex++] = sid;
                }
            }
            resultPositions.add(joined);
        }

        if (supportIndex != supportingSids.length) {
            supportingSids = java.util.Arrays.copyOf(supportingSids, supportIndex);
        }
        SequenceBlock resultSequenceBlock = SequenceBlock.of(sidCount, supportingSids);
        return new PatternEncoding(resultSequenceBlock, resultPositions);
    }

    private static SequenceBlock intersectSequenceBlocks(SequenceBlock left, SequenceBlock right) {
        int[] products = new int[left.blockCount()];
        for (int block = 0; block < products.length; block++) {
            products[block] = PrimalBlockCodec.gcdProduct(
                    left.blockProduct(block), right.blockProduct(block));
        }
        return SequenceBlock.fromVector(
                left.sidCount(), EncodedVector.fromProducts(left.sidCount(), products));
    }

    private static PositionBlock intersectPositionBlocks(
            PositionBlock left, PositionBlock right) {
        int[] products = new int[left.blockCount()];
        for (int block = 0; block < products.length; block++) {
            products[block] = PrimalBlockCodec.gcdProduct(
                    left.blockProduct(block), right.blockProduct(block));
        }
        return PositionBlock.fromProducts(left.sid(), left.sequenceSize(), products);
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
        for (int sid = 1; sid <= left.sequenceBlock().sidCount(); sid++) {
            int leftSize = left.positionBlock(sid).sequenceSize();
            int rightSize = right.positionBlock(sid).sequenceSize();
            if (leftSize != rightSize) {
                throw new IllegalArgumentException(
                        "Join operands use different position coordinate systems for SID "
                                + sid + ": " + leftSize + " != " + rightSize);
            }
        }
    }
}
