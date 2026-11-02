package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.ExtensionType;

/** B17 full Table 1 paper-oracle, structural-oracle, and production comparison. */
class Table1EndToEndCorrectnessTest {

    private static final double TOLERANCE = 1.0e-12;
    private static final MinimumSupport TABLE_ONE_MINIMUM_SUPPORT =
            MinimumSupport.percentage(14.0);

    /** Figure 2 oracle using the B4 numeric-label test convention. Never used by production. */
    private static final Map<Pattern, Integer> PAPER_ORACLE = paperOracle();
    private static final Map<Pattern, List<Integer>> PAPER_SUPPORTING_SIDS =
            paperSupportingSids();

    private static SequenceDatabase database;

    private final ItemStatisticsCalculator statisticsCalculator = new ItemStatisticsCalculator();
    private final ItemWeightCalculator weightCalculator = new ItemWeightCalculator();
    private final MinimumSupportResolver minimumSupportResolver = new MinimumSupportResolver();
    private final F1Builder f1Builder = new F1Builder();
    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();

    @BeforeAll
    static void parsePaperFixture() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                Table1EndToEndCorrectnessTest.class.getResource(
                        "/datasets/wspm/table1.spmf"));
        database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    @Test
    void fixtureWeightsThresholdAndF1ReproducePaperInputs() {
        assertEquals(7, database.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7),
                database.records().stream().map(record -> record.sid()).toList());
        assertEquals(expectedSequences(),
                database.records().stream().map(record -> record.sequence()).toList());

        var statistics = statisticsCalculator.calculate(database);
        var weights = weightCalculator.calculate(statistics);
        var minimumSupport = minimumSupportResolver.resolve(
                TABLE_ONE_MINIMUM_SUPPORT, database.size());
        var f1 = f1Builder.build(statistics, weights, minimumSupport);

        assertEquals(8, statistics.numberOfDistinctItems());
        assertEquals(66, statistics.totalDatabaseLength());
        assertEquals(23.0 / 66.0, weights.maxWeight().orElseThrow(), TOLERANCE);
        assertEquals(1.0 / 66.0, weights.minWeight().orElseThrow(), TOLERANCE);
        assertEquals(12.0 / 66.0, weights.meanWeight().orElseThrow(), TOLERANCE);
        assertEquals(0.98, minimumSupport.effectiveThreshold(), TOLERANCE);
        assertEquals(List.of(
                new WeightedOnePattern(1, 7),
                new WeightedOnePattern(2, 7),
                new WeightedOnePattern(3, 6)), f1);
    }

    @Test
    void independentStructuralOracleConfirmsEveryFigureTwoPatternAndSidSet() {
        var structuralSupports = new LinkedHashMap<Pattern, Integer>();

        for (var entry : PAPER_ORACLE.entrySet()) {
            var supportingSids = CandidateTestOracle.directSupportingSids(
                    database, entry.getKey());
            structuralSupports.put(entry.getKey(), supportingSids.size());
            assertEquals(PAPER_SUPPORTING_SIDS.get(entry.getKey()), supportingSids,
                    "structural supporting SIDs for " + entry.getKey().serialize());
        }

        assertEquals(PAPER_ORACLE, structuralSupports);
    }

    @Test
    void productionResultExactlyEqualsPaperAndIndependentStructuralOracles() {
        var result = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var actual = result.supports();

        assertExactMapEquality(PAPER_ORACLE, actual);
        assertEquals(9, actual.size());
        assertEquals(9, new HashSet<>(result.acceptedPatterns()).size());

        for (var pattern : PAPER_ORACLE.keySet()) {
            assertEquals(
                    CandidateTestOracle.directSupportingSids(database, pattern),
                    CandidateTestOracle.supportingSids(result.encoding(pattern)),
                    "production supporting SIDs for " + pattern.serialize());
            assertEquals(PAPER_ORACLE.get(pattern), result.encoding(pattern).support());
        }
    }

    @Test
    void knownRejectedAcHasSupportThreeAndNeitherItNorItsDescendantsEnterTree() {
        var rejectedAc = pattern(itemset(1, 3));
        var result = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var directSids = CandidateTestOracle.directSupportingSids(database, rejectedAc);

        assertEquals(List.of(1, 4, 6), directSids);
        assertEquals(3, directSids.size());
        assertFalse(result.minimumSupport().meetsThreshold(
                directSids.size(), result.globalMeanWeight().orElseThrow()));
        assertFalse(result.supports().containsKey(rejectedAc));
        assertTrue(result.prefixTree().find(rejectedAc).isEmpty());
        assertTrue(result.acceptedPatterns().stream().noneMatch(candidate ->
                candidate.itemsets().getFirst().equals(itemset(1, 3))));
    }

    @Test
    void prefixTreeExactlyMatchesFigureTwoParentAndExtensionSemantics() {
        var result = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var tree = result.prefixTree();

        assertEquals(Pattern.empty(), tree.root().pattern());
        assertTrue(tree.root().parent().isEmpty());
        assertEquals(List.of(singleton(1), singleton(2), singleton(3)),
                tree.root().children().stream().map(node -> node.pattern()).toList());
        assertEquals(10, tree.nodeCount());
        assertEquals(PAPER_ORACLE.keySet(), result.encodings().keySet());
        assertEquals(PAPER_ORACLE.keySet(), result.supports().keySet());

        assertEdge(result, singleton(1), pattern(itemset(1, 2)), ExtensionType.I_EXTENSION);
        assertEdge(result, singleton(1), pattern(itemset(1), itemset(1)),
                ExtensionType.S_EXTENSION);
        assertEdge(result, singleton(1), pattern(itemset(1), itemset(2)),
                ExtensionType.S_EXTENSION);
        assertEdge(result, singleton(1), pattern(itemset(1), itemset(3)),
                ExtensionType.S_EXTENSION);
        assertEdge(result, singleton(2), pattern(itemset(2), itemset(3)),
                ExtensionType.S_EXTENSION);
        assertEdge(result, pattern(itemset(1, 2)), pattern(itemset(1, 2), itemset(3)),
                ExtensionType.S_EXTENSION);

        for (var pattern : result.acceptedPatterns()) {
            var node = tree.find(pattern).orElseThrow();
            assertEquals(pattern.length(), node.level());
            assertTrue(node.parent().isPresent());
            assertTrue(node.extensionType().isPresent());
            assertEquals(result.support(pattern), result.encoding(pattern).support());
        }
    }

    @Test
    void repeatedProductionRunsAreExactlyDeterministic() {
        var first = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var second = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var third = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);

        assertEquals(first.acceptedPatterns(), second.acceptedPatterns());
        assertEquals(first.acceptedPatterns(), third.acceptedPatterns());
        assertEquals(first.supports(), second.supports());
        assertEquals(first.supports(), third.supports());
        assertEquals(first.encodings(), second.encodings());
        assertEquals(first.encodings(), third.encodings());
        assertEquals(treeEdges(first), treeEdges(second));
        assertEquals(treeEdges(first), treeEdges(third));
    }

    @Test
    void miningStatisticsMatchTheExecutedTableOneSearch() {
        var result = miner.mine(database, TABLE_ONE_MINIMUM_SUPPORT);
        var statistics = result.statistics();

        assertEquals(7, statistics.sequenceCount());
        assertEquals(3, statistics.f1Count());
        assertEquals(34, statistics.candidateCount());
        assertEquals(6, statistics.acceptedCandidateCount());
        assertEquals(28, statistics.rejectedCandidateCount());
        assertEquals(7, statistics.iCandidateCount());
        assertEquals(27, statistics.sCandidateCount());
        assertEquals(9, statistics.expandedNodeCount());
        assertEquals(9, statistics.patternCount());
        assertEquals(statistics.candidateCount(),
                statistics.iCandidateCount() + statistics.sCandidateCount());
        assertEquals(statistics.candidateCount(),
                statistics.acceptedCandidateCount() + statistics.rejectedCandidateCount());
        assertEquals(statistics.patternCount(),
                statistics.f1Count() + statistics.acceptedCandidateCount());
        assertEquals(result.prefixTree().nodeCount() - 1, statistics.patternCount());
        assertTrue(statistics.elapsedNanos() >= 0);
        assertExactMapEquality(PAPER_ORACLE, result.supports());
    }

    private static void assertExactMapEquality(
            Map<Pattern, Integer> expected, Map<Pattern, Integer> actual) {
        var missing = new HashSet<>(expected.keySet());
        missing.removeAll(actual.keySet());
        var unexpected = new HashSet<>(actual.keySet());
        unexpected.removeAll(expected.keySet());
        var supportMismatches = new LinkedHashMap<Pattern, String>();
        for (var pattern : expected.keySet()) {
            if (actual.containsKey(pattern) && !expected.get(pattern).equals(actual.get(pattern))) {
                supportMismatches.put(pattern,
                        "expected=" + expected.get(pattern) + ", actual=" + actual.get(pattern));
            }
        }

        assertEquals(Set.of(), missing, "missing patterns");
        assertEquals(Set.of(), unexpected, "unexpected patterns");
        assertEquals(Map.of(), supportMismatches, "support mismatches");
        assertEquals(expected, actual, "full pattern -> support map");
    }

    private static void assertEdge(
            WspmMiningResult result,
            Pattern parent,
            Pattern child,
            ExtensionType expectedType) {
        var parentNode = result.prefixTree().find(parent).orElseThrow();
        var childNode = result.prefixTree().find(child).orElseThrow();
        assertSame(parentNode, childNode.parent().orElseThrow());
        assertEquals(expectedType, childNode.extensionType().orElseThrow());
        assertEquals(parent.length() + 1, child.length());
    }

    private static List<String> treeEdges(WspmMiningResult result) {
        return result.acceptedPatterns().stream().map(pattern -> {
            var node = result.prefixTree().find(pattern).orElseThrow();
            return node.parent().orElseThrow().pattern().serialize()
                    + " -> " + pattern.serialize()
                    + " [" + node.extensionType().orElseThrow() + "]";
        }).toList();
    }

    private static Map<Pattern, Integer> paperOracle() {
        var result = new LinkedHashMap<Pattern, Integer>();
        result.put(singleton(1), 7);
        result.put(singleton(2), 7);
        result.put(singleton(3), 6);
        result.put(pattern(itemset(1), itemset(1)), 6);
        result.put(pattern(itemset(1), itemset(2)), 7);
        result.put(pattern(itemset(1, 2)), 7);
        result.put(pattern(itemset(1), itemset(3)), 6);
        result.put(pattern(itemset(2), itemset(3)), 6);
        result.put(pattern(itemset(1, 2), itemset(3)), 6);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Map<Pattern, List<Integer>> paperSupportingSids() {
        var all = List.of(1, 2, 3, 4, 5, 6, 7);
        var oneThroughSix = List.of(1, 2, 3, 4, 5, 6);
        var result = new LinkedHashMap<Pattern, List<Integer>>();
        result.put(singleton(1), all);
        result.put(singleton(2), all);
        result.put(singleton(3), oneThroughSix);
        result.put(pattern(itemset(1), itemset(1)), List.of(1, 2, 4, 5, 6, 7));
        result.put(pattern(itemset(1), itemset(2)), all);
        result.put(pattern(itemset(1, 2)), all);
        result.put(pattern(itemset(1), itemset(3)), oneThroughSix);
        result.put(pattern(itemset(2), itemset(3)), oneThroughSix);
        result.put(pattern(itemset(1, 2), itemset(3)), oneThroughSix);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static List<Sequence> expectedSequences() {
        return List.of(
                Sequence.of(itemset(1), itemset(1, 2, 3), itemset(1, 3),
                        itemset(1, 4), itemset(1, 3, 6)),
                Sequence.of(itemset(1, 2, 4), itemset(3), itemset(2, 3),
                        itemset(1, 5), itemset(2), itemset(3)),
                Sequence.of(itemset(5, 6), itemset(1, 2), itemset(4, 6),
                        itemset(3), itemset(2)),
                Sequence.of(itemset(5), itemset(7), itemset(1, 6),
                        itemset(1, 2, 3), itemset(2), itemset(3)),
                Sequence.of(itemset(1), itemset(1, 2, 7), itemset(3, 4, 7),
                        itemset(5, 7), itemset(7), itemset(7, 8)),
                Sequence.of(itemset(1), itemset(1, 2, 4), itemset(2), itemset(1, 3)),
                Sequence.of(itemset(1), itemset(1, 2), itemset(1), itemset(1),
                        itemset(1), itemset(1), itemset(1), itemset(1)));
    }

    private static Pattern singleton(int id) {
        return Pattern.singleton(new Item(id));
    }

    private static Pattern pattern(Itemset... itemsets) {
        return Pattern.of(itemsets);
    }

    private static Itemset itemset(int... ids) {
        var items = new ArrayList<Item>(ids.length);
        for (int id : ids) {
            items.add(new Item(id));
        }
        return new Itemset(items);
    }
}
