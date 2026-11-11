package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.statistics.ItemWeightResult;
import vn.edu.wspm.statistics.SequenceDatabaseStatistics;

class F1BuilderTest {

    private static final double TOLERANCE = 1.0e-12;

    private final ItemStatisticsCalculator statisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator weightCalculator = new ItemWeightCalculator();
    private final MinimumSupportResolver minimumSupportResolver = new MinimumSupportResolver();
    private final F1Builder builder = new F1Builder();

    @Test
    void reproducesExactTableOneF1AndEvaluatesEveryItem()
            throws IOException, URISyntaxException {
        var statistics = statisticsCalculator.calculate(parseTableOne());
        var weights = weightCalculator.calculate(statistics);
        var minimumSupport = minimumSupportResolver.resolve(
                MinimumSupport.percentage(14.0), statistics.numberOfSequences());
        double meanWeight = weights.meanWeight().orElseThrow();

        var result = builder.build(statistics, weights, minimumSupport);

        assertEquals(List.of(
                new WeightedOnePattern(1, 7),
                new WeightedOnePattern(2, 7),
                new WeightedOnePattern(3, 6)), result);
        assertEquals(3, result.size());

        var expectedSupports = List.of(7, 7, 6, 5, 4, 3, 2, 1);
        var expectedWeightedScores = List.of(
                84.0 / 66.0,
                84.0 / 66.0,
                72.0 / 66.0,
                60.0 / 66.0,
                48.0 / 66.0,
                36.0 / 66.0,
                24.0 / 66.0,
                12.0 / 66.0);
        var expectedDecisions = List.of(true, true, true, false, false, false, false, false);
        for (int itemId = 1; itemId <= 8; itemId++) {
            int support = statistics.statisticsFor(itemId).orElseThrow().sequenceSupport();
            assertEquals(expectedSupports.get(itemId - 1), support, "support for item " + itemId);
            assertEquals(
                    expectedWeightedScores.get(itemId - 1),
                    support * meanWeight,
                    TOLERANCE,
                    "weighted score for item " + itemId);
            assertEquals(
                    expectedDecisions.get(itemId - 1),
                    minimumSupport.meetsThreshold(support, meanWeight),
                    "acceptance for item " + itemId);
        }
        assertEquals(12.0 / 66.0, meanWeight, TOLERANCE);
        assertEquals(0.98, minimumSupport.effectiveThreshold(), TOLERANCE);
    }

    @Test
    void usesSequenceSupportRatherThanOccurrenceCount() {
        var inputs = inputsForDiscriminatorDatabase(1.0);
        var itemOne = inputs.statistics().statisticsFor(1).orElseThrow();

        assertEquals(3, itemOne.occurrenceCount());
        assertEquals(1, itemOne.sequenceSupport());
        assertTrue(itemOne.occurrenceCount()
                * inputs.weights().meanWeight().orElseThrow() >= 1.0);
        assertEquals(List.of(), builder.build(
                inputs.statistics(), inputs.weights(), inputs.minimumSupport()));
    }

    @Test
    void usesGlobalMeanWeightRatherThanCurrentItemWeight() {
        var inputs = inputsForDiscriminatorDatabase(0.6);

        assertEquals(0.75, inputs.weights().weightFor(1).orElseThrow(), TOLERANCE);
        assertEquals(0.5, inputs.weights().meanWeight().orElseThrow(), TOLERANCE);
        assertTrue(inputs.weights().weightFor(1).orElseThrow() >= 0.6);
        assertEquals(List.of(), builder.build(
                inputs.statistics(), inputs.weights(), inputs.minimumSupport()));
    }

    @Test
    void acceptsEqualityAndPreservesAbsoluteThresholdWithoutScaling() {
        var inputs = inputsForDiscriminatorDatabase(0.5);

        assertEquals(0.5, inputs.minimumSupport().effectiveThreshold());
        assertEquals(List.of(
                new WeightedOnePattern(1, 1),
                new WeightedOnePattern(2, 1)),
                builder.build(inputs.statistics(), inputs.weights(), inputs.minimumSupport()));
    }

    @Test
    void zeroThresholdReturnsOnlyExistingItemsInAscendingOrder() {
        var database = database(Sequence.of(Itemset.of(item(10), item(2))));
        var inputs = inputs(database, MinimumSupport.absolute(0.0));

        var result = builder.build(
                inputs.statistics(), inputs.weights(), inputs.minimumSupport());

        assertEquals(List.of(
                new WeightedOnePattern(2, 1),
                new WeightedOnePattern(10, 1)), result);
        assertFalse(result.stream().anyMatch(pattern -> pattern.itemId() == 3));
        assertThrows(UnsupportedOperationException.class,
                () -> result.add(new WeightedOnePattern(3, 1)));
    }

    @Test
    void returnsEmptyF1ForConsistentEmptyInputsWithoutInventingMeanWeight() {
        var inputs = inputs(
                new SequenceDatabase(List.of()), MinimumSupport.absolute(0.0));

        assertTrue(inputs.weights().meanWeight().isEmpty());
        assertEquals(List.of(), builder.build(
                inputs.statistics(), inputs.weights(), inputs.minimumSupport()));
    }

    @Test
    void rejectsInconsistentInputs() {
        var oneItem = inputs(
                database(Sequence.of(Itemset.of(item(1)))),
                MinimumSupport.absolute(0.0));
        var twoItemStatistics = statisticsCalculator.calculate(
                database(Sequence.of(Itemset.of(item(1), item(2)))));
        var wrongSequenceCount = minimumSupportResolver.resolve(
                MinimumSupport.absolute(0.0), 2);

        assertThrows(NullPointerException.class,
                () -> builder.build(null, oneItem.weights(), oneItem.minimumSupport()));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(
                        oneItem.statistics(), oneItem.weights(), wrongSequenceCount));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(
                        twoItemStatistics, oneItem.weights(), oneItem.minimumSupport()));
    }

    private Inputs inputsForDiscriminatorDatabase(double absoluteThreshold) {
        return inputs(database(
                Sequence.of(Itemset.of(item(1)), Itemset.of(item(1)), Itemset.of(item(1))),
                Sequence.of(Itemset.of(item(2)))),
                MinimumSupport.absolute(absoluteThreshold));
    }

    private Inputs inputs(SequenceDatabase database, MinimumSupport minimumSupport) {
        var statistics = statisticsCalculator.calculate(database);
        var weights = weightCalculator.calculate(statistics);
        var resolvedMinimumSupport = minimumSupportResolver.resolve(
                minimumSupport, statistics.numberOfSequences());
        return new Inputs(statistics, weights, resolvedMinimumSupport);
    }

    private static SequenceDatabase parseTableOne() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                F1BuilderTest.class.getResource("/datasets/wspm/table1.spmf"));
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

    private record Inputs(
            SequenceDatabaseStatistics statistics,
            ItemWeightResult weights,
            vn.edu.wspm.config.ResolvedMinimumSupport minimumSupport) {
    }
}
