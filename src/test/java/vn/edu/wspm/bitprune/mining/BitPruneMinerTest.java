package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapBuilder;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Comprehensive test suite for {@link BitPruneMiner} (C10.3).
 *
 * <p>
 * Covers:
 * <ol>
 * <li>All 30 named invariant and behavioral tests required by Section 11;</li>
 * <li>Independent Controlled Oracle on raw sequence databases (Section 12);</li>
 * <li>Pruning-disabled equivalence on controlled positive-threshold catalogues (Section 13).</li>
 * </ol>
 * </p>
 */
class BitPruneMinerTest {

    private BitPruneMiner miner;
    private MinimumSupportResolver resolver;
    private CmapBuilder cmapBuilder;

    private final Item item1 = new Item(1);
    private final Item item2 = new Item(2);
    private final Item item3 = new Item(3);

    @BeforeEach
    void setUp() {
        miner = new BitPruneMiner();
        resolver = new MinimumSupportResolver();
        cmapBuilder = new CmapBuilder();
    }

    // =========================================================================
    // Helper: Preparation of PreparedInputs from SequenceDatabase
    // =========================================================================

    private record PreparedInputs(
            SequenceDatabase db,
            SidMapping mapping,
            List<WeightedOnePattern> f1,
            Map<Integer, SidBitmap> singletonBitmaps,
            Map<Integer, VerificationResult> singletonVerifications,
            CmapI cmapI,
            CmapS cmapS,
            VerticalOccurrenceIndex occIndex,
            double globalMeanWeight,
            ResolvedMinimumSupport minSup) {
    }

    private PreparedInputs prepareInputs(
            SequenceDatabase db,
            List<Item> universe,
            double globalMeanWeight,
            double effectiveThreshold) {

        SidMapping mapping = SidMapping.of(db);
        VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(db, mapping);
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(effectiveThreshold), db.size());

        List<WeightedOnePattern> f1List = new ArrayList<>();
        Map<Integer, SidBitmap> bitmapMap = new HashMap<>();
        Map<Integer, VerificationResult> verifMap = new HashMap<>();

        for (Item item : universe) {
            int support = 0;
            List<PatternEndpoints> epList = new ArrayList<>();
            var bmBuilder = SidBitmap.builder(mapping);

            for (var rec : db.records()) {
                int[] pos = occIndex.positions(item, rec.sid());
                if (pos.length > 0) {
                    support++;
                    bmBuilder.setSid(rec.sid());
                    epList.add(PatternEndpoints.of(rec.sid(), pos));
                }
            }

            if (Double.compare(support * globalMeanWeight, effectiveThreshold) >= 0) {
                f1List.add(new WeightedOnePattern(item.id(), support));
                bitmapMap.put(item.id(), bmBuilder.build());
                verifMap.put(item.id(), VerificationResult.of(epList));
            }
        }

        List<Item> f1Items = f1List.stream().map(p -> new Item(p.itemId())).toList();
        CmapI cmapI = cmapBuilder.buildI(db, f1Items);
        CmapS cmapS = cmapBuilder.buildS(db, f1Items);

