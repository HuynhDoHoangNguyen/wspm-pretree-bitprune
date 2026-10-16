package vn.edu.wspm.prism;

import java.util.Arrays;

/**
 * Codec for PRISM / Prime-block encoding of a single 8-bit logical block.
 *
 * <p><b>Terminology note:</b> The original PRISM journal paper (Gouda, Hassaan, Zaki, 2010) uses
 * the term <em>primal block encoding</em>. The WSPM_PreTree paper (Pham et al., 2023) uses
 * <em>prime-block encoding</em>. This implementation treats them as the same mechanism. Classes
 * in this package use the PRISM technical vocabulary where precise; the {@code PrimeBlock}
 * shorthand is kept in external-facing identifiers for readability.</p>
 *
 * <h2>Fixed generator — SOURCE-CONFIRMED (PRISM Sec. 2.2, Sec. 3.4, author source)</h2>
 * <p>The PRISM implementation fixes the generator to exactly eight primes:<br>
 * {@code G = {2, 3, 5, 7, 11, 13, 17, 19}}<br>
 * giving block width {@code g = 8} and {@code 2^8 = 256} possible factor subsets.</p>
 *
 * <h2>Bit-offset to prime mapping — SOURCE-CONFIRMED (PRISM Sec. 2.1-2.2)</h2>
 * <pre>
 * offset 0 → prime 2
 * offset 1 → prime 3
 * offset 2 → prime 5
 * offset 3 → prime 7
 * offset 4 → prime 11
 * offset 5 → prime 13
 * offset 6 → prime 17
 * offset 7 → prime 19
 * </pre>
 * <p>Primes represent <em>bit positions inside a block</em>, not item identifiers. A 1-bit at
 * offset {@code j} contributes prime {@code G[j]} once to the product.</p>
 *
 * <h2>Rank representation — SOURCE-CONFIRMED (PRISM Sec. 3.4)</h2>
 * <p>The paper assigns each product a <em>rank</em> in {@code 0..255} where the leftmost bit is
 * least significant. This is identical to treating the 8-bit block as an unsigned integer whose
 * bit {@code 0} (least significant) maps to offset 0 / prime 2. Java does not have unsigned
 * byte; ranks are stored and validated as {@code int} in the range {@code [0, 255]}.</p>
 *
 * <h2>GCD equals bitwise AND — SOURCE-CONFIRMED (PRISM Sec. 2.2)</h2>
 * <p>The GCD of two valid block products equals the product of the primes shared by both blocks.
 * This is equivalent to a bitwise AND of their rank representations because the primes are
 * distinct and the products are square-free.</p>
 *
 * <h2>Factor cardinality — SOURCE-CONFIRMED (PRISM Sec. 2.1)</h2>
 * <p>{@code ||v||_G} is the number of generator primes that divide {@code v}. This is equivalent
 * to the population count of the rank (number of set bits).</p>
 */
public final class PrimalBlockCodec {

    // -----------------------------------------------------------------------
    // Generator — SOURCE-CONFIRMED (PRISM Sec. 2.2, Sec. 3.4, author source)
    // -----------------------------------------------------------------------

    /** Block width: number of primes in the fixed generator set. */
    public static final int BLOCK_WIDTH = 8;

    /** Number of possible factor subsets, i.e. 2^BLOCK_WIDTH = 256. */
    public static final int NUMBER_OF_STATES = 256;

    /**
     * The fixed ordered generator set G = {2, 3, 5, 7, 11, 13, 17, 19}.
     * Offset {@code j} maps to {@code PRIMES[j]}.
     * SOURCE-CONFIRMED: PRISM Sec. 2.2 and Sec. 3.4 / author C++ source declares exactly these 8 primes.
     */
    public static final int[] PRIMES = { 2, 3, 5, 7, 11, 13, 17, 19 };

    /** Product of the empty subset = multiplicative identity. */
    public static final int EMPTY_PRODUCT = 1;

    /** Maximum product: product of all 8 primes = 2*3*5*7*11*13*17*19 = 9,699,690.
     * Fits in a Java signed int (max ~2.1 billion). SOURCE-CONFIRMED: PRISM Sec. 3.4. */
    public static final int MAX_PRODUCT = 2 * 3 * 5 * 7 * 11 * 13 * 17 * 19; // = 9_699_690

    // -----------------------------------------------------------------------
    // Precomputed lookup tables — SOURCE-CONFIRMED design (PRISM Sec. 3.4)
    // -----------------------------------------------------------------------

    /**
     * PRODUCT[rank] = prime product for each rank 0..255.
     * At B13 this is used for transparent reference tests.
     * SOURCE-CONFIRMED strategy (PRISM Sec. 3.4): precomputed lookup; g=8 gives 256 entries.
     */
    private static final int[] PRODUCT;

    /**
     * RANK[product] is not indexed by product value directly (max 9,699,690 entries would be
     * wasteful). Instead rank is looked up by iterating PRODUCT or via inverse map.
     * We use a simple scan here; a HashMap could be used but 256 entries make it negligible.
     */
    private static final int[] RANK_BY_PRODUCT_INDEX; // parallel to PRODUCT

