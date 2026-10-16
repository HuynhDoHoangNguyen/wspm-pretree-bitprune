package vn.edu.wspm.prism;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PrimalBlockCodec}.
 *
 * <h2>Test coverage</h2>
 * <ol>
 *   <li>Exact 8-prime generator values</li>
 *   <li>Block-width and state-count constants</li>
 *   <li>Empty block → product 1, rank 0</li>
 *   <li>All-set block → product MAX_PRODUCT = 9,699,690</li>
 *   <li>Selected-bit product examples (offset {0,3} → product 14)</li>
 *   <li>Max-product overflow safety (fits in signed int)</li>
 *   <li>Factor cardinality tests</li>
 *   <li>Exhaustive 256-state rank ↔ product round-trip</li>
 *   <li>Invalid rank rejection</li>
 *   <li>Invalid product rejection (non-G prime, repeated prime, negative, zero)</li>
 *   <li>GCD rank ↔ bitwise AND equivalence (exhaustive 65,536 pairs)</li>
 *   <li>GCD matches logical bit intersection</li>
 *   <li>PRISM worked-example values {14,3} and {210,2}</li>
 *   <li>Immutability: productTable / cardTable return copies</li>
 *   <li>rankFromOffsets and rankToBinaryString</li>
 * </ol>
 */
class PrimalBlockCodecTest {

    // -----------------------------------------------------------------------
    // 1. Generator values — SOURCE-CONFIRMED (PRISM Sec. 2.2, Sec. 3.4)
    // -----------------------------------------------------------------------

    @Test
    void generatorContainsExactlyEightPrimes() {
        assertEquals(8, PrimalBlockCodec.PRIMES.length,
                "Generator G must have exactly 8 primes");
    }

    @Test
    void generatorOrderedValues() {
        int[] expected = {2, 3, 5, 7, 11, 13, 17, 19};
        assertArrayEquals(expected, PrimalBlockCodec.PRIMES,
                "Generator must be {2,3,5,7,11,13,17,19} in order");
    }

    @Test
    void blockWidthIsEight() {
        assertEquals(8, PrimalBlockCodec.BLOCK_WIDTH);
    }

    @Test
    void numberOfStatesIs256() {
        assertEquals(256, PrimalBlockCodec.NUMBER_OF_STATES);
    }

    @Test
    void emptyProductIsOne() {
        assertEquals(1, PrimalBlockCodec.EMPTY_PRODUCT,
                "Empty product must be multiplicative identity = 1");
    }

    @Test
    void maxProductIs9699690() {
        // 2*3*5*7*11*13*17*19 = 9,699,690
        assertEquals(9_699_690, PrimalBlockCodec.MAX_PRODUCT,
                "MAX_PRODUCT must be 9,699,690 (product of all 8 generator primes)");
    }

    // -----------------------------------------------------------------------
    // 2. Empty block — product 1
    // -----------------------------------------------------------------------

    @Test
    void emptyBlockRankZeroGivesProductOne() {
        assertEquals(1, PrimalBlockCodec.rankToProduct(0),
                "rank 0 (all bits clear) must encode product 1");
    }

    @Test
    void productOneRoundTripsToRankZero() {
        assertEquals(0, PrimalBlockCodec.productToRank(1),
                "product 1 must decode to rank 0");
    }

    @Test
    void rankZeroIsEmpty() {
        assertTrue(PrimalBlockCodec.isEmptyRank(0));
    }

    @Test
    void productOneIsEmpty() {
        assertTrue(PrimalBlockCodec.isEmptyProduct(1));
    }

    // -----------------------------------------------------------------------
    // 3. All-set block — product MAX_PRODUCT
    // -----------------------------------------------------------------------

    @Test
    void allBitsSetRank255GivesMaxProduct() {
        assertEquals(9_699_690, PrimalBlockCodec.rankToProduct(255),
                "rank 255 (all 8 bits set) must give product 9,699,690");
    }

    @Test
    void maxProductRoundTripsToRank255() {
        assertEquals(255, PrimalBlockCodec.productToRank(9_699_690),
                "product 9,699,690 must decode to rank 255");
    }

