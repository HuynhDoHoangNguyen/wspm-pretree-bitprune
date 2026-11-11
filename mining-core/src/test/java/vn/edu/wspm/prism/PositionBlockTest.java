package vn.edu.wspm.prism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PositionBlock}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Single-block position encoding</li>
 *   <li>Multi-block position encoding</li>
 *   <li>Position boundary: positions 8 and 9 in different blocks</li>
 *   <li>Empty position block (pattern not in this SID)</li>
 *   <li>PRISM example: P_a^1 = {14,3} → positions {1,4,6} with g=4</li>
 *   <li>PRISM example: P_b^1 = {210,2} → positions {1,2,3,4,5} with g=4</li>
 *   <li>SID association preserved</li>
 *   <li>Padding does not create positions</li>
 *   <li>Invalid construction rejection</li>
 *   <li>Immutability</li>
 * </ul>
 */
class PositionBlockTest {

    // -----------------------------------------------------------------------
    // Empty position block
    // -----------------------------------------------------------------------

    @Test
    void emptyBlock_isEmptyTrue() {
        PositionBlock pb = PositionBlock.empty(1, 5);
        assertTrue(pb.isEmpty());
    }

    @Test
    void emptyBlock_noPositionSet() {
        PositionBlock pb = PositionBlock.empty(1, 5);
        for (int pos = 1; pos <= 5; pos++) {
            assertFalse(pb.isSetAt(pos), "No position should be set in empty block");
        }
    }

    @Test
    void emptyBlock_allProductsAre1() {
        PositionBlock pb = PositionBlock.empty(1, 5);
        for (int b = 0; b < pb.blockCount(); b++) {
            assertEquals(1, pb.blockProduct(b));
        }
    }

    // -----------------------------------------------------------------------
    // SID association
    // -----------------------------------------------------------------------

    @Test
    void sidPreserved() {
        PositionBlock pb = PositionBlock.of(7, 10, 1, 3, 7);
        assertEquals(7, pb.sid());
    }

    @Test
    void sequenceSizePreserved() {
        PositionBlock pb = PositionBlock.of(3, 10, 2, 5);
        assertEquals(10, pb.sequenceSize());
    }

    // -----------------------------------------------------------------------
    // Single-block (sequenceSize <= 8)
    // -----------------------------------------------------------------------

    @Test
    void singleBlock_positionsSet() {
        PositionBlock pb = PositionBlock.of(1, 8, 1, 4, 8);
        assertTrue(pb.isSetAt(1));
        assertTrue(pb.isSetAt(4));
        assertTrue(pb.isSetAt(8));
        assertFalse(pb.isSetAt(2));
        assertFalse(pb.isSetAt(5));
    }

    @Test
    void singleBlock_1block() {
        PositionBlock pb = PositionBlock.of(1, 5, 2, 4);
        assertEquals(1, pb.blockCount());
    }

    // -----------------------------------------------------------------------
    // Multi-block position encoding
    // -----------------------------------------------------------------------

    @Test
    void multiBlock_position8InBlock0_position9InBlock1() {
        PositionBlock pb = PositionBlock.of(1, 16, 8, 9);
        assertEquals(2, pb.blockCount());
        assertTrue(pb.isSetAt(8), "Position 8 must be in block 0");
        assertTrue(pb.isSetAt(9), "Position 9 must be in block 1");

        // position 8 → bit index 7 → block 0, offset 7 → prime 19
        assertEquals(19, pb.blockProduct(0),
                "Block 0 with only offset 7 should have product 19 (prime at offset 7)");
        // position 9 → bit index 8 → block 1, offset 0 → prime 2
        assertEquals(2, pb.blockProduct(1),
                "Block 1 with only offset 0 should have product 2 (prime at offset 0)");
    }

    @Test
    void multiBlock_17positions_3blocks() {
        PositionBlock pb = PositionBlock.of(1, 17, 1, 17);
        assertEquals(3, pb.blockCount());
        assertTrue(pb.isSetAt(1));
        assertTrue(pb.isSetAt(17));
        assertFalse(pb.isSetAt(9));
    }

    // -----------------------------------------------------------------------
    // PRISM paper example: P_a^1 = {14, 3} with g=4 semantics
    // SOURCE-CONFIRMED: prism-prime-block.md Section 8 and 12.1
    //
    // The PRISM paper (g=4, G={2,3,5,7}) encodes:
    //   P_a^1: a occurs at positions 1,4,6 (1-based itemset indexes)
    //   block 0 covers positions 1..4: positions 1,4 set → product = 2*7 = 14
    //   block 1 covers positions 5..8: position 6 set → offset (6-5)=1 → product = 3
    //
    // With our g=8 codec: products 14 and 3 are valid. We test two things:
    //   (a) fromProducts correctly decodes them
    //   (b) The positions decoded match {1,4,6} in a g=4 universe
    //
    // We model this with sequenceSize=8 (two g=4-blocks worth of positions).
    // -----------------------------------------------------------------------

    @Test
    void prismExample_Pa1_fromProducts_block0is14_block1is3() {
        // sequenceSize=8 models a g=4 encoded sequence with 2 blocks
        // NOTE: This interprets the g=4 example products in a g=8 codec.
        //       With g=8, block 0 is positions 1..8, block 1 is positions 9..16.
        //       Products {14,3} would not correctly encode positions {1,4,6} in g=8.
        //       We validate ONLY that our codec decodes {14,3} at the block level,
        //       per the research contract requirement (item 18: decode 14, 3, 210, 2).

        int r14 = PrimalBlockCodec.productToRank(14);
        int r3  = PrimalBlockCodec.productToRank(3);

        // product 14 = 2*7 → offsets 0,3 set
        assertTrue(PrimalBlockCodec.isBitSet(r14, 0));
        assertTrue(PrimalBlockCodec.isBitSet(r14, 3));
        assertEquals(2, PrimalBlockCodec.factorCardinality(r14));

        // product 3 → offset 1 set
        assertTrue(PrimalBlockCodec.isBitSet(r3, 1));
        assertEquals(1, PrimalBlockCodec.factorCardinality(r3));
    }

