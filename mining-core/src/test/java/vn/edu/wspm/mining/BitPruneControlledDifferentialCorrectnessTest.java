package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapBuilder;
import vn.edu.wspm.bitprune.cmap.CmapI;
import vn.edu.wspm.bitprune.cmap.CmapS;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation.Outcome;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluator;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateResult;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Task C11.2 — Controlled Boundary & Pruning-Safety Matrix.
 *
 * <p>Deterministic differential correctness test verifying complete {@code Map<Pattern, Integer>}
 * output between WSPM_BitPrune, frozen WSPM_PreTree baseline, and independent BruteForceWeightedPatternOracle
 * across nine named database families and thirteen positive configurations.</p>
 */
class BitPruneControlledDifferentialCorrectnessTest {

    private final WspmPreTreeMiner baselineMiner = new WspmPreTreeMiner();
    private final BruteForceWeightedPatternOracle bruteForceOracle = new BruteForceWeightedPatternOracle();
    private final BitPruneMiner bitPruneMiner = new BitPruneMiner();
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    // =========================================================================
    // 1. Matrix Runner: All 13 Configurations Across 9 Database Families
    // =========================================================================

    @Test
    @DisplayName("controlled_matrix_complete_maps_match_all_three_paths")
    void controlled_matrix_complete_maps_match_all_three_paths() {
        List<ControlledMatrixCase> cases = createMatrixCases();
        assertEquals(13, cases.size(), "Matrix must contain exactly 13 configurations");

        int completedConfigs = 0;
        int completedBaselines = 0;
        int completedBruteForces = 0;
        int completedBitPrunes = 0;
        int completedThreeWayMatches = 0;
        int unexplainedMismatches = 0;
        Set<String> databaseFamilies = new TreeSet<>();

        for (ControlledMatrixCase c : cases) {
            databaseFamilies.add(c.familyName());
            PrefixTree tree = new PrefixTree();

            boolean baselineNormalized = !isContiguous1ToN(c.database());
            SequenceDatabase baselineDb = toContiguousBaselineDatabase(c.database());
            Map<Pattern, Integer> baselineMap = baselineMiner.mine(baselineDb, c.minimumSupport()).supports();
            completedBaselines++;

            Map<Pattern, Integer> bruteForceMap = bruteForceOracle.mine(c.database(), c.minimumSupport());
            completedBruteForces++;

            Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, c.database(), c.minimumSupport());
            completedBitPrunes++;

            boolean matches = baselineMap.equals(bruteForceMap) && baselineMap.equals(bitPruneMap);
            if (!matches) {
                unexplainedMismatches++;
                assertCompleteMapEquality(
                        c.caseId(), c.configIndex(), c.database(), c.minimumSupport(),
                        baselineMap, bruteForceMap, bitPruneMap, tree, null, baselineNormalized);
            }

            completedThreeWayMatches++;
            completedConfigs++;
        }

        // Deterministic evidence summary required by Contract Section 8
        System.out.println("=== C11.2 CONTROLLED MATRIX EVIDENCE SUMMARY ===");
        System.out.println("Database families: " + databaseFamilies.size());
        System.out.println("Configurations: " + completedConfigs);
        System.out.println("Baseline maps: " + completedBaselines);
        System.out.println("Brute-force maps: " + completedBruteForces);
        System.out.println("BitPrune maps: " + completedBitPrunes);
        System.out.println("Three-way complete-map configurations: " + completedThreeWayMatches);
        System.out.println("Unexplained mismatches: " + unexplainedMismatches);
        System.out.println("================================================");

