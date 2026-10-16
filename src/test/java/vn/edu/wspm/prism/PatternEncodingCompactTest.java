package vn.edu.wspm.prism;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PatternEncodingCompactTest {

    @Test
    void compactArraysPreserveSparseSidsInteriorEmptyBlocksAndLongPositions() {
        var coordinates = PositionCoordinateSystem.of(25, 9, 4);
        var encoding = PatternEncoding.fromCompact(
                SequenceBlock.of(3, 1, 2), coordinates,
                new int[] {1, 2},
                new int[] {0, 2, 3},
                new int[] {0, 2, 1},
                new int[] {0b0000_0001, 0b0000_0010, 0b0000_0001});

        assertSame(coordinates, encoding.coordinates());
        assertEquals(2, encoding.compactSupportingSidCount());
        assertEquals(3, encoding.compactPositionRankCount());
        assertTrue(encoding.positionBlock(1).isSetAt(1));
        assertTrue(encoding.positionBlock(1).isSetAt(18));
        assertEquals(0, encoding.positionBlock(1).blockRank(1),
                "absolute block index must preserve the interior empty block");
        assertTrue(encoding.positionBlock(2).isSetAt(9));
        assertTrue(encoding.positionBlock(3).isEmpty());
    }

    @Test
    void publicCompactFactoryDefensivelyCopiesInputsAndAccessors() {
        var coordinates = PositionCoordinateSystem.of(9);
        int[] sids = {1};
        int[] offsets = {0, 1};
        int[] blocks = {1};
        int[] ranks = {1};
        var encoding = PatternEncoding.fromCompact(
                SequenceBlock.of(1, 1), coordinates, sids, offsets, blocks, ranks);

        sids[0] = 99;
        offsets[1] = 0;
        blocks[0] = 0;
        ranks[0] = 0;
        assertArrayEquals(new int[] {1}, encoding.supportingSids());
        assertArrayEquals(new int[] {0, 1}, encoding.positionOffsets());
        assertArrayEquals(new int[] {1}, encoding.positionBlockIndexes());
        assertArrayEquals(new int[] {1}, encoding.positionRanks());

        int[] exported = encoding.positionRanks();
        exported[0] = 255;
        assertArrayEquals(new int[] {1}, encoding.positionRanks());
        assertNotSame(exported, encoding.positionRanks());
    }

    @Test
    void compatibilityViewIsReadOnlyAndLogicallyEqualToFullEncoding() {
        var full = new PatternEncoding(
                SequenceBlock.of(2, 1),
                List.of(PositionBlock.of(1, 17, 1, 17), PositionBlock.empty(2, 5)));

        assertEquals(List.of(
                PositionBlock.of(1, 17, 1, 17), PositionBlock.empty(2, 5)),
                full.positionBlocks());
        assertThrows(UnsupportedOperationException.class,
                () -> full.positionBlocks().add(PositionBlock.empty(3, 0)));
    }

    @Test
    void rejectsMalformedOffsetsIndexesRanksPaddingAndMembership() {
        var coordinates = PositionCoordinateSystem.of(9);
        SequenceBlock supporting = SequenceBlock.of(1, 1);

        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                supporting, coordinates, new int[] {1}, new int[] {0},
                new int[] {0}, new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                supporting, coordinates, new int[] {1}, new int[] {0, Integer.MAX_VALUE},
                new int[] {0}, new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                supporting, coordinates, new int[] {1}, new int[] {0, 2},
                new int[] {1, 0}, new int[] {1, 1}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                supporting, coordinates, new int[] {1}, new int[] {0, 1},
                new int[] {0}, new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                supporting, coordinates, new int[] {1}, new int[] {0, 1},
                new int[] {1}, new int[] {2}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                SequenceBlock.empty(1), coordinates, new int[] {1}, new int[] {0, 1},
                new int[] {0}, new int[] {1}));
    }

    @Test
    void rejectsDuplicateSidsAndMismatchedCoordinateUniverse() {
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                SequenceBlock.of(2, 1), PositionCoordinateSystem.of(8),
                new int[] {1}, new int[] {0, 1}, new int[] {0}, new int[] {1}));
        assertThrows(IllegalArgumentException.class, () -> PatternEncoding.fromCompact(
                SequenceBlock.of(2, 1, 2), PositionCoordinateSystem.of(8, 8),
                new int[] {1, 1}, new int[] {0, 1, 2},
                new int[] {0, 0}, new int[] {1, 1}));
    }
}
