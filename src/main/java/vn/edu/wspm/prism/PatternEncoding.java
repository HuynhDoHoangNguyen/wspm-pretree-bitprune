package vn.edu.wspm.prism;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Immutable full PRISM encoding {@code E(X) = (S_X, P_X)} for one pattern.
 *
 * <p>The position list contains exactly one {@link PositionBlock} for every SID, in ascending
 * SID order. Empty position vectors are retained. This full representation is source-faithful
 * and makes block alignment explicit; compact PRISM storage is outside B14.</p>
 *
 * <p>The constructor enforces the source invariant that a SID belongs to {@code S_X} if and only
 * if its corresponding position vector is non-empty. Consequently {@link #support()} can be
 * derived exclusively from the result sequence block as required by PRISM Theorem 3.1.</p>
 */
public final class PatternEncoding {

    private final SequenceBlock sequenceBlock;
    private final List<PositionBlock> positionBlocks;

    public PatternEncoding(
            SequenceBlock sequenceBlock, Collection<PositionBlock> positionBlocks) {
        this.sequenceBlock = Objects.requireNonNull(
                sequenceBlock, "sequenceBlock must not be null");
        Objects.requireNonNull(positionBlocks, "positionBlocks must not be null");
        this.positionBlocks = List.copyOf(positionBlocks);

        if (this.positionBlocks.size() != sequenceBlock.sidCount()) {
            throw new IllegalArgumentException(
                    "Full encoding requires one PositionBlock per SID: expected "
                            + sequenceBlock.sidCount() + ", got " + this.positionBlocks.size());
        }

        for (int index = 0; index < this.positionBlocks.size(); index++) {
            int expectedSid = index + 1;
            PositionBlock positions = Objects.requireNonNull(
                    this.positionBlocks.get(index), "positionBlocks must not contain null");
            if (positions.sid() != expectedSid) {
                throw new IllegalArgumentException(
                        "PositionBlock at index " + index + " must belong to SID "
                                + expectedSid + ", got SID " + positions.sid());
            }
            boolean sequenceMembership = sequenceBlock.isSupporting(expectedSid);
            if (sequenceMembership == positions.isEmpty()) {
                throw new IllegalArgumentException(
                        "SID membership and position-vector emptiness disagree for SID "
                                + expectedSid);
            }
        }
    }

    public SequenceBlock sequenceBlock() {
        return sequenceBlock;
    }

    public List<PositionBlock> positionBlocks() {
        return positionBlocks;
    }

    /** Returns the full position vector for a 1-based SID. */
    public PositionBlock positionBlock(int sid) {
        if (sid < 1 || sid > sequenceBlock.sidCount()) {
            throw new IllegalArgumentException(
                    "SID " + sid + " is outside [1, " + sequenceBlock.sidCount() + "]");
        }
        return positionBlocks.get(sid - 1);
    }

    /** SOURCE-CONFIRMED: support is factor-cardinality of the sequence block. */
    public int support() {
        return sequenceBlock.support();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PatternEncoding that
                && sequenceBlock.equals(that.sequenceBlock)
                && positionBlocks.equals(that.positionBlocks);
    }

    @Override
    public int hashCode() {
        return 31 * sequenceBlock.hashCode() + positionBlocks.hashCode();
    }

    @Override
    public String toString() {
        return "PatternEncoding{sequenceBlock=" + sequenceBlock
                + ", positionBlocks=" + positionBlocks + '}';
    }
}
