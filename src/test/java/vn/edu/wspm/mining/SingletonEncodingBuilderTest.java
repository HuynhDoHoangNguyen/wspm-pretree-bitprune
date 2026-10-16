package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class SingletonEncodingBuilderTest {

    private final SingletonEncodingBuilder builder = new SingletonEncodingBuilder();

    @Test
    void buildsExactFullPositionAndSequenceBlocksInF1Order() {
        var database = database(
                Sequence.of(itemset(1), itemset(2), itemset(1, 2)),
                Sequence.of(itemset(2)));

        var result = builder.build(database, List.of(
                new WeightedOnePattern(2, 2),
                new WeightedOnePattern(1, 1)));

        assertEquals(List.of(1, 2), result.keySet().stream().toList());
        assertEquals(1, result.get(1).support());
        assertEquals(2, result.get(2).support());
        assertEquals(List.of(true, false, true), positions(result.get(1), 1));
        assertEquals(List.of(false, true, true), positions(result.get(2), 1));
        assertEquals(List.of(true), positions(result.get(2), 2));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void rejectsNonContiguousSidCoordinatesAndF1SupportMismatch() {
        var wrongSid = new SequenceDatabase(List.of(
                new SequenceRecord(2, Sequence.of(itemset(1)))));
        var database = database(Sequence.of(itemset(1)));

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(wrongSid, List.of(new WeightedOnePattern(1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(database, List.of(new WeightedOnePattern(1, 2))));
    }

    private static List<Boolean> positions(
            vn.edu.wspm.prism.PatternEncoding encoding, int sid) {
        var block = encoding.positionBlock(sid);
        var values = new java.util.ArrayList<Boolean>();
        for (int position = 1; position <= block.sequenceSize(); position++) {
            values.add(block.isSetAt(position));
        }
        return List.copyOf(values);
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new java.util.ArrayList<SequenceRecord>();
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(java.util.Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
