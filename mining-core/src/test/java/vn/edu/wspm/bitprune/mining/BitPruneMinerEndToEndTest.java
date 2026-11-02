package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.mining.F1Builder;
import vn.edu.wspm.mining.WeightedOnePattern;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.statistics.ItemWeightResult;
import vn.edu.wspm.statistics.SequenceDatabaseStatistics;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Independent end-to-end differential and boundary verification for the public
 * BitPruneMiner preprocessing overload (C10.4).
 *
 * <p>
 * Evaluates the public entry point:
 * {@code mineInto(PrefixTree, SequenceDatabase, MinimumSupport)}
 * directly against an independent raw SequenceDatabase oracle that reconstructs
 * item occurrence counting, item weights, MeanW, effective threshold, F1,
 * and direct subsequence containment without calling any BitPrune production classes.
 * </p>
 */
public class BitPruneMinerEndToEndTest {

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

    // DB 1: Same-itemset co-occurrence
    // SID 1: (1 2), (3)
    // SID 2: (1 2), (3 4)
    // SID 3: (2), (3 4)
    private static SequenceDatabase db1() {
        return db(
                record(1, itemset(1, 2), itemset(3)),
                record(2, itemset(1, 2), itemset(3, 4)),
                record(3, itemset(2), itemset(3, 4)));
    }

    // DB 2: Strict sequential order
    // SID 10: (1), (2), (3)
    // SID 20: (1), (2), (3), (4)
    // SID 30: (1), (3), (4)
    private static SequenceDatabase db2() {
        return db(
                record(10, itemset(1), itemset(2), itemset(3)),
                record(20, itemset(1), itemset(2), itemset(3), itemset(4)),
                record(30, itemset(1), itemset(3), itemset(4)));
    }

    // DB 3: Repeated items and multiple embeddings
    // SID 100: (1), (2), (1), (2)
    // SID 200: (1), (1), (2)
    // SID 300: (2), (1), (2)
    private static SequenceDatabase db3() {
        return db(
                record(100, itemset(1), itemset(2), itemset(1), itemset(2)),
                record(200, itemset(1), itemset(1), itemset(2)),
                record(300, itemset(2), itemset(1), itemset(2)));
    }

    // DB 4: Negative, zero, and non-contiguous SIDs
    // SID -5: (1 2), (3)
    // SID 0: (1), (2 3)
    // SID 42: (1 2), (2 3)
    // SID 999: (1), (3)
    private static SequenceDatabase db4() {
        return db(
                record(-5, itemset(1, 2), itemset(3)),
                record(0, itemset(1), itemset(2, 3)),
                record(42, itemset(1, 2), itemset(2, 3)),
                record(999, itemset(1), itemset(3)));
    }

    // =========================================================================
    // Independent Direct Oracle (Section 11)
    // =========================================================================

    /**
     * Derives the raw-data finite upper bound: maximum sequence length across the database.
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

    private static int countDirectSupport(SequenceDatabase db, Pattern pattern) {
        int count = 0;
        for (var rec : db.records()) {
            if (directIsSubsequence(rec.sequence(), pattern)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calculates MeanW directly from raw SequenceDatabase data without calling any production classes.
     */
    static double calculateDirectMeanW(SequenceDatabase db) {
        Map<Integer, Integer> occurrences = new TreeMap<>();
        long totalLength = 0;
        for (var rec : db.records()) {
            for (var is : rec.sequence().itemsets()) {
                totalLength += is.size();
                for (var it : is.items()) {
                    occurrences.merge(it.id(), 1, Integer::sum);
                }
            }
        }

        if (occurrences.isEmpty() || totalLength == 0) {
            return 0.0;
        }

        double maxW = Double.NEGATIVE_INFINITY;
        double minW = Double.POSITIVE_INFINITY;
        for (int count : occurrences.values()) {
            double w = (double) count / totalLength;
            maxW = Math.max(maxW, w);
            minW = Math.min(minW, w);
        }
        return (maxW + minW) / 2.0;
    }

