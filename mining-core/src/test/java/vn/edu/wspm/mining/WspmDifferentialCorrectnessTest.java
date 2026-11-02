package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
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

/** B19 production-versus-brute-force differential correctness suite. */
class WspmDifferentialCorrectnessTest {

    private static final double TOLERANCE = 1.0e-12;
    private static final long RANDOM_SEED = 20_261_016L;
    private static final int RANDOM_CASE_COUNT = 32;
    private static final List<MinimumSupport> RANDOM_THRESHOLDS = List.of(
            MinimumSupport.absolute(0.05),
            MinimumSupport.absolute(0.5),
            MinimumSupport.absolute(10.0),
            MinimumSupport.percentage(25.0));

    private final BruteForceWeightedPatternOracle oracle =
            new BruteForceWeightedPatternOracle();
    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();

    @Test
    void singleSequenceMatchesExactly() {
        var database = database(sequence(itemset(1, 2), itemset(3), itemset(3, 4)));

        var outcome = assertDifferential(
                "fixed-single-mixed-i-s", -1L, database, MinimumSupport.absolute(0.05));

        assertTrue(outcome.actual().containsKey(pattern(itemset(1, 2))));
        assertTrue(outcome.actual().containsKey(
                pattern(itemset(1, 2), itemset(3), itemset(3, 4))));
    }

    @Test
    void multipleSequencesMatchMapAndSupportingSids() {
        var database = database(
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(3)));

        var outcome = assertDifferential(
                "fixed-multiple-sids", -1L, database, MinimumSupport.absolute(0.1));

