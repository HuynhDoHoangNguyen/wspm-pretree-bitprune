package vn.edu.wspm.statistics;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

class ItemStatisticsTest {

    private final ItemStatisticsCalculator calculator = new ItemStatisticsCalculator();

    @Test
    void matchesTableOneStatisticsForEveryItem() throws IOException, URISyntaxException {
        var result = calculator.calculate(parseTableOne());

        assertEquals(Map.of(
                1, statistics(1, 23, 7, 66),
                2, statistics(2, 12, 7, 66),
                3, statistics(3, 11, 6, 57),
                4, statistics(4, 5, 5, 48),
                5, statistics(5, 4, 4, 39),
                6, statistics(6, 4, 3, 28),
                7, statistics(7, 6, 2, 21),
                8, statistics(8, 1, 1, 12)),
                result.itemStatistics());
        var itemA = result.statisticsFor(1).orElseThrow();
        assertEquals(statistics(1, 23, 7, 66), itemA);
        assertNotEquals(itemA.occurrenceCount(), (long) itemA.sequenceSupport());
        assertEquals(7, result.numberOfSequences());
        assertEquals(8, result.numberOfDistinctItems());
        assertEquals(66, result.totalDatabaseLength());
    }

    @Test
    void countsRepeatedItemOccurrencesButOnlyOneSupportPerSequence() {
        var result = calculator.calculate(database(
                Sequence.of(Itemset.of(item(1)), Itemset.of(item(1)), Itemset.of(item(1)))));

        assertEquals(statistics(1, 3, 1, 3), result.statisticsFor(1).orElseThrow());
    }

    @Test
    void countsSameItemOnceAcrossMultipleSequences() {
        var result = calculator.calculate(database(
                Sequence.of(Itemset.of(item(1))),
                Sequence.of(Itemset.of(item(1), item(2)))));

        assertEquals(statistics(1, 2, 2, 3), result.statisticsFor(1).orElseThrow());
    }

    @Test
    void handlesMixedOccurrencesAndContainingSequenceLengths() {
        var result = calculator.calculate(database(
                Sequence.of(Itemset.of(item(1)), Itemset.of(item(1))),
                Sequence.of(Itemset.of(item(1), item(2)), Itemset.of(item(2)))));

        assertEquals(statistics(1, 3, 2, 5), result.statisticsFor(1).orElseThrow());
        assertEquals(statistics(2, 2, 1, 3), result.statisticsFor(2).orElseThrow());
        assertEquals(5, result.totalDatabaseLength());
    }

    @Test
    void returnsEmptyDeterministicResultForEmptyDatabaseAndAbsentItem() {
        var result = calculator.calculate(new SequenceDatabase(List.of()));

        assertEquals(0, result.numberOfSequences());
        assertEquals(0, result.numberOfDistinctItems());
        assertEquals(0, result.totalDatabaseLength());
        assertEquals(Map.of(), result.itemStatistics());
        assertFalse(result.statisticsFor(99).isPresent());
    }

    @Test
    void exposesImmutableStatisticsInAscendingNumericItemOrder() {
        var result = calculator.calculate(database(
                Sequence.of(Itemset.of(item(10)), Itemset.of(item(2)))));

        assertEquals(List.of(2, 10), new ArrayList<>(result.itemStatistics().keySet()));
        assertFalse(result.statisticsFor(99).isPresent());
        assertThrows(UnsupportedOperationException.class,
                () -> result.itemStatistics().put(3, statistics(3, 1, 1, 1)));
    }

    private static SequenceDatabase parseTableOne() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                ItemStatisticsTest.class.getResource("/datasets/wspm/table1.spmf"));
        return new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new ArrayList<SequenceRecord>();
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static ItemStatistics statistics(
            int itemId,
            long occurrenceCount,
            int sequenceSupport,
            long sumOfContainingSequenceLengths) {
        return new ItemStatistics(
                itemId, occurrenceCount, sequenceSupport, sumOfContainingSequenceLengths);
    }

    private static Item item(int id) {
        return new Item(id);
    }
}