    /**
     * Calculates the effective threshold directly from raw SequenceDatabase data and MinimumSupport
     * without calling any production classes or resolvers.
     */
    static double calculateDirectEffectiveThreshold(SequenceDatabase db, MinimumSupport minSup) {
        if (minSup.mode() == MinimumSupportMode.PERCENT) {
            return (minSup.configuredValue() / 100.0) * db.size();
        } else {
            return minSup.configuredValue();
        }
    }

    /**
     * Formats a comprehensive, deterministic mismatch diagnostic per C10.4-R1 Section 4.
     */
    static String formatMismatchDiagnostic(
            String caseName,
            SequenceDatabase db,
            MinimumSupport minSup,
            Map<Pattern, Integer> expected,
            Map<Pattern, Integer> actual) {

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== END-TO-END DIFFERENTIAL MISMATCH DIAGNOSTIC ===\n");
        sb.append("Case Name: ").append(caseName).append("\n");

        // 1. Raw SequenceDatabase
        sb.append("Raw SequenceDatabase (recordCount=").append(db.size()).append("):\n");
        for (int i = 0; i < db.records().size(); i++) {
            SequenceRecord rec = db.records().get(i);
            sb.append("  [").append(i).append("] SID=").append(rec.sid()).append(": ");
            String seqStr = rec.sequence().itemsets().stream()
                    .map(is -> "(" + is.items().stream()
                            .map(it -> String.valueOf(it.id()))
                            .collect(Collectors.joining(" ")) + ")")
                    .collect(Collectors.joining(", "));
            sb.append(seqStr).append("\n");
        }

        // 2. Minimum-support configuration
        double effectiveThreshold = calculateDirectEffectiveThreshold(db, minSup);
        sb.append("Minimum-Support Configuration:\n");
        sb.append("  Mode: ").append(minSup.mode()).append("\n");
        sb.append("  Configured Value: ").append(minSup.configuredValue()).append("\n");
        sb.append("  Independently Calculated Effective Threshold: ").append(effectiveThreshold).append("\n");

        // 3. Independently calculated MeanW
        double meanW = calculateDirectMeanW(db);
        sb.append("Independently Calculated MeanW: ").append(meanW).append("\n");

        // 4. Raw-data-derived maximum pattern-length bound
        int maxBound = deriveMaxPatternLength(db);
        sb.append("Raw-Data-Derived Maximum Pattern-Length Bound: ").append(maxBound).append("\n");

        // 5. Pattern counts
        sb.append("Pattern Counts:\n");
        sb.append("  Expected Count: ").append(expected.size()).append("\n");
        sb.append("  Actual Count: ").append(actual.size()).append("\n");

        // 6. Missing patterns (expected - actual) in deterministic order
        List<Pattern> missing = expected.keySet().stream()
                .filter(p -> !actual.containsKey(p))
                .sorted(Comparator.comparing(Pattern::serialize))
                .toList();
        sb.append("Missing Patterns (expected - actual, count=").append(missing.size()).append("):\n");
        if (missing.isEmpty()) {
            sb.append("  <none>\n");
        } else {
            for (Pattern p : missing) {
                sb.append("  ").append(p.serialize()).append(" (expectedSupport=").append(expected.get(p)).append(")\n");
            }
        }

        // 7. Unexpected patterns (actual - expected) in deterministic order
        List<Pattern> unexpected = actual.keySet().stream()
                .filter(p -> !expected.containsKey(p))
                .sorted(Comparator.comparing(Pattern::serialize))
                .toList();
        sb.append("Unexpected Patterns (actual - expected, count=").append(unexpected.size()).append("):\n");
        if (unexpected.isEmpty()) {
            sb.append("  <none>\n");
        } else {
            for (Pattern p : unexpected) {
                sb.append("  ").append(p.serialize()).append(" (actualSupport=").append(actual.get(p)).append(")\n");
            }
        }

        // 8. First common pattern with different support in deterministic order
        List<Pattern> commonDifferentSupport = expected.keySet().stream()
                .filter(actual::containsKey)
                .filter(p -> !expected.get(p).equals(actual.get(p)))
                .sorted(Comparator.comparing(Pattern::serialize))
                .toList();
        sb.append("First Common Pattern Support Mismatch:\n");
        if (commonDifferentSupport.isEmpty()) {
            sb.append("  <none>\n");
        } else {
            Pattern firstMismatch = commonDifferentSupport.getFirst();
            sb.append("  Pattern: ").append(firstMismatch.serialize())
                    .append(" | Expected Support: ").append(expected.get(firstMismatch))
                    .append(" | Actual Support: ").append(actual.get(firstMismatch))
                    .append("\n");
        }

        // 9. Full expected and actual maps in deterministic order
        Map<String, Integer> sortedExpected = new TreeMap<>();
        expected.forEach((k, v) -> sortedExpected.put(k.serialize(), v));
        Map<String, Integer> sortedActual = new TreeMap<>();
        actual.forEach((k, v) -> sortedActual.put(k.serialize(), v));

        sb.append("Full Expected Map (size=").append(sortedExpected.size()).append("):\n");
        sb.append("  ").append(sortedExpected).append("\n");
        sb.append("Full Actual Map (size=").append(sortedActual.size()).append("):\n");
        sb.append("  ").append(sortedActual).append("\n");
        sb.append("===================================================\n");

        return sb.toString();
    }

