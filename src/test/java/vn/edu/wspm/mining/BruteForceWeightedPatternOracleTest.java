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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/** B18 exhaustive-oracle tests. No production mining or support component is used. */
class BruteForceWeightedPatternOracleTest {

    private static final double TOLERANCE = 1.0e-12;
    private static final MinimumSupport ACCEPT_ALL_OBSERVED = MinimumSupport.absolute(0.01);

    private final BruteForceWeightedPatternOracle oracle =
            new BruteForceWeightedPatternOracle();

    @Test
    void singleItemProducesExactlyOnePattern() {
        var actual = oracle.mine(database(sequence(itemset(1))), ACCEPT_ALL_OBSERVED);

        assertEquals(Map.of(pattern(itemset(1)), 1), actual);
    }

    @Test
    void preservesSequentialOrderAndRejectsReverseOrder() {
        var database = database(sequence(itemset(1), itemset(2)));
        var forward = pattern(itemset(1), itemset(2));
        var reverse = pattern(itemset(2), itemset(1));

        assertEquals(1, oracle.countSupport(database, forward));
        assertEquals(0, oracle.countSupport(database, reverse));
        assertTrue(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(forward));
        assertFalse(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(reverse));
    }

    @Test
    void distinguishesSameItemsetFromSeparateItemsets() {
        var database = database(sequence(itemset(1, 2)));
        var sameItemset = pattern(itemset(1, 2));
        var separateItemsets = pattern(itemset(1), itemset(2));

        assertEquals(1, oracle.countSupport(database, sameItemset));
        assertEquals(0, oracle.countSupport(database, separateItemsets));
        assertTrue(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(sameItemset));
        assertFalse(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(separateItemsets));
    }

    @Test
    void enumeratesNonContiguousSubsequences() {
        var database = database(sequence(itemset(1), itemset(2), itemset(3)));
        var gapped = pattern(itemset(1), itemset(3));

        assertEquals(1, oracle.countSupport(database, gapped));
        assertTrue(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(gapped));
    }

    @Test
    void preservesRepeatedItemsAcrossDifferentItemsets() {
        var database = database(sequence(itemset(1), itemset(1), itemset(2)));
        var repeated = pattern(itemset(1), itemset(1));
        var repeatedThenTwo = pattern(itemset(1), itemset(1), itemset(2));

        assertEquals(1, oracle.countSupport(database, repeated));
        assertEquals(1, oracle.countSupport(database, repeatedThenTwo));
        assertTrue(oracle.mine(database, ACCEPT_ALL_OBSERVED).keySet()
                .containsAll(List.of(repeated, repeatedThenTwo)));
    }

    @Test
    void countsOneSupportPerSidDespiteMultipleEmbeddings() {
        var database = database(sequence(itemset(1), itemset(1), itemset(1)));

        assertEquals(1, oracle.countSupport(database, pattern(itemset(1))));
        assertEquals(1, oracle.countSupport(database, pattern(itemset(1), itemset(1))));
    }

    @Test
    void countsSupportAcrossMultipleSids() {
        var database = database(
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(3)));
        var actual = oracle.mine(database, ACCEPT_ALL_OBSERVED);

