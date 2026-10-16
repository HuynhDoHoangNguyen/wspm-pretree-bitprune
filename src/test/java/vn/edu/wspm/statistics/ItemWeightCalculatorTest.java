package vn.edu.wspm.statistics;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class ItemWeightCalculatorTest {

    private static final double TOLERANCE = 1.0e-12;

    private final ItemStatisticsCalculator statisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator weightCalculator = new ItemWeightCalculator();

    @Test
    void reproducesEveryTableTwoWeightFromComputedStatistics()
            throws IOException, URISyntaxException {
        var result = calculateWeights(parseTableOne());
        var expectedNumerators = Map.of(
                1, 23L, 2, 12L, 3, 11L, 4, 5L,
                5, 4L, 6, 4L, 7, 6L, 8, 1L);

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8),
                new ArrayList<>(result.itemWeights().keySet()));
        expectedNumerators.forEach((itemId, numerator) -> assertEquals(
                (double) numerator / 66,
                result.weightFor(itemId).orElseThrow(),
                TOLERANCE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.itemWeights().put(9, 0.5));
    }

    @Test
    void computesMaxMinAndMeanFromWeightsRatherThanItemLabels() throws Exception {
        var result = calculateWeights(parseTableOne());
        double arithmeticMeanOfAllWeights = result.itemWeights().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();

        assertEquals(23.0 / 66, result.maxWeight().orElseThrow(), TOLERANCE);
        assertEquals(1.0 / 66, result.minWeight().orElseThrow(), TOLERANCE);
        assertEquals(12.0 / 66, result.meanWeight().orElseThrow(), TOLERANCE);
        assertNotEquals(arithmeticMeanOfAllWeights, result.meanWeight().orElseThrow(), TOLERANCE);
    }

    @Test
    void usesTotalDatabaseLengthRatherThanContainingSequenceLength() {
        var database = database(
                Sequence.of(Itemset.of(item(1))),
                Sequence.of(Itemset.of(item(2)), Itemset.of(item(2)), Itemset.of(item(2))));

        var result = calculateWeights(database);

        assertEquals(0.25, result.weightFor(1).orElseThrow(), TOLERANCE);
        assertEquals(0.75, result.weightFor(2).orElseThrow(), TOLERANCE);
        assertEquals(0.75, result.maxWeight().orElseThrow(), TOLERANCE);
        assertEquals(0.25, result.minWeight().orElseThrow(), TOLERANCE);
        assertEquals(0.5, result.meanWeight().orElseThrow(), TOLERANCE);
    }

    @Test
    void givesWeightOneForSingleItemDatabaseWithRepeatedOccurrences() {
        var result = calculateWeights(database(
                Sequence.of(Itemset.of(item(1)), Itemset.of(item(1)))));

        assertEquals(1.0, result.weightFor(1).orElseThrow(), TOLERANCE);
        assertEquals(1.0, result.maxWeight().orElseThrow(), TOLERANCE);
        assertEquals(1.0, result.minWeight().orElseThrow(), TOLERANCE);
        assertEquals(1.0, result.meanWeight().orElseThrow(), TOLERANCE);
    }

    @Test
    void preservesDoublePrecisionWithoutPaperDisplayRounding() throws Exception {
        var result = calculateWeights(parseTableOne());
        double weightA = result.weightFor(1).orElseThrow();

        assertEquals(23.0 / 66, weightA, TOLERANCE);
        assertNotEquals(0.348, weightA);
    }

    @Test
    void representsEmptyDatabaseWithoutFabricatedSummaryValues() {
        var result = calculateWeights(new SequenceDatabase(List.of()));

        assertTrue(result.itemWeights().isEmpty());
        assertTrue(result.maxWeight().isEmpty());
        assertTrue(result.minWeight().isEmpty());
        assertTrue(result.meanWeight().isEmpty());
        assertFalse(result.weightFor(1).isPresent());
    }

    @Test
    void rejectsInvalidStatisticsInsteadOfReturningNanOrInfinity() {
        var zeroDatabaseLength = new SequenceDatabaseStatistics(
                1, 0, Map.of(1, new ItemStatistics(1, 1, 1, 1)));
        var zeroContainingLength = new SequenceDatabaseStatistics(
                1, 1, Map.of(1, new ItemStatistics(1, 1, 1, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> weightCalculator.calculate(zeroDatabaseLength));
        assertThrows(IllegalArgumentException.class,
                () -> weightCalculator.calculate(zeroContainingLength));
    }

    private ItemWeightResult calculateWeights(SequenceDatabase database) {
        return weightCalculator.calculate(statisticsCalculator.calculate(database));
    }

    private static SequenceDatabase parseTableOne() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                ItemWeightCalculatorTest.class.getResource("/datasets/wspm/table1.spmf"));
        return new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new ArrayList<SequenceRecord>();
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static Item item(int id) {
        return new Item(id);
    }
}
