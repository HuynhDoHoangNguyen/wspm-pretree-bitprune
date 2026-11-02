package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.tree.ExtensionType;

class WspmPreTreeMinerTest {

    private static final double TOLERANCE = 1.0e-12;

    private final WspmPreTreeMiner miner = new WspmPreTreeMiner();
    private final PatternExtensions extensions = new PatternExtensions();

    @Test
    void emptyDatabaseProducesOnlyFrozenRootWithoutInventingMeanWeight() {
        var result = miner.mine(
                new SequenceDatabase(List.of()), MinimumSupport.absolute(0.0));

        assertEquals(Pattern.empty(), result.prefixTree().root().pattern());
        assertEquals(1, result.prefixTree().nodeCount());
        assertTrue(result.prefixTree().isFrozen());
        assertTrue(result.acceptedPatterns().isEmpty());
        assertTrue(result.supports().isEmpty());
        assertTrue(result.encodings().isEmpty());
        assertTrue(result.globalMeanWeight().isEmpty());
    }

    @Test
    void databaseWithoutAcceptedF1ProducesOnlyRoot() {
        var result = miner.mine(
                database(Sequence.of(itemset(1))), MinimumSupport.absolute(2.0));

        assertEquals(1, result.prefixTree().nodeCount());
        assertTrue(result.acceptedPatterns().isEmpty());
        assertEquals(1.0, result.globalMeanWeight().orElseThrow(), TOLERANCE);
    }

    @Test
    void initializesExactlyF1WithPrimeBlockSupport() {
        var result = miner.mine(
                database(Sequence.of(itemset(1)), Sequence.of(itemset(1), itemset(2))),
                MinimumSupport.absolute(0.5));
        var one = singleton(1);
        var two = singleton(2);

        assertEquals(List.of(one, two), result.prefixTree().root().children().stream()
                .map(node -> node.pattern()).toList());
        assertEquals(2, result.support(one));
        assertEquals(1, result.support(two));
        assertEquals(result.support(one), result.encoding(one).sequenceBlock().support());
        assertEquals(result.support(two), result.encoding(two).sequenceBlock().support());
    }

    @Test
    void minesIAndSChildrenWithCorrectEdgesAndContinuesMultipleDfsLevels() {
        var result = miner.mine(
                database(Sequence.of(itemset(1, 2), itemset(2), itemset(3))),
                MinimumSupport.absolute(0.3));
        var one = singleton(1);
        var iChild = extensions.iExtend(one, new Item(2));
        var sChild = extensions.sExtend(one, new Item(2));
        var thirdLevel = extensions.sExtend(sChild, new Item(3));

        assertEdge(result, one, iChild, ExtensionType.I_EXTENSION);
        assertEdge(result, one, sChild, ExtensionType.S_EXTENSION);
        assertEdge(result, sChild, thirdLevel, ExtensionType.S_EXTENSION);
        assertEquals(2, result.prefixTree().find(iChild).orElseThrow().level());
        assertEquals(2, result.prefixTree().find(sChild).orElseThrow().level());
        assertEquals(3, result.prefixTree().find(thirdLevel).orElseThrow().level());
        assertEquals(1, result.support(thirdLevel));
    }

    @Test
    void neverInsertsOrExpandsRejectedCandidate() {
        var result = miner.mine(
                database(
                        Sequence.of(itemset(1), itemset(2)),
                        Sequence.of(itemset(1)),
                        Sequence.of(itemset(2))),
                MinimumSupport.absolute(0.75));
        var rejected = Pattern.of(itemset(1), itemset(2));
        var forbiddenDescendant = Pattern.of(itemset(1), itemset(2), itemset(1));

        assertEquals(List.of(singleton(1), singleton(2)), result.acceptedPatterns());
        assertTrue(result.prefixTree().find(rejected).isEmpty());
        assertTrue(result.prefixTree().find(forbiddenDescendant).isEmpty());
        assertFalse(result.encodings().containsKey(rejected));
    }

    @Test
    void acceptsRepeatedItemAcrossDifferentItemsets() {
        var result = miner.mine(
                database(Sequence.of(itemset(1), itemset(1))),
                MinimumSupport.absolute(1.0));
        var repeated = Pattern.of(itemset(1), itemset(1));

        assertEquals(1, result.support(repeated));
        assertEdge(result, singleton(1), repeated, ExtensionType.S_EXTENSION);
        assertEquals(2, repeated.size());
        assertTrue(repeated.itemsets().stream().allMatch(itemset -> itemset.size() == 1));
    }

    @Test
    void resultHasUniquePatternsConsistentMetadataAndReadOnlyBoundary() {
        var result = miner.mine(
                database(Sequence.of(itemset(1, 2), itemset(1), itemset(2))),
                MinimumSupport.absolute(0.25));

        assertEquals(result.acceptedPatterns().size(),
                new HashSet<>(result.acceptedPatterns()).size());
        assertEquals(new HashSet<>(result.acceptedPatterns()), result.encodings().keySet());
        assertEquals(new HashSet<>(result.acceptedPatterns()), result.supports().keySet());
        for (var pattern : result.acceptedPatterns()) {
            assertEquals(result.support(pattern), result.encoding(pattern).sequenceBlock().support());
            var node = result.prefixTree().find(pattern).orElseThrow();
            assertEquals(pattern.length(), node.level());
            assertTrue(node.parent().isPresent());
        }
        assertThrows(UnsupportedOperationException.class, () -> result.supports().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.encodings().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.acceptedPatterns().clear());
        assertThrows(IllegalStateException.class, () -> result.prefixTree().insert(
                Pattern.empty(), singleton(9), ExtensionType.S_EXTENSION));
    }

