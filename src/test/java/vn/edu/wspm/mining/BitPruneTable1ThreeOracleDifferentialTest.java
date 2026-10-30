package vn.edu.wspm.mining;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Task C11.1 — Table 1 Three-Oracle Complete-Map Differential.
 *
 * <p>Exhaustive differential correctness comparison for WSPM_BitPrune on the Table 1 paper fixture
 * against three independent oracles:
 * <ol>
 *   <li>The published 9-pattern paper oracle from Table 1 / Figure 2 (Pham et al., 2023);</li>
 *   <li>The frozen Phase B WSPM_PreTree baseline implementation (Prime-block / Prefix Tree);</li>
 *   <li>The independent exhaustive brute-force oracle (direct subsequence scan).</li>
 * </ol>
 * </p>
 */
class BitPruneTable1ThreeOracleDifferentialTest {

    private static final double TOLERANCE = 1.0e-12;
    private static final MinimumSupport TABLE_ONE_MINIMUM_SUPPORT =
            MinimumSupport.percentage(14.0);

    private static final Map<Pattern, Integer> PAPER_ORACLE = createPaperOracle();

    private static SequenceDatabase database;
    private static int distinctItemCount;
    private static double meanWeight;
    private static double effectiveThreshold;

    private static Map<Pattern, Integer> baselineSupports;
    private static Map<Pattern, Integer> bruteForceSupports;
    private static Map<Pattern, Integer> bitPruneSupports;
    private static PrefixTree bitPruneTree;
    private static Map<String, Map<Pattern, Integer>> allOracleMaps;

    private final BitPruneMiner bitPruneMiner = new BitPruneMiner();

    @BeforeAll
    static void setUp() throws IOException, URISyntaxException {
        var resource = requireNonNull(
                BitPruneTable1ThreeOracleDifferentialTest.class.getResource(
                        "/datasets/wspm/table1.spmf"));
        database = new SpmfSequenceParser().parse(Path.of(resource.toURI()));

        var statistics = new ItemStatisticsCalculator().calculate(database);
        var weights = new ItemWeightCalculator().calculate(statistics);
        var resolvedMinSup = new MinimumSupportResolver().resolve(
                TABLE_ONE_MINIMUM_SUPPORT, database.size());

        distinctItemCount = statistics.numberOfDistinctItems();
        meanWeight = weights.meanWeight().orElseThrow();
        effectiveThreshold = resolvedMinSup.effectiveThreshold();

        // 1. Run baseline miner
        baselineSupports = new WspmPreTreeMiner().mine(database, TABLE_ONE_MINIMUM_SUPPORT).supports();

        // 2. Run brute-force oracle
        bruteForceSupports = new BruteForceWeightedPatternOracle().mine(database, TABLE_ONE_MINIMUM_SUPPORT);

        // 3. Run BitPrune miner
        bitPruneTree = new PrefixTree();
        bitPruneSupports = new BitPruneMiner().mineInto(bitPruneTree, database, TABLE_ONE_MINIMUM_SUPPORT);

        // 4. Collect all oracle maps for cross-oracle diagnostics
        var maps = new LinkedHashMap<String, Map<Pattern, Integer>>();
        maps.put("Paper Oracle", PAPER_ORACLE);
        maps.put("WSPM_PreTree Baseline", baselineSupports);
        maps.put("BruteForce Oracle", bruteForceSupports);
        maps.put("WSPM_BitPrune", bitPruneSupports);
        allOracleMaps = Collections.unmodifiableMap(maps);
    }

    @Test
    void table1_input_metadata_matches_contract() {
        assertEquals(7, database.size(), "Table 1 must contain exactly 7 sequences");
        assertEquals(8, distinctItemCount, "Table 1 must contain exactly 8 distinct items");
        assertEquals(12.0 / 66.0, meanWeight, TOLERANCE, "Table 1 MeanW must be 12/66");
        assertEquals(0.98, effectiveThreshold, TOLERANCE, "Table 1 effective threshold must be 0.98");
        assertEquals(9, PAPER_ORACLE.size(), "Paper oracle must contain exactly 9 patterns");
    }

    @Test
    void paper_vs_baseline_complete_map() {
        assertCompleteMapEquality(
                "paper_vs_baseline_complete_map",
                PAPER_ORACLE,
                baselineSupports,
                allOracleMaps);
    }

    @Test
    void paper_vs_bruteforce_complete_map() {
        assertCompleteMapEquality(
                "paper_vs_bruteforce_complete_map",
                PAPER_ORACLE,
                bruteForceSupports,
                allOracleMaps);
    }

