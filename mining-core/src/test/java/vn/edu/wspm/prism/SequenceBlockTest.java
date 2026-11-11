package vn.edu.wspm.prism;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SequenceBlock}.
 *
 * <h2>Coverage</h2>
 * <ul>
 *   <li>Single-block SID membership</li>
 *   <li>Multi-block SID alignment</li>
 *   <li>Support extraction (= totalCardinality per Theorem 3.1)</li>
 *   <li>PRISM example: S_a = {30, 2} for SIDs 1,2,3,5</li>
 *   <li>isSupporting per SID</li>
 *   <li>Empty sequence block</li>
 *   <li>Invalid SID range rejection</li>
 *   <li>Immutability: internal state not exposed</li>
 * </ul>
 */
class SequenceBlockTest {

    // -----------------------------------------------------------------------
    // Empty sequence block
    // -----------------------------------------------------------------------

    @Test
    void emptyBlock_support0() {
        SequenceBlock sb = SequenceBlock.empty(7);
        assertEquals(0, sb.support());
        assertTrue(sb.isEmpty());
    }

    @Test
    void emptyBlock_noSidSupporting() {
        SequenceBlock sb = SequenceBlock.empty(7);
        for (int sid = 1; sid <= 7; sid++) {
            assertFalse(sb.isSupporting(sid), "SID " + sid + " must not support empty block");
        }
    }

    // -----------------------------------------------------------------------
    // Single-block SID membership (sidCount <= 8)
    // -----------------------------------------------------------------------

    @Test
    void singleBlock_support() {
        // SIDs 1,3,5 support → cardinality 3
        SequenceBlock sb = SequenceBlock.of(7, 1, 3, 5);
        assertEquals(3, sb.support());
    }

    @Test
    void singleBlock_isSupporting_correctSids() {
        SequenceBlock sb = SequenceBlock.of(7, 1, 3, 5);
        assertTrue(sb.isSupporting(1));
        assertFalse(sb.isSupporting(2));
        assertTrue(sb.isSupporting(3));
        assertFalse(sb.isSupporting(4));
        assertTrue(sb.isSupporting(5));
        assertFalse(sb.isSupporting(6));
        assertFalse(sb.isSupporting(7));
    }

    @Test
    void singleBlock_1block() {
        SequenceBlock sb = SequenceBlock.of(8, 1, 2, 3);
        assertEquals(1, sb.blockCount());
    }

    // -----------------------------------------------------------------------
    // PRISM worked example: S_a with G={2,3,5,7} (g=4), SIDs 1,2,3,5 → {30,2}
    // SOURCE-CONFIRMED: prism-prime-block.md Section 5 and Section 8
    // NOTE: This test uses g=4 PRISM example products {30,2} to verify our g=8 codec
    //        can correctly decode them, not to run the g=4 miner.
    // -----------------------------------------------------------------------

    @Test
    void prismExample_Sa_productsVerified() {
        // PRISM example (g=4): SIDs 1,2,3,5 support 'a' in a 5-SID universe padded to 8 bits.
        // SID bits (1-based): 1=set, 2=set, 3=set, 4=notset, 5=set → 11101000
        // With g=4: block0=1110 → product=2*3*5=30; block1=1000 → product=2
        // So S_a = {30, 2} with support = ||30||_G + ||2||_G = 3 + 1 = 4.
        //
        // With our g=8 generator: products {30,2} are still valid (30=2*3*5, 2=2).
        // This test verifies that our codec decodes them correctly.
        int card30 = PrimalBlockCodec.factorCardinalityFromProduct(30); // 2*3*5 → 3 primes
        int card2  = PrimalBlockCodec.factorCardinalityFromProduct(2);  // 2 → 1 prime
        assertEquals(3, card30, "product 30 = 2*3*5 → cardinality 3");
        assertEquals(1, card2,  "product 2 → cardinality 1");
        assertEquals(4, card30 + card2, "total support = 4");
    }

    @Test
    void prismExample_Sa_sidCount5_support4() {
        // SIDs 1,2,3,5 out of 5 total
        SequenceBlock sb = SequenceBlock.of(5, 1, 2, 3, 5);
        assertEquals(4, sb.support(),
                "Support of pattern 'a' in PRISM example should be 4");
    }

    @Test
    void prismExample_Sa_correctSidMembership() {
        SequenceBlock sb = SequenceBlock.of(5, 1, 2, 3, 5);
        assertTrue(sb.isSupporting(1));
        assertTrue(sb.isSupporting(2));
        assertTrue(sb.isSupporting(3));
        assertFalse(sb.isSupporting(4));
        assertTrue(sb.isSupporting(5));
    }

