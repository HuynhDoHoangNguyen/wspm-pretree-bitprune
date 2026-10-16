package vn.edu.wspm.prism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PrimeBlockJoinsTest {

    @Test
    void prismSiblingExampleIJoinRetainsPositionsOneAndFour() {
        // PRISM Sec. 3.3: P_a={1,4,6}, P_b={1,2,3,4,5} for the example SID.
        PatternEncoding result = PrimeBlockJoins.iJoin(
                encoding(PositionBlock.of(1, 8, 1, 4, 6)),
                encoding(PositionBlock.of(1, 8, 1, 2, 3, 4, 5)));

        assertPositions(result.positionBlock(1), 1, 4);
        assertEquals(14, result.positionBlock(1).blockProduct(0));
    }

    @Test
    void prismSiblingExampleSJoinRetainsPositionsTwoThroughFive() {
        // The first a endpoint is 1, so all b endpoints strictly after it remain.
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 8, 1, 4, 6)),
                encoding(PositionBlock.of(1, 8, 1, 2, 3, 4, 5)));

        assertPositions(result.positionBlock(1), 2, 3, 4, 5);
        assertEquals(1, result.support());
    }

    @Test
    void iJoinRetainsOnlyCommonEndpoints() {
        PatternEncoding result = PrimeBlockJoins.iJoin(
                encoding(PositionBlock.of(1, 5, 2, 5)),
                encoding(PositionBlock.of(1, 5, 1, 2, 4)));

        assertPositions(result.positionBlock(1), 2);
        assertEquals(1, result.support());
    }

    @Test
    void iJoinDropsSidWhenOperandsHaveDifferentEndpoints() {
        PatternEncoding result = PrimeBlockJoins.iJoin(
                encoding(PositionBlock.of(1, 3, 2)),
                encoding(PositionBlock.of(1, 3, 3)));

        assertTrue(result.positionBlock(1).isEmpty());
        assertFalse(result.sequenceBlock().isSupporting(1));
        assertEquals(0, result.support());
    }

    @Test
    void iJoinRetainsEndpointsAcrossBlockBoundary() {
        PatternEncoding result = PrimeBlockJoins.iJoin(
                encoding(PositionBlock.of(1, 9, 2, 5, 9)),
                encoding(PositionBlock.of(1, 9, 2, 4, 9)));

        assertPositions(result.positionBlock(1), 2, 9);
    }

    @Test
    void iJoinRebuildsSequenceBlockAfterPositionVerification() {
        PatternEncoding left = encoding(
                PositionBlock.of(1, 4, 2),
                PositionBlock.of(2, 4, 2),
                PositionBlock.of(3, 4, 2));
        PatternEncoding right = encoding(
                PositionBlock.of(1, 4, 2),
                PositionBlock.of(2, 4, 3),
                PositionBlock.empty(3, 4));

        PatternEncoding result = PrimeBlockJoins.iJoin(left, right);

        assertSupportingSids(result, 1);
        assertPositions(result.positionBlock(1), 2);
        assertTrue(result.positionBlock(2).isEmpty());
        assertTrue(result.positionBlock(3).isEmpty());
        assertEquals(result.sequenceBlock().support(), result.support());
    }

    @Test
    void sJoinUsesStrictlyLaterEndpoints() {
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 5, 2)),
                encoding(PositionBlock.of(1, 5, 1, 2, 3, 5)));

        assertPositions(result.positionBlock(1), 3, 5);
    }

    @Test
    void sJoinUsesEarliestOfMultipleLeftEndpoints() {
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 7, 2, 6)),
                encoding(PositionBlock.of(1, 7, 3, 5, 7)));

        assertPositions(result.positionBlock(1), 3, 5, 7);
    }

    @Test
    void sJoinCrossesBoundaryFromPositionEightToNine() {
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 12, 8)),
                encoding(PositionBlock.of(1, 12, 9)));

        assertPositions(result.positionBlock(1), 9);
        assertEquals(1, result.support());
    }

    @Test
    void sJoinRejectsEqualPosition() {
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 12, 8)),
                encoding(PositionBlock.of(1, 12, 8)));

        assertTrue(result.sequenceBlock().isEmpty());
    }

    @Test
    void sJoinRejectsEarlierRightPosition() {
        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 12, 8)),
                encoding(PositionBlock.of(1, 12, 7)));

        assertTrue(result.sequenceBlock().isEmpty());
    }

    @Test
    void strictAfterSpansAtLeastThreeBlocksAndNeverSetsPadding() {
        PositionBlock mask = PrimeBlockJoins.strictAfter(PositionBlock.of(1, 20, 7));

        for (int position = 1; position <= 7; position++) {
            assertFalse(mask.isSetAt(position));
        }
        for (int position = 8; position <= 20; position++) {
            assertTrue(mask.isSetAt(position));
        }
        assertEquals(0b0000_1111, mask.blockRank(2),
                "Only valid positions 17..20 may be set in the final block");

        PatternEncoding result = PrimeBlockJoins.sJoin(
                encoding(PositionBlock.of(1, 20, 7)),
                encoding(PositionBlock.of(1, 20, 6, 7, 8, 9, 16, 17, 20)));
        assertPositions(result.positionBlock(1), 8, 9, 16, 17, 20);
    }

    @Test
    void strictAfterOfEmptyLeftIsEmpty() {
        PositionBlock mask = PrimeBlockJoins.strictAfter(PositionBlock.empty(1, 20));
        assertTrue(mask.isEmpty());
    }

    @Test
    void emptyRightPositionBlockCannotKeepSidInEitherJoin() {
        PatternEncoding left = encoding(PositionBlock.of(1, 5, 2));
        PatternEncoding right = encoding(PositionBlock.empty(1, 5));

        assertEquals(0, PrimeBlockJoins.iJoin(left, right).support());
        assertEquals(0, PrimeBlockJoins.sJoin(left, right).support());
    }

    @Test
    void supportCountsValidSidsNotEndpointsOrPrefilterSids() {
        PatternEncoding left = encoding(
                PositionBlock.of(1, 10, 2, 5),
                PositionBlock.of(2, 10, 8),
                PositionBlock.of(3, 10, 4));
        PatternEncoding right = encoding(
                PositionBlock.of(1, 10, 3, 6, 9),
                PositionBlock.of(2, 10, 8),
                PositionBlock.of(3, 10, 2));

        PatternEncoding result = PrimeBlockJoins.sJoin(left, right);

        assertSupportingSids(result, 1);
        assertPositions(result.positionBlock(1), 3, 6, 9);
        assertEquals(1, result.support());
        assertEquals(result.sequenceBlock().support(), result.support());
    }

    @Test
    void rejectsDifferentSidUniverses() {
        PatternEncoding oneSid = encoding(PositionBlock.of(1, 3, 1));
        PatternEncoding twoSids = encoding(
                PositionBlock.of(1, 3, 1), PositionBlock.of(2, 3, 1));

        assertThrows(IllegalArgumentException.class,
                () -> PrimeBlockJoins.iJoin(oneSid, twoSids));
    }

    @Test
    void rejectsDifferentPositionCoordinateSystems() {
        PatternEncoding sizeEight = encoding(PositionBlock.of(1, 8, 1));
        PatternEncoding sizeNine = encoding(PositionBlock.of(1, 9, 1));

        assertThrows(IllegalArgumentException.class,
                () -> PrimeBlockJoins.sJoin(sizeEight, sizeNine));
    }

    @Test
    void patternEncodingRejectsMembershipPositionMismatch() {
        assertThrows(IllegalArgumentException.class, () -> new PatternEncoding(
                SequenceBlock.of(1, 1), List.of(PositionBlock.empty(1, 3))));
        assertThrows(IllegalArgumentException.class, () -> new PatternEncoding(
                SequenceBlock.empty(1), List.of(PositionBlock.of(1, 3, 2))));
    }

    private static PatternEncoding encoding(PositionBlock... positions) {
        int[] supportingSids = java.util.Arrays.stream(positions)
                .filter(position -> !position.isEmpty())
                .mapToInt(PositionBlock::sid)
                .toArray();
        return new PatternEncoding(
                SequenceBlock.of(positions.length, supportingSids), List.of(positions));
    }

    private static void assertSupportingSids(PatternEncoding encoding, int... expectedSids) {
        var expected = java.util.Arrays.stream(expectedSids).boxed().toList();
        var actual = new java.util.ArrayList<Integer>();
        for (int sid = 1; sid <= encoding.sequenceBlock().sidCount(); sid++) {
            if (encoding.sequenceBlock().isSupporting(sid)) {
                actual.add(sid);
            }
        }
        assertEquals(expected, actual);
    }

    private static void assertPositions(PositionBlock positions, int... expectedPositions) {
        var expected = java.util.Arrays.stream(expectedPositions).boxed().toList();
        var actual = new java.util.ArrayList<Integer>();
        for (int position = 1; position <= positions.sequenceSize(); position++) {
            if (positions.isSetAt(position)) {
                actual.add(position);
            }
        }
        assertEquals(expected, actual);
    }
}