    @Test
    void paper_vs_bitprune_complete_map() {
        // Direct comparison against published paper oracle
        assertCompleteMapEquality(
                "paper_vs_bitprune_complete_map",
                PAPER_ORACLE,
                bitPruneSupports,
                allOracleMaps);

        // Cross-algorithm differential checks: Baseline == BitPrune == BruteForce
        assertCompleteMapEquality(
                "baseline_vs_bitprune_complete_map",
                baselineSupports,
                bitPruneSupports,
                allOracleMaps);

        assertCompleteMapEquality(
                "bruteforce_vs_bitprune_complete_map",
                bruteForceSupports,
                bitPruneSupports,
                allOracleMaps);

        // Prefix Tree integrity checks
        assertTrue(bitPruneTree.isFrozen(), "PrefixTree must be frozen after mining");
        assertEquals(10, bitPruneTree.nodeCount(), "PrefixTree must have 10 nodes (root + 9 patterns)");
    }

    @Test
    void repeated_run_is_deterministic() {
        PrefixTree tree1 = new PrefixTree();
        PrefixTree tree2 = new PrefixTree();
        PrefixTree tree3 = new PrefixTree();

        Map<Pattern, Integer> run1 = bitPruneMiner.mineInto(tree1, database, TABLE_ONE_MINIMUM_SUPPORT);
        Map<Pattern, Integer> run2 = bitPruneMiner.mineInto(tree2, database, TABLE_ONE_MINIMUM_SUPPORT);
        Map<Pattern, Integer> run3 = bitPruneMiner.mineInto(tree3, database, TABLE_ONE_MINIMUM_SUPPORT);

        assertEquals(run1, run2, "run1 and run2 must produce identical map");
        assertEquals(run2, run3, "run2 and run3 must produce identical map");

        // Iteration order stability
        assertEquals(new ArrayList<>(run1.entrySet()), new ArrayList<>(run2.entrySet()),
                "run1 and run2 must iterate entries in identical order");
        assertEquals(new ArrayList<>(run2.entrySet()), new ArrayList<>(run3.entrySet()),
                "run2 and run3 must iterate entries in identical order");

        // Prefix Tree deterministic structure
        assertEquals(tree1.preOrderPatterns(), tree2.preOrderPatterns(),
                "tree1 and tree2 must have identical pre-order patterns");
        assertEquals(tree2.preOrderPatterns(), tree3.preOrderPatterns(),
                "tree2 and tree3 must have identical pre-order patterns");
    }

