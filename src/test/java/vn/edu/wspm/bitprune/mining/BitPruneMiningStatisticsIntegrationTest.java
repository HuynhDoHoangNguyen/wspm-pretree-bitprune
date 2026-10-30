package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapBuilder;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.result.BitPruneMiningResult;
import vn.edu.wspm.bitprune.statistics.BitPruneStatistics;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Comprehensive statistics integration and exact counter oracle tests for
 * {@link BitPruneMiner} and {@link BitPruneMiningResult} (C12.3, C12.3-R1).
 *
 * <p>
 * Validates all 22 required test cases from the C12.3 and C12.3-R1 implementation contracts.
 * Replaces weak inequality assertions with exact full-vector counter oracles independently
 * derived by hand from candidate lifecycles, proving first-failure terminal routing,
 * structural I/S extension splits, exact pruning tier counts, and candidate conservation.
 * </p>
 */
class BitPruneMiningStatisticsIntegrationTest {

    private final BitPruneMiner miner = new BitPruneMiner();

    // =========================================================================
    // SequenceDatabase Helpers
    // =========================================================================

    private static Itemset itemset(int... itemIds) {
        Item[] items = new Item[itemIds.length];
        for (int i = 0; i < itemIds.length; i++) {
            items[i] = new Item(itemIds[i]);
        }
        return Itemset.of(items);
    }

    private static SequenceRecord record(int sid, Itemset... itemsets) {
        return new SequenceRecord(sid, Sequence.of(itemsets));
    }

    private static SequenceDatabase db(SequenceRecord... records) {
        return new SequenceDatabase(Arrays.asList(records));
    }

    /**
     * Authoritative exact oracle assertion helper (C12.3-R1 Section 7.1).
     *
     * <p>
     * Verifies all 12 primary counters by explicit name, asserts the derived
     * {@code rejectedExtensions} statistic, and checks the 4 mathematical
     * conservation invariants without computing expected values from actual counters.
     * </p>
     */
    private void assertExactStatistics(
            BitPruneStatistics actual,
            long expectedSequenceCount,
            long expectedF1Count,
            long expectedGeneratedExtensions,
            long expectedGeneratedIExtensions,
            long expectedGeneratedSExtensions,
            long expectedCmapRejected,
            long expectedBitmapRejected,
            long expectedExactRejected,
            long expectedAcceptedExtensions,
            long expectedExpandedNodes,
            long expectedPatternCount,
            long expectedExactVerifications) {

        assertNotNull(actual, "actual statistics must not be null");

        // 1. Authoritative 12 primary counters checked individually with explicit names
        assertEquals(expectedSequenceCount, actual.sequenceCount(), "sequenceCount");
        assertEquals(expectedF1Count, actual.f1Count(), "f1Count");
        assertEquals(expectedGeneratedExtensions, actual.generatedExtensions(), "generatedExtensions");
        assertEquals(expectedGeneratedIExtensions, actual.generatedIExtensions(), "generatedIExtensions");
        assertEquals(expectedGeneratedSExtensions, actual.generatedSExtensions(), "generatedSExtensions");
        assertEquals(expectedCmapRejected, actual.cmapRejected(), "cmapRejected");
        assertEquals(expectedBitmapRejected, actual.bitmapRejected(), "bitmapRejected");
        assertEquals(expectedExactRejected, actual.exactRejected(), "exactRejected");
        assertEquals(expectedAcceptedExtensions, actual.acceptedExtensions(), "acceptedExtensions");
        assertEquals(expectedExpandedNodes, actual.expandedNodes(), "expandedNodes");
        assertEquals(expectedPatternCount, actual.patternCount(), "patternCount");
        assertEquals(expectedExactVerifications, actual.exactVerifications(), "exactVerifications");

        // 2. Derived statistic: rejectedExtensions = cmapRejected + bitmapRejected + exactRejected
        long expectedRejected = expectedCmapRejected + expectedBitmapRejected + expectedExactRejected;
        assertEquals(expectedRejected, actual.rejectedExtensions(), "rejectedExtensions");

        // 3. Mandatory invariants verification
        assertEquals(actual.generatedExtensions(),
                actual.generatedIExtensions() + actual.generatedSExtensions(),
                "Invariant 1: generatedExtensions == generatedIExtensions + generatedSExtensions");
        assertEquals(actual.generatedExtensions(),
                actual.cmapRejected() + actual.bitmapRejected() + actual.exactRejected() + actual.acceptedExtensions(),
                "Invariant 2: generatedExtensions == cmap + bitmap + exact + accepted");
        assertEquals(actual.exactVerifications(),
                actual.exactRejected() + actual.acceptedExtensions(),
                "Invariant 3: exactVerifications == exactRejected + acceptedExtensions");
        assertEquals(actual.patternCount(),
                actual.f1Count() + actual.acceptedExtensions(),
                "Invariant 4: patternCount == f1Count + acceptedExtensions");
    }

