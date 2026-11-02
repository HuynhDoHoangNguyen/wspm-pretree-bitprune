package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.ExtensionType;

class CandidateEngineDifferentialTest {

    private static final Item A = new Item(1);
    private static final Item B = new Item(2);
    private static final Item C = new Item(3);
    private static final Item D = new Item(4);

    private final CandidateEngine engine = new CandidateEngine();
    private final ItemStatisticsCalculator statisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator weightCalculator = new ItemWeightCalculator();
    private final MinimumSupportResolver minimumSupportResolver = new MinimumSupportResolver();

    private static SequenceDatabase table1;

    @BeforeAll
    static void loadTable1() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                CandidateEngineDifferentialTest.class.getResource(
                        "/datasets/wspm/table1.spmf"));
        table1 = new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    @Test
    void everyGeneratedTinyDatabaseCandidateMatchesIndependentOracle() {
        SequenceDatabase database = tinyDatabase();
        var statistics = statisticsCalculator.calculate(database);
        var weights = weightCalculator.calculate(statistics);
        double meanWeight = weights.meanWeight().orElseThrow();
        var threshold = minimumSupportResolver.resolve(
                MinimumSupport.absolute(0.1), database.size());
        var f1 = new F1Builder().build(statistics, weights, threshold);
        Pattern parent = Pattern.singleton(A);

        var results = engine.generateOneStep(
                parent,
                CandidateTestOracle.encoding(database, parent),
                f1,
                CandidateTestOracle.singletonEncodings(database, f1),
                meanWeight,
                threshold);

        assertFalse(results.isEmpty());
        for (var result : results) {
            var expected = CandidateTestOracle.encoding(database, result.pattern());
            var expectedSids = CandidateTestOracle.directSupportingSids(
                    database, result.pattern());
            assertEquals(expected.sequenceBlock(), result.encoding().sequenceBlock(),
                    () -> "SID vector differs for " + result.pattern());
            assertEquals(expected.positionBlocks(), result.encoding().positionBlocks(),
                    () -> "endpoints differ for " + result.pattern());
            assertEquals(expectedSids,
                    CandidateTestOracle.supportingSids(result.encoding()),
                    () -> "supporting SIDs differ for " + result.pattern());
            assertEquals(expectedSids.size(), result.support(),
                    () -> "support differs for " + result.pattern());
            assertEquals(
                    threshold.meetsThreshold(expectedSids.size(), meanWeight),
                    result.accepted(),
                    () -> "weighted acceptance differs for " + result.pattern());
        }
    }

    @Test
    void table1FocusedCandidatesHaveExactSupportSidsAndAcceptance() {
        var statistics = statisticsCalculator.calculate(table1);
        var weights = weightCalculator.calculate(statistics);
        double meanWeight = weights.meanWeight().orElseThrow();
        var threshold = minimumSupportResolver.resolve(
                MinimumSupport.percentage(14.0), table1.size());
        var f1 = new F1Builder().build(statistics, weights, threshold);
        Pattern parent = Pattern.singleton(A);

        assertEquals(List.of(
                new WeightedOnePattern(1, 7),
                new WeightedOnePattern(2, 7),
                new WeightedOnePattern(3, 6)), f1);
        assertEquals(12.0 / 66.0, meanWeight, 1.0e-12);
        assertEquals(0.98, threshold.effectiveThreshold(), 1.0e-12);

        var results = engine.generateOneStep(
                parent,
                CandidateTestOracle.encoding(table1, parent),
                f1,
                CandidateTestOracle.singletonEncodings(table1, f1),
                meanWeight,
                threshold);

        assertEquals(List.of(
                "I_EXTENSION:<(1 2)>",
                "I_EXTENSION:<(1 3)>",
                "S_EXTENSION:<(1)(1)>",
                "S_EXTENSION:<(1)(2)>",
                "S_EXTENSION:<(1)(3)>"),
                results.stream()
                        .map(result -> result.extensionType() + ":" + result.pattern().serialize())
                        .toList());

        assertCandidate(results, "<(1)(1)>", ExtensionType.S_EXTENSION,
                6, List.of(1, 2, 4, 5, 6, 7), true);
        assertCandidate(results, "<(1)(2)>", ExtensionType.S_EXTENSION,
                7, List.of(1, 2, 3, 4, 5, 6, 7), true);
        assertCandidate(results, "<(1)(3)>", ExtensionType.S_EXTENSION,
                6, List.of(1, 2, 3, 4, 5, 6), true);
        assertCandidate(results, "<(1 2)>", ExtensionType.I_EXTENSION,
                7, List.of(1, 2, 3, 4, 5, 6, 7), true);

        // Structurally valid but rejected candidate: this distinction is required by B15.
        assertCandidate(results, "<(1 3)>", ExtensionType.I_EXTENSION,
                3, List.of(1, 4, 6), false);
        assertEquals(4, results.stream().filter(CandidateResult::accepted).count());
    }

    @Test
    void sExtensionFromPositionEightToNineMatchesOracle() {
        Sequence sequence = Sequence.of(
                Itemset.of(D), Itemset.of(D), Itemset.of(D), Itemset.of(D),
                Itemset.of(D), Itemset.of(D), Itemset.of(D), Itemset.of(A), Itemset.of(B));
        SequenceDatabase database = new SequenceDatabase(List.of(new SequenceRecord(1, sequence)));
        Pattern parent = Pattern.singleton(A);
        var f1 = List.of(new WeightedOnePattern(2, 1));
        var threshold = minimumSupportResolver.resolve(
                MinimumSupport.absolute(0.1), 1);

        var results = engine.generateOneStep(
                parent,
                CandidateTestOracle.encoding(database, parent),
                f1,
                CandidateTestOracle.singletonEncodings(database, f1),
                0.5,
                threshold);
        CandidateResult sResult = results.stream()
                .filter(result -> result.extensionType() == ExtensionType.S_EXTENSION)
                .findFirst()
                .orElseThrow();

        assertEquals("<(1)(2)>", sResult.pattern().serialize());
        assertEquals(1, sResult.support());
        assertEquals(List.of(1), CandidateTestOracle.supportingSids(sResult.encoding()));
        assertTrue(sResult.encoding().positionBlock(1).isSetAt(9));
        assertTrue(sResult.accepted());
    }

    private static void assertCandidate(
            List<CandidateResult> results,
            String serialization,
            ExtensionType extensionType,
            int expectedSupport,
            List<Integer> expectedSids,
            boolean expectedAccepted) {
        CandidateResult result = results.stream()
                .filter(candidate -> candidate.extensionType() == extensionType)
                .filter(candidate -> candidate.pattern().serialize().equals(serialization))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedSupport, result.support());
        assertEquals(expectedSids, CandidateTestOracle.supportingSids(result.encoding()));
        assertEquals(expectedAccepted, result.accepted());
    }

    private static SequenceDatabase tinyDatabase() {
        return new SequenceDatabase(List.of(
                new SequenceRecord(1, Sequence.of(
                        Itemset.of(A), Itemset.of(D), Itemset.of(A), Itemset.of(D),
                        Itemset.of(D), Itemset.of(D), Itemset.of(A), Itemset.of(A, B),
                        Itemset.of(B, C), Itemset.of(C))),
                new SequenceRecord(2, Sequence.of(
                        Itemset.of(B), Itemset.of(A, C), Itemset.of(A), Itemset.of(B),
                        Itemset.of(C))),
                new SequenceRecord(3, Sequence.of(
                        Itemset.of(C), Itemset.of(B), Itemset.of(A))),
                new SequenceRecord(4, Sequence.of(
                        Itemset.of(D), Itemset.of(D), Itemset.of(D)))));
    }
}