    /**
     * CARD[rank] = factor cardinality = population count of rank bits.
     * SOURCE-CONFIRMED: PRISM Sec. 3.4 declares a CARD table of 256 entries.
     */
    private static final int[] CARD;

    /**
     * GCD_TABLE[rankA][rankB] = rank of gcd(productA, productB).
     * Equivalent to bitwise AND of ranks because all primes are distinct and products are square-free.
     * SOURCE-CONFIRMED: PRISM Sec. 3.4 declares a 256x256 GCD matrix.
     * IMPLEMENTATION NOTE (B13): stored as int to avoid Java signed-byte semantics.
     */
    private static final int[][] GCD_TABLE;

    static {
        PRODUCT = new int[NUMBER_OF_STATES];
        CARD = new int[NUMBER_OF_STATES];

        for (int rank = 0; rank < NUMBER_OF_STATES; rank++) {
            int product = 1;
            int card = 0;
            for (int offset = 0; offset < BLOCK_WIDTH; offset++) {
                if ((rank & (1 << offset)) != 0) {
                    product = Math.multiplyExact(product, PRIMES[offset]);
                    card++;
                }
            }
            PRODUCT[rank] = product;
            CARD[rank] = card;
        }

        // Build inverse: product -> rank. 256 entries, O(256) scan is fine.
        RANK_BY_PRODUCT_INDEX = Arrays.copyOf(PRODUCT, PRODUCT.length); // not used as index

        GCD_TABLE = new int[NUMBER_OF_STATES][NUMBER_OF_STATES];
        for (int a = 0; a < NUMBER_OF_STATES; a++) {
            for (int b = 0; b < NUMBER_OF_STATES; b++) {
                // For square-free products over distinct primes: gcd = bitwise AND of ranks
                GCD_TABLE[a][b] = a & b;
            }
        }
    }

    /** Not instantiable; all methods are static utilities. */
    private PrimalBlockCodec() {}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the prime product for a given rank.
     *
     * @param rank an integer in [0, 255]; rank is the 8-bit logical block treated as unsigned int
     * @return the square-free product of primes at set-bit offsets; {@code 1} when rank is 0
     * @throws IllegalArgumentException if rank is outside [0, 255]
     */
    public static int rankToProduct(int rank) {
        validateRank(rank);
        return PRODUCT[rank];
    }

    /**
     * Returns the rank (unsigned 8-bit value) for a given prime product.
     *
     * <p>Only products that are valid square-free products over the fixed generator G are accepted.
     * For example, {@code 4 = 2*2} is rejected because it is not square-free over G.
     * {@code 23} is rejected because 23 is not in G.</p>
     *
     * @param product a valid square-free product of a subset of G; must be ≥ 1
     * @return rank in [0, 255]
     * @throws IllegalArgumentException if product is not a valid block product
     */
    public static int productToRank(int product) {
        validateProduct(product);
        // Decompose: for each prime in G, check divisibility
        int rank = 0;
        int remaining = product;
        for (int offset = 0; offset < BLOCK_WIDTH; offset++) {
            int p = PRIMES[offset];
            if (remaining % p == 0) {
                rank |= (1 << offset);
                remaining /= p;
            }
        }
        if (remaining != 1) {
            throw new IllegalArgumentException(
                    "Product " + product + " is not a valid square-free product over G");
        }
        return rank;
    }

    /**
     * Returns the rank for a given 8-bit logical block where bit {@code offset} (0=LSB) is set
     * when the prime at that offset contributes.
     *
     * <p>This is an identity operation since rank IS the 8-bit logical representation, but it
     * enforces the validity range.</p>
     *
     * @param rank value in [0, 255]
     * @return same value, validated
     */
    public static int validateRankValue(int rank) {
        validateRank(rank);
        return rank;
    }

    /**
     * Returns {@code true} if bit {@code offset} is set in the given rank.
     *
     * @param rank   in [0, 255]
     * @param offset in [0, 7]
     */
    public static boolean isBitSet(int rank, int offset) {
        validateRank(rank);
        validateOffset(offset);
        return (rank & (1 << offset)) != 0;
    }

    /**
     * Returns the prime associated with the given bit offset in the block.
     *
     * @param offset in [0, 7]
     * @return the prime at that offset
     */
    public static int primeAtOffset(int offset) {
        validateOffset(offset);
        return PRIMES[offset];
    }

    /**
     * Returns the factor cardinality {@code ||v||_G} — the number of generator primes that divide
     * the encoded block product.
     *
     * <p>Equivalent to the population count (number of set bits) of the rank.
     * SOURCE-CONFIRMED: PRISM Theorem 3.1 uses factor cardinalities to compute support.</p>
     *
     * @param rank in [0, 255]
     * @return count of set bits in rank, in [0, 8]
     */
    public static int factorCardinality(int rank) {
        validateRank(rank);
        return CARD[rank];
    }