    @Test
    void returned_bitprune_map_is_immutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> bitPruneSupports.put(singleton(99), 1),
                "Returned support map must be unmodifiable on put");
        assertThrows(UnsupportedOperationException.class,
                () -> bitPruneSupports.remove(singleton(1)),
                "Returned support map must be unmodifiable on remove");
        assertThrows(UnsupportedOperationException.class,
                () -> bitPruneSupports.clear(),
                "Returned support map must be unmodifiable on clear");
    }

    @Test
    void pattern_identity_distinguishes_i_and_s_extensions() {
        Pattern iExt = pattern(itemset(1, 2));
        Pattern sExt = pattern(itemset(1), itemset(2));

        // 1. Structural distinction between I- and S-extension
        assertNotEquals(iExt, sExt, "I-extension <(1 2)> and S-extension <(1)(2)> must not be equal");
        assertEquals("<(1 2)>", iExt.serialize());
        assertEquals("<(1)(2)>", sExt.serialize());
        assertEquals(1, iExt.size(), "I-extension <(1 2)> has 1 itemset");
        assertEquals(2, sExt.size(), "S-extension <(1)(2)> has 2 itemsets");
        assertEquals(2, iExt.length(), "Both have total length 2");
        assertEquals(2, sExt.length(), "Both have total length 2");

        // 2. Both patterns are accepted in Table 1 BitPrune output with support 7
        assertTrue(bitPruneSupports.containsKey(iExt), "BitPrune output must contain I-extension <(1 2)>");
        assertTrue(bitPruneSupports.containsKey(sExt), "BitPrune output must contain S-extension <(1)(2)>");
        assertEquals(7, bitPruneSupports.get(iExt));
        assertEquals(7, bitPruneSupports.get(sExt));

        // 3. Tree node extension type and parent distinction
        var iNode = bitPruneTree.find(iExt).orElseThrow();
        var sNode = bitPruneTree.find(sExt).orElseThrow();
        assertNotSame(iNode, sNode, "Nodes for I- and S-extensions must be distinct instances");
        assertEquals(ExtensionType.I_EXTENSION, iNode.extensionType().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, sNode.extensionType().orElseThrow());
        assertEquals(singleton(1), iNode.parent().orElseThrow().pattern());
        assertEquals(singleton(1), sNode.parent().orElseThrow().pattern());

        // 4. Contrast: rejected I-extension <(1 3)> vs accepted S-extension <(1)(3)>
        Pattern i13 = pattern(itemset(1, 3));
        Pattern s13 = pattern(itemset(1), itemset(3));
        assertNotEquals(i13, s13);
        assertEquals("<(1 3)>", i13.serialize());
        assertEquals("<(1)(3)>", s13.serialize());

        assertFalse(bitPruneSupports.containsKey(i13),
                "BitPrune output must NOT contain rejected I-extension <(1 3)> (support 3 * 12/66 < 0.98)");
        assertTrue(bitPruneSupports.containsKey(s13),
                "BitPrune output must contain accepted S-extension <(1)(3)> (support 6 * 12/66 >= 0.98)");
        assertEquals(6, bitPruneSupports.get(s13));
        assertTrue(bitPruneTree.find(i13).isEmpty(), "Tree must not contain rejected I-extension <(1 3)>");
        assertTrue(bitPruneTree.find(s13).isPresent(), "Tree must contain accepted S-extension <(1)(3)>");
    }

    private void assertCompleteMapEquality(
            String caseId,
            Map<Pattern, Integer> expected,
            Map<Pattern, Integer> actual,
            Map<String, Map<Pattern, Integer>> allOracleMaps) {
        Set<Pattern> missing = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        missing.addAll(expected.keySet());
        missing.removeAll(actual.keySet());

        Set<Pattern> unexpected = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        unexpected.addAll(actual.keySet());
        unexpected.removeAll(expected.keySet());

        Map<String, String> supportMismatches = new LinkedHashMap<>();
        String firstSupportMismatch = null;
        for (Pattern pattern : expected.keySet().stream().sorted(Comparator.comparing(Pattern::serialize)).toList()) {
            if (actual.containsKey(pattern)) {
                int exp = expected.get(pattern);
                int act = actual.get(pattern);
                if (exp != act) {
                    String detail = pattern.serialize() + " (expected=" + exp + ", actual=" + act + ")";
                    supportMismatches.put(pattern.serialize(), detail);
                    if (firstSupportMismatch == null) {
                        firstSupportMismatch = detail;
                    }
                }
            }
        }

        if (!missing.isEmpty() || !unexpected.isEmpty() || !supportMismatches.isEmpty() || !expected.equals(actual)) {
            String diag = buildMismatchDiagnostic(
                    caseId, database, TABLE_ONE_MINIMUM_SUPPORT, meanWeight, effectiveThreshold,
                    allOracleMaps, missing, unexpected, supportMismatches, firstSupportMismatch);
            fail(diag);
        }
    }

    private static String buildMismatchDiagnostic(
            String caseId,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            double meanWeight,
            double effectiveThreshold,
            Map<String, Map<Pattern, Integer>> allOracleMaps,
            Set<Pattern> missing,
            Set<Pattern> unexpected,
            Map<String, String> supportMismatches,
            String firstSupportMismatch) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== DIFFERENTIAL MISMATCH DIAGNOSTIC ===").append(System.lineSeparator());
        sb.append("caseId: ").append(caseId).append(System.lineSeparator());
        sb.append("raw SPMF sequences / SID order:").append(System.lineSeparator());
        for (var record : database.records()) {
            sb.append("  SID ").append(record.sid()).append(": ");
            for (var itemset : record.sequence().itemsets()) {
                for (var item : itemset.items()) {
                    sb.append(item.id()).append(' ');
                }
                sb.append("-1 ");
            }
            sb.append("-2").append(System.lineSeparator());
        }
        sb.append("minsup mode: ").append(minimumSupport.mode()).append(System.lineSeparator());
        sb.append("configured value: ").append(minimumSupport.configuredValue()).append(System.lineSeparator());
        sb.append("effective value: ").append(effectiveThreshold).append(System.lineSeparator());
        sb.append("MeanW: ").append(meanWeight).append(" (expected 12/66 = ").append(12.0 / 66.0).append(")").append(System.lineSeparator());
        sb.append("all oracle maps:").append(System.lineSeparator());
        for (var entry : allOracleMaps.entrySet()) {
            sb.append("  [").append(entry.getKey()).append("] (count=").append(entry.getValue().size()).append("):").append(System.lineSeparator());
            entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(Pattern::serialize)))
                    .forEach(e -> sb.append("    ").append(e.getKey().serialize()).append(" -> ").append(e.getValue()).append(System.lineSeparator()));
        }
        sb.append("missing patterns (Pattern.serialize order): ")
                .append(missing.stream().map(Pattern::serialize).toList()).append(System.lineSeparator());
        sb.append("unexpected patterns (Pattern.serialize order): ")
                .append(unexpected.stream().map(Pattern::serialize).toList()).append(System.lineSeparator());
        sb.append("all support mismatches: ").append(supportMismatches).append(System.lineSeparator());
        sb.append("first support mismatch: ").append(firstSupportMismatch != null ? firstSupportMismatch : "none").append(System.lineSeparator());
        return sb.toString();
    }

    private static Map<Pattern, Integer> createPaperOracle() {
        var result = new LinkedHashMap<Pattern, Integer>();
        result.put(singleton(1), 7);
        result.put(singleton(2), 7);
        result.put(singleton(3), 6);
        result.put(pattern(itemset(1, 2)), 7);
        result.put(pattern(itemset(1), itemset(1)), 6);
        result.put(pattern(itemset(1), itemset(2)), 7);
        result.put(pattern(itemset(1), itemset(3)), 6);
        result.put(pattern(itemset(2), itemset(3)), 6);
        result.put(pattern(itemset(1, 2), itemset(3)), 6);
        return Collections.unmodifiableMap(result);
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