        assertEquals(2, actual.get(pattern(itemset(1), itemset(2))));
        assertEquals(1, actual.get(pattern(itemset(1), itemset(3))));
        assertEquals(3, actual.get(pattern(itemset(1))));
    }

    @Test
    void enumeratesEveryNonEmptySubsetOfAnItemsetOnce() {
        var expected = new HashSet<>(List.of(
                itemset(1), itemset(2), itemset(3),
                itemset(1, 2), itemset(1, 3), itemset(2, 3),
                itemset(1, 2, 3)));
        var subsets = oracle.enumerateNonEmptySubsets(itemset(1, 2, 3));
        var actual = new HashSet<>(subsets);

        assertEquals(expected, actual);
        assertEquals(7, subsets.size());
        assertEquals(subsets.size(), actual.size());
        assertEquals(7, oracle.mine(
                database(sequence(itemset(1, 2, 3))), ACCEPT_ALL_OBSERVED).size());
    }

    @Test
    void independentlyCalculatesMeanWeightAndExactWeightedAcceptance() {
        var database = database(
                sequence(itemset(1)),
                sequence(itemset(1)),
                sequence(itemset(2)));
        double meanWeight = oracle.calculateMeanWeight(database).orElseThrow();
        var accepted = oracle.mine(database, MinimumSupport.absolute(0.75));

        assertEquals(0.5, meanWeight, TOLERANCE);
        assertEquals(2, oracle.countSupport(database, pattern(itemset(1))));
        assertEquals(1.0, 2 * meanWeight, TOLERANCE);
        assertEquals(1, oracle.countSupport(database, pattern(itemset(2))));
        assertEquals(0.5, 1 * meanWeight, TOLERANCE);
        assertEquals(Map.of(pattern(itemset(1)), 2), accepted);
    }

    @Test
    void acceptsThresholdEqualityWithoutEpsilonOrRounding() {
        var database = database(
                sequence(itemset(1)),
                sequence(itemset(1)),
                sequence(itemset(2)));

        var accepted = oracle.mine(database, MinimumSupport.absolute(0.5));

        assertEquals(0.5, oracle.calculateMeanWeight(database).orElseThrow(), TOLERANCE);
        assertEquals(1, accepted.get(pattern(itemset(2))));
    }

    @Test
    void emptyDatabaseReturnsEmptyWithoutInventingMeanWeight() {
        var empty = new SequenceDatabase(List.of());

        assertTrue(oracle.calculateMeanWeight(empty).isEmpty());
        assertEquals(Map.of(), oracle.mine(empty, MinimumSupport.absolute(0.5)));
        assertEquals(Map.of(), oracle.mine(empty, MinimumSupport.percentage(14.0)));
    }

    @Test
    void repeatedRunsHaveStableMapAndIterationOrder() {
        var database = database(
                sequence(itemset(2, 3), itemset(1)),
                sequence(itemset(1), itemset(3)));

        var first = oracle.mine(database, ACCEPT_ALL_OBSERVED);
        var second = oracle.mine(database, ACCEPT_ALL_OBSERVED);
        var third = oracle.mine(database, ACCEPT_ALL_OBSERVED);

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(new ArrayList<>(first.entrySet()), new ArrayList<>(second.entrySet()));
        assertEquals(new ArrayList<>(first.entrySet()), new ArrayList<>(third.entrySet()));
    }

    @Test
    void eliminatesDuplicatesBeforeCountingSidSupport() {
        var database = database(
                sequence(itemset(1), itemset(1)),
                sequence(itemset(1)));
        var observed = oracle.enumerateAllObservedPatterns(database);
        var singleton = pattern(itemset(1));

        assertEquals(1L, observed.stream().filter(singleton::equals).count());
        assertEquals(2, oracle.mine(database, ACCEPT_ALL_OBSERVED).get(singleton));
    }

    @Test
    void tableOneAtFourteenPercentExactlyMatchesB17PaperOracle()
            throws IOException, URISyntaxException {
        var resource = requireNonNull(getClass().getResource("/datasets/wspm/table1.spmf"));
        var database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));

        var actual = oracle.mine(database, MinimumSupport.percentage(14.0));

        assertEquals(tableOnePaperOracle(), actual);
        assertEquals(9, actual.size());
    }

    @Test
    void negativePatternHasZeroSupportAndIsNeverEnumerated() {
        var database = database(sequence(itemset(1), itemset(2)));
        var absent = pattern(itemset(3));

        assertEquals(0, oracle.countSupport(database, absent));
        assertFalse(oracle.enumerateAllObservedPatterns(database).contains(absent));
        assertFalse(oracle.mine(database, ACCEPT_ALL_OBSERVED).containsKey(absent));
    }

    @Test
    void failsFastBeforeSilentlyTruncatingOversizedEnumeration() {
        var itemsets = IntStream.rangeClosed(1, 20)
                .mapToObj(id -> itemset(id))
                .toArray(Itemset[]::new);
        var database = database(Sequence.of(itemsets));

        var error = assertThrows(IllegalArgumentException.class,
                () -> oracle.mine(database, ACCEPT_ALL_OBSERVED));

        assertTrue(error.getMessage().contains("sequence limit exceeded"));
        assertTrue(error.getMessage().contains("length=20"));
    }

    @Test
    void rejectsZeroEffectiveThresholdBecauseR6RemainsOpen() {
        var database = database(sequence(itemset(1)));

        var error = assertThrows(IllegalArgumentException.class,
                () -> oracle.mine(database, MinimumSupport.absolute(0.0)));

        assertTrue(error.getMessage().contains("R6"));
    }

    private static Map<Pattern, Integer> tableOnePaperOracle() {
        var expected = new LinkedHashMap<Pattern, Integer>();
        expected.put(pattern(itemset(1)), 7);
        expected.put(pattern(itemset(1), itemset(1)), 6);
        expected.put(pattern(itemset(1), itemset(2)), 7);
        expected.put(pattern(itemset(1), itemset(3)), 6);
        expected.put(pattern(itemset(1, 2)), 7);
        expected.put(pattern(itemset(1, 2), itemset(3)), 6);
        expected.put(pattern(itemset(2)), 7);
        expected.put(pattern(itemset(2), itemset(3)), 6);
        expected.put(pattern(itemset(3)), 6);
        return expected;
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new ArrayList<SequenceRecord>(sequences.length);
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static Sequence sequence(Itemset... itemsets) {
        return Sequence.of(itemsets);
    }

    private static Pattern pattern(Itemset... itemsets) {
        return Pattern.of(itemsets);
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(IntStream.of(ids).mapToObj(Item::new).toList());
    }
}