    @Test
    void maxProductFitsInSignedInt() {
        // Guard: verify the product is positive and within Java int range
        assertTrue(PrimalBlockCodec.MAX_PRODUCT > 0,
                "MAX_PRODUCT must be positive (no signed int overflow)");
        assertTrue(PrimalBlockCodec.MAX_PRODUCT < Integer.MAX_VALUE,
                "MAX_PRODUCT must be less than Integer.MAX_VALUE");
    }

    // -----------------------------------------------------------------------
    // 4. Selected-bit product example — offset {0,3} → product 14
    // -----------------------------------------------------------------------

    @Test
    void offsetsZeroAndThreeGiveProduct14() {
        // prime at offset 0 = 2, prime at offset 3 = 7 → product = 2*7 = 14
        int rank = PrimalBlockCodec.rankFromOffsets(0, 3);
        assertEquals(14, PrimalBlockCodec.rankToProduct(rank),
                "Offsets {0,3} → primes {2,7} → product 14");
    }

    @Test
    void product14DecodesToOffsets0and3() {
        int rank = PrimalBlockCodec.productToRank(14);
        assertTrue(PrimalBlockCodec.isBitSet(rank, 0), "Offset 0 (prime 2) must be set for product 14");
        assertTrue(PrimalBlockCodec.isBitSet(rank, 3), "Offset 3 (prime 7) must be set for product 14");
        // Only offsets 0 and 3
        assertFalse(PrimalBlockCodec.isBitSet(rank, 1));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 2));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 4));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 5));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 6));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 7));
    }

    // -----------------------------------------------------------------------
    // 5. PRISM paper example: G={2,3,5,7}, but verifying our G={2,3,5,7,11,13,17,19}
    //    PRISM Sec. 8 (our contract): P_a^1 = {14,3}, P_b^1 = {210,2}
    // -----------------------------------------------------------------------

    @Test
    void prismExample_product14_decodesCorrectly() {
        // 14 = 2*7. In our G: prime 2 at offset 0, prime 7 at offset 3.
        // So bits set at offsets 0 and 3.
        int rank = PrimalBlockCodec.productToRank(14);
        assertEquals(2, PrimalBlockCodec.factorCardinality(rank),
                "product 14 = 2*7 has factor cardinality 2");
    }

    @Test
    void prismExample_product3_decodesCorrectly() {
        // 3 is a generator prime at offset 1. Factor cardinality = 1.
        int rank = PrimalBlockCodec.productToRank(3);
        assertEquals(1, PrimalBlockCodec.factorCardinality(rank));
        assertTrue(PrimalBlockCodec.isBitSet(rank, 1), "Offset 1 (prime 3) must be set");
    }

    @Test
    void prismExample_product210_decodesCorrectly() {
        // 210 = 2*3*5*7. Offsets 0,1,2,3. Factor cardinality = 4.
        int rank = PrimalBlockCodec.productToRank(210);
        assertEquals(4, PrimalBlockCodec.factorCardinality(rank),
                "product 210 = 2*3*5*7 has factor cardinality 4");
        assertTrue(PrimalBlockCodec.isBitSet(rank, 0));
        assertTrue(PrimalBlockCodec.isBitSet(rank, 1));
        assertTrue(PrimalBlockCodec.isBitSet(rank, 2));
        assertTrue(PrimalBlockCodec.isBitSet(rank, 3));
        assertFalse(PrimalBlockCodec.isBitSet(rank, 4));
    }

    @Test
    void prismExample_product2_decodesCorrectly() {
        // 2 is a generator prime at offset 0. Factor cardinality = 1.
        int rank = PrimalBlockCodec.productToRank(2);
        assertEquals(1, PrimalBlockCodec.factorCardinality(rank));
        assertTrue(PrimalBlockCodec.isBitSet(rank, 0), "Offset 0 (prime 2) must be set");
    }

    // -----------------------------------------------------------------------
    // 6. Factor cardinality
    // -----------------------------------------------------------------------

    @Test
    void factorCardinality_product1_is0() {
        assertEquals(0, PrimalBlockCodec.factorCardinalityFromProduct(1));
    }

    @Test
    void factorCardinality_product2_is1() {
        assertEquals(1, PrimalBlockCodec.factorCardinalityFromProduct(2));
    }

    @Test
    void factorCardinality_product14_is2() {
        assertEquals(2, PrimalBlockCodec.factorCardinalityFromProduct(14));
    }

    @Test
    void factorCardinality_maxProduct_is8() {
        assertEquals(8, PrimalBlockCodec.factorCardinalityFromProduct(9_699_690));
    }

    @Test
    void factorCardinality_rank0_is0() {
        assertEquals(0, PrimalBlockCodec.factorCardinality(0));
    }

    @Test
    void factorCardinality_rank255_is8() {
        assertEquals(8, PrimalBlockCodec.factorCardinality(255));
    }

    // -----------------------------------------------------------------------
    // 7. Exhaustive 256-state rank ↔ product round-trip
    //    REQUIRED: all 256 states tested (acceptance criterion B13.6)
    // -----------------------------------------------------------------------

    @Test
    void exhaustive256StateRoundTrip_rankToProductToRank() {
        for (int rank = 0; rank < PrimalBlockCodec.NUMBER_OF_STATES; rank++) {
            int product = PrimalBlockCodec.rankToProduct(rank);
            int backRank = PrimalBlockCodec.productToRank(product);
            assertEquals(rank, backRank,
                    "Round-trip failed at rank=" + rank + " product=" + product);
        }
    }

    @Test
    void exhaustive256StateRoundTrip_productIsPositive() {
        for (int rank = 0; rank < PrimalBlockCodec.NUMBER_OF_STATES; rank++) {
            int product = PrimalBlockCodec.rankToProduct(rank);
            assertTrue(product >= 1,
                    "Product must be >= 1 at rank=" + rank);
        }
    }

    @Test
    void exhaustive256StateRoundTrip_cardinalityEqualsPopCount() {
        for (int rank = 0; rank < PrimalBlockCodec.NUMBER_OF_STATES; rank++) {
            int card = PrimalBlockCodec.factorCardinality(rank);
            int popCount = Integer.bitCount(rank);
            assertEquals(popCount, card,
                    "factorCardinality must equal population count at rank=" + rank);
        }
    }

    // -----------------------------------------------------------------------
    // 8. Invalid rank rejection
    // -----------------------------------------------------------------------

    @Test
    void rankNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.rankToProduct(-1));
    }

    @Test
    void rank256Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.rankToProduct(256));
    }

    @ParameterizedTest
    @ValueSource(ints = {-100, -1, 256, 257, 1000})
    void invalidRankThrows(int invalidRank) {
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.rankToProduct(invalidRank));
    }

    // -----------------------------------------------------------------------
    // 9. Invalid product rejection
    // -----------------------------------------------------------------------

    @Test
    void productZeroThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(0));
    }

    @Test
    void productNegativeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(-1));
    }

    @Test
    void productWithNonGeneratorPrime23Throws() {
        // 23 is a prime but not in G = {2,3,5,7,11,13,17,19}
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(23));
    }

    @Test
    void productWithNonGeneratorPrime29Throws() {
        // 29 is prime but not in G
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(29));
    }

    @Test
    void productRepeatedPrime4Throws() {
        // 4 = 2*2 is not square-free
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(4));
    }

    @Test
    void productRepeatedPrime9Throws() {
        // 9 = 3*3 is not square-free
        assertThrows(IllegalArgumentException.class,
                () -> PrimalBlockCodec.productToRank(9));
    }

    // -----------------------------------------------------------------------
    // 10. GCD rank ↔ bitwise AND — exhaustive 65,536 pairs
    //     SOURCE-CONFIRMED: PRISM Sec. 2.2, GCD table in Sec. 3.4
    // -----------------------------------------------------------------------

    @Test
    void exhaustive65536GcdPairs_gcdEqualsAndForRanks() {
        for (int a = 0; a < PrimalBlockCodec.NUMBER_OF_STATES; a++) {
            for (int b = 0; b < PrimalBlockCodec.NUMBER_OF_STATES; b++) {
                int gcdRank = PrimalBlockCodec.gcdRank(a, b);
                int andRank = a & b;
                assertEquals(andRank, gcdRank,
                        "GCD rank must equal bitwise AND at a=" + a + " b=" + b);
            }
        }
    }

    @Test
    void exhaustive65536GcdPairs_decodedGcdEqualsDecodedAnd() {
        // Also verify via products: decode(gcd(productA,productB)) == decode(bitsA AND bitsB)
        for (int a = 0; a < PrimalBlockCodec.NUMBER_OF_STATES; a++) {
            for (int b = 0; b < PrimalBlockCodec.NUMBER_OF_STATES; b++) {
                int productA = PrimalBlockCodec.rankToProduct(a);
                int productB = PrimalBlockCodec.rankToProduct(b);
                int gcdProduct = PrimalBlockCodec.gcdProduct(productA, productB);
                int andProduct = PrimalBlockCodec.rankToProduct(a & b);
                assertEquals(andProduct, gcdProduct,
                        "gcd product must match AND product at rankA=" + a + " rankB=" + b);
            }
        }
    }

    @Test
    void gcdKnownExample_14and210() {
        // gcd(14, 210) = 14 because 14 = 2*7 and 210 = 2*3*5*7; intersection = 2*7 = 14
        int result = PrimalBlockCodec.gcdProduct(14, 210);
        assertEquals(14, result, "gcd(14, 210) = 14");
    }

    @Test
    void gcdKnownExample_verifyBitIntersection_14and210() {
        // bits(14) = offset 0 and 3 → rank = 0b00001001 = 9
        // bits(210) = offset 0,1,2,3 → rank = 0b00001111 = 15
        // AND = 0b00001001 = 9 → product = 14
        int r14  = PrimalBlockCodec.productToRank(14);
        int r210 = PrimalBlockCodec.productToRank(210);
        int andResult = r14 & r210;
        assertEquals(r14, andResult, "14 AND 210 in rank space should equal rank(14)");
        assertEquals(14, PrimalBlockCodec.rankToProduct(andResult));
    }

    // -----------------------------------------------------------------------
    // 11. Immutability: productTable and cardTable return copies
    // -----------------------------------------------------------------------

    @Test
    void productTableReturnsCopy() {
        int[] t1 = PrimalBlockCodec.productTable();
        int[] t2 = PrimalBlockCodec.productTable();
        assertNotSame(t1, t2, "productTable must return independent copy each time");
        t1[0] = -999;
        assertNotEquals(-999, PrimalBlockCodec.rankToProduct(0),
                "Mutating returned array must not affect internal state");
    }

    @Test
    void cardTableReturnsCopy() {
        int[] c1 = PrimalBlockCodec.cardTable();
        int[] c2 = PrimalBlockCodec.cardTable();
        assertNotSame(c1, c2);
    }

    // -----------------------------------------------------------------------
    // 12. rankFromOffsets and rankToBinaryString
    // -----------------------------------------------------------------------

    @Test
    void rankFromOffsets_emptyGivesZero() {
        assertEquals(0, PrimalBlockCodec.rankFromOffsets());
    }

    @Test
    void rankFromOffsets_allGives255() {
        assertEquals(255, PrimalBlockCodec.rankFromOffsets(0, 1, 2, 3, 4, 5, 6, 7));
    }

    @Test
    void rankToBinaryString_rank0() {
        assertEquals("00000000", PrimalBlockCodec.rankToBinaryString(0));
    }

    @Test
    void rankToBinaryString_rank255() {
        assertEquals("11111111", PrimalBlockCodec.rankToBinaryString(255));
    }

    @Test
    void rankToBinaryString_rank1() {
        // rank=1 = 0b00000001: offset 0 (LSB) is set → displayed as "00000001" (right = LSB)
        assertEquals("00000001", PrimalBlockCodec.rankToBinaryString(1));
    }

    @Test
    void rankToBinaryString_rank128() {
        // rank=128 = 0b10000000: offset 7 (MSB) is set → displayed as "10000000"
        assertEquals("10000000", PrimalBlockCodec.rankToBinaryString(128));
    }

    // -----------------------------------------------------------------------
    // 13. primeAtOffset boundary tests
    // -----------------------------------------------------------------------

    @Test
    void primeAtOffset0Is2() {
        assertEquals(2, PrimalBlockCodec.primeAtOffset(0));
    }

    @Test
    void primeAtOffset7Is19() {
        assertEquals(19, PrimalBlockCodec.primeAtOffset(7));
    }

    @Test
    void primeAtOffsetNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> PrimalBlockCodec.primeAtOffset(-1));
    }

    @Test
    void primeAtOffset8Throws() {
        assertThrows(IllegalArgumentException.class, () -> PrimalBlockCodec.primeAtOffset(8));
    }
}