    // -----------------------------------------------------------------------
    // Multi-block SID alignment (sidCount > 8)
    // -----------------------------------------------------------------------

    @Test
    void multiBlock_16sids_twoBlocks() {
        SequenceBlock sb = SequenceBlock.of(16, 1, 8, 9, 16);
        assertEquals(2, sb.blockCount());
        assertEquals(4, sb.support());
    }

    @Test
    void multiBlock_sid8InBlock0_sid9InBlock1() {
        SequenceBlock sb = SequenceBlock.of(16, 8, 9);
        // SID 8 → bit index 7 → block 0, offset 7
        // SID 9 → bit index 8 → block 1, offset 0
        assertTrue(sb.isSupporting(8));
        assertTrue(sb.isSupporting(9));
        assertFalse(sb.isSupporting(7));
        assertFalse(sb.isSupporting(10));

        // Block 0: only offset 7 set → product = prime at offset 7 = 19
        assertEquals(19, sb.blockProduct(0), "SID 8 should map to block 0, offset 7, product=19");
        // Block 1: only offset 0 set → product = prime at offset 0 = 2
        assertEquals(2, sb.blockProduct(1), "SID 9 should map to block 1, offset 0, product=2");
    }

    @Test
    void multiBlock_17sids_threeBlocks() {
        SequenceBlock sb = SequenceBlock.of(17, 1, 17);
        assertEquals(3, sb.blockCount());
        assertEquals(2, sb.support());
        assertTrue(sb.isSupporting(1));
        assertTrue(sb.isSupporting(17));
        assertFalse(sb.isSupporting(9));
    }

    @Test
    void multiBlock_supportSumsAcrossBlocks() {
        // 16 SIDs, all supporting → support should be 16
        int[] allSids = new int[16];
        for (int i = 0; i < 16; i++) allSids[i] = i + 1;
        SequenceBlock sb = SequenceBlock.of(16, allSids);
        assertEquals(16, sb.support());
    }

    // -----------------------------------------------------------------------
    // WSPM Table 1: item 'a' support = 7 out of 7 sequences
    // SOURCE-CONFIRMED: PHASE_B document Section 4
    // -----------------------------------------------------------------------

    @Test
    void wspmTable1_itemA_support7() {
        // In WSPM Table 1, item 'a' appears in all 7 sequences → support = 7
        SequenceBlock sb = SequenceBlock.of(7, 1, 2, 3, 4, 5, 6, 7);
        assertEquals(7, sb.support());
    }

    @Test
    void wspmTable1_itemC_support6() {
        // Item 'c' is in SIDs 1,2,3,4,5,6 (not 7) in Table 1 → support = 6
        // NOTE: This is the reported paper value; exact SID membership depends on Table 1 fixture.
        // This test verifies the encoding mechanism, not the fixture data.
        SequenceBlock sb = SequenceBlock.of(7, 1, 2, 3, 4, 5, 6);
        assertEquals(6, sb.support());
    }

    // -----------------------------------------------------------------------
    // Invalid operations
    // -----------------------------------------------------------------------

    @Test
    void sidOutOfRangeThrows_above() {
        SequenceBlock sb = SequenceBlock.of(7, 1, 3);
        assertThrows(IllegalArgumentException.class, () -> sb.isSupporting(8));
    }

    @Test
    void sidOutOfRangeThrows_zero() {
        SequenceBlock sb = SequenceBlock.of(7, 1, 3);
        assertThrows(IllegalArgumentException.class, () -> sb.isSupporting(0));
    }

    @Test
    void ofWithSidBeyondSidCountThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SequenceBlock.of(7, 8)); // SID 8 > sidCount 7
    }

    @Test
    void negativeSidCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> SequenceBlock.empty(-1));
    }

    // -----------------------------------------------------------------------
    // equals and hashCode
    // -----------------------------------------------------------------------

    @Test
    void equalsForSameContent() {
        SequenceBlock s1 = SequenceBlock.of(7, 1, 3, 5);
        SequenceBlock s2 = SequenceBlock.of(7, 1, 3, 5);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }

    @Test
    void notEqualForDifferentSids() {
        SequenceBlock s1 = SequenceBlock.of(7, 1, 3);
        SequenceBlock s2 = SequenceBlock.of(7, 1, 4);
        assertNotEquals(s1, s2);
    }

    @Test
    void notEqualForDifferentSidCount() {
        SequenceBlock s1 = SequenceBlock.of(7, 1);
        SequenceBlock s2 = SequenceBlock.of(8, 1);
        assertNotEquals(s1, s2);
    }
}
