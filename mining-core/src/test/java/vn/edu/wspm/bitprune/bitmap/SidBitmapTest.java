package vn.edu.wspm.bitprune.bitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class SidBitmapTest {

    private static final Item ITEM = new Item(1);

    private static Sequence dummySequence() {
        return Sequence.of(Itemset.of(ITEM));
    }

    private static SequenceDatabase db(int... sids) {
        var list = new ArrayList<SequenceRecord>();
        for (int sid : sids) {
            list.add(new SequenceRecord(sid, dummySequence()));
        }
        return new SequenceDatabase(list);
    }

    private static SidMapping mappingOf(int... sids) {
        return SidMapping.of(db(sids));
    }

    private static SidMapping mappingOfSize(int count) {
        int[] sids = new int[count];
        for (int i = 0; i < count; i++) {
            sids[i] = 1000 + i;
        }
        return mappingOf(sids);
    }

    @Test
    void validatesLogicalUniverseSizesAndWordCountBoundaries() {
        // Test 11 & 12: Sizes 0, 1, 63, 64, 65
        int[] sizes = {0, 1, 63, 64, 65};
        int[] expectedWords = {0, 1, 1, 1, 2};

        for (int i = 0; i < sizes.length; i++) {
            int size = sizes[i];
            int expectedWordCount = expectedWords[i];

            assertEquals(expectedWordCount, SidBitmap.computeWordCount(size),
                    "computeWordCount failed for size " + size);

            var mapping = mappingOfSize(size);
            var emptyBm = SidBitmap.empty(mapping);

            assertEquals(size, emptyBm.sequenceCount());
            assertEquals(expectedWordCount, emptyBm.wordCount());
            assertEquals(expectedWordCount, emptyBm.toWordArray().length);
            assertEquals(0, emptyBm.cardinality());
        }
    }

    @Test
    void setsAndChecksValidIndicesAtBoundaries() {
        // Test 13: Set/check index 0, 62, 63, 64 on size 65
        var mapping = mappingOfSize(65);
        var builder = SidBitmap.builder(mapping);

        builder.setIndex(0);
        builder.setIndex(62);
        builder.setIndex(63);
        builder.setIndex(64);

        var bitmap = builder.build();

        assertEquals(4, bitmap.cardinality());
        assertTrue(bitmap.isSet(0));
        assertTrue(bitmap.isSet(62));
        assertTrue(bitmap.isSet(63));
        assertTrue(bitmap.isSet(64));

        assertFalse(bitmap.isSet(1));
        assertFalse(bitmap.isSet(61));
    }

    @Test
    void rejectsNegativeIndexAndSequenceCountIndex() {
        // Test 14: Set/check -1 and sequenceCount
        var mapping = mappingOfSize(64);
        var bitmap = SidBitmap.empty(mapping);

        assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(64));
        assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(65));

        var builder = SidBitmap.builder(mapping);
        assertThrows(IndexOutOfBoundsException.class, () -> builder.setIndex(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> builder.setIndex(64));
        assertThrows(IndexOutOfBoundsException.class, () -> builder.setIndex(100));
    }

    @Test
    void setSidAndContainsSidUseActualSidCorrectly() {
        // Test 15: Actual SIDs differ from bit index
        var mapping = mappingOf(500, -20, 300);
        var builder = SidBitmap.builder(mapping);

        builder.setSid(-20);
        var bitmap = builder.build();

        assertEquals(1, bitmap.cardinality());
        assertTrue(bitmap.containsSid(-20));
        assertFalse(bitmap.containsSid(500));
        assertFalse(bitmap.containsSid(300));

        // Index 1 corresponds to SID -20
        assertTrue(bitmap.isSet(1));
        assertFalse(bitmap.isSet(0));
        assertFalse(bitmap.isSet(2));
    }

    @Test
    void rejectsUnknownSidInBuilderAndBitmap() {
        // Test 16: Unknown SID rejection
        var mapping = mappingOf(10, 20, 30);
        var builder = SidBitmap.builder(mapping);
        var bitmap = SidBitmap.empty(mapping);

        assertThrows(IllegalArgumentException.class, () -> builder.setSid(999));
        assertThrows(IllegalArgumentException.class, () -> bitmap.containsSid(999));
    }

    @Test
    void rejectsInvalidRawWordArrayLength() {
        // Test 17: Raw word-array length mismatch
        var mapping65 = mappingOfSize(65);
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.of(mapping65, new long[1]));
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.of(mapping65, new long[3]));

        var mapping0 = mappingOfSize(0);
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.of(mapping0, new long[1]));

        var mapping64 = mappingOfSize(64);
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.of(mapping64, new long[0]));
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.of(mapping64, new long[2]));
    }

    @Test
    void canonicalizesPaddingToZeroAndDoesNotIncreaseCardinality() {
        // Test 18 & 19: Padding canonicalization and cardinality safety
        var mapping = mappingOfSize(65); // wordCount = 2, valid indices: 0..64
        // Pass all 1s (-1L) for both words (128 bits set)
        long[] dirtyWords = new long[]{-1L, -1L};

        var bitmap = SidBitmap.of(mapping, dirtyWords);

        // Word 0: 64 bits set
        // Word 1: Only bit 0 (index 64) is valid. Bits 1..63 must be canonicalized to 0.
        // Therefore, word 1 must equal 1L.
        long[] cleanWords = bitmap.toWordArray();
        assertEquals(-1L, cleanWords[0]);
        assertEquals(1L, cleanWords[1]);

        // Total cardinality must be 64 + 1 = 65, NOT 128
        assertEquals(65, bitmap.cardinality());

        // Verify logical bit queries
        assertTrue(bitmap.isSet(64));
        assertThrows(IndexOutOfBoundsException.class, () -> bitmap.isSet(65));
    }

    @Test
    void canonicalizesSingleBitUniversePadding() {
        // Test 18 & 19 edge case: sequenceCount = 1
        var mapping = mappingOfSize(1);
        long[] dirtyWords = new long[]{-1L};

        var bitmap = SidBitmap.of(mapping, dirtyWords);

        assertEquals(1, bitmap.cardinality());
        assertEquals(1L, bitmap.toWordArray()[0]);
        assertTrue(bitmap.isSet(0));
    }

    @Test
    void verifiesEmptyBitmapBehavior() {
        // Test 20: Empty bitmap behavior
        var mapping = mappingOfSize(64);
        var emptyBm = SidBitmap.empty(mapping);

        assertEquals(0, emptyBm.cardinality());
        for (int i = 0; i < 64; i++) {
            assertFalse(emptyBm.isSet(i));
        }
        assertArrayEquals(new int[0], emptyBm.toSupportingSids());
    }

    @Test
    void verifiesCardinalityBeforeAndAfterAndDoesNotMutateInputs() {
        // Test 21, 22, 23: Cardinality, immutability, same-universe AND
        var mapping = mappingOfSize(100);
        var bA = SidBitmap.builder(mapping);
        bA.setIndex(0).setIndex(10).setIndex(20).setIndex(70);
        var bmA = bA.build();

        var bB = SidBitmap.builder(mapping);
        bB.setIndex(10).setIndex(20).setIndex(30).setIndex(80);
        var bmB = bB.build();

        assertEquals(4, bmA.cardinality());
        assertEquals(4, bmB.cardinality());

        var bmAnd = bmA.and(bmB);

        // Inputs remain unchanged
        assertEquals(4, bmA.cardinality());
        assertEquals(4, bmB.cardinality());

        // Intersection has indices 10 and 20
        assertEquals(2, bmAnd.cardinality());
        assertTrue(bmAnd.isSet(10));
        assertTrue(bmAnd.isSet(20));
        assertFalse(bmAnd.isSet(0));
        assertFalse(bmAnd.isSet(30));
        assertFalse(bmAnd.isSet(70));
        assertFalse(bmAnd.isSet(80));
    }

    @Test
    void rejectsAndBetweenDifferentSidUniversesWithSameSize() {
        // Test 24: Same size but different SID universe
        var mapA = mappingOf(1, 2, 3);
        var mapB = mappingOf(1, 2, 4);

        var bmA = SidBitmap.empty(mapA);
        var bmB = SidBitmap.empty(mapB);

        var ex = assertThrows(IllegalArgumentException.class, () -> bmA.and(bmB));
        assertTrue(ex.getMessage().contains("different SID universes"));
    }

    @Test
    void rejectsAndBetweenSameSidSetWithDifferentOrder() {
        // Test 25: Same SID set but different order
        var mapA = mappingOf(1, 2, 3);
        var mapB = mappingOf(3, 2, 1);

        var bmA = SidBitmap.empty(mapA);
        var bmB = SidBitmap.empty(mapB);

        assertThrows(IllegalArgumentException.class, () -> bmA.and(bmB));
    }

    @Test
    void rejectsNullInAnd() {
        var bm = SidBitmap.empty(mappingOf(1));
        assertThrows(NullPointerException.class, () -> bm.and(null));
    }

    @Test
    void ofDefensivelyCopiesInputWordArray() {
        // Test 26: Input array defensive copying
        var mapping = mappingOfSize(1);
        long[] words = new long[]{1L};

        var bitmap = SidBitmap.of(mapping, words);
        words[0] = 0L; // mutate caller's array

        assertEquals(1, bitmap.cardinality());
        assertTrue(bitmap.isSet(0));
    }

    @Test
    void toWordArrayReturnsDefensiveCopy() {
        // Test 27: Output array defensive copying
        var mapping = mappingOfSize(1);
        var bitmap = SidBitmap.of(mapping, new long[]{1L});

        long[] copy = bitmap.toWordArray();
        copy[0] = 0L; // mutate copy

        assertEquals(1, bitmap.cardinality());
        assertTrue(bitmap.isSet(0));
    }

    @Test
    void builderMutationAfterBuildDoesNotAffectPreviousBitmap() {
        // Test 28: Builder mutation after build()
        var mapping = mappingOfSize(65);
        var builder = SidBitmap.builder(mapping);

        builder.setIndex(0);
        var snapshot1 = builder.build();

        builder.setIndex(1);
        builder.setIndex(64);
        var snapshot2 = builder.build();

        assertEquals(1, snapshot1.cardinality());
        assertTrue(snapshot1.isSet(0));
        assertFalse(snapshot1.isSet(1));
        assertFalse(snapshot1.isSet(64));

        assertEquals(3, snapshot2.cardinality());
        assertTrue(snapshot2.isSet(0));
        assertTrue(snapshot2.isSet(1));
        assertTrue(snapshot2.isSet(64));
    }

    @Test
    void toSupportingSidsReturnsActualSidsInDeterministicBitIndexOrder() {
        // Test 29: Deterministic bit-index order actual SIDs
        // Record order: SID 50 -> index 0, SID 10 -> index 1, SID 30 -> index 2
        var mapping = mappingOf(50, 10, 30);
        var builder = SidBitmap.builder(mapping);

        // Set index 2 then index 0
        builder.setIndex(2);
        builder.setIndex(0);
        var bitmap = builder.build();

        // Must return in bit-index order: index 0 (SID 50), index 2 (SID 30)
        assertArrayEquals(new int[]{50, 30}, bitmap.toSupportingSids());
    }

    @Test
    void andBetweenEmptyBitmapsInSameUniverseReturnsNewEmptyBitmap() {
        // Test 30: Empty bitmap AND empty bitmap
        var emptyMap = mappingOf();
        var emptyA = SidBitmap.empty(emptyMap);
        var emptyB = SidBitmap.empty(emptyMap);

        var result = emptyA.and(emptyB);

        assertEquals(0, result.sequenceCount());
        assertEquals(0, result.wordCount());
        assertEquals(0, result.cardinality());
        assertArrayEquals(new int[0], result.toSupportingSids());
        assertArrayEquals(new long[0], result.toWordArray());

        // Also test non-empty universe with all zeros
        var map10 = mappingOfSize(10);
        var zerosA = SidBitmap.empty(map10);
        var zerosB = SidBitmap.empty(map10);
        var result10 = zerosA.and(zerosB);

        assertEquals(10, result10.sequenceCount());
        assertEquals(0, result10.cardinality());
        assertArrayEquals(new int[0], result10.toSupportingSids());
    }

    @Test
    void wordCountArithmeticTestedWithIntegerMaxValueWithoutLargeAllocation() {
        // Test 31: Word-count arithmetic with Integer.MAX_VALUE
        // ((2147483647L + 63L) >>> 6) = 2147483710L >>> 6 = 33554432
        // Contrast with naive int: (2147483647 + 63) overflows to -2147483602 in 32-bit int!
        assertEquals(33554432, SidBitmap.computeWordCount(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.computeWordCount(-1));
        assertThrows(IllegalArgumentException.class, () -> SidBitmap.computeWordCount(-100));
    }
}