    private void assertInvariants(BitPruneStatistics stats) {
        assertNotNull(stats, "statistics must not be null");

        // Invariant 1: generatedExtensions = generatedIExtensions + generatedSExtensions
        assertEquals(stats.generatedExtensions(),
                stats.generatedIExtensions() + stats.generatedSExtensions(),
                "Invariant 1: generatedExtensions == generatedIExtensions + generatedSExtensions");

        // Invariant 2: generatedExtensions = cmapRejected + bitmapRejected + exactRejected + acceptedExtensions
        assertEquals(stats.generatedExtensions(),
                stats.cmapRejected() + stats.bitmapRejected() + stats.exactRejected() + stats.acceptedExtensions(),
                "Invariant 2: generatedExtensions == cmap + bitmap + exact + accepted");

        // Invariant 3: exactVerifications = exactRejected + acceptedExtensions
        assertEquals(stats.exactVerifications(),
                stats.exactRejected() + stats.acceptedExtensions(),
                "Invariant 3: exactVerifications == exactRejected + acceptedExtensions");

        // Invariant 4: patternCount = f1Count + acceptedExtensions
        assertEquals(stats.patternCount(),
                stats.f1Count() + stats.acceptedExtensions(),
                "Invariant 4: patternCount == f1Count + acceptedExtensions");
    }

    // =========================================================================
    // Required Cases 1-22
    // =========================================================================

