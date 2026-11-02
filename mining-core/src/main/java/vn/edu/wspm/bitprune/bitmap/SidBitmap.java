package vn.edu.wspm.bitprune.bitmap;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable, 64-bit packed bitmap representation of supporting sequence
 * identifiers (SIDs).
 *
 * <p>
 * Each bit in {@code [0, sequenceCount)} represents whether the corresponding
 * sequence
 * in {@link SidMapping} is active. Bit positions beyond {@code sequenceCount}
 * are canonicalized
 * zero-padding and are never counted toward cardinality.
 * </p>
 */
public final class SidBitmap {

    private final SidMapping mapping;
    private final long[] words;
    private final int cardinality;

    private SidBitmap(SidMapping mapping, long[] words, int cardinality) {
        this.mapping = mapping;
        this.words = words;
        this.cardinality = cardinality;
    }

    /**
     * Computes the number of 64-bit words required to represent
     * {@code sequenceCount} bits
     * without 32-bit integer arithmetic overflow.
     *
     * @param sequenceCount the number of sequences (must be non-negative)
     * @return the word count
     * @throws IllegalArgumentException if {@code sequenceCount < 0} or word count
     *                                  exceeds array limits
     */
    static int computeWordCount(int sequenceCount) {
        if (sequenceCount < 0) {
            throw new IllegalArgumentException("sequenceCount must not be negative: " + sequenceCount);
        }
        long wordCountLong = ((long) sequenceCount + 63L) >>> 6;
        if (wordCountLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Word count exceeds maximum array size: " + wordCountLong);
        }
        return (int) wordCountLong;
    }

    /**
     * Creates an immutable {@link SidBitmap} from a mapping and raw 64-bit words.
     *
     * <p>
     * The input words are defensively copied and padding bits beyond
     * {@code mapping.size()}
     * are canonicalized to zero.
     * </p>
     *
     * @param mapping the SID mapping (must not be null)
     * @param words   the word array matching the expected word count
     * @return the canonical immutable {@link SidBitmap}
     * @throws NullPointerException     if {@code mapping} or {@code words} is null
     * @throws IllegalArgumentException if {@code words.length} does not match
     *                                  expected word count
     */
    public static SidBitmap of(SidMapping mapping, long[] words) {
        Objects.requireNonNull(mapping, "mapping must not be null");
        Objects.requireNonNull(words, "words must not be null");

        int sequenceCount = mapping.size();
        int expectedWordCount = computeWordCount(sequenceCount);
        if (words.length != expectedWordCount) {
            throw new IllegalArgumentException(
                    "Invalid word array length: expected " + expectedWordCount + ", but got " + words.length);
        }

        if (sequenceCount == 0) {
            return new SidBitmap(mapping, new long[0], 0);
        }

        long[] canonicalWords = words.clone();
        int remainder = sequenceCount & 63;
        if (remainder != 0) {
            long mask = -1L >>> (64 - remainder);
            canonicalWords[expectedWordCount - 1] &= mask;
        }

        int card = 0;
        for (long word : canonicalWords) {
            card += Long.bitCount(word);
        }

        return new SidBitmap(mapping, canonicalWords, card);
    }

    /**
     * Creates an empty {@link SidBitmap} (all bits 0) for the given mapping.
     *
     * @param mapping the SID mapping (must not be null)
     * @return an empty bitmap
     * @throws NullPointerException if {@code mapping} is null
     */
    public static SidBitmap empty(SidMapping mapping) {
        Objects.requireNonNull(mapping, "mapping must not be null");
        int expectedWordCount = computeWordCount(mapping.size());
        return new SidBitmap(mapping, new long[expectedWordCount], 0);
    }

    /**
     * Creates a new mutable {@link Builder} for the given mapping.
     *
     * @param mapping the SID mapping (must not be null)
     * @return a new builder instance
     * @throws NullPointerException if {@code mapping} is null
     */
    public static Builder builder(SidMapping mapping) {
        return new Builder(mapping);
    }

    /**
     * Returns the underlying SID mapping.
     *
     * @return the SID mapping
     */
    public SidMapping mapping() {
        return mapping;
    }

    /**
     * Returns the logical number of sequences in this bitmap's universe.
     *
     * @return sequence count
     */
    public int sequenceCount() {
        return mapping.size();
    }

    /**
     * Returns the number of 64-bit words in the backing array.
     *
     * @return word count
     */
    public int wordCount() {
        return words.length;
    }

    /**
     * Tests whether the compact bit index is set.
     *
     * @param bitIndex index in {@code [0, sequenceCount())}
     * @return {@code true} if set, {@code false} otherwise
     * @throws IndexOutOfBoundsException if {@code bitIndex < 0} or
     *                                   {@code bitIndex >= sequenceCount()}
     */
    public boolean isSet(int bitIndex) {
        if (bitIndex < 0 || bitIndex >= mapping.size()) {
            throw new IndexOutOfBoundsException(
                    "Bit index out of bounds: " + bitIndex + " for universe size: " + mapping.size());
        }
        int wordIndex = bitIndex >>> 6;
        long bitMask = 1L << (bitIndex & 63);
        return (words[wordIndex] & bitMask) != 0;
    }