    /**
     * Independent Direct Oracle that computes statistics, item weights, MeanW,
     * effective threshold, F1, and recursive I/S-extensions directly on raw SequenceDatabase data.
     */
    private static Map<Pattern, Integer> directOracleMine(SequenceDatabase db, MinimumSupport minSup) {
        if (db.size() == 0) {
            return Collections.emptyMap();
        }

        // 1. Raw occurrence counts and total database length
        Map<Integer, Integer> occurrences = new TreeMap<>();
        for (var rec : db.records()) {
            for (var is : rec.sequence().itemsets()) {
                for (var it : is.items()) {
                    occurrences.merge(it.id(), 1, Integer::sum);
                }
            }
        }

        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. Raw item weights, MaxW, MinW, MeanW
        double meanW = calculateDirectMeanW(db);

        // 3. Raw effective threshold
        double effectiveThreshold = calculateDirectEffectiveThreshold(db, minSup);

        // 4. Raw F1
        List<Item> f1Items = new ArrayList<>();
        Map<Pattern, Integer> expected = new LinkedHashMap<>();
        for (int itemId : occurrences.keySet()) {
            Item item = new Item(itemId);
            Pattern p = Pattern.singleton(item);
            int supp = countDirectSupport(db, p);
            if (Double.compare(supp * meanW, effectiveThreshold) >= 0) {
                expected.put(p, supp);
                f1Items.add(item);
            }
        }

        // 5. Bounded recursive extension
        int maxDepth = deriveMaxPatternLength(db);
        for (Item rootItem : f1Items) {
            Pattern root = Pattern.singleton(rootItem);
            directExtend(db, root, f1Items, meanW, effectiveThreshold, 1, maxDepth, expected);
        }

        return expected;
    }

    private static void directExtend(
            SequenceDatabase db,
            Pattern parent,
            List<Item> f1Items,
            double meanW,
            double effectiveThreshold,
            int currentDepth,
            int maxDepth,
            Map<Pattern, Integer> expected) {

        if (currentDepth >= maxDepth) {
            return;
        }

        Item parentLastItem = parent.lastItem().orElseThrow();

        // I-extensions: extItem.id > parentLastItem.id
        for (Item extItem : f1Items) {
            if (extItem.id() > parentLastItem.id() && !parent.itemsets().getLast().contains(extItem)) {
                List<Item> newLast = new ArrayList<>(parent.itemsets().getLast().items());
                newLast.add(extItem);
                Collections.sort(newLast);
                List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                newItemsets.set(newItemsets.size() - 1, new Itemset(newLast));
                Pattern candidate = new Pattern(newItemsets);

                int supp = countDirectSupport(db, candidate);
                if (Double.compare(supp * meanW, effectiveThreshold) >= 0) {
                    expected.put(candidate, supp);
                    directExtend(db, candidate, f1Items, meanW, effectiveThreshold,
                            currentDepth + 1, maxDepth, expected);
                }
            }
        }

        // S-extensions: for all extItem in f1Items
        for (Item extItem : f1Items) {
            List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
            newItemsets.add(Itemset.of(extItem));
            Pattern candidate = new Pattern(newItemsets);

            int supp = countDirectSupport(db, candidate);
            if (Double.compare(supp * meanW, effectiveThreshold) >= 0) {
                expected.put(candidate, supp);
                directExtend(db, candidate, f1Items, meanW, effectiveThreshold,
                        currentDepth + 1, maxDepth, expected);
            }
        }
    }

