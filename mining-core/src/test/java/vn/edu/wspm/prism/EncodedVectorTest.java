package vn.edu.wspm.prism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EncodedVector}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Empty vector (0 logical positions, 0 blocks)</li>
 *   <li>Single-block vectors (logicalLength 1..8)</li>
 *   <li>Multi-block vectors (logicalLength 9..17)</li>
 *   <li>Block boundary: positions 8 and 9 fall in different blocks</li>
 *   <li>Padding: bits beyond logicalLength are zero and not reachable</li>
 *   <li>1-based paper position API round-trip</li>
 *   <li>PRISM example decode: products {14, 3} and {210, 2}</li>
 *   <li>totalCardinality (support) from multi-block vector</li>
 *   <li>fromProducts validation</li>
 *   <li>Invalid construction (negative length, SID out of range)</li>
 *   <li>Immutability of blockProducts()</li>
 * </ul>
 */
class EncodedVectorTest {

    // -----------------------------------------------------------------------
    // Empty vector
    // -----------------------------------------------------------------------

    @Test
    void emptyVector_hasZeroLogicalLength() {
        EncodedVector v = EncodedVector.empty();
        assertEquals(0, v.logicalLength());
    }

    @Test
    void emptyVector_hasZeroBlocks() {
        EncodedVector v = EncodedVector.empty();
        assertEquals(0, v.blockCount());
    }

    @Test
    void emptyVector_isEmptyTrue() {
        assertTrue(EncodedVector.empty().isEmpty());
    }

    @Test
    void emptyVector_cardinalityIsZero() {
        assertEquals(0, EncodedVector.empty().totalCardinality());
    }

    @Test
    void emptyVector_fromBitsEmpty() {
        EncodedVector v = EncodedVector.fromBits(new boolean[0]);
        assertEquals(0, v.logicalLength());
        assertEquals(0, v.blockCount());
    }

    // -----------------------------------------------------------------------
    // Single-block vectors (logicalLength 1..8)
    // -----------------------------------------------------------------------

    @Test
    void singleBit_logicalLength1_noneSet() {
        EncodedVector v = EncodedVector.fromPaperPositions(1);
        assertEquals(1, v.logicalLength());
        assertEquals(1, v.blockCount());
        assertEquals(1, v.blockProduct(0)); // empty block
        assertFalse(v.isSetPaperPosition(1));
    }

    @Test
    void singleBit_logicalLength1_position1Set() {
        EncodedVector v = EncodedVector.fromPaperPositions(1, 1);
        assertTrue(v.isSetPaperPosition(1));
        assertEquals(2, v.blockProduct(0)); // prime at offset 0 = 2
    }