    @Test
    void zeroThresholdStillTerminatesAtDataDerivedMaximumLength() {
        var result = miner.mine(
                database(Sequence.of(itemset(1))), MinimumSupport.absolute(0.0));

        assertEquals(List.of(singleton(1)), result.acceptedPatterns());
        assertEquals(2, result.prefixTree().nodeCount());
    }

    @Test
    void zeroThresholdIncludesZeroSupportCandidatesUnderCurrentLiteralPolicy() {
        var database = database(
                Sequence.of(itemset(1), itemset(1)),
                Sequence.of(itemset(2)));

        var absoluteZero = miner.mine(database, MinimumSupport.absolute(0.0));
        var percentageZero = miner.mine(database, MinimumSupport.percentage(0.0));
        var zeroSupportIExtension = Pattern.of(itemset(1, 2));
        var zeroSupportSExtension = Pattern.of(itemset(1), itemset(2));

        assertEquals(0, absoluteZero.support(zeroSupportIExtension));
        assertEquals(0, absoluteZero.support(zeroSupportSExtension));
        assertTrue(absoluteZero.acceptedPatterns().contains(zeroSupportIExtension));
        assertTrue(absoluteZero.acceptedPatterns().contains(zeroSupportSExtension));
        assertEquals(absoluteZero.acceptedPatterns(), percentageZero.acceptedPatterns());
        assertEquals(absoluteZero.supports(), percentageZero.supports());
    }

    @Test
    void anyPositiveThresholdExcludesTheSameZeroSupportCandidates() {
        var result = miner.mine(
                database(
                        Sequence.of(itemset(1), itemset(1)),
                        Sequence.of(itemset(2))),
                MinimumSupport.absolute(1.0e-12));

        assertTrue(result.prefixTree().find(Pattern.of(itemset(1, 2))).isEmpty());
        assertTrue(result.prefixTree().find(Pattern.of(itemset(1), itemset(2))).isEmpty());
        assertTrue(result.acceptedPatterns().stream()
                .allMatch(pattern -> result.support(pattern) > 0));
    }

    @Test
    void tableOneFocusedPatternsMatchKnownOracleWithoutClaimingFullR4()
            throws IOException, URISyntaxException {
        var database = parseTableOne();
        var result = miner.mine(database, MinimumSupport.percentage(14.0));

        assertSupport(result, Pattern.of(itemset(1)), 7);
        assertSupport(result, Pattern.of(itemset(2)), 7);
        assertSupport(result, Pattern.of(itemset(3)), 6);
        assertSupport(result, Pattern.of(itemset(1, 2)), 7);
        assertSupport(result, Pattern.of(itemset(1), itemset(1)), 6);
        assertSupport(result, Pattern.of(itemset(1), itemset(2)), 7);
        assertSupport(result, Pattern.of(itemset(1), itemset(3)), 6);
        var rejectedAc = Pattern.of(itemset(1, 3));
        int rejectedAcSupport = CandidateTestOracle.encoding(database, rejectedAc).support();
        assertEquals(3, rejectedAcSupport);
        assertFalse(result.minimumSupport().meetsThreshold(
                rejectedAcSupport, result.globalMeanWeight().orElseThrow()));
        assertTrue(result.prefixTree().find(rejectedAc).isEmpty());
        assertEquals(12.0 / 66.0, result.globalMeanWeight().orElseThrow(), TOLERANCE);
        assertEquals(0.98, result.minimumSupport().effectiveThreshold(), TOLERANCE);
    }

    @Test
    void repeatedRunsHaveIdenticalPatternSupportAndTreeStructure() {
        var database = database(
                Sequence.of(itemset(1, 2), itemset(1), itemset(2)),
                Sequence.of(itemset(1), itemset(2)));

        var first = miner.mine(database, MinimumSupport.absolute(0.3));
        var second = miner.mine(database, MinimumSupport.absolute(0.3));

        assertEquals(first.acceptedPatterns(), second.acceptedPatterns());
        assertEquals(first.supports(), second.supports());
        assertEquals(first.encodings(), second.encodings());
        assertEquals(treeEdges(first), treeEdges(second));
    }

    private static void assertSupport(WspmMiningResult result, Pattern pattern, int support) {
        assertEquals(support, result.support(pattern), pattern.serialize());
        assertEquals(support, result.encoding(pattern).sequenceBlock().support());
    }

    private static void assertEdge(
            WspmMiningResult result,
            Pattern parent,
            Pattern child,
            ExtensionType extensionType) {
        var childNode = result.prefixTree().find(child).orElseThrow();
        assertSame(result.prefixTree().find(parent).orElseThrow(), childNode.parent().orElseThrow());
        assertEquals(extensionType, childNode.extensionType().orElseThrow());
    }

    private static List<String> treeEdges(WspmMiningResult result) {
        return result.acceptedPatterns().stream().map(pattern -> {
            var node = result.prefixTree().find(pattern).orElseThrow();
            return node.parent().orElseThrow().pattern().serialize()
                    + " -> " + pattern.serialize()
                    + " [" + node.extensionType().orElseThrow() + "]";
        }).toList();
    }

    private static SequenceDatabase parseTableOne() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                WspmPreTreeMinerTest.class.getResource("/datasets/wspm/table1.spmf"));
        return new SpmfSequenceParser().parse(Path.of(resource.toURI()));
    }

    private static SequenceDatabase database(Sequence... sequences) {
        var records = new ArrayList<SequenceRecord>();
        for (int index = 0; index < sequences.length; index++) {
            records.add(new SequenceRecord(index + 1, sequences[index]));
        }
        return new SequenceDatabase(records);
    }

    private static Pattern singleton(int id) {
        return Pattern.singleton(new Item(id));
    }

    private static Itemset itemset(int... ids) {
        return new Itemset(java.util.Arrays.stream(ids).mapToObj(Item::new).toList());
    }
}