    // =========================================================================
    // 28 Named Tests (Section 12)
    // =========================================================================

    @Test
    public void test01_absoluteThresholdEndToEndControlledDatabase() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Map<Pattern, Integer> expected = directOracleMine(db, minSup);

        assertEquals(expected, actual);
        assertTrue(tree.isFrozen());
    }

    @Test
    public void test02_percentageThresholdEndToEndControlledDatabase() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.percentage(50.0);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Map<Pattern, Integer> expected = directOracleMine(db, minSup);

        assertEquals(expected, actual);
        assertTrue(tree.isFrozen());
    }

    @Test
    public void test03_iExtensionAcceptedThroughPublicBoundary() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern iExtPattern = Pattern.of(itemset(1, 2));

        assertTrue(actual.containsKey(iExtPattern));
        assertEquals(2, actual.get(iExtPattern));
        assertTrue(tree.find(iExtPattern).isPresent());
    }

    @Test
    public void test04_sExtensionAcceptedThroughPublicBoundary() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern sExtPattern = Pattern.of(itemset(1), itemset(3));

        assertTrue(actual.containsKey(sExtPattern));
        assertEquals(2, actual.get(sExtPattern));
        assertTrue(tree.find(sExtPattern).isPresent());
    }

    @Test
    public void test05_repeatedItemSExtension() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db3();
        MinimumSupport minSup = MinimumSupport.absolute(0.3);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Map<Pattern, Integer> expected = directOracleMine(db, minSup);

        assertEquals(expected, actual);
        Pattern repeatedPattern = Pattern.of(itemset(1), itemset(2), itemset(1));
        assertTrue(actual.containsKey(repeatedPattern));
    }

    @Test
    public void test06_sameItemsetVersusSequentialItemsetIdentity() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern sameItemset = Pattern.of(itemset(1, 2));
        Pattern sequentialItemset = Pattern.of(itemset(1), itemset(2));

        assertNotEquals(sameItemset, sequentialItemset);
        assertTrue(actual.containsKey(sameItemset));
        // In DB 1: SID 1 has (1 2), SID 2 has (1 2), SID 3 has (2).
        // Sequential (1), (2) appears 0 times because item 2 never strictly follows item 1 in a later itemset!
        assertFalse(actual.containsKey(sequentialItemset));
        assertTrue(tree.find(sameItemset).isPresent());
        assertTrue(tree.find(sequentialItemset).isEmpty());
    }

    @Test
    public void test07_multipleEmbeddingsCountOneSidOnce() {
        PrefixTree tree = new PrefixTree();
        // Single sequence containing multiple occurrences of item 1 and item 2
        SequenceDatabase db = db(record(1, itemset(1), itemset(1), itemset(1)));
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern single = Pattern.of(itemset(1));
        Pattern doubleOne = Pattern.of(itemset(1), itemset(1));

        assertTrue(actual.containsKey(single));
        assertEquals(1, actual.get(single));
        assertTrue(actual.containsKey(doubleOne));
        assertEquals(1, actual.get(doubleOne));
    }

    @Test
    public void test08_multiLevelBranchReachesRawDataDerivedBoundWhereSupported() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db2();
        MinimumSupport minSup = MinimumSupport.absolute(0.1);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        int maxBound = deriveMaxPatternLength(db);
        assertEquals(4, maxBound);

        Pattern length4Pattern = Pattern.of(itemset(1), itemset(2), itemset(3), itemset(4));
        assertTrue(actual.containsKey(length4Pattern));
        assertEquals(1, actual.get(length4Pattern));
    }

    @Test
    public void test09_f1ExcludesItemsBelowWeightedThreshold() {
        PrefixTree tree = new PrefixTree();
        // DB with item 1 frequent and item 99 infrequent
        SequenceDatabase db = db(
                record(1, itemset(1), itemset(99)),
                record(2, itemset(1)),
                record(3, itemset(1)),
                record(4, itemset(1)));
        // Total length = 5. Items: 1 (4 times), 99 (1 time).
        // MaxW = 4/5 = 0.8, MinW = 1/5 = 0.2, MeanW = 0.5.
        // Item 1: supp = 4, 4 * 0.5 = 2.0.
        // Item 99: supp = 1, 1 * 0.5 = 0.5.
        // If threshold = 1.0: item 99 is below threshold, excluded from F1!
        MinimumSupport minSup = MinimumSupport.absolute(1.0);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern p99 = Pattern.of(itemset(99));

        assertFalse(actual.containsKey(p99));
        for (Pattern p : actual.keySet()) {
            assertFalse(p.serialize().contains("99"));
        }
    }

    @Test
    public void test10_f1EmptyPositiveThresholdDatabaseReturnsRootOnlyFrozenTree() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(9999.0);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);

        assertTrue(actual.isEmpty());
        assertTrue(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test11_negativeZeroAndNonContiguousSids() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db4();
        MinimumSupport minSup = MinimumSupport.absolute(0.4);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Map<Pattern, Integer> expected = directOracleMine(db, minSup);

        assertEquals(expected, actual);
        assertTrue(tree.isFrozen());
    }

    @Test
    public void test12_unsortedDatabaseRecordSidsPreserveRecordOrderMappingSemantics() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db(
                record(100, itemset(1, 2), itemset(3)),
                record(-50, itemset(1, 2), itemset(3, 4)),
                record(42, itemset(2), itemset(3, 4)));
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Map<Pattern, Integer> expected = directOracleMine(db, minSup);

        assertEquals(expected, actual);
    }

    @Test
    public void test13_returnedSupportMapIsImmutable() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);

        assertThrows(UnsupportedOperationException.class, () -> actual.put(Pattern.empty(), 0));
        assertThrows(UnsupportedOperationException.class, actual::clear);
    }

    @Test
    public void test14_inputDatabaseRemainsUnchanged() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        List<SequenceRecord> originalRecords = new ArrayList<>(db.records());
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        miner.mineInto(tree, db, minSup);

        assertEquals(originalRecords.size(), db.records().size());
        for (int i = 0; i < originalRecords.size(); i++) {
            assertEquals(originalRecords.get(i).sid(), db.records().get(i).sid());
            assertEquals(originalRecords.get(i).sequence(), db.records().get(i).sequence());
        }
    }

    @Test
    public void test15_repeatedRunsProduceIdenticalMapAndTreePreOrder() {
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        PrefixTree tree1 = new PrefixTree();
        Map<Pattern, Integer> actual1 = miner.mineInto(tree1, db, minSup);

        PrefixTree tree2 = new PrefixTree();
        Map<Pattern, Integer> actual2 = miner.mineInto(tree2, db, minSup);

        assertEquals(actual1, actual2);
        assertEquals(tree1.preOrderPatterns(), tree2.preOrderPatterns());
    }

    @Test
    public void test16_acceptedSupportKeysEqualEveryNonRootTreeNode() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);

        Set<Pattern> nonRootPatterns = tree.preOrderPatterns().stream()
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toSet());

        assertEquals(nonRootPatterns, actual.keySet());
        assertEquals(tree.nodeCount() - 1, actual.size());
    }

    @Test
    public void test17_emptyDatabaseWithPositiveAbsoluteThresholdReturnsEmptyResult() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db();
        MinimumSupport minSup = MinimumSupport.absolute(1.5);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);

        assertTrue(actual.isEmpty());
        assertTrue(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test18_emptyDatabaseWithZeroAbsoluteThresholdReturnsEmptyResultWithoutFabricatedMeanW() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db();
        MinimumSupport minSup = MinimumSupport.absolute(0.0);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);

        assertTrue(actual.isEmpty());
        assertTrue(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test19_emptyDatabaseWithPercentageThresholdFailsBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db();
        MinimumSupport minSup = MinimumSupport.percentage(50.0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> miner.mineInto(tree, db, minSup));

        assertTrue(ex.getMessage().contains("percentage minimum support cannot be resolved for an empty database"));
        assertFalse(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test20_nonEmptyDatabaseWithZeroAbsoluteThresholdFailsBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> miner.mineInto(tree, db, minSup));

        assertTrue(ex.getMessage().contains("R6 remains OPEN"));
        assertFalse(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test21_nonEmptyDatabaseWithZeroPercentageThresholdFailsBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.percentage(0.0);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> miner.mineInto(tree, db, minSup));

        assertTrue(ex.getMessage().contains("R6 remains OPEN"));
        assertFalse(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test22_frozenTreeFailsBeforePreprocessing() {
        PrefixTree tree = new PrefixTree();
        tree.freeze();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        assertThrows(IllegalStateException.class, () -> miner.mineInto(tree, db, minSup));
    }

    @Test
    public void test23_nonRootOnlyTreeFailsBeforePreprocessing() {
        PrefixTree tree = new PrefixTree();
        tree.insert(Pattern.empty(), Pattern.singleton(new Item(1)), ExtensionType.S_EXTENSION);
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        assertThrows(IllegalArgumentException.class, () -> miner.mineInto(tree, db, minSup));
    }

    @Test
    public void test24_nullInputsFailBeforeMutation() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        assertThrows(NullPointerException.class, () -> miner.mineInto(null, db, minSup));
        assertThrows(NullPointerException.class, () -> miner.mineInto(tree, null, minSup));
        assertThrows(NullPointerException.class, () -> miner.mineInto(tree, db, null));

        assertFalse(tree.isFrozen());
        assertEquals(1, tree.nodeCount());
    }

    @Test
    public void test25_independentOracleFullMapEqualityForSeveralControlledDatabases() {
        record TestCase(String name, SequenceDatabase db, MinimumSupport minSup) {}

        List<TestCase> testCases = List.of(
                // DB 1
                new TestCase("DB1-Abs0.4", db1(), MinimumSupport.absolute(0.4)),
                new TestCase("DB1-Abs0.6", db1(), MinimumSupport.absolute(0.6)),
                new TestCase("DB1-Pct40", db1(), MinimumSupport.percentage(40.0)),
                new TestCase("DB1-Pct60", db1(), MinimumSupport.percentage(60.0)),
                // DB 2
                new TestCase("DB2-Abs0.4", db2(), MinimumSupport.absolute(0.4)),
                new TestCase("DB2-Abs0.6", db2(), MinimumSupport.absolute(0.6)),
                new TestCase("DB2-Pct40", db2(), MinimumSupport.percentage(40.0)),
                new TestCase("DB2-Pct60", db2(), MinimumSupport.percentage(60.0)),
                // DB 3
                new TestCase("DB3-Abs0.4", db3(), MinimumSupport.absolute(0.4)),
                new TestCase("DB3-Abs0.6", db3(), MinimumSupport.absolute(0.6)),
                new TestCase("DB3-Pct40", db3(), MinimumSupport.percentage(40.0)),
                new TestCase("DB3-Pct60", db3(), MinimumSupport.percentage(60.0)),
                // DB 4
                new TestCase("DB4-Abs0.4", db4(), MinimumSupport.absolute(0.4)),
                new TestCase("DB4-Abs0.6", db4(), MinimumSupport.absolute(0.6)),
                new TestCase("DB4-Pct30", db4(), MinimumSupport.percentage(30.0)),
                new TestCase("DB4-Pct50", db4(), MinimumSupport.percentage(50.0))
        );

        int totalExpectedPatterns = 0;
        int totalActualPatterns = 0;
        int maxDerivedBound = 0;
        int maxProductionPatternLength = 0;

        for (TestCase tc : testCases) {
            PrefixTree tree = new PrefixTree();
            Map<Pattern, Integer> actual = miner.mineInto(tree, tc.db, tc.minSup);
            Map<Pattern, Integer> expected = directOracleMine(tc.db, tc.minSup);

            int derivedBound = deriveMaxPatternLength(tc.db);
            maxDerivedBound = Math.max(maxDerivedBound, derivedBound);

            int currentMaxActualLen = actual.keySet().stream()
                    .mapToInt(p -> p.itemsets().stream().mapToInt(Itemset::size).sum())
                    .max()
                    .orElse(0);
            maxProductionPatternLength = Math.max(maxProductionPatternLength, currentMaxActualLen);

            totalExpectedPatterns += expected.size();
            totalActualPatterns += actual.size();

            if (!expected.equals(actual)) {
                String diagnostic = formatMismatchDiagnostic(tc.name, tc.db, tc.minSup, expected, actual);
                fail(diagnostic);
            }
        }

        System.out.printf(
                "C10.4 End-to-End Oracle Full-Map Equivalence: controlledDatabases=4, configurations=%d, totalExpectedPatterns=%d, totalActualPatterns=%d, maxDerivedBound=%d, maxProductionPatternLength=%d, mismatches=0%n",
                testCases.size(), totalExpectedPatterns, totalActualPatterns, maxDerivedBound, maxProductionPatternLength);

        assertEquals(totalExpectedPatterns, totalActualPatterns);
    }

    @Test
    public void test26_thresholdEqualityIsAccepted() {
        // Single sequence: (1). Total length = 1.
        // Item 1 occurrences = 1.
        // MaxW = 1.0, MinW = 1.0, MeanW = 1.0.
        // Support of item 1 is 1.
        // Weighted support = 1 * 1.0 = 1.0.
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db(record(1, itemset(1)));
        MinimumSupport minSup = MinimumSupport.absolute(1.0);

        Map<Pattern, Integer> actual = miner.mineInto(tree, db, minSup);
        Pattern p1 = Pattern.of(itemset(1));

        assertTrue(actual.containsKey(p1));
        assertEquals(1, actual.get(p1));
        assertTrue(tree.find(p1).isPresent());
    }

    @Test
    public void test27_noRejectedCandidateAppearsInTree() {
        PrefixTree tree = new PrefixTree();
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        miner.mineInto(tree, db, minSup);

        // In DB 1: items 1 and 4 never appear together in the same itemset.
        Pattern rejectedCandidate = Pattern.of(itemset(1, 4));
        assertTrue(tree.find(rejectedCandidate).isEmpty());
    }

    @Test
    public void test28_existingPreparedInputOverloadRemainsBehaviorallyUnchanged() {
        SequenceDatabase db = db1();
        MinimumSupport minSup = MinimumSupport.absolute(0.5);

        // 1. Run through new end-to-end overload
        PrefixTree e2eTree = new PrefixTree();
        Map<Pattern, Integer> e2eMap = miner.mineInto(e2eTree, db, minSup);

        // 2. Prepare inputs manually and run through prepared-input overload
        ItemStatisticsCalculator statsCalc = new ItemStatisticsCalculator();
        ItemWeightCalculator weightCalc = new ItemWeightCalculator();
        F1Builder f1Builder = new F1Builder();
        MinimumSupportResolver resolver = new MinimumSupportResolver();
        CmapBuilder cmapBuilder = new CmapBuilder();

        ResolvedMinimumSupport resolvedMinSup = resolver.resolve(minSup, db.size());
        SequenceDatabaseStatistics stats = statsCalc.calculate(db);
        ItemWeightResult weightResult = weightCalc.calculate(stats);
        double globalMeanWeight = weightResult.meanWeight().orElseThrow();
        List<WeightedOnePattern> f1 = f1Builder.build(stats, weightResult, resolvedMinSup);

        SidMapping mapping = SidMapping.of(db);
        VerticalOccurrenceIndex occIndex = VerticalOccurrenceIndex.build(db, mapping);

        Map<Integer, SidBitmap> singletonBitmaps = new LinkedHashMap<>();
        Map<Integer, VerificationResult> singletonVerifications = new LinkedHashMap<>();
        List<Item> f1Items = new ArrayList<>();

        for (WeightedOnePattern p : f1) {
            int itemId = p.itemId();
            Item item = new Item(itemId);
            f1Items.add(item);

            SidBitmap.Builder bitmapBuilder = SidBitmap.builder(mapping);
            List<PatternEndpoints> endpoints = new ArrayList<>();

            for (SequenceRecord record : db.records()) {
                int sid = record.sid();
                int[] positions = occIndex.positions(item, sid);
                if (positions.length > 0) {
                    bitmapBuilder.setSid(sid);
                    endpoints.add(PatternEndpoints.of(sid, positions));
                }
            }

            singletonBitmaps.put(itemId, bitmapBuilder.build());
            singletonVerifications.put(itemId, VerificationResult.of(endpoints));
        }

        CmapI cmapI = cmapBuilder.buildI(db, f1Items);
        CmapS cmapS = cmapBuilder.buildS(db, f1Items);

        PrefixTree preparedTree = new PrefixTree();
        Map<Pattern, Integer> preparedMap = miner.mineInto(
                preparedTree, f1, singletonBitmaps, singletonVerifications,
                cmapI, cmapS, occIndex, globalMeanWeight, resolvedMinSup);

        assertEquals(e2eMap, preparedMap);
        assertEquals(e2eTree.preOrderPatterns(), preparedTree.preOrderPatterns());
    }

    @Test
    public void test29_diagnosticFormatterReportsAllRequiredFieldsOnMismatch() {
        SequenceDatabase sampleDb = db(
                record(10, itemset(1, 2)),
                record(20, itemset(1), itemset(3)));
        MinimumSupport minSup = MinimumSupport.percentage(50.0);

        Pattern patternCommon = Pattern.of(itemset(1));
        Pattern patternMissing = Pattern.of(itemset(1, 2));
        Pattern patternUnexpected = Pattern.of(itemset(3));

        Map<Pattern, Integer> mockExpected = new LinkedHashMap<>();
        mockExpected.put(patternCommon, 2);
        mockExpected.put(patternMissing, 1);

        Map<Pattern, Integer> mockActual = new LinkedHashMap<>();
        mockActual.put(patternCommon, 1);
        mockActual.put(patternUnexpected, 1);

        String diagnostic = formatMismatchDiagnostic(
                "ControlledDiagnosticSelfTest", sampleDb, minSup, mockExpected, mockActual);

        assertNotNull(diagnostic);
        assertTrue(diagnostic.contains("ControlledDiagnosticSelfTest"));
        assertTrue(diagnostic.contains("SID=10: (1 2)"));
        assertTrue(diagnostic.contains("SID=20: (1), (3)"));
        assertTrue(diagnostic.contains("Mode: PERCENT"));
        assertTrue(diagnostic.contains("Configured Value: 50.0"));
        assertTrue(diagnostic.contains("Independently Calculated Effective Threshold: 1.0"));
        assertTrue(diagnostic.contains("Independently Calculated MeanW: 0.375"));
        assertTrue(diagnostic.contains("Raw-Data-Derived Maximum Pattern-Length Bound: 2"));
        assertTrue(diagnostic.contains("Expected Count: 2"));
        assertTrue(diagnostic.contains("Actual Count: 2"));
        assertTrue(diagnostic.contains("Missing Patterns (expected - actual, count=1):"));
        assertTrue(diagnostic.contains(patternMissing.serialize()));
        assertTrue(diagnostic.contains("expectedSupport=1"));
        assertTrue(diagnostic.contains("Unexpected Patterns (actual - expected, count=1):"));
        assertTrue(diagnostic.contains(patternUnexpected.serialize()));
        assertTrue(diagnostic.contains("actualSupport=1"));
        assertTrue(diagnostic.contains("First Common Pattern Support Mismatch:"));
        assertTrue(diagnostic.contains("Pattern: " + patternCommon.serialize()));
        assertTrue(diagnostic.contains("Expected Support: 2"));
        assertTrue(diagnostic.contains("Actual Support: 1"));
    }
}