    @Test
    @DisplayName("1. Empty database + positive ABSOLUTE produces zero statistics")
    void test01_emptyDatabasePositiveAbsolute() {
        SequenceDatabase emptyDb = new SequenceDatabase(Collections.emptyList());
        BitPruneMiningResult result = miner.mine(emptyDb, MinimumSupport.absolute(1.0));

        assertNotNull(result);
        assertTrue(result.supports().isEmpty());
        assertTrue(result.acceptedPatterns().isEmpty());
        assertTrue(result.prefixTree().isFrozen());
        assertEquals(1, result.prefixTree().nodeCount());

        assertExactStatistics(result.statistics(),
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("2. Empty database + zero ABSOLUTE produces zero statistics")
    void test02_emptyDatabaseZeroAbsolute() {
        SequenceDatabase emptyDb = new SequenceDatabase(Collections.emptyList());
        BitPruneMiningResult result = miner.mine(emptyDb, MinimumSupport.absolute(0.0));

        assertNotNull(result);
        assertTrue(result.supports().isEmpty());
        assertTrue(result.prefixTree().isFrozen());

        assertExactStatistics(result.statistics(),
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("3. Empty database + PERCENT rejection fails before tree mutation")
    void test03_emptyDatabasePercentRejection() {
        SequenceDatabase emptyDb = new SequenceDatabase(Collections.emptyList());
        PrefixTree tree = new PrefixTree();

        assertThrows(IllegalArgumentException.class,
                () -> miner.mine(emptyDb, MinimumSupport.percentage(50.0)));

        assertThrows(IllegalArgumentException.class,
                () -> miner.mineIntoResult(tree, emptyDb, MinimumSupport.percentage(50.0)));

        assertFalse(tree.isFrozen(), "tree must not be frozen upon failure before mutation");
        assertEquals(1, tree.nodeCount());
    }

    @Test
    @DisplayName("4. Non-empty zero threshold rejection while Decision R6 is OPEN")
    void test04_nonEmptyZeroThresholdRejection() {
        SequenceDatabase database = db(record(1, itemset(1)));
        PrefixTree tree = new PrefixTree();

        assertThrows(IllegalArgumentException.class,
                () -> miner.mine(database, MinimumSupport.absolute(0.0)));

        assertThrows(IllegalArgumentException.class,
                () -> miner.mineIntoResult(tree, database, MinimumSupport.absolute(0.0)));

        assertFalse(tree.isFrozen(), "tree must not be frozen when R6 rejection occurs");
    }

    @Test
    @DisplayName("5. F1-empty positive-threshold result")
    void test05_f1EmptyPositiveThresholdResult() {
        SequenceDatabase database = db(record(1, itemset(1)));
        // Item 1 has support 1, MeanW 1.0 -> weighted support = 1.0 < 5.0 -> F1 is empty
        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(5.0));

        assertTrue(result.supports().isEmpty());
        assertTrue(result.acceptedPatterns().isEmpty());
        assertTrue(result.prefixTree().isFrozen());
        assertEquals(1, result.prefixTree().nodeCount());

        assertExactStatistics(result.statistics(),
                1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    @Test
    @DisplayName("6. F1-only accepted result (all extensions rejected)")
    void test06_f1OnlyAcceptedResult() {
        // Independent Candidate Lifecycle Derivation:
        // Input: 2 sequences with single item 1:
        //   SID 1: (1), SID 2: (1)
        // Item 1: count 2, total len 2 -> weight 1.0, MeanW 1.0. Support 2 * 1.0 = 2.0 >= 1.0 -> F1 = {1}.
        // Expanded parents:
        //   1) <(1)>:
        //      - I-Extension: candidate item must be in F1 and > 1. None (0 I-extensions).
        //      - S-Extension with item 1: <(1)(1)>.
        //        CMAP-S(1, 1) = false (neither sequence has multiple occurrences of 1).
        //        Outcome: CMAP_REJECTED (1).
        // Result: only F1 pattern <(1)> accepted.
        // Exact vector:
        //   sequenceCount=2, f1Count=1, generatedExtensions=1, generatedIExtensions=0, generatedSExtensions=1,
        //   cmapRejected=1, bitmapRejected=0, exactRejected=0, acceptedExtensions=0,
        //   expandedNodes=1, patternCount=1, exactVerifications=0.
        SequenceDatabase database = db(
                record(1, itemset(1)),
                record(2, itemset(1)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertEquals(1, result.acceptedPatterns().size());
        assertEquals(1, result.supports().size());
        Pattern p1 = Pattern.singleton(new Item(1));
        assertEquals(2, result.support(p1));

        assertExactStatistics(result.statistics(),
                2L, 1L, 1L, 0L, 1L, 1L, 0L, 0L, 0L, 1L, 1L, 0L);
    }

    @Test
    @DisplayName("7. I-extension generation and acceptance exact oracle")
    void test07_iExtensionGenerationAndAcceptance() {
        // Independent Candidate Lifecycle Derivation:
        // Input: 2 sequences with co-occurring items 1 and 2 in same itemset:
        //   SID 1: (1 2), SID 2: (1 2)
        // Item 1: count 2, Item 2: count 2, total len 4. Weights: w(1)=0.5, w(2)=0.5, MeanW=0.5.
        // minSup = absolute(1.0). Threshold = 1.0.
        // Both items have support 2 * 0.5 = 1.0 >= 1.0 -> F1 = {1, 2}.
        //
        // Expanded DFS parents:
        // 1) Parent <(1)>:
        //    - Candidate <(1 2)> (I-ext, 2 > 1):
        //      CMAP-I(1, 2) is true; bitmap {1, 2} & {1, 2} = {1, 2} (card 2 >= 2);
        //      exact support in S1, S2 = 2. 2 * 0.5 = 1.0 >= 1.0 -> ACCEPTED (1).
        //    - Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (1).
        //    - Candidate <(1)(2)> (S-ext): CMAP-S(1, 2) false (in same itemset, not strictly later) -> CMAP_REJECTED (2).
        // 2) Child <(1 2)> (accepted leaf, expanded):
        //    - I-ext: item > 2 in F1 -> none.
        //    - Candidate <(1 2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (3).
        //    - Candidate <(1 2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (4).
        // 3) Parent <(2)>:
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (5).
        //    - Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (6).
        //
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=7, generatedIExtensions=1, generatedSExtensions=6,
        //   cmapRejected=6, bitmapRejected=0, exactRejected=0, acceptedExtensions=1,
        //   expandedNodes=3, patternCount=3, exactVerifications=1.
        SequenceDatabase database = db(
                record(1, itemset(1, 2)),
                record(2, itemset(1, 2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));
        Pattern p12I = Pattern.of(itemset(1, 2));

        assertTrue(result.supports().containsKey(p12I));
        assertEquals(2, result.support(p12I));

        assertExactStatistics(result.statistics(),
                2L, 2L, 7L, 1L, 6L, 6L, 0L, 0L, 1L, 3L, 3L, 1L);
    }

    @Test
    @DisplayName("8. S-extension generation and acceptance exact oracle")
    void test08_sExtensionGenerationAndAcceptance() {
        // Independent Candidate Lifecycle Derivation:
        // Input: 2 sequences with sequential items 1 then 2:
        //   SID 1: (1), (2); SID 2: (1), (2)
        // Item 1: count 2, Item 2: count 2, total len 4. Weights: w(1)=0.5, w(2)=0.5, MeanW=0.5.
        // minSup = absolute(1.0). Threshold = 1.0.
        // Both items have support 2 * 0.5 = 1.0 >= 1.0 -> F1 = {1, 2}.
        //
        // Expanded DFS parents:
        // 1) Parent <(1)>:
        //    - Candidate <(1 2)> (I-ext, 2 > 1): CMAP-I(1, 2) false -> CMAP_REJECTED (1).
        //    - Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (2).
        //    - Candidate <(1)(2)> (S-ext):
        //      CMAP-S(1, 2) is true; bitmap {1, 2} & {1, 2} = {1, 2} (card 2 >= 2);
        //      exact support in S1, S2 = 2. 2 * 0.5 = 1.0 >= 1.0 -> ACCEPTED (1).
        // 2) Child <(1)(2)> (accepted leaf, expanded):
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(1)(2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (3).
        //    - Candidate <(1)(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (4).
        // 3) Parent <(2)>:
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (5).
        //    - Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (6).
        //
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=7, generatedIExtensions=1, generatedSExtensions=6,
        //   cmapRejected=6, bitmapRejected=0, exactRejected=0, acceptedExtensions=1,
        //   expandedNodes=3, patternCount=3, exactVerifications=1.
        SequenceDatabase database = db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(1), itemset(2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));
        Pattern p12S = Pattern.of(itemset(1), itemset(2));

        assertTrue(result.supports().containsKey(p12S));
        assertEquals(2, result.support(p12S));

        assertExactStatistics(result.statistics(),
                2L, 2L, 7L, 1L, 6L, 6L, 0L, 0L, 1L, 3L, 3L, 1L);
    }

    @Test
    @DisplayName("9. CMAP_REJECTED witness exact oracle")
    void test09_cmapRejectedWitness() {
        // Independent Candidate Lifecycle Derivation:
        // Uses the database from test 8 (SID 1: (1), (2); SID 2: (1), (2)).
        // Exactly 6 candidates receive terminal outcome CMAP_REJECTED:
        //   - From <(1)>: <(1 2)> (CMAP-I false), <(1)(1)> (CMAP-S false)
        //   - From <(1)(2)>: <(1)(2)(1)> (CMAP-S false), <(1)(2)(2)> (CMAP-S false)
        //   - From <(2)>: <(2)(1)> (CMAP-S false), <(2)(2)> (CMAP-S false)
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=7, generatedIExtensions=1, generatedSExtensions=6,
        //   cmapRejected=6, bitmapRejected=0, exactRejected=0, acceptedExtensions=1,
        //   expandedNodes=3, patternCount=3, exactVerifications=1.
        SequenceDatabase database = db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(1), itemset(2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertExactStatistics(result.statistics(),
                2L, 2L, 7L, 1L, 6L, 6L, 0L, 0L, 1L, 3L, 3L, 1L);
    }

    @Test
    @DisplayName("10. BITMAP_REJECTED witness exact oracle")
    void test10_bitmapRejectedWitness() {
        // Independent Candidate Lifecycle Derivation:
        // Prepared inputs on 4 sequences (mapping universe size 4):
        //   Item 1 bitmap = {1, 2} (card 2), Item 2 bitmap = {1, 3} (card 2).
        //   threshold = 2.0, meanWeight = 1.0. F1 = {1, 2}.
        //   CMAP-I: all false.
        //   CMAP-S: (1, 2) is true (from S1); (1, 1), (2, 1), (2, 2) are false.
        //
        // Expanded DFS parents:
        // 1) Parent <(1)>:
        //    - Candidate <(1 2)> (I-ext): CMAP-I(1, 2) false -> CMAP_REJECTED (1).
        //    - Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (2).
        //    - Candidate <(1)(2)> (S-ext):
        //      CMAP-S(1, 2) is true!
        //      Candidate bitmap: {1, 2} & {1, 3} = {1} (cardinality 1).
        //      Upper bound: 1 * 1.0 = 1.0 < 2.0 -> BITMAP_REJECTED (1).
        // 2) Parent <(2)>:
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (3).
        //    - Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (4).
        //
        // Exact vector:
        //   sequenceCount=4, f1Count=2, generatedExtensions=5, generatedIExtensions=1, generatedSExtensions=4,
        //   cmapRejected=4, bitmapRejected=1, exactRejected=0, acceptedExtensions=0,
        //   expandedNodes=2, patternCount=2, exactVerifications=0.
        SequenceDatabase dbMock = db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(1)),
                record(3, itemset(2)),
                record(4, itemset(3)));
        SidMapping mapping = SidMapping.of(dbMock);
        PrefixTree tree = new PrefixTree();

        Item item1 = new Item(1);
        Item item2 = new Item(2);

        List<WeightedOnePattern> f1 = List.of(
                new WeightedOnePattern(1, 2),
                new WeightedOnePattern(2, 2));

        SidBitmap bm1 = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        SidBitmap bm2 = SidBitmap.builder(mapping).setSid(1).setSid(3).build();
        Map<Integer, SidBitmap> bitmaps = Map.of(1, bm1, 2, bm2);

        VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(dbMock, mapping);

        VerificationResult vr1 = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));
        VerificationResult vr2 = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{2}),
                PatternEndpoints.of(3, new int[]{1})));
        Map<Integer, VerificationResult> verifications = Map.of(1, vr1, 2, vr2);

        CmapBuilder cb = new CmapBuilder();
        CmapI cmapI = cb.buildI(dbMock, List.of(item1, item2));
        CmapS cmapS = cb.buildS(dbMock, List.of(item1, item2));

        ResolvedMinimumSupport minSup = new MinimumSupportResolver().resolve(
                MinimumSupport.absolute(2.0), 4);

        BitPruneMiningResult result = miner.minePreparedIntoResult(
                tree, f1, bitmaps, verifications, cmapI, cmapS, occIndex, 1.0, minSup);

        assertExactStatistics(result.statistics(),
                4L, 2L, 5L, 1L, 4L, 4L, 1L, 0L, 0L, 2L, 2L, 0L);
    }

    @Test
    @DisplayName("11. EXACT_REJECTED witness exact oracle")
    void test11_exactRejectedWitness() {
        // Independent Candidate Lifecycle Derivation:
        // Input: 2 sequences with inverted temporal order:
        //   SID 1: (1), (2); SID 2: (2), (1)
        // Item 1: count 2, Item 2: count 2, total len 4. Weights: w(1)=0.5, w(2)=0.5, MeanW=0.5.
        // minSup = absolute(1.0). Threshold = 1.0. Both items in F1 (support 2 * 0.5 = 1.0 >= 1.0).
        //
        // Expanded DFS parents:
        // 1) Parent <(1)>:
        //    - Candidate <(1 2)> (I-ext): CMAP-I(1, 2) false -> CMAP_REJECTED (1).
        //    - Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (2).
        //    - Candidate <(1)(2)> (S-ext):
        //      CMAP-S(1, 2) true (from S1); bitmap {1, 2} & {1, 2} = {1, 2} (card 2 >= 2);
        //      exact verification: only S1 has 1 before 2; S2 has 2 before 1 (invalid).
        //      exactSupport = 1. 1 * 0.5 = 0.5 < 1.0 -> EXACT_REJECTED (1).
        // 2) Parent <(2)>:
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(2)(1)> (S-ext):
        //      CMAP-S(2, 1) true (from S2); bitmap card 2 >= 2;
        //      exact verification: only S2 has 2 before 1; S1 has 1 before 2 (invalid).
        //      exactSupport = 1. 1 * 0.5 = 0.5 < 1.0 -> EXACT_REJECTED (2).
        //    - Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (3).
        //
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=5, generatedIExtensions=1, generatedSExtensions=4,
        //   cmapRejected=3, bitmapRejected=0, exactRejected=2, acceptedExtensions=0,
        //   expandedNodes=2, patternCount=2, exactVerifications=2.
        SequenceDatabase database = db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(2), itemset(1)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertExactStatistics(result.statistics(),
                2L, 2L, 5L, 1L, 4L, 3L, 0L, 2L, 0L, 2L, 2L, 2L);
    }

    @Test
    @DisplayName("12. ACCEPTED witness exact oracle")
    void test12_acceptedWitness() {
        // Independent Candidate Lifecycle Derivation:
        // Input: SID 1: (1 2), SID 2: (1 2) with minSup = absolute(1.0).
        // Candidate <(1 2)> passes CMAP-I, bitmap bound, and exact verification (support 2 >= 1.0).
        // Accepted extensions = 1 (<(1 2)>).
        // Exact vector matches test 7 exactly:
        //   sequenceCount=2, f1Count=2, generatedExtensions=7, generatedIExtensions=1, generatedSExtensions=6,
        //   cmapRejected=6, bitmapRejected=0, exactRejected=0, acceptedExtensions=1,
        //   expandedNodes=3, patternCount=3, exactVerifications=1.
        SequenceDatabase database = db(
                record(1, itemset(1, 2)),
                record(2, itemset(1, 2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertExactStatistics(result.statistics(),
                2L, 2L, 7L, 1L, 6L, 6L, 0L, 0L, 1L, 3L, 3L, 1L);
    }

    @Test
    @DisplayName("13. Mixed four-outcome controlled run exact oracle")
    void test13_mixedFourOutcomeControlledRun() {
        // Independent Candidate Lifecycle Derivation (C12.3-R1 Section 7.3 & 7.4):
        // Input: 4 sequences, 3 items:
        //   SID 1: (1), (2), (3)   [contains 1, 2, 3; len 3]
        //   SID 2: (1), (2)        [contains 1, 2;    len 2]
        //   SID 3: (2), (1)        [contains 1, 2;    len 2]
        //   SID 4: (3)             [contains 3;       len 1]
        // Total database length = 8.
        // Item occurrences: item 1: 3, item 2: 3, item 3: 2.
        // Weights: w(1) = 3/8, w(2) = 3/8, w(3) = 2/8.
        // MaxW = 3/8, MinW = 2/8 -> MeanW = (3/8 + 2/8) / 2 = 5/16.
        // minSup = absolute(10.0 / 16.0). Effective threshold = 10/16.
        //
        // F1 Membership:
        //   Item 1: support 3, 3 * (5/16) = 15/16 >= 10/16 -> F1
        //   Item 2: support 3, 3 * (5/16) = 15/16 >= 10/16 -> F1
        //   Item 3: support 2, 2 * (5/16) = 10/16 >= 10/16 -> F1
        //   All 3 items in F1 -> f1Count = 3 (sorted: 1, 2, 3).
        //
        // Relations:
        //   CMAP-I: all false (no items co-occur in the same itemset).
        //   CMAP-S: (1, 2) true (S1, S2); (1, 3) true (S1); (2, 1) true (S3); (2, 3) true (S1); all others false.
        //   Bitmaps: item 1 = {1, 2, 3} (card 3); item 2 = {1, 2, 3} (card 3); item 3 = {1, 4} (card 2).
        //   Bitmap filter threshold: card * (5/16) >= 10/16 <=> card >= 2.
        //
        // Complete DFS Candidate Lifecycle Trace:
        //
        // Parent 1: <(1)> (expanded):
        //   1. Candidate <(1 2)> (I-ext): CMAP-I(1, 2) false -> CMAP_REJECTED (1).
        //   2. Candidate <(1 3)> (I-ext): CMAP-I(1, 3) false -> CMAP_REJECTED (2).
        //   3. Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (3).
        //   4. Candidate <(1)(2)> (S-ext):
        //      CMAP-S(1, 2) true; bitmap {1, 2, 3} & {1, 2, 3} = {1, 2, 3} (card 3 >= 2);
        //      exact verification in S1 (1 < 2), S2 (1 < 2) -> support = 2.
        //      2 * (5/16) = 10/16 >= 10/16 -> ACCEPTED (1).
        //   5. Candidate <(1)(3)> (S-ext):
        //      CMAP-S(1, 3) true; bitmap {1, 2, 3} & {1, 4} = {1} (cardinality 1 < 2) -> BITMAP_REJECTED (1).
        //
        // Parent 2: <(1)(2)> (child recursion from accepted candidate 4, expanded):
        //   6. Candidate <(1)(2 3)> (I-ext, 3 > 2): CMAP-I(2, 3) false -> CMAP_REJECTED (4).
        //   7. Candidate <(1)(2)(1)> (S-ext):
        //      CMAP-S(2, 1) true (from S3); bitmap {1, 2, 3} (card 3 >= 2);
        //      exact verification: none of S1, S2, S3 contains (1) then (2) then (1) -> support = 0.
        //      0 < 10/16 -> EXACT_REJECTED (1).
        //   8. Candidate <(1)(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (5).
        //   9. Candidate <(1)(2)(3)> (S-ext):
        //      CMAP-S(2, 3) true (from S1); parent bitmap of <(1)(2)> is {1, 2};
        //      candidate bitmap = {1, 2} & {1, 4} = {1} (cardinality 1 < 2) -> BITMAP_REJECTED (2).
        //
        // Parent 3: <(2)> (expanded):
        //  10. Candidate <(2 3)> (I-ext, 3 > 2): CMAP-I(2, 3) false -> CMAP_REJECTED (6).
        //  11. Candidate <(2)(1)> (S-ext):
        //      CMAP-S(2, 1) true (from S3); bitmap {1, 2, 3} (card 3 >= 2);
        //      exact verification: only S3 has 2 before 1 (S1 and S2 have 1 before 2) -> support = 1.
        //      1 * (5/16) = 5/16 < 10/16 -> EXACT_REJECTED (2).
        //  12. Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (7).
        //  13. Candidate <(2)(3)> (S-ext):
        //      CMAP-S(2, 3) true (from S1); bitmap {1, 2, 3} & {1, 4} = {1} (cardinality 1 < 2) -> BITMAP_REJECTED (3).
        //
        // Parent 4: <(3)> (expanded):
        //  14. Candidate <(3)(1)> (S-ext): CMAP-S(3, 1) false -> CMAP_REJECTED (8).
        //  15. Candidate <(3)(2)> (S-ext): CMAP-S(3, 2) false -> CMAP_REJECTED (9).
        //  16. Candidate <(3)(3)> (S-ext): CMAP-S(3, 3) false -> CMAP_REJECTED (10).
        //
        // Complete Summary Verification:
        //   sequenceCount = 4
        //   f1Count = 3 (<(1)>, <(2)>, <(3)>)
        //   generatedExtensions = 16 (candidates 1 to 16)
        //   generatedIExtensions = 4 (<(1 2)>, <(1 3)>, <(1)(2 3)>, <(2 3)>)
        //   generatedSExtensions = 12 (16 - 4)
        //   cmapRejected = 10 (candidates 1, 2, 3, 6, 8, 10, 12, 14, 15, 16)
        //   bitmapRejected = 3 (candidates 5, 9, 13)
        //   exactRejected = 2 (candidates 7, 11)
        //   acceptedExtensions = 1 (candidate 4: <(1)(2)>)
        //   expandedNodes = 4 (<(1)>, <(1)(2)>, <(2)>, <(3)>)
        //   patternCount = 4 (3 F1 patterns + 1 accepted pattern)
        //   exactVerifications = 3 (2 exact rejected + 1 accepted)
        SequenceDatabase database = db(
                record(1, itemset(1), itemset(2), itemset(3)),
                record(2, itemset(1), itemset(2)),
                record(3, itemset(2), itemset(1)),
                record(4, itemset(3)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(10.0 / 16.0));

        assertExactStatistics(result.statistics(),
                4L, 3L, 16L, 4L, 12L, 10L, 3L, 2L, 1L, 4L, 4L, 3L);
    }

    @Test
    @DisplayName("14. Accepted leaf counts as expanded exact oracle")
    void test14_acceptedLeafCountsAsExpanded() {
        // Independent Candidate Lifecycle Derivation:
        // Input: SID 1: (1 2), SID 2: (1 2) with minSup = absolute(1.0).
        // Pattern <(1 2)> is an accepted leaf (accepted from parent <(1)>).
        // During DFS, <(1 2)> is passed into dfs() as a parent node and executes
        // recordExpandedNode(). It produces 2 candidates (<(1 2)(1)> and <(1 2)(2)>),
        // both CMAP_REJECTED, accepting no descendants.
        // Thus expandedNodes equals 3 (<(1)>, <(1 2)> [leaf], <(2)>), exactly matching patternCount.
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=7, generatedIExtensions=1, generatedSExtensions=6,
        //   cmapRejected=6, bitmapRejected=0, exactRejected=0, acceptedExtensions=1,
        //   expandedNodes=3, patternCount=3, exactVerifications=1.
        SequenceDatabase database = db(
                record(1, itemset(1, 2)),
                record(2, itemset(1, 2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertExactStatistics(result.statistics(),
                2L, 2L, 7L, 1L, 6L, 6L, 0L, 0L, 1L, 3L, 3L, 1L);
    }

    @Test
    @DisplayName("15. Repeated deterministic runs produce identical results and statistics")
    void test15_repeatedDeterministicRuns() {
        SequenceDatabase database = db(
                record(1, itemset(1, 2), itemset(3)),
                record(2, itemset(1, 2), itemset(3, 4)),
                record(3, itemset(2), itemset(3, 4)));

        MinimumSupport minSup = MinimumSupport.absolute(1.0);

        BitPruneMiningResult run1 = miner.mine(database, minSup);
        BitPruneMiningResult run2 = miner.mine(database, minSup);

        assertEquals(run1.supports(), run2.supports());
        assertEquals(run1.acceptedPatterns(), run2.acceptedPatterns());
        assertEquals(run1.statistics(), run2.statistics());
    }

    @Test
    @DisplayName("16. Independent statistics snapshots per invocation")
    void test16_independentStatisticsSnapshots() {
        SequenceDatabase db1 = db(record(1, itemset(1)));
        SequenceDatabase db2 = db(record(1, itemset(1, 2)), record(2, itemset(1, 2)));

        BitPruneMiningResult result1 = miner.mine(db1, MinimumSupport.absolute(1.0));
        BitPruneMiningResult result2 = miner.mine(db2, MinimumSupport.absolute(1.0));

        assertNotSame(result1.statistics(), result2.statistics());
        assertNotEquals(result1.statistics(), result2.statistics());
    }

    @Test
    @DisplayName("17. Existing mineInto(...) supports equal new result API supports")
    void test17_existingMineIntoSupportsEqualNewResultApiSupports() {
        SequenceDatabase database = db(
                record(1, itemset(1, 2), itemset(3)),
                record(2, itemset(1, 2), itemset(3, 4)),
                record(3, itemset(2), itemset(3, 4)));

        MinimumSupport minSup = MinimumSupport.absolute(1.0);

        PrefixTree tree1 = new PrefixTree();
        Map<Pattern, Integer> supports1 = miner.mineInto(tree1, database, minSup);

        PrefixTree tree2 = new PrefixTree();
        BitPruneMiningResult result2 = miner.mineIntoResult(tree2, database, minSup);

        BitPruneMiningResult result3 = miner.mine(database, minSup);

        assertEquals(supports1, result2.supports());
        assertEquals(supports1, result3.supports());

        // Also check iteration order equality
        List<Map.Entry<Pattern, Integer>> list1 = new ArrayList<>(supports1.entrySet());
        List<Map.Entry<Pattern, Integer>> list2 = new ArrayList<>(result2.supports().entrySet());
        assertEquals(list1, list2, "Iteration order must be strictly identical");
    }

    @Test
    @DisplayName("18. Tree, support, and statistics count identities")
    void test18_treeSupportStatisticsIdentities() {
        SequenceDatabase database = db(
                record(1, itemset(1, 2), itemset(3)),
                record(2, itemset(1, 2), itemset(3, 4)),
                record(3, itemset(2), itemset(3, 4)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));
        BitPruneStatistics stats = result.statistics();

        assertInvariants(stats);
        assertEquals((long) result.supports().size(), stats.patternCount());
        assertEquals((long) result.acceptedPatterns().size(), stats.patternCount());
        assertEquals((long) result.prefixTree().nodeCount() - 1L, stats.patternCount());
        assertEquals((long) result.prefixTree().root().children().size(), stats.f1Count());
    }

    @Test
    @DisplayName("19. Prepared path statistics exact oracle")
    void test19_preparedPathStatistics() {
        // Independent Candidate Lifecycle Derivation:
        // Prepared inputs on 2 sequences with F1 = {1} (support 2):
        // Parent <(1)> generates only 1 candidate: S-extension <(1)(1)>.
        // CMAP-S(1, 1) is false -> CMAP_REJECTED (1).
        // Exact vector:
        //   sequenceCount=2, f1Count=1, generatedExtensions=1, generatedIExtensions=0, generatedSExtensions=1,
        //   cmapRejected=1, bitmapRejected=0, exactRejected=0, acceptedExtensions=0,
        //   expandedNodes=1, patternCount=1, exactVerifications=0.
        SequenceDatabase dbMock = db(record(1, itemset(1)), record(2, itemset(1)));
        SidMapping mapping = SidMapping.of(dbMock);
        PrefixTree tree = new PrefixTree();

        Item item1 = new Item(1);
        List<WeightedOnePattern> f1 = List.of(new WeightedOnePattern(1, 2));

        SidBitmap bm1 = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        Map<Integer, SidBitmap> bitmaps = Map.of(1, bm1);

        VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(dbMock, mapping);

        VerificationResult vr1 = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));
        Map<Integer, VerificationResult> verifications = Map.of(1, vr1);

        CmapBuilder cb = new CmapBuilder();
        CmapI cmapI = cb.buildI(dbMock, List.of(item1));
        CmapS cmapS = cb.buildS(dbMock, List.of(item1));

        ResolvedMinimumSupport minSup = new MinimumSupportResolver().resolve(
                MinimumSupport.absolute(1.0), 2);

        BitPruneMiningResult result = miner.minePreparedIntoResult(
                tree, f1, bitmaps, verifications, cmapI, cmapS, occIndex, 1.0, minSup);

        assertNotNull(result);
        assertExactStatistics(result.statistics(),
                2L, 1L, 1L, 0L, 1L, 1L, 0L, 0L, 0L, 1L, 1L, 0L);
    }

    @Test
    @DisplayName("20. Result tree is frozen")
    void test20_resultTreeFrozen() {
        SequenceDatabase database = db(record(1, itemset(1, 2)));
        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertTrue(result.prefixTree().isFrozen(), "PrefixTree in completed result must be frozen");
    }

    @Test
    @DisplayName("21. Result support map is unmodifiable")
    void test21_resultSupportMapImmutable() {
        SequenceDatabase database = db(record(1, itemset(1, 2)));
        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(1.0));

        assertThrows(UnsupportedOperationException.class,
                () -> result.supports().put(Pattern.singleton(new Item(999)), 1));
        assertThrows(UnsupportedOperationException.class,
                () -> result.supports().clear());
    }

    @Test
    @DisplayName("22. F1 items are not counted in generated extensions exact oracle")
    void test22_noF1CountedAsGeneratedExtension() {
        // Independent Candidate Lifecycle Derivation:
        // Input: 2 sequences with singletons:
        //   SID 1: (1); SID 2: (2)
        // Item 1: count 1, weight 0.5. Item 2: count 1, weight 0.5. MeanW = 0.5.
        // minSup = absolute(0.5). Threshold = 0.5. Both in F1 (support 1 * 0.5 = 0.5 >= 0.5).
        // F1 = {1, 2}. f1Count = 2.
        //
        // Expanded DFS parents:
        // 1) Parent <(1)>:
        //    - Candidate <(1 2)> (I-ext, 2 > 1): CMAP-I(1, 2) false -> CMAP_REJECTED (1).
        //    - Candidate <(1)(1)> (S-ext): CMAP-S(1, 1) false -> CMAP_REJECTED (2).
        //    - Candidate <(1)(2)> (S-ext): CMAP-S(1, 2) false -> CMAP_REJECTED (3).
        // 2) Parent <(2)>:
        //    - I-ext: item > 2 -> none.
        //    - Candidate <(2)(1)> (S-ext): CMAP-S(2, 1) false -> CMAP_REJECTED (4).
        //    - Candidate <(2)(2)> (S-ext): CMAP-S(2, 2) false -> CMAP_REJECTED (5).
        //
        // Neither F1 item is counted in generatedExtensions (which is exactly 5 from DFS candidates).
        // Exact vector:
        //   sequenceCount=2, f1Count=2, generatedExtensions=5, generatedIExtensions=1, generatedSExtensions=4,
        //   cmapRejected=5, bitmapRejected=0, exactRejected=0, acceptedExtensions=0,
        //   expandedNodes=2, patternCount=2, exactVerifications=0.
        SequenceDatabase database = db(
                record(1, itemset(1)),
                record(2, itemset(2)));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.absolute(0.5));

        assertExactStatistics(result.statistics(),
                2L, 2L, 5L, 1L, 4L, 5L, 0L, 0L, 0L, 2L, 2L, 0L);
    }

    @Test
    @DisplayName("Table 1 paper dataset complete statistics audit")
    void testTable1PaperDatasetStatistics() throws IOException, URISyntaxException {
        var resource = Objects.requireNonNull(
                BitPruneMiningStatisticsIntegrationTest.class.getResource("/datasets/wspm/table1.spmf"));
        SequenceDatabase database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));

        BitPruneMiningResult result = miner.mine(database, MinimumSupport.percentage(14.0));
        BitPruneStatistics stats = result.statistics();

        assertInvariants(stats);
        assertEquals(7L, stats.sequenceCount()); // Table 1 has 7 sequences
        assertEquals(9L, stats.patternCount());  // Table 1 paper oracle has exactly 9 patterns
        assertEquals(9L, (long) result.supports().size());
        assertEquals(9L, (long) result.acceptedPatterns().size());
        assertEquals(10, result.prefixTree().nodeCount()); // 9 patterns + 1 root

        // All 9 patterns verified
        assertTrue(stats.f1Count() > 0L);
        assertTrue(stats.acceptedExtensions() > 0L);
        assertTrue(stats.expandedNodes() > 0L);
        assertTrue(stats.exactVerifications() > 0L);
    }
}