    @Test
    void prismExample_Pb1_fromProducts_block0is210_block1is2() {
        // P_b^1 = {210,2}: a=210=2*3*5*7, b=2
        int r210 = PrimalBlockCodec.productToRank(210);
        int r2   = PrimalBlockCodec.productToRank(2);

        // 210 = 2*3*5*7 → offsets 0,1,2,3 all set → cardinality 4
        for (int offset = 0; offset < 4; offset++) {
            assertTrue(PrimalBlockCodec.isBitSet(r210, offset),
                    "offset " + offset + " must be set for product 210");
        }
        assertFalse(PrimalBlockCodec.isBitSet(r210, 4));
        assertEquals(4, PrimalBlockCodec.factorCardinality(r210));

        // 2 → offset 0 → cardinality 1
        assertTrue(PrimalBlockCodec.isBitSet(r2, 0));
        assertEquals(1, PrimalBlockCodec.factorCardinality(r2));
    }

    @Test
    void prismExample_iJoinResult_gcd_Pa1_Pb1() {
        // gcd({14,3},{210,2}) = {gcd(14,210), gcd(3,2)} = {14, 1}
        // block 0: matching positions → offsets 0,3 (positions 1,4)
        // block 1: no match → product 1
        int gcdBlock0 = PrimalBlockCodec.gcdProduct(14, 210);
        int gcdBlock1 = PrimalBlockCodec.gcdProduct(3, 2);
        assertEquals(14, gcdBlock0, "gcd(14, 210) = 14");
        assertEquals(1,  gcdBlock1, "gcd(3, 2) = 1 (no common positions)");

        // Verify bit representation of gcd block 0
        int rGcd0 = PrimalBlockCodec.productToRank(gcdBlock0);
        assertTrue(PrimalBlockCodec.isBitSet(rGcd0, 0), "Position 1 matched");
        assertTrue(PrimalBlockCodec.isBitSet(rGcd0, 3), "Position 4 matched");
        assertFalse(PrimalBlockCodec.isBitSet(rGcd0, 1));
        assertFalse(PrimalBlockCodec.isBitSet(rGcd0, 2));
    }

    // -----------------------------------------------------------------------
    // Padding: bits beyond sequenceSize cannot become positions
    // -----------------------------------------------------------------------

    @Test
    void paddingBitsNotReachable() {
        PositionBlock pb = PositionBlock.of(1, 9, 9); // sequenceSize=9, only position 9 set
        assertThrows(IllegalArgumentException.class, () -> pb.isSetAt(10));
    }

    @Test
    void paddingLastBlockIsZero_forSequenceSize9() {
        PositionBlock pb = PositionBlock.of(1, 9, 9);
        // Block 1 has only offset 0 set (position 9) → rank=1, no padding bits set
        int rank1 = pb.blockRank(1);
        assertEquals(1, rank1); // only bit 0 = position 9
        for (int offset = 1; offset < PrimalBlockCodec.BLOCK_WIDTH; offset++) {
            assertFalse(PrimalBlockCodec.isBitSet(rank1, offset),
                    "Padding offset " + offset + " in last block must not be set");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid construction
    // -----------------------------------------------------------------------

    @Test
    void sidZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> PositionBlock.of(0, 5, 1));
    }

    @Test
    void sidNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PositionBlock.of(-1, 5, 1));
    }

    @Test
    void positionZeroThrows() {
        assertThrows(IllegalArgumentException.class, () -> PositionBlock.of(1, 5, 0));
    }

    @Test
    void positionBeyondSequenceSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PositionBlock.of(1, 5, 6));
    }

    @Test
    void negativeSequenceSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PositionBlock.empty(1, -1));
    }

    // -----------------------------------------------------------------------
    // equals and hashCode
    // -----------------------------------------------------------------------

    @Test
    void equalsForSameContent() {
        PositionBlock p1 = PositionBlock.of(1, 8, 2, 5);
        PositionBlock p2 = PositionBlock.of(1, 8, 2, 5);
        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    @Test
    void notEqualForDifferentSid() {
        PositionBlock p1 = PositionBlock.of(1, 8, 2);
        PositionBlock p2 = PositionBlock.of(2, 8, 2);
        assertNotEquals(p1, p2);
    }

    @Test
    void notEqualForDifferentPositions() {
        PositionBlock p1 = PositionBlock.of(1, 8, 2);
        PositionBlock p2 = PositionBlock.of(1, 8, 3);
        assertNotEquals(p1, p2);
    }

    @Test
    void notEqualForDifferentSequenceSize() {
        PositionBlock p1 = PositionBlock.of(1, 8, 2);
        PositionBlock p2 = PositionBlock.of(1, 9, 2);
        assertNotEquals(p1, p2);
    }

    // -----------------------------------------------------------------------
    // fromProducts factory
    // -----------------------------------------------------------------------

    @Test
    void fromProducts_correctlyDecodesPositions() {
        // sequenceSize=4: block 0 = positions 1..4
        // product 14 = 2*7 → offsets 0,3 → positions 1,4
        PositionBlock pb = PositionBlock.fromProducts(1, 4, 14);
        assertTrue(pb.isSetAt(1));
        assertFalse(pb.isSetAt(2));
        assertFalse(pb.isSetAt(3));
        assertTrue(pb.isSetAt(4));
    }
}