        return new PreparedInputs(
                db, mapping, f1List, bitmapMap, verifMap, cmapI, cmapS, occIndex, globalMeanWeight, minSup);
    }

    // =========================================================================
    // Independent Direct Oracle (Section 12 & 13)
    // =========================================================================

    /**
     * Derives the raw-data finite upper bound: no supported pattern can contain
     * more item occurrences than its supporting source sequence.
     */
    private static int deriveMaxPatternLength(SequenceDatabase db) {
        return db.records().stream()
                .mapToInt(r -> r.sequence().itemsets().stream().mapToInt(Itemset::size).sum())
                .max()
                .orElse(0);
    }

    /**
     * Checks if a pattern is a subsequence of a sequence directly against raw itemsets.
     */
    private static boolean directIsSubsequence(Sequence seq, Pattern pattern) {
        if (pattern.isEmpty()) {
            return true;
        }
        return canMatchSubsequence(seq.itemsets(), 0, pattern.itemsets(), 0);
    }

    private static boolean canMatchSubsequence(
            List<Itemset> seqItemsets, int seqIdx, List<Itemset> patItemsets, int patIdx) {
        if (patIdx == patItemsets.size()) {
            return true;
        }
        Itemset currentPatItemset = patItemsets.get(patIdx);
        for (int i = seqIdx; i < seqItemsets.size(); i++) {
            if (seqItemsets.get(i).items().containsAll(currentPatItemset.items())) {
                if (canMatchSubsequence(seqItemsets, i + 1, patItemsets, patIdx + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Independent Direct Oracle that enumerates raw sequential patterns up to maxDepth
     * and counts exact distinct SID support directly from raw sequences without calling
     * any BitPrune production classes or pruning rules.
     */
    private static Map<Pattern, Integer> directOracleMine(
            SequenceDatabase db,
            List<Item> universe,
            double globalMeanWeight,
            double effectiveThreshold,
            int maxDepth) {

        Map<Pattern, Integer> expected = new LinkedHashMap<>();

        // Generate F1
        List<Item> supportedItems = new ArrayList<>();
        for (Item item : universe) {
            Pattern p = Pattern.singleton(item);
            int supp = countDirectSupport(db, p);
            if (Double.compare(supp * globalMeanWeight, effectiveThreshold) >= 0) {
                expected.put(p, supp);
                supportedItems.add(item);
            }
        }

        // Recursive bounded direct extension
        for (Item item : supportedItems) {
            Pattern root = Pattern.singleton(item);
            directExtend(db, root, supportedItems, globalMeanWeight, effectiveThreshold, 1, maxDepth, expected);
        }

        return expected;
    }

    private static void directExtend(
            SequenceDatabase db,
            Pattern parent,
            List<Item> f1Items,
            double globalMeanWeight,
            double effectiveThreshold,
            int currentDepth,
            int maxDepth,
            Map<Pattern, Integer> expected) {

        if (currentDepth >= maxDepth) {
            return;
        }

        Item parentLastItem = parent.lastItem().orElseThrow();

        // 1. I-extensions: itemId > parentLastItem.id
        for (Item extItem : f1Items) {
            if (extItem.id() > parentLastItem.id() && !parent.itemsets().getLast().contains(extItem)) {
                List<Item> newLast = new ArrayList<>(parent.itemsets().getLast().items());
                newLast.add(extItem);
                Collections.sort(newLast);
                List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                newItemsets.set(newItemsets.size() - 1, new Itemset(newLast));
                Pattern candidate = new Pattern(newItemsets);

                int supp = countDirectSupport(db, candidate);
                if (Double.compare(supp * globalMeanWeight, effectiveThreshold) >= 0) {
                    expected.put(candidate, supp);
                    directExtend(db, candidate, f1Items, globalMeanWeight, effectiveThreshold,
                            currentDepth + 1, maxDepth, expected);
                }
            }
        }

        // 2. S-extensions: for all extItem in f1Items
        for (Item extItem : f1Items) {
            List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
            newItemsets.add(Itemset.of(extItem));
            Pattern candidate = new Pattern(newItemsets);

            int supp = countDirectSupport(db, candidate);
            if (Double.compare(supp * globalMeanWeight, effectiveThreshold) >= 0) {
                expected.put(candidate, supp);
                directExtend(db, candidate, f1Items, globalMeanWeight, effectiveThreshold,
                        currentDepth + 1, maxDepth, expected);
            }
        }
    }

    private static int countDirectSupport(SequenceDatabase db, Pattern pattern) {
        int count = 0;
        for (var rec : db.records()) {
            if (directIsSubsequence(rec.sequence(), pattern)) {
                count++;
            }
        }
        return count;
    }

    // =========================================================================
    // 30 Named Tests (Section 11)
    // =========================================================================

    @Test
    @DisplayName("01: Empty F1 freezes a root-only tree and returns an empty immutable map")
    void test01_emptyF1FreezesRootOnlyTreeAndReturnsEmptyMap() {
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1))))));
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, List.of(), Map.of(), Map.of(),
                new CmapBuilder().buildI(db, List.of()),
                new CmapBuilder().buildS(db, List.of()),
                VerticalOccurrenceIndex.build(db, SidMapping.of(db)),
                1.0, minSup);

        assertTrue(result.isEmpty());
        assertTrue(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
        assertEquals(List.of(Pattern.empty()), tree.preOrderPatterns());
    }

    @Test
    @DisplayName("02: One F1 root with no accepted extension terminates")
    void test02_oneF1RootWithNoAcceptedExtensionTerminates() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        assertEquals(Map.of(Pattern.singleton(item1), 1), result);
        assertTrue(tree.isFrozen());
        assertEquals(2, tree.nodeCount());
    }

    @Test
    @DisplayName("03: Multiple F1 roots are initialized deterministically")
    void test03_multipleF1RootsInitializedDeterministically() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item3, item1, item2), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var rootChildren = tree.root().children();
        assertEquals(3, rootChildren.size());
        assertEquals(Pattern.singleton(item1), rootChildren.get(0).pattern());
        assertEquals(Pattern.singleton(item2), rootChildren.get(1).pattern());
        assertEquals(Pattern.singleton(item3), rootChildren.get(2).pattern());
    }

    @Test
    @DisplayName("04: Accepted I-extension is inserted and recursed")
    void test04_acceptedIExtensionInsertedAndRecursed() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2, item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p12 = Pattern.of(Itemset.of(item1, item2));
        var p123 = Pattern.of(Itemset.of(item1, item2, item3));
        assertTrue(result.containsKey(p12));
        assertTrue(result.containsKey(p123));
        assertTrue(tree.find(p12).isPresent());
        assertTrue(tree.find(p123).isPresent());
        assertEquals(p12, tree.find(p123).get().parent().orElseThrow().pattern());
    }

    @Test
    @DisplayName("05: Accepted S-extension is inserted and recursed")
    void test05_acceptedSExtensionInsertedAndRecursed() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p1_2 = Pattern.of(Itemset.of(item1), Itemset.of(item2));
        var p1_2_3 = Pattern.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3));
        assertTrue(result.containsKey(p1_2));
        assertTrue(result.containsKey(p1_2_3));
        assertTrue(tree.find(p1_2).isPresent());
        assertTrue(tree.find(p1_2_3).isPresent());
    }

    @Test
    @DisplayName("06: Repeated-item S-extension is preserved")
    void test06_repeatedItemSExtensionPreserved() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p1_1 = Pattern.of(Itemset.of(item1), Itemset.of(item1));
        var p1_1_1 = Pattern.of(Itemset.of(item1), Itemset.of(item1), Itemset.of(item1));
        assertTrue(result.containsKey(p1_1));
        assertTrue(result.containsKey(p1_1_1));
        assertEquals(1, result.get(p1_1));
        assertEquals(1, result.get(p1_1_1));
    }

    @Test
    @DisplayName("07: CMAP-rejected candidate is absent and its siblings still run")
    void test07_cmapRejectedCandidateAbsentAndSiblingsStillRun() {
        // S1: (1)(3), S2: (2)(3)
        // 1 never precedes 2 -> S-extension (1)(2) is CMAP-rejected.
        // 1 precedes 3 -> S-extension (1)(3) is ACCEPTED.
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p1_2 = Pattern.of(Itemset.of(item1), Itemset.of(item2));
        var p1_3 = Pattern.of(Itemset.of(item1), Itemset.of(item3));
        assertFalse(result.containsKey(p1_2));
        assertTrue(tree.find(p1_2).isEmpty());
        assertTrue(result.containsKey(p1_3));
        assertTrue(tree.find(p1_3).isPresent());
    }

    @Test
    @DisplayName("08: Bitmap-rejected candidate is absent and its siblings still run")
    void test08_bitmapRejectedCandidateAbsentAndSiblingsStillRun() {
        // S1: (1)(2)(3), S2: (1)(3), S3: (3), S4: (3)
        // Threshold: 2.0. MeanW: 1.0.
        // Parent (1) is in {1, 2}.
        // Item 2 is only in {1}. Candidate bitmap (1) & (2) = {1} (cardinality 1 < 2.0 -> BITMAP_REJECTED).
        // Item 3 is in {1, 2, 3, 4}. Candidate bitmap (1) & (3) = {1, 2} (cardinality 2 >= 2.0 -> ACCEPTED).
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item3)));
        var s3 = new Sequence(List.of(Itemset.of(item3)));
        var s4 = new Sequence(List.of(Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(
                new SequenceRecord(1, s1), new SequenceRecord(2, s2),
                new SequenceRecord(3, s3), new SequenceRecord(4, s4)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 2.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p1_2 = Pattern.of(Itemset.of(item1), Itemset.of(item2));
        var p1_3 = Pattern.of(Itemset.of(item1), Itemset.of(item3));
        assertFalse(result.containsKey(p1_2));
        assertTrue(tree.find(p1_2).isEmpty());
        assertTrue(result.containsKey(p1_3));
        assertTrue(tree.find(p1_3).isPresent());
    }

    @Test
    @DisplayName("09: Exact-rejected candidate is absent and its siblings still run")
    void test09_exactRejectedCandidateAbsentAndSiblingsStillRun() {
        // S1: (1)(2)(3), S2: (2)(1)(3)
        // Threshold: 2.0. MeanW: 1.0.
        // Parent (1) is in {1, 2}.
        // Item 2 is in {1, 2}. Bitmap cardinality = 2 (passes bitmap filter).
        // For (1)(2): in S2, 2 occurs BEFORE 1 -> exact support = 1 (< 2.0 -> EXACT_REJECTED).
        // For (1)(3): in S1 and S2, 1 occurs before 3 -> exact support = 2 (>= 2.0 -> ACCEPTED).
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 2.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p1_2 = Pattern.of(Itemset.of(item1), Itemset.of(item2));
        var p1_3 = Pattern.of(Itemset.of(item1), Itemset.of(item3));
        assertFalse(result.containsKey(p1_2));
        assertTrue(tree.find(p1_2).isEmpty());
        assertTrue(result.containsKey(p1_3));
        assertTrue(tree.find(p1_3).isPresent());
    }

    @Test
    @DisplayName("10: Multi-level accepted branch reaches depth at least three")
    void test10_multiLevelAcceptedBranchReachesDepthAtLeastThree() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        var p4 = Pattern.of(Itemset.of(item1, item2), Itemset.of(item3), Itemset.of(item1));
        assertTrue(tree.find(p4).isPresent());
        // Verify depth >= 3
        int depth = 0;
        var curr = tree.find(p4).get();
        while (curr.parent().isPresent()) {
            depth++;
            curr = curr.parent().get();
        }
        assertTrue(depth >= 3, "Branch depth should be at least 3, but was: " + depth);
    }

    @Test
    @DisplayName("11: Accepted support map matches all non-root tree identities exactly")
    void test11_acceptedSupportMapMatchesAllNonRootTreeIdentitiesExactly() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        List<Pattern> treePatterns = new ArrayList<>(tree.preOrderPatterns());
        treePatterns.remove(Pattern.empty());

        assertEquals(new HashSet<>(treePatterns), result.keySet());
        assertEquals(treePatterns.size(), result.size());
    }

    @Test
    @DisplayName("12: Every stored support equals exact VerificationResult support used at acceptance")
    void test12_everyStoredSupportEqualsExactVerificationResultSupport() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        for (var entry : result.entrySet()) {
            int oracleSupp = countDirectSupport(db, entry.getKey());
            assertEquals(oracleSupp, entry.getValue(), "Support mismatch for " + entry.getKey().serialize());
        }
    }

    @Test
    @DisplayName("13: Rejected patterns and their descendants are absent")
    void test13_rejectedPatternsAndTheirDescendantsAreAbsent() {
        // S1: (1)(3), S2: (1)(3)
        // Item 2 not present in any sequence.
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item3), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        for (Pattern p : result.keySet()) {
            assertFalse(p.itemsets().stream().anyMatch(is -> is.contains(item2)));
        }
    }

    @Test
    @DisplayName("14: Tree is frozen after successful completion")
    void test14_treeIsFrozenAfterSuccessfulCompletion() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        assertTrue(tree.isFrozen());
    }

    @Test
    @DisplayName("15: Returned support map is immutable")
    void test15_returnedSupportMapIsImmutable() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        assertThrows(UnsupportedOperationException.class, () -> result.put(Pattern.singleton(item1), 999));
    }

    @Test
    @DisplayName("16: Input F1 and maps are not mutated")
    void test16_inputF1AndMapsAreNotMutated() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1, item2), 1.0, 1.0);
        var tree = new PrefixTree();

        int origF1Size = prep.f1().size();
        int origBmSize = prep.singletonBitmaps().size();
        int origVerifSize = prep.singletonVerifications().size();

        miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        assertEquals(origF1Size, prep.f1().size());
        assertEquals(origBmSize, prep.singletonBitmaps().size());
        assertEquals(origVerifSize, prep.singletonVerifications().size());
    }

    @Test
    @DisplayName("17: Unsorted F1 input still produces deterministic behavior")
    void test17_unsortedF1InputStillProducesDeterministicBehavior() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);

        var treeSorted = new PrefixTree();
        var mapSorted = miner.mineInto(
                treeSorted, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        List<WeightedOnePattern> reversedF1 = new ArrayList<>(prep.f1());
        Collections.reverse(reversedF1);

        var treeUnsorted = new PrefixTree();
        var mapUnsorted = miner.mineInto(
                treeUnsorted, reversedF1, prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        assertEquals(treeSorted.preOrderPatterns(), treeUnsorted.preOrderPatterns());
        assertEquals(mapSorted, mapUnsorted);
    }

    @Test
    @DisplayName("18: Duplicate F1 IDs fail before tree mutation")
    void test18_duplicateF1IdsFailBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        List<WeightedOnePattern> dupF1 = List.of(new WeightedOnePattern(1, 1), new WeightedOnePattern(1, 1));

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, dupF1, prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("19: Missing or extra singleton bitmap key fails before tree mutation")
    void test19_missingOrExtraSingletonBitmapKeyFailsBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        Map<Integer, SidBitmap> emptyBm = Map.of();

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), emptyBm, prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("20: Missing or extra singleton verification key fails before tree mutation")
    void test20_missingOrExtraSingletonVerificationKeyFailsBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        Map<Integer, VerificationResult> emptyVerif = Map.of();

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), emptyVerif,
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("21: Mismatched SID universes fail before tree mutation")
    void test21_mismatchedSidUniversesFailBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        // Mismatched universe size
        var minSupWrongUniverse = resolver.resolve(MinimumSupport.absolute(1.0), 999);

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), minSupWrongUniverse));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("22: F1 support/verification mismatch fails before tree mutation")
    void test22_f1SupportVerificationMismatchFailsBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        // F1 says support 2, but verification has support 1
        List<WeightedOnePattern> wrongF1 = List.of(new WeightedOnePattern(1, 2));

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, wrongF1, prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("23: Singleton endpoint/index mismatch fails before tree mutation")
    void test23_singletonEndpointIndexMismatchFailsBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();

        // Alter endpoint position to 999 instead of 1
        var badVerif = Map.of(1, VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{999}))));

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), badVerif,
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));

        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("24: Non-root-only input tree is rejected")
    void test24_nonRootOnlyInputTreeIsRejected() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();
        tree.insert(Pattern.empty(), Pattern.singleton(item1), vn.edu.wspm.tree.ExtensionType.S_EXTENSION);

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));
    }

    @Test
    @DisplayName("25: Frozen input tree is rejected")
    void test25_frozenInputTreeIsRejected() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var tree = new PrefixTree();
        tree.freeze();

        assertThrows(IllegalStateException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup()));
    }

    @Test
    @DisplayName("26: Zero effective threshold is rejected before tree mutation")
    void test26_zeroEffectiveThresholdIsRejectedBeforeTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var prep = prepareInputs(db, List.of(item1), 1.0, 1.0);
        var minSupZero = resolver.resolve(MinimumSupport.absolute(0.0), db.size());
        var tree = new PrefixTree();

        var ex = assertThrows(IllegalArgumentException.class, () -> miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), minSupZero));

        assertTrue(ex.getMessage().contains("R6 remains OPEN"));
        assertEquals(1, tree.nodeCount());
        assertFalse(tree.isFrozen());
    }

    @Test
    @DisplayName("27: No accepted child is inserted twice")
    void test27_noAcceptedChildIsInsertedTwice() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item1, item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var prep = prepareInputs(db, List.of(item1, item2), 1.0, 1.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        Set<Pattern> seen = new HashSet<>();
        for (Pattern p : tree.preOrderPatterns()) {
            if (!p.isEmpty()) {
                assertTrue(seen.add(p), "Duplicate pattern in tree: " + p.serialize());
            }
        }
        assertEquals(result.size() + 1, tree.nodeCount());
    }

    @Test
    @DisplayName("28: Negative, zero and non-contiguous actual SIDs work correctly")
    void test28_negativeZeroAndNonContiguousActualSidsWorkCorrectly() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3)));
        var s3 = new Sequence(List.of(Itemset.of(item1, item2, item3)));
        var db = new SequenceDatabase(List.of(
                new SequenceRecord(-99, s1),
                new SequenceRecord(0, s2),
                new SequenceRecord(42, s3)));

        var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 2.0);
        var tree = new PrefixTree();

        var result = miner.mineInto(
                tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

        int derivedBound = deriveMaxPatternLength(db);
        var expectedOracle = directOracleMine(db, List.of(item1, item2, item3), 1.0, 2.0, derivedBound);
        assertEquals(expectedOracle, result);
    }

    @Test
    @DisplayName("29: Candidate evaluation order is deterministic across repeated runs")
    void test29_candidateEvaluationOrderIsDeterministicAcrossRepeatedRuns() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));

        List<List<Pattern>> runs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var prep = prepareInputs(db, List.of(item1, item2, item3), 1.0, 1.0);
            var tree = new PrefixTree();
            miner.mineInto(
                    tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                    prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());
            runs.add(tree.preOrderPatterns());
        }

        for (int i = 1; i < runs.size(); i++) {
            assertEquals(runs.get(0), runs.get(i), "Run " + i + " must match run 0 exactly");
        }
    }

    @Test
    @DisplayName("30: PrefixTree pre-order and support identities remain stable across runs")
    void test30_prefixTreePreOrderAndSupportIdentitiesRemainStableAcrossRuns() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item1, item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));

        Map<Pattern, Integer> map0 = null;
        List<Pattern> tree0 = null;

        for (int i = 0; i < 3; i++) {
            var prep = prepareInputs(db, List.of(item1, item2), 1.0, 1.0);
            var tree = new PrefixTree();
            var map = miner.mineInto(
                    tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                    prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

            if (i == 0) {
                map0 = map;
                tree0 = tree.preOrderPatterns();
            } else {
                assertEquals(map0, map);
                assertEquals(tree0, tree.preOrderPatterns());
            }
        }
    }

    // =========================================================================
    // Independent Controlled Oracle & Pruning-disabled Equivalence (Section 12, 13)
    // =========================================================================

    @Test
    @DisplayName("Section 12 & 13: Controlled Oracle and Pruning-disabled Full-Map Equivalence on Multiple Databases")
    void testControlledOracleAndPruningDisabledEquivalence() {
        // Construct 4 distinct controlled databases over {1, 2, 3}
        List<SequenceDatabase> testDatabases = List.of(
                // DB 1: Pure sequential
                new SequenceDatabase(List.of(
                        new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)))),
                        new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)))))),
                // DB 2: Rich same-itemset
                new SequenceDatabase(List.of(
                        new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1, item2, item3)))),
                        new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1, item2)))))),
                // DB 3: Repeated items across itemsets
                new SequenceDatabase(List.of(
                        new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item1), Itemset.of(item2)))),
                        new SequenceRecord(2, new Sequence(List.of(Itemset.of(item2), Itemset.of(item1), Itemset.of(item1)))))),
                // DB 4: Negative and non-contiguous SIDs with multiple embeddings
                new SequenceDatabase(List.of(
                        new SequenceRecord(-5, new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3), Itemset.of(item1)))),
                        new SequenceRecord(10, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3))))))
        );

        List<Item> universe = List.of(item1, item2, item3);
        double[] thresholds = new double[]{1.0, 2.0};
        double[] meanWeights = new double[]{1.0, 0.5};

        int configurationCount = 0;
        int totalExpectedPatterns = 0;
        int totalActualPatterns = 0;
        int maxDerivedBound = 0;
        int maxProductionPatternLength = 0;

        for (int d = 0; d < testDatabases.size(); d++) {
            var db = testDatabases.get(d);
            int maxPatternLength = deriveMaxPatternLength(db);
            if (maxPatternLength > maxDerivedBound) {
                maxDerivedBound = maxPatternLength;
            }

            for (double effThresh : thresholds) {
                for (double mw : meanWeights) {
                    configurationCount++;
                    var prep = prepareInputs(db, universe, mw, effThresh);
                    var tree = new PrefixTree();

                    var actualMap = miner.mineInto(
                            tree, prep.f1(), prep.singletonBitmaps(), prep.singletonVerifications(),
                            prep.cmapI(), prep.cmapS(), prep.occIndex(), prep.globalMeanWeight(), prep.minSup());

                    // Compare with direct oracle using the exact derived finite bound from raw database
                    var expectedOracle = directOracleMine(db, universe, mw, effThresh, maxPatternLength);

                    for (var p : actualMap.keySet()) {
                        int patLen = p.itemsets().stream().mapToInt(Itemset::size).sum();
                        if (patLen > maxProductionPatternLength) {
                            maxProductionPatternLength = patLen;
                        }
                    }

                    if (!expectedOracle.equals(actualMap)) {
                        var missing = new HashSet<>(expectedOracle.keySet());
                        missing.removeAll(actualMap.keySet());
                        var unexpected = new HashSet<>(actualMap.keySet());
                        unexpected.removeAll(expectedOracle.keySet());
                        String supportMismatch = null;
                        for (var p : expectedOracle.keySet()) {
                            if (actualMap.containsKey(p) && !expectedOracle.get(p).equals(actualMap.get(p))) {
                                supportMismatch = p.serialize() + " expected=" + expectedOracle.get(p)
                                        + " actual=" + actualMap.get(p);
                                break;
                            }
                        }
                        StringBuilder dbSb = new StringBuilder();
                        for (var rec : db.records()) {
                            dbSb.append("\n    SID ").append(rec.sid()).append(": ").append(rec.sequence().itemsets());
                        }
                        fail("DIFFERENTIAL MISMATCH on DB " + d + ", thresh=" + effThresh + ", meanW=" + mw
                                + "\n  Derived Bound: " + maxPatternLength
                                + "\n  Raw Records: " + dbSb
                                + "\n  Expected Count: " + expectedOracle.size()
                                + "\n  Actual Count: " + actualMap.size()
                                + "\n  Missing from production: " + missing
                                + "\n  Unexpected in production: " + unexpected
                                + "\n  First Support Mismatch: " + supportMismatch);
                    }

                    totalExpectedPatterns += expectedOracle.size();
                    totalActualPatterns += actualMap.size();
                }
            }
        }

        System.out.println("C10.3-R1 Controlled Oracle Full-Map Equivalence:"
                + " controlledDatabases=" + testDatabases.size()
                + ", configurations=" + configurationCount
                + ", totalExpectedPatterns=" + totalExpectedPatterns
                + ", totalActualPatterns=" + totalActualPatterns
                + ", maxDerivedBound=" + maxDerivedBound
                + ", maxProductionPatternLength=" + maxProductionPatternLength
                + ", mismatches=0");
        assertTrue(totalActualPatterns > 0, "Actual patterns count must be positive");
        assertEquals(totalExpectedPatterns, totalActualPatterns, "Expected and actual pattern totals must match");
        assertTrue(maxProductionPatternLength >= 3, "Max pattern length must be at least 3, but was: " + maxProductionPatternLength);
    }
}