    /**
     * Tests whether the sequence with the given actual SID is present in this
     * bitmap.
     *
     * @param actualSid the actual sequence identifier
     * @return {@code true} if present, {@code false} otherwise
     * @throws IllegalArgumentException if {@code actualSid} is not in the universe
     */
    public boolean containsSid(int actualSid) {
        return isSet(mapping.sidToIndex(actualSid));
    }

    /**
     * Returns the population count (cardinality) of set bits.
     *
     * @return number of set bits
     */
    public int cardinality() {
        return cardinality;
    }

    /**
     * Computes the bitwise intersection (AND) between this bitmap and another
     * bitmap
     * belonging to the same SID universe.
     *
     * @param other the other bitmap (must not be null, must have equal mapping)
     * @return a new {@link SidBitmap} representing the intersection
     * @throws NullPointerException     if {@code other} is null
     * @throws IllegalArgumentException if {@code other} belongs to a different SID
     *                                  universe
     */
    public SidBitmap and(SidBitmap other) {
        Objects.requireNonNull(other, "other bitmap must not be null");
        if (!this.mapping.equals(other.mapping)) {
            throw new IllegalArgumentException("Cannot AND bitmaps from different SID universes");
        }
        if (this.words.length == 0) {
            return new SidBitmap(this.mapping, new long[0], 0);
        }

        int len = this.words.length;
        long[] resultWords = new long[len];
        int card = 0;
        for (int i = 0; i < len; i++) {
            long w = this.words[i] & other.words[i];
            resultWords[i] = w;
            card += Long.bitCount(w);
        }
        return new SidBitmap(this.mapping, resultWords, card);
    }

    /**
     * Recovers the list of actual SIDs whose bits are set, ordered
     * deterministically
     * by compact bit index.
     *
     * @return array of actual SIDs
     */
    public int[] toSupportingSids() {
        if (cardinality == 0) {
            return new int[0];
        }

        int[] result = new int[cardinality];
        int pos = 0;
        int sequenceCount = mapping.size();

        for (int wordIdx = 0; wordIdx < words.length; wordIdx++) {
            long word = words[wordIdx];
            while (word != 0) {
                int bitInWord = Long.numberOfTrailingZeros(word);
                int bitIndex = (wordIdx << 6) + bitInWord;
                if (bitIndex < sequenceCount) {
                    result[pos++] = mapping.indexToSid(bitIndex);
                }
                word &= (word - 1);
            }
        }
        return result;
    }

    /**
     * Returns a defensive copy of the backing 64-bit word array.
     *
     * @return defensive copy of words
     */
    public long[] toWordArray() {
        return words.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SidBitmap that)) {
            return false;
        }
        return this.mapping.equals(that.mapping) && Arrays.equals(this.words, that.words);
    }

    @Override
    public int hashCode() {
        return 31 * mapping.hashCode() + Arrays.hashCode(words);
    }

    @Override
    public String toString() {
        return "SidBitmap[cardinality=" + cardinality + ", sequenceCount=" + sequenceCount() + "]";
    }

    /**
     * Mutable builder for constructing {@link SidBitmap} snapshots.
     */
    public static final class Builder {

        private final SidMapping mapping;
        private final long[] words;

        private Builder(SidMapping mapping) {
            this.mapping = Objects.requireNonNull(mapping, "mapping must not be null");
            this.words = new long[computeWordCount(mapping.size())];
        }

        /**
         * Sets the bit at the given compact bit index.
         *
         * @param bitIndex index in {@code [0, sequenceCount())}
         * @return this builder
         * @throws IndexOutOfBoundsException if {@code bitIndex < 0} or
         *                                   {@code bitIndex >= sequenceCount()}
         */
        public Builder setIndex(int bitIndex) {
            if (bitIndex < 0 || bitIndex >= mapping.size()) {
                throw new IndexOutOfBoundsException(
                        "Bit index out of bounds: " + bitIndex + " for universe size: " + mapping.size());
            }
            words[bitIndex >>> 6] |= (1L << (bitIndex & 63));
            return this;
        }

        /**
         * Sets the bit corresponding to the given actual SID.
         *
         * @param actualSid the actual sequence identifier
         * @return this builder
         * @throws IllegalArgumentException if {@code actualSid} is not in the universe
         */
        public Builder setSid(int actualSid) {
            int bitIndex = mapping.sidToIndex(actualSid);
            return setIndex(bitIndex);
        }

        /**
         * Builds an immutable {@link SidBitmap} snapshot. Subsequent modifications to
         * this
         * builder will not affect the returned bitmap.
         *
         * @return an immutable {@link SidBitmap}
         */
        public SidBitmap build() {
            return SidBitmap.of(mapping, words.clone());
        }
    }
}