        assertEquals(9, databaseFamilies.size());
        assertEquals(13, completedConfigs);
        assertEquals(13, completedBaselines);
        assertEquals(13, completedBruteForces);
        assertEquals(13, completedBitPrunes);
        assertEquals(13, completedThreeWayMatches);
        assertEquals(0, unexplainedMismatches);
    }

    // =========================================================================
    // 2. Specific Behavioral & Structural Verification
    // =========================================================================

    @Test
    @DisplayName("i_and_s_extension_patterns_are_retained")
    void i_and_s_extension_patterns_are_retained() {
        SequenceDatabase db = createM01Database();
        MinimumSupport minSup = MinimumSupport.absolute(0.10);
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, db, minSup);

        Pattern iPattern = pattern(itemset(1, 2));
        Pattern sPattern = pattern(itemset(1), itemset(2));
        Pattern complexPattern = pattern(itemset(1, 2), itemset(3));

        assertTrue(bitPruneMap.containsKey(iPattern), "I-extension <(1 2)> must be retained");
        assertTrue(bitPruneMap.containsKey(sPattern), "S-extension <(1)(2)> must be retained");
        assertTrue(bitPruneMap.containsKey(complexPattern), "Extension <(1 2)(3)> must be retained");

        assertEquals(1, bitPruneMap.get(iPattern));
        assertEquals(1, bitPruneMap.get(sPattern));
        assertEquals(1, bitPruneMap.get(complexPattern));

        var iNode = tree.find(iPattern).orElseThrow();
        var sNode = tree.find(sPattern).orElseThrow();
        var cNode = tree.find(complexPattern).orElseThrow();

        assertEquals(ExtensionType.I_EXTENSION, iNode.extensionType().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, sNode.extensionType().orElseThrow());
        assertEquals(ExtensionType.S_EXTENSION, cNode.extensionType().orElseThrow());

        assertEquals(singleton(1), iNode.parent().orElseThrow().pattern());
        assertEquals(singleton(1), sNode.parent().orElseThrow().pattern());
        assertEquals(iPattern, cNode.parent().orElseThrow().pattern());
    }

    @Test
    @DisplayName("same_itemset_and_sequential_patterns_remain_distinct")
    void same_itemset_and_sequential_patterns_remain_distinct() {
        Pattern iPattern = pattern(itemset(1, 2));
        Pattern sPattern = pattern(itemset(1), itemset(2));

        assertNotEquals(iPattern, sPattern);
        assertEquals("<(1 2)>", iPattern.serialize());
        assertEquals("<(1)(2)>", sPattern.serialize());
        assertEquals(1, iPattern.size());
        assertEquals(2, sPattern.size());
        assertEquals(2, iPattern.length());
        assertEquals(2, sPattern.length());

        SequenceDatabase db = createM01Database();
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.10));

        assertTrue(map.containsKey(iPattern));
        assertTrue(map.containsKey(sPattern));
        assertEquals(1, map.get(iPattern));
        assertEquals(1, map.get(sPattern));
    }

    @Test
    @DisplayName("repeated_items_and_multiple_embeddings_count_distinct_sids")
    void repeated_items_and_multiple_embeddings_count_distinct_sids() {
        SequenceDatabase db = createM02Database();
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.25));

        Pattern p11 = pattern(itemset(1), itemset(1));
        Pattern p111 = pattern(itemset(1), itemset(1), itemset(1));

        assertTrue(map.containsKey(p11));
        assertTrue(map.containsKey(p111));

        // In SID 1, (1)(1) has 3 embeddings, but must count once; in SID 2 it has 1 embedding. Total = 2 SIDs.
        assertEquals(2, map.get(p11), "<(1)(1)> must have support 2 (counted once per SID)");
        assertEquals(1, map.get(p111), "<(1)(1)(1)> must have support 1");
    }

    @Test
    @DisplayName("sparse_database_has_no_false_patterns")
    void sparse_database_has_no_false_patterns() {
        SequenceDatabase db = createM03Database();
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.20));

        assertFalse(map.containsKey(pattern(itemset(2), itemset(3))), "Cross-structure <(2)(3)> must be absent");
        assertFalse(map.containsKey(pattern(itemset(3), itemset(1))), "Cross-structure <(3)(1)> must be absent");
        assertFalse(map.containsKey(pattern(itemset(4), itemset(1))), "Cross-structure <(4)(1)> must be absent");
        assertFalse(map.containsKey(pattern(itemset(2, 3))), "Cross-structure <(2 3)> must be absent");
    }

    @Test
    @DisplayName("dense_database_has_no_missing_or_duplicate_patterns")
    void dense_database_has_no_missing_or_duplicate_patterns() {
        SequenceDatabase db = createM04Database();
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.20));

        Pattern p123 = pattern(itemset(1, 2, 3));
        Pattern p123_123 = pattern(itemset(1, 2, 3), itemset(1, 2, 3));

        assertTrue(map.containsKey(p123), "Dense itemset <(1 2 3)> must be retained");
        assertTrue(map.containsKey(p123_123), "Repeated dense sequence <(1 2 3)(1 2 3)> must be retained");
        assertEquals(tree.nodeCount() - 1, map.size(), "Tree non-root node count must equal pattern map size");
    }

    @Test
    @DisplayName("negative_zero_noncontiguous_unsorted_sids_are_preserved")
    void negative_zero_noncontiguous_unsorted_sids_are_preserved() {
        SequenceDatabase db = createM05Database();
        List<Integer> sids = db.records().stream().map(SequenceRecord::sid).toList();
        assertEquals(List.of(42, -5, 999, 0), sids, "Input record order and exact SIDs must be preserved");

        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.20));

        for (var entry : map.entrySet()) {
            assertTrue(entry.getValue() >= 1 && entry.getValue() <= 4,
                    "Support must count distinct SIDs [1..4], not maximum SID value (999); got: " + entry.getValue());
        }
    }

    @Test
    @DisplayName("positive_absolute_and_percentage_modes_match")
    void positive_absolute_and_percentage_modes_match() {
        // M01 has 3 sequences. 5.0% of 3 = 0.15. Absolute 0.15.
        SequenceDatabase db1 = createM01Database();
        PrefixTree treeAbs = new PrefixTree();
        PrefixTree treePct = new PrefixTree();
        Map<Pattern, Integer> mapAbs = bitPruneMiner.mineInto(treeAbs, db1, MinimumSupport.absolute(0.15));
        Map<Pattern, Integer> mapPct = bitPruneMiner.mineInto(treePct, db1, MinimumSupport.percentage(5.0));
        assertEquals(mapAbs, mapPct, "Equivalent absolute and percentage thresholds must produce identical results");

        // M05 has 4 sequences. 5.0% of 4 = 0.20. Absolute 0.20.
        SequenceDatabase db5 = createM05Database();
        PrefixTree treeAbs5 = new PrefixTree();
        PrefixTree treePct5 = new PrefixTree();
        Map<Pattern, Integer> mapAbs5 = bitPruneMiner.mineInto(treeAbs5, db5, MinimumSupport.absolute(0.20));
        Map<Pattern, Integer> mapPct5 = bitPruneMiner.mineInto(treePct5, db5, MinimumSupport.percentage(5.0));
        assertEquals(mapAbs5, mapPct5, "Equivalent absolute and percentage thresholds on M05 must match");
    }

    @Test
    @DisplayName("threshold_equality_is_accepted")
    void threshold_equality_is_accepted() {
        SequenceDatabase db = createM06Database();
        var stats = new ItemStatisticsCalculator().calculate(db);
        var weights = new ItemWeightCalculator().calculate(stats);
        double meanW = weights.meanWeight().orElseThrow();
        assertEquals(0.50, meanW, 1e-12, "MeanW must be exactly 0.50");

        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.50));

        Pattern p2 = singleton(2);
        assertTrue(map.containsKey(p2), "Pattern <(2)> with support*MeanW == threshold must be accepted");
        assertEquals(1, map.get(p2));
        assertEquals(0.50, 1 * meanW, 1e-12, "support * MeanW must exactly equal effective threshold");
    }

    @Test
    @DisplayName("cmap_i_true_is_only_a_loose_necessary_condition")
    void cmap_i_true_is_only_a_loose_necessary_condition() {
        SequenceDatabase db = createM07Database();
        var mapping = SidMapping.of(db);
        var f1 = List.of(new Item(1), new Item(2), new Item(3));
        CmapI cmapI = new CmapBuilder().buildI(db, f1);

        // CMAP-I(2, 3) is true because SID 2 contains (2 3)
        assertTrue(cmapI.contains(new Item(2), new Item(3)), "CMAP-I(2, 3) must be true");

        // Witness candidate: <(1)(2 3)>
        Pattern witness = pattern(itemset(1), itemset(2, 3));
        int exactSupport = bruteForceOracle.countSupport(db, witness);
        assertEquals(0, exactSupport, "Witness <(1)(2 3)> has exact support 0");

        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.25));
        assertFalse(bitPruneMap.containsKey(witness), "<(1)(2 3)> must not be accepted");
        assertTrue(tree.find(witness).isEmpty(), "<(1)(2 3)> must not appear in PrefixTree");
    }

    @Test
    @DisplayName("cmap_s_and_bitmap_can_pass_before_exact_temporal_rejection")
    void cmap_s_and_bitmap_can_pass_before_exact_temporal_rejection() {
        SequenceDatabase db = createM08Database();
        MinimumSupport minSup = MinimumSupport.absolute(0.25);
        ResolvedMinimumSupport resolved = resolver.resolve(minSup, db.size());

        var stats = new ItemStatisticsCalculator().calculate(db);
        var weights = new ItemWeightCalculator().calculate(stats);
        double meanW = weights.meanWeight().orElseThrow();
        assertEquals(0.30, meanW, 1e-12, "MeanW must be 3/10 = 0.30");

        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1Items = List.of(new Item(1), new Item(2), new Item(3));
        CmapI cmapI = new CmapBuilder().buildI(db, f1Items);
        CmapS cmapS = new CmapBuilder().buildS(db, f1Items);

        Item item1 = new Item(1);
        Item item2 = new Item(2);
        Item item3 = new Item(3);

        // 1. occurrenceIndex.positions(item 2, SID -5) equals [3] (1-based position in <(3)(1)(2)>)
        int[] item2Positions = occIndex.positions(item2, -5);
        assertArrayEquals(new int[]{3}, item2Positions, "occurrenceIndex.positions(item 2, SID -5) must equal [3]");

        int[] item3Positions = occIndex.positions(item3, -5);
        assertArrayEquals(new int[]{1}, item3Positions, "occurrenceIndex.positions(item 3, SID -5) must equal [1]");

        // 2. Supplied parent VerificationResult contains endpoint [3] in SID -5
        Pattern parent = pattern(itemset(1), itemset(2));
        VerificationResult parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(-5, new int[]{3})));
        assertEquals(1, parentVerif.endpointStates().size());
        assertArrayEquals(new int[]{3}, parentVerif.endpointStates().get(0).endpoints(),
                "Supplied parent VerificationResult must contain endpoint [3]");

        // 3. Brute-force support of parent <(1)(2)> is 1
        int parentBfSupport = bruteForceOracle.countSupport(db, parent);
        assertEquals(1, parentBfSupport, "brute-force support of parent <(1)(2)> must be 1");

        // 4. Parent bitmap contains exactly SID -5
        SidBitmap parentBitmap = SidBitmap.builder(mapping).setSid(-5).build();
        assertEquals(1, parentBitmap.cardinality());
        assertArrayEquals(new int[]{-5}, parentBitmap.toSupportingSids(),
                "parent bitmap must contain exactly SID -5");

        // 5. CMAP-S(2, 3) is true from SID 10: <(2)(3)>
        assertTrue(cmapS.contains(item2, item3), "CMAP-S(2, 3) must be true");

        // 6. Ext item 3 occurs in SID -5 and SID 10; candidate bitmap cardinality is 1
        SidBitmap extBitmap = SidBitmap.builder(mapping)
                .setSid(-5)
                .setSid(10)
                .build();
        SidBitmap candidateBitmap = parentBitmap.and(extBitmap);
        assertEquals(1, candidateBitmap.cardinality(), "candidate bitmap cardinality must be 1");

        // 7. Weighted Bitmap bound is 1 * 0.30 = 0.30 and passes threshold 0.25
        double weightedBitmapBound = candidateBitmap.cardinality() * meanW;
        assertEquals(0.30, weightedBitmapBound, 1e-12, "weighted Bitmap bound must be 0.30");
        assertTrue(weightedBitmapBound >= resolved.effectiveThreshold(),
                "weighted Bitmap bound must pass threshold 0.25");

        // 8. Exact support of <(1)(2)(3)> is 0
        Pattern candidatePattern = pattern(itemset(1), itemset(2), itemset(3));
        int candidateBfSupport = bruteForceOracle.countSupport(db, candidatePattern);
        assertEquals(0, candidateBfSupport, "exact support of <(1)(2)(3)> must be 0");

        // 9. Evaluator outcome is EXACT_REJECTED
        BitPruneCandidateResult candidate = new BitPruneCandidateResult(
                candidatePattern,
                ExtensionType.S_EXTENSION,
                item3);

        BitPruneCandidateEvaluator evaluator = new BitPruneCandidateEvaluator();
        BitPruneCandidateEvaluation eval = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, meanW, resolved);

        assertEquals(Outcome.EXACT_REJECTED, eval.outcome(),
                "Candidate must pass CMAP and Bitmap bound, then fail exact temporal verification");
        assertTrue(eval.candidateBitmap().isPresent());
        assertEquals(1, eval.candidateBitmap().get().cardinality(), "Bitmap upper bound cardinality must be 1");
        assertTrue(eval.verificationResult().isPresent());
        assertEquals(0, eval.verificationResult().get().exactSupport(), "Exact temporal support must be 0");

        // 10. Complete mining verifies absence from tree and map
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, db, minSup);
        assertFalse(bitPruneMap.containsKey(candidatePattern), "<(1)(2)(3)> must not appear in result map");
        assertTrue(tree.find(candidatePattern).isEmpty(), "<(1)(2)(3)> must not appear in PrefixTree");
    }

    @Test
    @DisplayName("empty_database_positive_absolute_returns_empty_maps")
    void empty_database_positive_absolute_returns_empty_maps() {
        SequenceDatabase db = createM09Database();
        MinimumSupport minSup = MinimumSupport.absolute(0.50);

        Map<Pattern, Integer> baselineMap = baselineMiner.mine(db, minSup).supports();
        Map<Pattern, Integer> bruteForceMap = bruteForceOracle.mine(db, minSup);
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, db, minSup);

        assertEquals(Collections.emptyMap(), baselineMap);
        assertEquals(Collections.emptyMap(), bruteForceMap);
        assertEquals(Collections.emptyMap(), bitPruneMap);
        assertTrue(tree.isFrozen(), "Tree must be frozen");
        assertEquals(1, tree.nodeCount(), "Tree must contain only root");
    }

    @Test
    @DisplayName("repeated_execution_is_deterministic")
    void repeated_execution_is_deterministic() {
        SequenceDatabase db = createM01Database();
        MinimumSupport minSup = MinimumSupport.absolute(0.10);

        PrefixTree t1 = new PrefixTree();
        PrefixTree t2 = new PrefixTree();
        PrefixTree t3 = new PrefixTree();

        Map<Pattern, Integer> r1 = bitPruneMiner.mineInto(t1, db, minSup);
        Map<Pattern, Integer> r2 = bitPruneMiner.mineInto(t2, db, minSup);
        Map<Pattern, Integer> r3 = bitPruneMiner.mineInto(t3, db, minSup);

        assertEquals(r1, r2);
        assertEquals(r2, r3);
        assertEquals(new ArrayList<>(r1.entrySet()), new ArrayList<>(r2.entrySet()));
        assertEquals(new ArrayList<>(r2.entrySet()), new ArrayList<>(r3.entrySet()));
        assertEquals(t1.preOrderPatterns(), t2.preOrderPatterns());
        assertEquals(t2.preOrderPatterns(), t3.preOrderPatterns());
    }

    @Test
    @DisplayName("returned_bitprune_maps_are_immutable")
    void returned_bitprune_maps_are_immutable() {
        SequenceDatabase db = createM01Database();
        PrefixTree tree = new PrefixTree();
        Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, db, MinimumSupport.absolute(0.10));

        assertThrows(UnsupportedOperationException.class, () -> map.put(singleton(99), 1));
        assertThrows(UnsupportedOperationException.class, () -> map.remove(singleton(1)));
        assertThrows(UnsupportedOperationException.class, () -> map.clear());
    }

    @Test
    @DisplayName("baseline_sid_normalization_invariance_for_special_sid_databases")
    void baseline_sid_normalization_invariance_for_special_sid_databases() {
        var itemStatsCalc = new ItemStatisticsCalculator();
        var itemWeightCalc = new ItemWeightCalculator();

        // ---------------------------------------------------------------------
        // Database M05: Raw SIDs [42, -5, 999, 0]
        // ---------------------------------------------------------------------
        SequenceDatabase rawM05 = createM05Database();
        SequenceDatabase normM05 = toContiguousBaselineDatabase(rawM05);

        // 1. Preserves database size
        assertEquals(rawM05.size(), normM05.size());
        assertEquals(4, normM05.size());

        // 2. Preserves record order and sequence content
        for (int i = 0; i < rawM05.size(); i++) {
            assertEquals(rawM05.records().get(i).sequence(), normM05.records().get(i).sequence(),
                    "Sequence content at index " + i + " must be identical");
        }

        // 3. Maps SIDs exactly to 1..N
        assertEquals(List.of(1, 2, 3, 4), normM05.records().stream().map(SequenceRecord::sid).toList());

        // 4. Does not modify raw database
        assertEquals(List.of(42, -5, 999, 0), rawM05.records().stream().map(SequenceRecord::sid).toList());

        // 5. Preserves item occurrence statistics
        var statsRawM05 = itemStatsCalc.calculate(rawM05);
        var statsNormM05 = itemStatsCalc.calculate(normM05);
        assertEquals(statsRawM05.numberOfDistinctItems(), statsNormM05.numberOfDistinctItems());
        assertEquals(statsRawM05.totalDatabaseLength(), statsNormM05.totalDatabaseLength());
        assertEquals(statsRawM05.itemStatistics(), statsNormM05.itemStatistics());

        // 6. Preserves MeanW
        double meanWRawM05 = itemWeightCalc.calculate(statsRawM05).meanWeight().orElseThrow();
        double meanWNormM05 = itemWeightCalc.calculate(statsNormM05).meanWeight().orElseThrow();
        assertEquals(meanWRawM05, meanWNormM05, 1e-12);

        // 7. Preserves effective thresholds and independent brute-force map
        List<MinimumSupport> m05Configs = List.of(
                MinimumSupport.absolute(0.20),
                MinimumSupport.percentage(5.0));
        for (MinimumSupport cfg : m05Configs) {
            assertEquals(
                    resolver.resolve(cfg, rawM05.size()).effectiveThreshold(),
                    resolver.resolve(cfg, normM05.size()).effectiveThreshold(),
                    1e-12);

            Map<Pattern, Integer> rawBf = bruteForceOracle.mine(rawM05, cfg);
            Map<Pattern, Integer> normBf = bruteForceOracle.mine(normM05, cfg);
            assertEquals(rawBf, normBf,
                    "bruteForce(rawDatabase, config) == bruteForce(normalizedDatabase, config) on M05 for " + cfg);
        }

        // ---------------------------------------------------------------------
        // Database M08: Raw SIDs [-5, 10]
        // ---------------------------------------------------------------------
        SequenceDatabase rawM08 = createM08Database();
        SequenceDatabase normM08 = toContiguousBaselineDatabase(rawM08);

        assertEquals(rawM08.size(), normM08.size());
        assertEquals(2, normM08.size());

        for (int i = 0; i < rawM08.size(); i++) {
            assertEquals(rawM08.records().get(i).sequence(), normM08.records().get(i).sequence(),
                    "Sequence content at index " + i + " must be identical");
        }

        assertEquals(List.of(1, 2), normM08.records().stream().map(SequenceRecord::sid).toList());
        assertEquals(List.of(-5, 10), rawM08.records().stream().map(SequenceRecord::sid).toList());

        var statsRawM08 = itemStatsCalc.calculate(rawM08);
        var statsNormM08 = itemStatsCalc.calculate(normM08);
        assertEquals(statsRawM08.numberOfDistinctItems(), statsNormM08.numberOfDistinctItems());
        assertEquals(statsRawM08.totalDatabaseLength(), statsNormM08.totalDatabaseLength());
        assertEquals(statsRawM08.itemStatistics(), statsNormM08.itemStatistics());

        double meanWRawM08 = itemWeightCalc.calculate(statsRawM08).meanWeight().orElseThrow();
        double meanWNormM08 = itemWeightCalc.calculate(statsNormM08).meanWeight().orElseThrow();
        assertEquals(meanWRawM08, meanWNormM08, 1e-12);

        MinimumSupport m08Cfg = MinimumSupport.absolute(0.25);
        assertEquals(
                resolver.resolve(m08Cfg, rawM08.size()).effectiveThreshold(),
                resolver.resolve(m08Cfg, normM08.size()).effectiveThreshold(),
                1e-12);

        Map<Pattern, Integer> rawBfM08 = bruteForceOracle.mine(rawM08, m08Cfg);
        Map<Pattern, Integer> normBfM08 = bruteForceOracle.mine(normM08, m08Cfg);
        assertEquals(rawBfM08, normBfM08,
                "bruteForce(rawDatabase, config) == bruteForce(normalizedDatabase, config) on M08 for " + m08Cfg);
    }

    @Test
    @DisplayName("diagnostic_formatter_contains_required_fields")
    void diagnostic_formatter_contains_required_fields() {
        SequenceDatabase db = createM01Database();
        MinimumSupport minSup = MinimumSupport.absolute(0.10);
        String diagnostic = buildMismatchDiagnostic(
                "TEST-CASE", 1, db, minSup, 0.25, 0.10,
                Map.of(singleton(1), 1),
                Map.of(singleton(1), 1),
                Map.of(singleton(1), 1),
                Map.of(singleton(1), 1),
                Map.of(singleton(1), 1),
                new PrefixTree(),
                "test-tier-witness",
                false);

        assertTrue(diagnostic.contains("caseId: TEST-CASE"));
        assertTrue(diagnostic.contains("configIndex: 1"));
        assertTrue(diagnostic.contains("seed: N/A — deterministic controlled case"));
        assertTrue(diagnostic.contains("raw sequences:"));
        assertTrue(diagnostic.contains("actual SID and record order:"));
        assertTrue(diagnostic.contains("raw SID order:"));
        assertTrue(diagnostic.contains("baseline SID normalization applied: false"));
        assertTrue(diagnostic.contains("normalized baseline SID order:"));
        assertTrue(diagnostic.contains("minimum-support mode:"));
        assertTrue(diagnostic.contains("configured minimum support:"));
        assertTrue(diagnostic.contains("number of sequences:"));
        assertTrue(diagnostic.contains("effective threshold:"));
        assertTrue(diagnostic.contains("MeanW:"));
        assertTrue(diagnostic.contains("baseline complete map:"));
        assertTrue(diagnostic.contains("brute-force complete map:"));
        assertTrue(diagnostic.contains("BitPrune complete map:"));
        assertTrue(diagnostic.contains("missing patterns:"));
        assertTrue(diagnostic.contains("unexpected patterns:"));
        assertTrue(diagnostic.contains("first support mismatch:"));
        assertTrue(diagnostic.contains("PrefixTree pre-order:"));
        assertTrue(diagnostic.contains("tier witness: test-tier-witness"));
    }

    // =========================================================================
    // 3. Diagnostics & Assertion Helpers
    // =========================================================================

    private void assertCompleteMapEquality(
            String caseId,
            int configIndex,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            Map<Pattern, Integer> baselineMap,
            Map<Pattern, Integer> bruteForceMap,
            Map<Pattern, Integer> bitPruneMap,
            PrefixTree prefixTree,
            String tierWitnessInfo,
            boolean baselineNormalized) {

        double meanWeight = database.size() == 0 ? Double.NaN
                : new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(database))
                        .meanWeight().orElse(Double.NaN);
        double effectiveThreshold = resolver.resolve(minimumSupport, database.size()).effectiveThreshold();

        boolean bfMatchesBaseline = baselineMap.equals(bruteForceMap);
        boolean bitPruneMatchesBaseline = baselineMap.equals(bitPruneMap);

        if (!bfMatchesBaseline) {
            String diag = buildMismatchDiagnostic(
                    caseId + " [BruteForce vs Baseline]", configIndex, database, minimumSupport,
                    meanWeight, effectiveThreshold, baselineMap, bruteForceMap, bitPruneMap,
                    baselineMap, bruteForceMap, prefixTree, tierWitnessInfo, baselineNormalized);
            fail(diag);
        }

        if (!bitPruneMatchesBaseline) {
            String diag = buildMismatchDiagnostic(
                    caseId + " [BitPrune vs Baseline]", configIndex, database, minimumSupport,
                    meanWeight, effectiveThreshold, baselineMap, bruteForceMap, bitPruneMap,
                    baselineMap, bitPruneMap, prefixTree, tierWitnessInfo, baselineNormalized);
            fail(diag);
        }
    }

    static String buildMismatchDiagnostic(
            String caseId,
            int configIndex,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            double meanWeight,
            double effectiveThreshold,
            Map<Pattern, Integer> baselineMap,
            Map<Pattern, Integer> bruteForceMap,
            Map<Pattern, Integer> bitPruneMap,
            Map<Pattern, Integer> expectedMap,
            Map<Pattern, Integer> actualMap,
            PrefixTree prefixTree,
            String tierWitnessInfo,
            boolean baselineNormalized) {

        Set<Pattern> missing = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        if (expectedMap != null && actualMap != null) {
            missing.addAll(expectedMap.keySet());
            missing.removeAll(actualMap.keySet());
        }

        Set<Pattern> unexpected = new TreeSet<>(Comparator.comparing(Pattern::serialize));
        if (expectedMap != null && actualMap != null) {
            unexpected.addAll(actualMap.keySet());
            unexpected.removeAll(expectedMap.keySet());
        }

        String firstSupportMismatch = "none";
        if (expectedMap != null && actualMap != null) {
            for (Pattern p : expectedMap.keySet().stream().sorted(Comparator.comparing(Pattern::serialize)).toList()) {
                if (actualMap.containsKey(p)) {
                    int exp = expectedMap.get(p);
                    int act = actualMap.get(p);
                    if (exp != act) {
                        firstSupportMismatch = p.serialize() + " (expected=" + exp + ", actual=" + act + ")";
                        break;
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== DIFFERENTIAL MISMATCH DIAGNOSTIC ===").append(System.lineSeparator());
        sb.append("caseId: ").append(caseId).append(System.lineSeparator());
        sb.append("configIndex: ").append(configIndex).append(System.lineSeparator());
        sb.append("seed: N/A — deterministic controlled case").append(System.lineSeparator());
        sb.append("raw sequences:").append(System.lineSeparator());
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
        sb.append("actual SID and record order: ")
                .append(database.records().stream().map(SequenceRecord::sid).toList())
                .append(System.lineSeparator());
        sb.append("raw SID order: ")
                .append(database.records().stream().map(SequenceRecord::sid).toList())
                .append(System.lineSeparator());
        sb.append("baseline SID normalization applied: ")
                .append(baselineNormalized)
                .append(System.lineSeparator());
        if (baselineNormalized) {
            sb.append("normalized baseline SID order: ")
                    .append(IntStream.rangeClosed(1, database.size()).boxed().toList())
                    .append(System.lineSeparator());
        } else {
            sb.append("normalized baseline SID order: N/A - raw SIDs used directly")
                    .append(System.lineSeparator());
        }
        sb.append("minimum-support mode: ").append(minimumSupport.mode()).append(System.lineSeparator());
        sb.append("configured minimum support: ").append(minimumSupport.configuredValue()).append(System.lineSeparator());
        sb.append("number of sequences: ").append(database.size()).append(System.lineSeparator());
        sb.append("effective threshold: ").append(effectiveThreshold).append(System.lineSeparator());
        sb.append("MeanW: ").append(meanWeight).append(System.lineSeparator());

        sb.append("baseline complete map:").append(System.lineSeparator());
        formatMap(sb, baselineMap);
        sb.append("brute-force complete map:").append(System.lineSeparator());
        formatMap(sb, bruteForceMap);
        sb.append("BitPrune complete map:").append(System.lineSeparator());
        formatMap(sb, bitPruneMap);

        sb.append("missing patterns: ").append(missing.stream().map(Pattern::serialize).toList()).append(System.lineSeparator());
        sb.append("unexpected patterns: ").append(unexpected.stream().map(Pattern::serialize).toList()).append(System.lineSeparator());
        sb.append("first support mismatch: ").append(firstSupportMismatch).append(System.lineSeparator());

        sb.append("PrefixTree pre-order: ");
        if (prefixTree != null) {
            sb.append(prefixTree.preOrderPatterns().stream().map(Pattern::serialize).toList());
        } else {
            sb.append("N/A");
        }
        sb.append(System.lineSeparator());

        sb.append("tier witness: ").append(tierWitnessInfo != null ? tierWitnessInfo : "none").append(System.lineSeparator());
        return sb.toString();
    }

    private static void formatMap(StringBuilder sb, Map<Pattern, Integer> map) {
        if (map == null) {
            sb.append("  null").append(System.lineSeparator());
            return;
        }
        sb.append("  size=").append(map.size()).append(System.lineSeparator());
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Pattern::serialize)))
                .forEach(e -> sb.append("    ").append(e.getKey().serialize()).append(" -> ").append(e.getValue()).append(System.lineSeparator()));
    }

    // =========================================================================
    // 4. Matrix Definitions (M01 to M09, 13 Configurations)
    // =========================================================================

    private static boolean isContiguous1ToN(SequenceDatabase database) {
        for (int i = 0; i < database.size(); i++) {
            if (database.records().get(i).sid() != i + 1) {
                return false;
            }
        }
        return true;
    }

    private static SequenceDatabase toContiguousBaselineDatabase(SequenceDatabase database) {
        if (isContiguous1ToN(database)) {
            return database;
        }
        var records = new ArrayList<SequenceRecord>(database.size());
        for (int i = 0; i < database.size(); i++) {
            records.add(new SequenceRecord(i + 1, database.records().get(i).sequence()));
        }
        return new SequenceDatabase(records);
    }

    record ControlledMatrixCase(
            String caseId,
            int configIndex,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            String familyName) {}

    private static List<ControlledMatrixCase> createMatrixCases() {
        var list = new ArrayList<ControlledMatrixCase>();
        // M01
        SequenceDatabase m01 = createM01Database();
        list.add(new ControlledMatrixCase("M01-ABS-0.10", 1, m01, MinimumSupport.absolute(0.10), "M01"));
        list.add(new ControlledMatrixCase("M01-PCT-5.0", 2, m01, MinimumSupport.percentage(5.0), "M01"));

        // M02
        SequenceDatabase m02 = createM02Database();
        list.add(new ControlledMatrixCase("M02-ABS-0.25", 3, m02, MinimumSupport.absolute(0.25), "M02"));
        list.add(new ControlledMatrixCase("M02-PCT-10.0", 4, m02, MinimumSupport.percentage(10.0), "M02"));

        // M03
        SequenceDatabase m03 = createM03Database();
        list.add(new ControlledMatrixCase("M03-ABS-0.20", 5, m03, MinimumSupport.absolute(0.20), "M03"));

        // M04
        SequenceDatabase m04 = createM04Database();
        list.add(new ControlledMatrixCase("M04-ABS-0.20", 6, m04, MinimumSupport.absolute(0.20), "M04"));
        list.add(new ControlledMatrixCase("M04-PCT-5.0", 7, m04, MinimumSupport.percentage(5.0), "M04"));

        // M05
        SequenceDatabase m05 = createM05Database();
        list.add(new ControlledMatrixCase("M05-ABS-0.20", 8, m05, MinimumSupport.absolute(0.20), "M05"));
        list.add(new ControlledMatrixCase("M05-PCT-5.0", 9, m05, MinimumSupport.percentage(5.0), "M05"));

        // M06
        SequenceDatabase m06 = createM06Database();
        list.add(new ControlledMatrixCase("M06-ABS-0.50", 10, m06, MinimumSupport.absolute(0.50), "M06"));

        // M07
        SequenceDatabase m07 = createM07Database();
        list.add(new ControlledMatrixCase("M07-ABS-0.25", 11, m07, MinimumSupport.absolute(0.25), "M07"));

        // M08
        SequenceDatabase m08 = createM08Database();
        list.add(new ControlledMatrixCase("M08-ABS-0.25", 12, m08, MinimumSupport.absolute(0.25), "M08"));

        // M09
        SequenceDatabase m09 = createM09Database();
        list.add(new ControlledMatrixCase("M09-ABS-0.50", 13, m09, MinimumSupport.absolute(0.50), "M09"));

        return Collections.unmodifiableList(list);
    }

    static SequenceDatabase createM01Database() {
        return db(
                record(1, itemset(1, 2), itemset(3)),
                record(2, itemset(1), itemset(2), itemset(3)),
                record(3, itemset(4)));
    }

    static SequenceDatabase createM02Database() {
        return db(
                record(1, itemset(1), itemset(1), itemset(1)),
                record(2, itemset(1), itemset(1)));
    }

    static SequenceDatabase createM03Database() {
        return db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(3), itemset(4)),
                record(3, itemset(1), itemset(4)));
    }

    static SequenceDatabase createM04Database() {
        return db(
                record(1, itemset(1, 2, 3), itemset(1, 2, 3)),
                record(2, itemset(1, 2, 3)));
    }

    static SequenceDatabase createM05Database() {
        return db(
                record(42, itemset(1, 2), itemset(2, 3)),
                record(-5, itemset(1, 2), itemset(3)),
                record(999, itemset(1), itemset(3)),
                record(0, itemset(1), itemset(2, 3)));
    }

    static SequenceDatabase createM06Database() {
        return db(
                record(1, itemset(1)),
                record(2, itemset(1)),
                record(3, itemset(2)));
    }

    static SequenceDatabase createM07Database() {
        return db(
                record(1, itemset(1), itemset(2)),
                record(2, itemset(2, 3)));
    }

    static SequenceDatabase createM08Database() {
        return db(
                record(-5, itemset(3), itemset(1), itemset(2)),
                record(10, itemset(2), itemset(3)));
    }

    static SequenceDatabase createM09Database() {
        return new SequenceDatabase(List.of());
    }

    // =========================================================================
    // 5. Sequence & Itemset Helpers
    // =========================================================================

    private static Itemset itemset(int... ids) {
        var items = new ArrayList<Item>(ids.length);
        for (int id : ids) {
            items.add(new Item(id));
        }
        return new Itemset(items);
    }

    private static SequenceRecord record(int sid, Itemset... itemsets) {
        return new SequenceRecord(sid, Sequence.of(itemsets));
    }

    private static SequenceDatabase db(SequenceRecord... records) {
        return new SequenceDatabase(List.of(records));
    }

    private static Pattern pattern(Itemset... itemsets) {
        return Pattern.of(itemsets);
    }

    private static Pattern singleton(int id) {
        return Pattern.singleton(new Item(id));
    }
}
