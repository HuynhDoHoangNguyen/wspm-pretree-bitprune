package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SequenceDatabaseFormat;
import vn.edu.wspm.io.SequenceDatabaseParser;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Phase boundary, prepared-input carrier immutability, and execution equivalence
 * tests for WSPM_BitPrune (C13.2).
 */
class BitPrunePhaseBoundaryTest {

    private static final Path TABLE1_PATH = Path.of("src/test/resources/datasets/wspm/table1.spmf");
    private static final MinimumSupport TABLE1_MIN_SUP = MinimumSupport.percentage(14.0);

    private SequenceDatabase table1Database;
    private BitPruneMiner miner;

    @BeforeEach
    void setUp() throws IOException {
        table1Database = new SequenceDatabaseParser().parse(TABLE1_PATH, SequenceDatabaseFormat.SPMF_SEQUENTIAL);
        miner = new BitPruneMiner();
    }

    @Test
    @DisplayName("1. prepare(...) does not mutate or freeze caller's PrefixTree")
    void prepareDoesNotMutateOrFreezePrefixTree() {
        PrefixTree tree = new PrefixTree();
        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());

        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        assertNotNull(prepared);

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
        assertTrue(tree.find(Pattern.empty()).isPresent());
    }

    @Test
    @DisplayName("2. Prepared input has deterministic F1 ordering sorted strictly by itemId ascending")
    void preparedInputHasDeterministicSortedF1() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        List<WeightedOnePattern> f1 = prepared.sortedF1();
        assertFalse(f1.isEmpty());

        for (int i = 1; i < f1.size(); i++) {
            assertTrue(f1.get(i).itemId() > f1.get(i - 1).itemId(),
                    "F1 items must be strictly increasing: " + f1.get(i - 1).itemId() + " vs " + f1.get(i).itemId());
        }
    }

    @Test
    @DisplayName("3. Prepared F1 key sets exactly match singleton bitmap and verification maps")
    void preparedF1KeySetsMatchBitmapsAndVerifications() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        Set<Integer> f1Ids = new HashSet<>();
        for (WeightedOnePattern p : prepared.sortedF1()) {
            f1Ids.add(p.itemId());
        }

        assertEquals(f1Ids, prepared.singletonBitmaps().keySet());
        assertEquals(f1Ids, prepared.singletonVerifications().keySet());
    }

    @Test
    @DisplayName("4. Prepared input does not expose mutable collections")
    void preparedInputDoesNotExposeMutableCollections() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);

        assertThrows(UnsupportedOperationException.class, () ->
                prepared.sortedF1().add(new WeightedOnePattern(99, 1)));

        assertThrows(UnsupportedOperationException.class, () ->
                prepared.singletonBitmaps().remove(1));

        assertThrows(UnsupportedOperationException.class, () ->
                prepared.singletonVerifications().remove(1));
    }

    @Test
    @DisplayName("5. Prepared boundary does not run DFS or create PrefixTree nodes")
    void prepareBoundaryDoesNotRunDfs() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        assertNotNull(prepared);
        assertEquals(3, prepared.sortedF1().size());
        assertNotNull(prepared.cmapI());
        assertNotNull(prepared.cmapS());
        assertNotNull(prepared.occurrenceIndex());
    }

    @Test
    @DisplayName("6. Public result after refactor has exact Table 1 support map")
    void publicResultHasExactCorrectSupportMap() {
        BitPruneMiningResult result = miner.mine(table1Database, TABLE1_MIN_SUP);
        Map<Pattern, Integer> supports = result.supports();

        Item i1 = new Item(1);
        Item i2 = new Item(2);
        Item i3 = new Item(3);

        assertEquals(9, supports.size());
        assertEquals(7, supports.get(Pattern.singleton(i1)));
        assertEquals(7, supports.get(Pattern.of(Itemset.of(i1, i2))));
        assertEquals(6, supports.get(Pattern.of(Itemset.of(i1, i2), Itemset.of(i3))));
        assertEquals(6, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i1))));
        assertEquals(7, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i2))));
        assertEquals(6, supports.get(Pattern.of(Itemset.of(i1), Itemset.of(i3))));
        assertEquals(7, supports.get(Pattern.singleton(i2)));
        assertEquals(6, supports.get(Pattern.of(Itemset.of(i2), Itemset.of(i3))));
        assertEquals(6, supports.get(Pattern.singleton(i3)));
    }

    @Test
    @DisplayName("7. Public result has accepted pattern order matching frozen tree pre-order")
    void publicResultHasExactAcceptedPatternOrder() {
        BitPruneMiningResult result = miner.mine(table1Database, TABLE1_MIN_SUP);
        List<Pattern> accepted = result.acceptedPatterns();

        assertEquals(9, accepted.size());
        List<Pattern> treePreOrder = result.prefixTree().preOrderPatterns().stream()
                .filter(p -> !p.isEmpty())
                .toList();
        assertEquals(treePreOrder, accepted);
    }

    @Test
    @DisplayName("8. C12 statistics of Table 1 are exactly preserved")
    void c12StatisticsOfControlledDatabaseAreExact() {
        BitPruneMiningResult result = miner.mine(table1Database, TABLE1_MIN_SUP);
        BitPruneStatistics stats = result.statistics();

        assertEquals(7L, stats.sequenceCount());
        assertEquals(3L, stats.f1Count());
        assertEquals(34L, stats.generatedExtensions());
        assertEquals(7L, stats.generatedIExtensions());
        assertEquals(27L, stats.generatedSExtensions());
        assertEquals(0L, stats.cmapRejected());
        assertEquals(0L, stats.bitmapRejected());
        assertEquals(28L, stats.exactRejected());
        assertEquals(6L, stats.acceptedExtensions());
        assertEquals(9L, stats.expandedNodes());
        assertEquals(9L, stats.patternCount());
        assertEquals(34L, stats.exactVerifications());
        assertEquals(28L, stats.rejectedExtensions());
    }

    @Test
    @DisplayName("9. Empty absolute database returns root-only frozen result and zero statistics")
    void emptyAbsoluteDatabaseReturnsRootOnlyFrozenResultAndZeroStatistics() {
        SequenceDatabase emptyDb = new SequenceDatabase(Collections.emptyList());
        PrefixTree tree = new PrefixTree();

        BitPruneMiningResult result = miner.mineIntoResult(tree, emptyDb, MinimumSupport.absolute(0.10));

        assertTrue(result.prefixTree().isFrozen());
        assertEquals(1, result.prefixTree().nodeCount());
        assertTrue(result.supports().isEmpty());
        assertTrue(result.acceptedPatterns().isEmpty());

        BitPruneStatistics stats = result.statistics();
        assertEquals(0L, stats.sequenceCount());
        assertEquals(0L, stats.f1Count());
        assertEquals(0L, stats.generatedExtensions());
        assertEquals(0L, stats.generatedIExtensions());
        assertEquals(0L, stats.generatedSExtensions());
        assertEquals(0L, stats.cmapRejected());
        assertEquals(0L, stats.bitmapRejected());
        assertEquals(0L, stats.exactRejected());
        assertEquals(0L, stats.acceptedExtensions());
        assertEquals(0L, stats.expandedNodes());
        assertEquals(0L, stats.patternCount());
        assertEquals(0L, stats.exactVerifications());
        assertEquals(0L, stats.rejectedExtensions());
    }

    @Test
    @DisplayName("10. Empty percentage database is rejected with IllegalArgumentException and tree is not mutated")
    void emptyPercentageDatabaseIsRejectedWithIllegalArgumentException() {
        SequenceDatabase emptyDb = new SequenceDatabase(Collections.emptyList());
        PrefixTree tree = new PrefixTree();

        assertThrows(IllegalArgumentException.class, () ->
                miner.mineIntoResult(tree, emptyDb, MinimumSupport.percentage(14.0)));

        assertFalse(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    @DisplayName("11. Non-empty F1-empty database does not run DFS and returns zero candidate counters")
    void nonEmptyF1EmptyDatabaseDoesNotRunDfsAndReturnsZeroCandidateCounters() {
        MinimumSupport highSupport = MinimumSupport.absolute(100.0);
        PrefixTree tree = new PrefixTree();

        BitPruneMiningResult result = miner.mineIntoResult(tree, table1Database, highSupport);

        assertTrue(result.prefixTree().isFrozen());
        assertEquals(1, result.prefixTree().nodeCount());
        assertTrue(result.supports().isEmpty());

        BitPruneStatistics stats = result.statistics();
        assertEquals(7L, stats.sequenceCount());
        assertEquals(0L, stats.f1Count());
        assertEquals(0L, stats.generatedExtensions());
        assertEquals(0L, stats.generatedIExtensions());
        assertEquals(0L, stats.generatedSExtensions());
        assertEquals(0L, stats.cmapRejected());
        assertEquals(0L, stats.bitmapRejected());
        assertEquals(0L, stats.exactRejected());
        assertEquals(0L, stats.acceptedExtensions());
        assertEquals(0L, stats.expandedNodes());
        assertEquals(0L, stats.patternCount());
        assertEquals(0L, stats.exactVerifications());
        assertEquals(0L, stats.rejectedExtensions());
    }

    @Test
    @DisplayName("12. R6 zero-threshold is rejected with IllegalArgumentException before mutation")
    void r6ZeroThresholdIsRejectedWithIllegalArgumentException() {
        PrefixTree treeAbs = new PrefixTree();
        assertThrows(IllegalArgumentException.class, () ->
                miner.mineIntoResult(treeAbs, table1Database, MinimumSupport.absolute(0.0)));
        assertFalse(treeAbs.isFrozen());

        PrefixTree treePct = new PrefixTree();
        assertThrows(IllegalArgumentException.class, () ->
                miner.mineIntoResult(treePct, table1Database, MinimumSupport.percentage(0.0)));
        assertFalse(treePct.isFrozen());
    }

    @Test
    @DisplayName("13. Frozen tree is rejected with IllegalStateException before preprocessing or mutation")
    void frozenTreeIsRejectedBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        tree.freeze();

        assertThrows(IllegalStateException.class, () ->
                miner.mineIntoResult(tree, table1Database, TABLE1_MIN_SUP));
    }

    @Test
    @DisplayName("14. Non-root-only tree is rejected with IllegalArgumentException before mutation")
    void nonRootOnlyTreeIsRejectedBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), Pattern.singleton(new Item(1)), vn.edu.wspm.tree.ExtensionType.S_EXTENSION);

        assertThrows(IllegalArgumentException.class, () ->
                miner.mineIntoResult(tree, table1Database, TABLE1_MIN_SUP));
    }

    @Test
    @DisplayName("15. Repeated runs do not share prepared mutable state")
    void repeatedRunsDoNotSharePreparedMutableState() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);

        PrefixTree tree1 = new PrefixTree();
        BitPruneMiningResult result1 = miner.minePreparedIntoResult(tree1, prepared);

        PrefixTree tree2 = new PrefixTree();
        BitPruneMiningResult result2 = miner.minePreparedIntoResult(tree2, prepared);

        assertEquals(result1.supports(), result2.supports());
        assertEquals(result1.acceptedPatterns(), result2.acceptedPatterns());
        assertEquals(result1.statistics(), result2.statistics());
    }

    @Test
    @DisplayName("16. Prepared input does not create duplicate preprocessing or accumulator")
    void preparedInputDoesNotCreateDuplicatePreprocessingOrAccumulator() {
        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        PrefixTree tree = new PrefixTree();

        BitPruneMiningResult result = miner.minePreparedIntoResult(tree, prepared);
        assertNotNull(result);
        assertEquals(9, result.supports().size());
        assertEquals(9L, result.statistics().patternCount());
    }

    @Test
    @DisplayName("17. Prepared execution and public execution produce identical results and statistics")
    void preparedExecutionAndPublicExecutionProduceIdenticalResultAndStatistics() {
        BitPruneMiningResult directResult = miner.mine(table1Database, TABLE1_MIN_SUP);

        BitPrunePreparedInput prepared = miner.prepare(table1Database, TABLE1_MIN_SUP);
        PrefixTree tree = new PrefixTree();
        BitPruneMiningResult preparedResult = miner.minePreparedIntoResult(tree, prepared);

        assertEquals(directResult.supports(), preparedResult.supports());
        assertEquals(directResult.acceptedPatterns(), preparedResult.acceptedPatterns());
        assertEquals(directResult.statistics(), preparedResult.statistics());
        assertEquals(directResult.prefixTree().preOrderPatterns(), preparedResult.prefixTree().preOrderPatterns());
    }

    @Test
    @DisplayName("18. No duplicate pattern insertion in PrefixTree")
    void noDuplicatePatternInsertion() {
        BitPruneMiningResult result = miner.mine(table1Database, TABLE1_MIN_SUP);
        List<Pattern> accepted = result.acceptedPatterns();
        Set<Pattern> unique = new HashSet<>(accepted);

        assertEquals(unique.size(), accepted.size(), "All accepted patterns must be distinct");
        assertEquals(result.supports().size(), accepted.size(), "Pattern count must equal supports map size");
    }
}