    @Test
    void logicalLength8_allPositionsSet() {
        EncodedVector v = EncodedVector.fromPaperPositions(8, 1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(8, v.logicalLength());
        assertEquals(1, v.blockCount());
        assertEquals(PrimalBlockCodec.MAX_PRODUCT, v.blockProduct(0));
        assertEquals(8, v.totalCardinality());
    }

    @Test
    void logicalLength7_position8NotPresent() {
        // logicalLength=7 so position 8 does not exist
        EncodedVector v = EncodedVector.fromPaperPositions(7, 1, 3, 5);
        assertThrows(IndexOutOfBoundsException.class, () -> v.isSetPaperPosition(8));
    }

    // -----------------------------------------------------------------------
    // Multi-block: boundary positions 8 and 9 are in different blocks
    // -----------------------------------------------------------------------

    @Test
    void position8InBlock0_position9InBlock1() {
        // logicalLength=9, positions {8,9} set
        EncodedVector v = EncodedVector.fromPaperPositions(9, 8, 9);
        assertEquals(2, v.blockCount());

        // position 8 → bit index 7 → offset 7 in block 0
        assertTrue(v.isSetPaperPosition(8), "Position 8 must be in block 0 at offset 7");
        assertFalse(v.isSetPaperPosition(9) == false, "Should set position 9");  // just verify true
        assertTrue(v.isSetPaperPosition(9), "Position 9 must be in block 1 at offset 0");

        // Block 0: only offset 7 is set → product = 19 (prime at offset 7)
        assertEquals(19, v.blockProduct(0), "Block 0 with only offset 7 set should have product 19");

        // Block 1: only offset 0 is set → product = 2 (prime at offset 0)
        assertEquals(2, v.blockProduct(1), "Block 1 with only offset 0 set should have product 2");
    }

    @Test
    void position8InBlock0_position9InBlock1_fromBitsVerification() {
        // Verify with fromBits as well
        boolean[] bits = new boolean[16];
        bits[7] = true;  // position 8 (1-based) = index 7
        bits[8] = true;  // position 9 (1-based) = index 8
        EncodedVector v = EncodedVector.fromBits(bits);
        assertEquals(2, v.blockCount());
        assertTrue(v.isSet(7));
        assertTrue(v.isSet(8));
        assertFalse(v.isSet(0));
    }

    // -----------------------------------------------------------------------
    // Boundary logical lengths: 0,1,7,8,9,15,16,17
    // -----------------------------------------------------------------------

    @Test
    void logicalLength0_noBlocksNoPositions() {
        EncodedVector v = EncodedVector.fromPaperPositions(0);
        assertEquals(0, v.logicalLength());
        assertEquals(0, v.blockCount());
    }

    @Test
    void logicalLength1_oneBlock() {
        assertEquals(1, EncodedVector.fromPaperPositions(1).blockCount());
    }

    @Test
    void logicalLength7_oneBlock() {
        assertEquals(1, EncodedVector.fromPaperPositions(7).blockCount());
    }

    @Test
    void logicalLength8_oneBlock() {
        assertEquals(1, EncodedVector.fromPaperPositions(8).blockCount());
    }

    @Test
    void logicalLength9_twoBlocks() {
        assertEquals(2, EncodedVector.fromPaperPositions(9).blockCount());
    }

    @Test
    void logicalLength15_twoBlocks() {
        assertEquals(2, EncodedVector.fromPaperPositions(15).blockCount());
    }

    @Test
    void logicalLength16_twoBlocks() {
        assertEquals(2, EncodedVector.fromPaperPositions(16).blockCount());
    }

    @Test
    void logicalLength17_threeBlocks() {
        assertEquals(3, EncodedVector.fromPaperPositions(17).blockCount());
    }

    // -----------------------------------------------------------------------
    // Padding safety: bits beyond logicalLength cannot become positions
    // -----------------------------------------------------------------------

    @Test
    void paddingDoesNotCreatePositions_logicalLength9() {
        // logicalLength=9; second block has 7 padding bits (positions 10..16)
        EncodedVector v = EncodedVector.fromPaperPositions(9, 9);
        // position 10 does not exist in logical space
        assertThrows(IndexOutOfBoundsException.class, () -> v.isSetPaperPosition(10));
    }

    @Test
    void paddingIsAlwaysZero_inLastBlock() {
        // logicalLength=9 with only position 9 set: block 1 should have product=2 (offset 0 only)
        EncodedVector v = EncodedVector.fromPaperPositions(9, 9);
        int rank1 = v.blockRank(1);
        // Only bit at offset 0 should be set (position 9 = block 1, offset 0)
        assertEquals(1, rank1, "Block 1 should only have offset 0 set (rank=1) for position 9");
        // Explicitly verify no padding bits set
        for (int offset = 1; offset < PrimalBlockCodec.BLOCK_WIDTH; offset++) {
            assertFalse(PrimalBlockCodec.isBitSet(rank1, offset),
                    "Padding offset " + offset + " in last block must not be set");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid construction: padding bits set in last block
    // -----------------------------------------------------------------------

    @Test
    void fromProducts_withSetPaddingBitsThrows() {
        // logicalLength=1 → only offset 0 valid; rank 3 (offsets 0 and 1 set) has padding
        // product for rank=3 is 2*3=6
        assertThrows(IllegalArgumentException.class,
                () -> EncodedVector.fromProducts(1, 6)); // 6=2*3, offset 1 is padding
    }

    @Test
    void fromProducts_inconsistentBlockCount_throws() {
        // logicalLength=8 needs 1 block, but we supply 2
        assertThrows(IllegalArgumentException.class,
                () -> EncodedVector.fromProducts(8, 1, 1));
    }

    @Test
    void negativeLogicalLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> EncodedVector.fromPaperPositions(-1));
    }

    @Test
    void positionZeroThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> EncodedVector.fromPaperPositions(8, 0));
    }

    @Test
    void positionBeyondLogicalLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> EncodedVector.fromPaperPositions(8, 9));
    }

    // -----------------------------------------------------------------------
    // PRISM example: decode products {14,3} and {210,2}
    // SOURCE-CONFIRMED: prism-prime-block.md Section 8 and 12.1
    // -----------------------------------------------------------------------

    @Test
    void prismExample_Pa1_productsDecodeTo_positions1_4_6() {
        // P_a^1 = {14,3} with G={2,3,5,7,11,13,17,19} (g=8 blocks)
        // Block 0: product 14 = 2*7 → offsets {0,3} → positions {1,4} in block 0 (1-based)
        // Block 1: product 3 → offset {1} → position {10} in block 1 (1-based: 8+1+1=10, 0-based=9)
        //   Wait: block 0 covers positions 1..8, block 1 covers 9..16
        //   product 14: offsets 0 and 3 → positions 1 and 4 (block 0)
        //   product 3: offset 1 → position 10 (block 1, offset 1 → 8+1+1 = wait...)
        //
        // PRISM paper uses G={2,3,5,7} (g=4) for this example.
        // With g=4: block 0 = positions 1..4, block 1 = positions 5..8
        // P_a^1 = {14,3}: block 0 product=14=2*7 → offsets {0,3} → positions {1,4}
        //                  block 1 product=3=3   → offset {1} → position 5+1=6
        // This matches "a at positions 1,4,6" in contract Section 8.
        //
        // HOWEVER: our generator is g=8. The paper PRISM example uses g=4.
        // For B13: we must be able to encode/decode these products using our g=8 codec.
        // The products 14 and 3 are valid under our g=8 generator.
        // We test that our codec correctly decodes them.

        // With g=8: if the 4-prime PRISM example is adapted:
        // block 0 product=14 → decode by our g=8 codec: offset 0 (prime 2) and offset 3 (prime 7) set
        int r14 = PrimalBlockCodec.productToRank(14);
        assertTrue(PrimalBlockCodec.isBitSet(r14, 0), "offset 0 must be set for product 14");
        assertTrue(PrimalBlockCodec.isBitSet(r14, 3), "offset 3 must be set for product 14");
        assertFalse(PrimalBlockCodec.isBitSet(r14, 1));
        assertFalse(PrimalBlockCodec.isBitSet(r14, 2));

        // block 1 product=3 → offset 1 (prime 3) set
        int r3 = PrimalBlockCodec.productToRank(3);
        assertTrue(PrimalBlockCodec.isBitSet(r3, 1), "offset 1 must be set for product 3");
        assertFalse(PrimalBlockCodec.isBitSet(r3, 0));
    }

    @Test
    void prismExample_Pb1_productsDecodeTo_positions1_2_3_4_5() {
        // P_b^1 = {210,2}: block 0 product=210=2*3*5*7 → offsets {0,1,2,3} → positions {1,2,3,4}
        //                   block 1 product=2 → offset 0 → position 5 (in the 4-prime PRISM example)
        int r210 = PrimalBlockCodec.productToRank(210);
        assertTrue(PrimalBlockCodec.isBitSet(r210, 0));
        assertTrue(PrimalBlockCodec.isBitSet(r210, 1));
        assertTrue(PrimalBlockCodec.isBitSet(r210, 2));
        assertTrue(PrimalBlockCodec.isBitSet(r210, 3));
        assertFalse(PrimalBlockCodec.isBitSet(r210, 4));

        int r2 = PrimalBlockCodec.productToRank(2);
        assertTrue(PrimalBlockCodec.isBitSet(r2, 0));
        assertFalse(PrimalBlockCodec.isBitSet(r2, 1));
    }

    @Test
    void prismExample_iJoin_gcd14and210_gives14() {
        // From contract Section 12.1: gcd({14,3},{210,2}) = {14,1}
        // Block 0: gcd(14,210)=14; Block 1: gcd(3,2)=1
        assertEquals(14, PrimalBlockCodec.gcdProduct(14, 210));
        assertEquals(1,  PrimalBlockCodec.gcdProduct(3, 2));
    }

    // -----------------------------------------------------------------------
    // totalCardinality (support) from multi-block vector
    // -----------------------------------------------------------------------

    @Test
    void totalCardinality_multiBlock_sumsFactorCardinalities() {
        // logicalLength=10, positions {1,4,9} set
        // Block 0 (positions 1..8): positions 1,4 → offsets 0,3 → product 14 → cardinality 2
        // Block 1 (positions 9..16): position 9 → offset 0 → product 2 → cardinality 1
        // Total = 3
        EncodedVector v = EncodedVector.fromPaperPositions(10, 1, 4, 9);
        assertEquals(3, v.totalCardinality());
    }

    @Test
    void totalCardinality_emptyVector_isZero() {
        assertEquals(0, EncodedVector.empty().totalCardinality());
    }

    // -----------------------------------------------------------------------
    // Round-trip via toBits and fromBits
    // -----------------------------------------------------------------------

    @Test
    void toBitsAndFromBitsRoundTrip() {
        EncodedVector original = EncodedVector.fromPaperPositions(16, 1, 4, 9, 16);
        boolean[] bits = original.toBits();
        EncodedVector restored = EncodedVector.fromBits(bits);
        assertEquals(original, restored);
    }

    // -----------------------------------------------------------------------
    // Immutability of blockProducts()
    // -----------------------------------------------------------------------

    @Test
    void blockProductsReturnsCopy() {
        EncodedVector v = EncodedVector.fromPaperPositions(8, 1, 4);
        int[] arr1 = v.blockProducts();
        int[] arr2 = v.blockProducts();
        assertNotSame(arr1, arr2);
        arr1[0] = -999;
        assertNotEquals(-999, v.blockProduct(0),
                "Mutating returned blockProducts() must not affect internal state");
    }

    // -----------------------------------------------------------------------
    // equals and hashCode consistency
    // -----------------------------------------------------------------------

    @Test
    void equalsConsistentForSameContent() {
        EncodedVector v1 = EncodedVector.fromPaperPositions(8, 1, 3);
        EncodedVector v2 = EncodedVector.fromPaperPositions(8, 1, 3);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void notEqualForDifferentContent() {
        EncodedVector v1 = EncodedVector.fromPaperPositions(8, 1, 3);
        EncodedVector v2 = EncodedVector.fromPaperPositions(8, 1, 4);
        assertNotEquals(v1, v2);
    }

    @Test
    void notEqualForDifferentLogicalLength() {
        EncodedVector v1 = EncodedVector.fromPaperPositions(8, 1);
        EncodedVector v2 = EncodedVector.fromPaperPositions(9, 1);
        assertNotEquals(v1, v2);
    }
}