        assertEquals(2, outcome.actual().get(pattern(itemset(1), itemset(2))));
        assertEquals(1, outcome.actual().get(pattern(itemset(1), itemset(3))));
        assertEquals(3, outcome.actual().get(pattern(itemset(1))));
    }

    @Test
    void sparseDatabaseHasNoFalsePositivePattern() {
        var database = database(
                sequence(itemset(1), itemset(2)),
                sequence(itemset(3), itemset(4)),
                sequence(itemset(1), itemset(4)));

        var outcome = assertDifferential(
                "fixed-sparse", -1L, database, MinimumSupport.absolute(0.2));

        assertFalse(outcome.actual().containsKey(pattern(itemset(2), itemset(3))));
        assertFalse(outcome.actual().containsKey(pattern(itemset(3), itemset(1))));
    }

    @Test
    void denseDatabaseRetainsAllAcceptedIExtensionsWithoutDuplicates() {
        var database = database(
                sequence(itemset(1, 2, 3), itemset(1, 2, 3)),
                sequence(itemset(1, 2, 3)));

        var outcome = assertDifferential(
                "fixed-dense", -1L, database, MinimumSupport.absolute(0.2));

        assertEquals(1, outcome.actual().get(pattern(itemset(1, 2, 3), itemset(1, 2, 3))));
        assertEquals(outcome.actual().size(), new HashSet<>(outcome.actual().keySet()).size());
    }

    @Test
    void repeatedItemsAndMultipleEmbeddingsMatchExactly() {
        var database = database(
                sequence(itemset(1), itemset(1), itemset(1)),
                sequence(itemset(1), itemset(1)));

        var outcome = assertDifferential(
                "fixed-repeated", -1L, database, MinimumSupport.absolute(0.25));

        assertEquals(2, outcome.actual().get(pattern(itemset(1), itemset(1))));
        assertEquals(1, outcome.actual().get(
                pattern(itemset(1), itemset(1), itemset(1))));
    }

    @Test
    void sameItemsetTemporalOrderAndGappedPatternsRemainDistinct() {
        var database = database(
                sequence(itemset(1, 2), itemset(1), itemset(2), itemset(3)),
                sequence(itemset(1), itemset(2), itemset(1), itemset(3)));

        var outcome = assertDifferential(
                "fixed-structural", -1L, database, MinimumSupport.absolute(0.1));

        assertEquals(1, outcome.actual().get(pattern(itemset(1, 2))));
        assertEquals(2, outcome.actual().get(pattern(itemset(1), itemset(2))));
        assertEquals(2, outcome.actual().get(pattern(itemset(1), itemset(3))));
        assertEquals(1, outcome.actual().get(
                pattern(itemset(1), itemset(2), itemset(1))));
    }

    @Test
    void missingF1ItemsAndRejectedBranchesDoNotLoseAcceptedResults() {
        var database = database(
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(2)),
                sequence(itemset(1), itemset(3)));

        var outcome = assertDifferential(
                "fixed-missing-f1-rejected-branch",
                -1L,
                database,
                MinimumSupport.absolute(0.8));

        assertEquals(Map.of(pattern(itemset(1)), 3), outcome.actual());
        assertFalse(outcome.result().prefixTree().find(pattern(itemset(1), itemset(2))).isPresent());
    }

    @Test
    void sidBoundaryEightToNineMatchesMapAndMembership() {
        var sequences = new ArrayList<Sequence>();
        for (int sid = 1; sid <= 7; sid++) {
            sequences.add(sequence(itemset(1)));
        }
        sequences.add(sequence(itemset(1, 2)));
        sequences.add(sequence(itemset(1, 2)));
        var database = database(sequences.toArray(Sequence[]::new));

        var outcome = assertDifferential(
                "fixed-sid-8-to-9", -1L, database, MinimumSupport.absolute(0.5));
        var pair = pattern(itemset(1, 2));

        assertEquals(2, outcome.result().encoding(pair).sequenceBlock().blockCount());
        assertFalse(outcome.result().encoding(pair).sequenceBlock().isSupporting(7));
        assertTrue(outcome.result().encoding(pair).sequenceBlock().isSupporting(8));
        assertTrue(outcome.result().encoding(pair).sequenceBlock().isSupporting(9));
    }

    @Test
    void positionBoundaryEightToNineMatchesEndpoint() {
        var itemsets = new ArrayList<Itemset>();
        for (int position = 1; position <= 8; position++) {
            itemsets.add(itemset(1));
        }
        itemsets.add(itemset(2));
        var database = database(sequence(itemsets.toArray(Itemset[]::new)));

        var outcome = assertDifferential(
                "fixed-position-8-to-9", -1L, database, MinimumSupport.absolute(0.25));
        var oneThenTwo = pattern(itemset(1), itemset(2));
        var positions = outcome.result().encoding(oneThenTwo).positionBlock(1);

        assertEquals(2, positions.blockCount());
        assertTrue(positions.isSetAt(9));
    }

    @Test
    void moreThanSixteenPositionsUseThreeBlocksAndMatchExactly() {
        var repeated = IntStream.rangeClosed(1, 17)
                .mapToObj(ignored -> itemset(1))
                .toArray(Itemset[]::new);
        var database = database(sequence(repeated));

        var outcome = assertDifferential(
                "fixed-position-over-16", -1L, database, MinimumSupport.absolute(0.25));
        var lengthSeventeen = new Pattern(List.of(repeated));
        var positions = outcome.result().encoding(lengthSeventeen).positionBlock(1);

        assertEquals(3, positions.blockCount());
        assertTrue(positions.isSetAt(17));
        assertEquals(17, outcome.actual().size());
    }

    @Test
    void multiplePositiveAbsoluteAndPercentageThresholdsMatchExactly() {
        var database = database(
                sequence(itemset(1, 2), itemset(1), itemset(3)),
                sequence(itemset(1), itemset(2), itemset(3)),
                sequence(itemset(2), itemset(3)));
        var thresholds = List.of(
                MinimumSupport.absolute(0.05),
                MinimumSupport.absolute(0.5),
                MinimumSupport.absolute(1.0),
                MinimumSupport.absolute(2.0),
                MinimumSupport.percentage(25.0));

        var outcomes = new ArrayList<DifferentialOutcome>();
        for (int index = 0; index < thresholds.size(); index++) {
            outcomes.add(assertDifferential(
                    "fixed-threshold-" + index, -1L, database, thresholds.get(index)));
        }

        assertFalse(outcomes.getFirst().actual().isEmpty());
        assertTrue(outcomes.get(3).actual().isEmpty(), "high positive threshold must empty F1");
    }

    @Test
    void exactEqualityBoundaryIsAcceptedByBothPaths() {
        var database = database(
                sequence(itemset(1)),
                sequence(itemset(1)),
                sequence(itemset(2)));

        var outcome = assertDifferential(
                "fixed-threshold-equality", -1L, database, MinimumSupport.absolute(0.5));

        assertEquals(0.5, oracle.calculateMeanWeight(database).orElseThrow(), TOLERANCE);
        assertEquals(1, outcome.actual().get(pattern(itemset(2))));
    }

    @Test
    void emptyDatabaseMatchesUnderResolvableAbsoluteThreshold() {
        var outcome = assertDifferential(
                "fixed-empty",
                -1L,
                new SequenceDatabase(List.of()),
                MinimumSupport.absolute(0.5));

        assertEquals(Map.of(), outcome.actual());
        assertTrue(outcome.result().globalMeanWeight().isEmpty());
    }

    @Test
    void deterministicRandomSuiteMatchesForEveryCaseAndThreshold() {
        for (int caseIndex = 0; caseIndex < RANDOM_CASE_COUNT; caseIndex++) {
            long caseSeed = RANDOM_SEED + caseIndex;
            var database = generateSmallDatabase(caseSeed);
            for (int thresholdIndex = 0;
                    thresholdIndex < RANDOM_THRESHOLDS.size();
                    thresholdIndex++) {
                assertDifferential(
                        "random-" + caseIndex + "-threshold-" + thresholdIndex,
                        caseSeed,
                        database,
                        RANDOM_THRESHOLDS.get(thresholdIndex));
            }
        }
    }

    @Test
    void randomGeneratorIsExactlyReproducibleFromCaseSeed() {
        for (int caseIndex = 0; caseIndex < RANDOM_CASE_COUNT; caseIndex++) {
            long caseSeed = RANDOM_SEED + caseIndex;
            assertEquals(
                    serializeDatabase(generateSmallDatabase(caseSeed)),
                    serializeDatabase(generateSmallDatabase(caseSeed)),
                    "case seed=" + caseSeed);
        }
    }

    @Test
    void tableOneRegressionMatchesAllNinePatterns()
            throws IOException, URISyntaxException {
        var resource = requireNonNull(getClass().getResource("/datasets/wspm/table1.spmf"));
        var database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));

        var outcome = assertDifferential(
                "fixed-table-1", -1L, database, MinimumSupport.percentage(14.0));

        assertEquals(9, outcome.actual().size());
    }

    @Test
    void oracleResourceLimitIsReportedAndNeverCountedAsDifferentialPass() {
        var itemsets = IntStream.rangeClosed(1, 20)
                .mapToObj(id -> itemset(id))
                .toArray(Itemset[]::new);
        var database = database(sequence(itemsets));

        var error = assertThrows(IllegalArgumentException.class,
                () -> oracle.mine(database, MinimumSupport.absolute(0.1)));

        assertTrue(error.getMessage().contains("sequence limit exceeded"));
    }

    private DifferentialOutcome assertDifferential(
            String caseId,
            long seed,
            SequenceDatabase database,
            MinimumSupport minimumSupport) {
        Map<Pattern, Integer> expected = oracle.mine(database, minimumSupport);
        WspmMiningResult result = miner.mine(database, minimumSupport);
        Map<Pattern, Integer> actual = result.supports();
        String diagnostic = diagnostic(
                caseId, seed, database, minimumSupport, expected, actual);

        assertEquals(expected, actual, diagnostic);
        assertEquals(expected.keySet(), result.encodings().keySet(), diagnostic);
        assertEquals(expected.size(), result.acceptedPatterns().size(), diagnostic);

        if (database.size() > 0) {
            assertEquals(
                    oracle.calculateMeanWeight(database).orElseThrow(),
                    result.globalMeanWeight().orElseThrow(),
                    TOLERANCE,
                    diagnostic);
            assertEquals(
                    oracle.effectiveThreshold(minimumSupport, database.size()),
                    result.minimumSupport().effectiveThreshold(),
                    TOLERANCE,
                    diagnostic);
        }

        for (var pattern : expected.keySet()) {
            List<Integer> expectedSids = directSupportingSids(database, pattern);
            List<Integer> actualSids = encodedSupportingSids(result, pattern);
            assertEquals(expectedSids, actualSids,
                    diagnostic + System.lineSeparator()
                            + "supporting SID mismatch for " + pattern.serialize());
            assertEquals(expected.get(pattern), result.encoding(pattern).support(), diagnostic);
        }
        return new DifferentialOutcome(expected, actual, result);
    }

    private List<Integer> directSupportingSids(
            SequenceDatabase database, Pattern pattern) {
        var result = new ArrayList<Integer>();
        for (var record : database.records()) {
            if (oracle.containsSubsequence(record.sequence(), pattern)) {
                result.add(record.sid());
            }
        }
        return List.copyOf(result);
    }

    private static List<Integer> encodedSupportingSids(
            WspmMiningResult result, Pattern pattern) {
        var encoding = result.encoding(pattern);
        var supportingSids = new ArrayList<Integer>();
        for (int sid = 1; sid <= encoding.sequenceBlock().sidCount(); sid++) {
            if (encoding.sequenceBlock().isSupporting(sid)) {
                supportingSids.add(sid);
            }
        }
        return List.copyOf(supportingSids);
    }

    private String diagnostic(
            String caseId,
            long seed,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            Map<Pattern, Integer> expected,
            Map<Pattern, Integer> actual) {
        Set<Pattern> missing = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        missing.addAll(expected.keySet());
        missing.removeAll(actual.keySet());
        Set<Pattern> unexpected = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        unexpected.addAll(actual.keySet());
        unexpected.removeAll(expected.keySet());
        var supportMismatches = new LinkedHashMap<String, String>();
        expected.keySet().stream()
                .sorted(Comparator.comparing(Pattern::serialize))
                .filter(actual::containsKey)
                .filter(pattern -> !expected.get(pattern).equals(actual.get(pattern)))
                .forEach(pattern -> supportMismatches.put(
                        pattern.serialize(),
                        "expected=" + expected.get(pattern) + ", actual=" + actual.get(pattern)));

        String meanWeight = oracle.calculateMeanWeight(database).isPresent()
                ? Double.toString(oracle.calculateMeanWeight(database).orElseThrow())
                : "undefined";
        String effective = database.size() == 0
                ? "not resolved for empty oracle input"
                : Double.toString(oracle.effectiveThreshold(minimumSupport, database.size()));

        return "Differential mismatch"
                + System.lineSeparator() + "caseId=" + caseId
                + System.lineSeparator() + "seed=" + seed
                + System.lineSeparator() + "database=" + serializeDatabase(database)
                + System.lineSeparator() + "minimumSupport=" + minimumSupport
                + System.lineSeparator() + "MeanW=" + meanWeight
                + System.lineSeparator() + "effectiveMinSup=" + effective
                + System.lineSeparator() + "expectedCount=" + expected.size()
                + System.lineSeparator() + "actualCount=" + actual.size()
                + System.lineSeparator() + "missing=" + serializePatterns(missing)
                + System.lineSeparator() + "unexpected=" + serializePatterns(unexpected)
                + System.lineSeparator() + "supportMismatches=" + supportMismatches;
    }

    private static SequenceDatabase generateSmallDatabase(long seed) {
        var random = new Random(seed);
        int sequenceCount = 1 + random.nextInt(5);
        int distinctItemCount = 1 + random.nextInt(4);
        var records = new ArrayList<SequenceRecord>(sequenceCount);

        for (int sid = 1; sid <= sequenceCount; sid++) {
            int itemsetCount = 1 + random.nextInt(4);
            var itemsets = new ArrayList<Itemset>(itemsetCount);
            for (int itemsetIndex = 0; itemsetIndex < itemsetCount; itemsetIndex++) {
                int itemCount = 1 + random.nextInt(Math.min(3, distinctItemCount));
                var ids = new TreeSet<Integer>();
                while (ids.size() < itemCount) {
                    ids.add(1 + random.nextInt(distinctItemCount));
                }
                itemsets.add(new Itemset(ids.stream().map(Item::new).toList()));
            }
            records.add(new SequenceRecord(sid, new Sequence(itemsets)));
        }
        return new SequenceDatabase(records);
    }

    private static String serializeDatabase(SequenceDatabase database) {
        var lines = new ArrayList<String>();
        for (var record : database.records()) {
            var line = new StringBuilder("SID ").append(record.sid()).append(": ");
            for (var itemset : record.sequence().itemsets()) {
                for (var item : itemset.items()) {
                    line.append(item.id()).append(' ');
                }
                line.append("-1 ");
            }
            lines.add(line.append("-2").toString());
        }
        return lines.toString();
    }

    private static List<String> serializePatterns(Set<Pattern> patterns) {
        return patterns.stream().map(Pattern::serialize).toList();
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

    private record DifferentialOutcome(
            Map<Pattern, Integer> expected,
            Map<Pattern, Integer> actual,
            WspmMiningResult result) {}
}