    /**
     * Returns the factor cardinality of a block product.
     *
     * @param product a valid block product
     * @return factor cardinality in [0, 8]
     */
    public static int factorCardinalityFromProduct(int product) {
        return factorCardinality(productToRank(product));
    }

    /**
     * Returns the rank of the GCD of two blocks given their ranks.
     *
     * <p>For square-free products over distinct primes, {@code gcd(a,b) = bitwise AND} of ranks.
     * SOURCE-CONFIRMED: PRISM Sec. 2.2 and the 256×256 GCD table in Sec. 3.4.</p>
     *
     * @param rankA rank in [0, 255]
     * @param rankB rank in [0, 255]
     * @return rank of the intersection block
     */
    public static int gcdRank(int rankA, int rankB) {
        validateRank(rankA);
        validateRank(rankB);
        return GCD_TABLE[rankA][rankB];
    }

    /**
     * Returns the product of the GCD of two blocks given their products.
     *
     * <p>Equivalent to computing {@code gcd(productA, productB)} mathematically.</p>
     *
     * @param productA a valid block product
     * @param productB a valid block product
     * @return product of the intersection block
     */
    public static int gcdProduct(int productA, int productB) {
        int ra = productToRank(productA);
        int rb = productToRank(productB);
        return PRODUCT[gcdRank(ra, rb)];
    }

    /**
     * Returns {@code true} if the block encoded by the given rank is empty (no set bits).
     *
     * @param rank in [0, 255]
     */
    public static boolean isEmptyRank(int rank) {
        validateRank(rank);
        return rank == 0;
    }

    /**
     * Returns {@code true} if the block encoded by the given product is empty (product = 1).
     *
     * @param product a valid block product
     */
    public static boolean isEmptyProduct(int product) {
        validateProduct(product);
        return product == EMPTY_PRODUCT;
    }

    // -----------------------------------------------------------------------
    // Validation helpers
    // -----------------------------------------------------------------------

    static void validateRank(int rank) {
        if (rank < 0 || rank >= NUMBER_OF_STATES) {
            throw new IllegalArgumentException(
                    "Rank must be in [0, 255], got: " + rank);
        }
    }

    static void validateOffset(int offset) {
        if (offset < 0 || offset >= BLOCK_WIDTH) {
            throw new IllegalArgumentException(
                    "Bit offset must be in [0, 7], got: " + offset);
        }
    }

    /**
     * Validates that {@code product} is a valid square-free subset product of G.
     * Rejects: negative, zero, values with prime factors outside G, or repeated factors.
     */
    static void validateProduct(int product) {
        if (product < 1) {
            throw new IllegalArgumentException(
                    "Block product must be >= 1, got: " + product);
        }
        // Trial division against G; if anything remains after dividing out all G primes, invalid
        int remaining = product;
        for (int p : PRIMES) {
            if (remaining % p == 0) {
                remaining /= p;
                // Must not divide again (square-free)
                if (remaining % p == 0) {
                    throw new IllegalArgumentException(
                            "Product " + product + " contains repeated prime " + p + " (not square-free)");
                }
            }
        }
        if (remaining != 1) {
            throw new IllegalArgumentException(
                    "Product " + product + " contains a prime factor not in generator G: "
                    + "residual = " + remaining);
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Converts a logical 8-bit block (as an unsigned integer in [0,255]) to a human-readable
     * binary string, MSB to LSB as {@code "b7b6b5b4b3b2b1b0"}.
     *
     * <p>Note: in PRISM rank convention, bit 0 is least significant and maps to prime 2. This
     * display shows offset 7 on the left for readability; the bit order matches standard binary.</p>
     */
    public static String rankToBinaryString(int rank) {
        validateRank(rank);
        char[] bits = new char[BLOCK_WIDTH];
        for (int offset = 0; offset < BLOCK_WIDTH; offset++) {
            // bit at offset 0 is LSB; display at position [BLOCK_WIDTH-1-offset]
            bits[BLOCK_WIDTH - 1 - offset] = (rank & (1 << offset)) != 0 ? '1' : '0';
        }
        return new String(bits);
    }

    /**
     * Builds the rank from an explicit list of set bit offsets.
     * Convenience for tests where specific primes are selected by name.
     *
     * @param offsets bit offsets in [0,7]; duplicates are silently ignored
     * @return rank in [0, 255]
     */
    public static int rankFromOffsets(int... offsets) {
        int rank = 0;
        for (int offset : offsets) {
            validateOffset(offset);
            rank |= (1 << offset);
        }
        return rank;
    }

    /** Returns an unmodifiable snapshot of the full PRODUCT lookup table (256 entries). */
    public static int[] productTable() {
        return Arrays.copyOf(PRODUCT, PRODUCT.length);
    }

    /** Returns an unmodifiable snapshot of the full CARD lookup table (256 entries). */
    public static int[] cardTable() {
        return Arrays.copyOf(CARD, CARD.length);
    }
}
