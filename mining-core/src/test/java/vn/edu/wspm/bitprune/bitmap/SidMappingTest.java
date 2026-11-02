package vn.edu.wspm.bitprune.bitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class SidMappingTest {

    private static final Item ITEM = new Item(1);

    private static Sequence dummySequence() {
        return Sequence.of(Itemset.of(ITEM));
    }

    private static SequenceRecord record(int sid) {
        return new SequenceRecord(sid, dummySequence());
    }

    private static SequenceDatabase db(int... sids) {
        var list = new ArrayList<SequenceRecord>();
        for (int sid : sids) {
            list.add(record(sid));
        }
        return new SequenceDatabase(list);
    }

    @Test
    void rejectsNullDatabase() {
        assertThrows(NullPointerException.class, () -> SidMapping.of(null));
    }

    @Test
    void handlesEmptyDatabase() {
        var mapping = SidMapping.of(db());

        assertEquals(0, mapping.size());
        assertEquals(0, mapping.actualSids().length);
    }

    @Test
    void handlesSingleSequenceDatabase() {
        var mapping = SidMapping.of(db(42));

        assertEquals(1, mapping.size());
        assertEquals(0, mapping.sidToIndex(42));
        assertEquals(42, mapping.indexToSid(0));
        assertArrayEquals(new int[] { 42 }, mapping.actualSids());
    }

    @Test
    void handlesNegativeZeroPositiveAndNoncontiguousSids() {
        var mapping = SidMapping.of(db(-5, 0, 12, 100));

        assertEquals(4, mapping.size());
        assertEquals(0, mapping.sidToIndex(-5));
        assertEquals(1, mapping.sidToIndex(0));
        assertEquals(2, mapping.sidToIndex(12));
        assertEquals(3, mapping.sidToIndex(100));

        assertEquals(-5, mapping.indexToSid(0));
        assertEquals(0, mapping.indexToSid(1));
        assertEquals(12, mapping.indexToSid(2));
        assertEquals(100, mapping.indexToSid(3));

        assertArrayEquals(new int[] { -5, 0, 12, 100 }, mapping.actualSids());
    }

    @Test
    void mapsAccordingToRecordOrderRatherThanNaturalSort() {
        var mapping = SidMapping.of(db(100, -5, 20, 0));

        assertEquals(4, mapping.size());
        assertEquals(0, mapping.sidToIndex(100));
        assertEquals(1, mapping.sidToIndex(-5));
        assertEquals(2, mapping.sidToIndex(20));
        assertEquals(3, mapping.sidToIndex(0));

        assertEquals(100, mapping.indexToSid(0));
        assertEquals(-5, mapping.indexToSid(1));
        assertEquals(20, mapping.indexToSid(2));
        assertEquals(0, mapping.indexToSid(3));

        assertArrayEquals(new int[] { 100, -5, 20, 0 }, mapping.actualSids());
    }

    @Test
    void verifiesRoundTripForEverySidAndIndex() {
        int[] sids = { 99, -10, 0, 5, 12345, -999 };
        var mapping = SidMapping.of(db(sids));

        for (int i = 0; i < mapping.size(); i++) {
            int sid = mapping.indexToSid(i);
            assertEquals(i, mapping.sidToIndex(sid));
        }

        for (int sid : mapping.actualSids()) {
            int idx = mapping.sidToIndex(sid);
            assertEquals(sid, mapping.indexToSid(idx));
        }
    }

    @Test
    void rejectsUnknownSid() {
        var mapping = SidMapping.of(db(10, 20, 30));

        var ex = assertThrows(IllegalArgumentException.class, () -> mapping.sidToIndex(999));
        assertEquals("SID not found in universe: 999", ex.getMessage());
    }

    @Test
    void rejectsNegativeIndexAndIndexEqualToSize() {
        var mapping = SidMapping.of(db(10, 20, 30));

        assertThrows(IndexOutOfBoundsException.class, () -> mapping.indexToSid(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.indexToSid(3));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.indexToSid(4));
    }

    @Test
    void duplicateSidRejectedBySequenceDatabaseAtDomainBoundary() {
        assertThrows(IllegalArgumentException.class, () -> db(1, 2, 1));
    }

    @Test
    void actualSidsReturnsDefensiveCopy() {
        var mapping = SidMapping.of(db(10, 20, 30));
        int[] copy = mapping.actualSids();
        copy[0] = 999;

        assertEquals(10, mapping.indexToSid(0));
        assertArrayEquals(new int[] { 10, 20, 30 }, mapping.actualSids());
    }

    @Test
    void validatesEqualsAndHashCodeContract() {
        var mapping1 = SidMapping.of(db(1, 2, 3));
        var mapping2 = SidMapping.of(db(1, 2, 3));
        var mappingDifferentSids = SidMapping.of(db(1, 2, 4));
        var mappingDifferentOrder = SidMapping.of(db(3, 2, 1));

        assertEquals(mapping1, mapping2);
        assertEquals(mapping1.hashCode(), mapping2.hashCode());

        assertNotEquals(mapping1, mappingDifferentSids);
        assertNotEquals(mapping1, mappingDifferentOrder);

        var empty1 = SidMapping.of(db());
        var empty2 = SidMapping.of(db());
        assertEquals(empty1, empty2);
        assertEquals(empty1.hashCode(), empty2.hashCode());
        assertNotEquals(empty1, mapping1);
    }
}
