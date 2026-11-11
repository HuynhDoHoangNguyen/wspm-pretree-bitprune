package vn.edu.wspm.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.mining.BitPruneMiner;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.statistics.ItemStatisticsCalculator;
import vn.edu.wspm.statistics.ItemWeightCalculator;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Task C11.4 — Fixed-Seed Randomized Three-Oracle Full-Map Differential
 * Correctness.
 *
 * <p>
 * Bounded empirical differential correctness testing across 1200 deterministic
 * randomized
 * trials comparing complete {@code Map<Pattern, Integer>} outputs between
 * frozen WSPM_PreTree baseline,
 * independent BruteForceWeightedPatternOracle, and WSPM_BitPrune.
 * </p>
 */
class BitPruneFixedSeedRandomizedDifferentialTest {

    static final long FIXED_SEED = 20261104L;
    static final int TRIAL_COUNT = 1200;

    private final WspmPreTreeMiner baselineMiner = new WspmPreTreeMiner();
    private final BruteForceWeightedPatternOracle bruteForceOracle = new BruteForceWeightedPatternOracle();
    private final BitPruneMiner bitPruneMiner = new BitPruneMiner();
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    // Canonical item universe: U = {1, 2, 3}
    static final Item ITEM_1 = new Item(1);
    static final Item ITEM_2 = new Item(2);
    static final Item ITEM_3 = new Item(3);

    // All 7 non-empty canonical itemsets over {1, 2, 3}
    static final List<Itemset> CANONICAL_ITEMSETS = List.of(
            Itemset.of(ITEM_1),
            Itemset.of(ITEM_2),
            Itemset.of(ITEM_3),
            Itemset.of(ITEM_1, ITEM_2),
            Itemset.of(ITEM_1, ITEM_3),
            Itemset.of(ITEM_2, ITEM_3),
            Itemset.of(ITEM_1, ITEM_2, ITEM_3));

    // Multi-value configuration catalogue exercising permissive and restrictive
    // thresholds in both modes
    static final List<MinimumSupport> CONFIG_CATALOGUE = List.of(
            MinimumSupport.absolute(0.10),
            MinimumSupport.absolute(0.25),
            MinimumSupport.absolute(0.50),
            MinimumSupport.absolute(0.85),
            MinimumSupport.percentage(10.0),
            MinimumSupport.percentage(25.0),
            MinimumSupport.percentage(50.0),
            MinimumSupport.percentage(85.0));

    record Trial(
            int trialIndex,
            SequenceDatabase database,
            MinimumSupport minimumSupport) {
    }

    // =========================================================================
    // 1. Deterministic Randomized Generator (Sections 6 & 7)
    // =========================================================================

    static List<Trial> generateTrials(long seed, int count) {
        Random random = new Random(seed);
        List<Trial> trials = new ArrayList<>(count);
        for (int t = 0; t < count; t++) {
            MinimumSupport minSup = CONFIG_CATALOGUE.get(random.nextInt(CONFIG_CATALOGUE.size()));
            int dbSize = 1 + random.nextInt(3); // 1 to 3 sequence records
            List<SequenceRecord> records = new ArrayList<>(dbSize);
            Sequence previousSeq = null;

            for (int sid = 1; sid <= dbSize; sid++) {
                Sequence seq;
                if (previousSeq != null && random.nextDouble() < 0.20) {
                    // 20% probability of duplicate sequence in multi-sequence database
                    seq = previousSeq;
                } else {
                    int seqLen = 1 + random.nextInt(3); // 1 to 3 itemsets
                    List<Itemset> itemsets = new ArrayList<>(seqLen);
                    for (int k = 0; k < seqLen; k++) {
                        itemsets.add(CANONICAL_ITEMSETS.get(random.nextInt(CANONICAL_ITEMSETS.size())));
                    }
                    seq = new Sequence(itemsets);
                }
                records.add(new SequenceRecord(sid, seq));
                previousSeq = seq;
            }
            trials.add(new Trial(t, new SequenceDatabase(records), minSup));
        }
        return List.copyOf(trials);
    }

    static String serializeSequence(Sequence sequence) {
        StringBuilder sb = new StringBuilder("<");
        for (Itemset itemset : sequence.itemsets()) {
            sb.append("(");
            for (int i = 0; i < itemset.items().size(); i++) {
                if (i > 0) {
                    sb.append(" ");
                }
                sb.append(itemset.items().get(i).id());
            }
            sb.append(")");
        }
        sb.append(">");
        return sb.toString();
    }

    static String serializeDatabase(SequenceDatabase database) {
        StringBuilder sb = new StringBuilder();
        for (SequenceRecord rec : database.records()) {
            sb.append("SID ").append(rec.sid()).append(": ").append(serializeSequence(rec.sequence())).append("; ");
        }
        return sb.toString();
    }

    /**
     * Independent test-only helper to count distinct embeddings of a pattern in a sequence.
     * An embedding maps pattern itemsets to strictly increasing sequence-itemset positions
     * (j_0 < j_1 < ... < j_{m-1}) such that each pattern itemset is a subset of its matched sequence itemset.
     * Caps counting at maxCount to avoid unnecessary enumeration.
     */
    static int countDistinctEmbeddings(Pattern pattern, Sequence sequence, int maxCount) {
        if (pattern.isEmpty()) {
            return 0;
        }
        return countEmbeddingsRecursive(pattern.itemsets(), 0, sequence.itemsets(), 0, maxCount);
    }

    private static int countEmbeddingsRecursive(
            List<Itemset> pItemsets, int pIdx,
            List<Itemset> sItemsets, int sIdx,
            int maxCount) {
        if (pIdx == pItemsets.size()) {
            return 1;
        }
        int total = 0;
        Itemset target = pItemsets.get(pIdx);
        for (int j = sIdx; j <= sItemsets.size() - (pItemsets.size() - pIdx); j++) {
            Itemset candidate = sItemsets.get(j);
            if (candidate.items().containsAll(target.items())) {
                int subCount = countEmbeddingsRecursive(pItemsets, pIdx + 1, sItemsets, j + 1, maxCount - total);
                total += subCount;
                if (total >= maxCount) {
                    return total;
                }
            }
        }
        return total;
    }

    // =========================================================================
    // 2. Test 1: Main Randomized Differential Test (1200 Trials)
    // =========================================================================

    @Test
    @DisplayName("fixed_seed_randomized_complete_maps_match_all_three_oracles")
    void fixed_seed_randomized_complete_maps_match_all_three_oracles() {
        List<Trial> trials = generateTrials(FIXED_SEED, TRIAL_COUNT);
        assertEquals(TRIAL_COUNT, trials.size());

        int completedTrials = 0;
        int completedAbsoluteConfigs = 0;
        int completedPercentConfigs = 0;
        int completedBaselineRuns = 0;
        int completedBruteForceRuns = 0;
        int completedBitPruneRuns = 0;
        int completedAlgorithmRuns = 0;
        int completedPairwiseComparisons = 0;

        int missingPatterns = 0;
        int unexpectedPatterns = 0;
        int supportMismatches = 0;
        int unexplainedMismatches = 0;

        // Coverage counters
        int cntAbsoluteMode = 0;
        int cntPercentMode = 0;
        int cntOneSequenceDb = 0;
        int cntMultiSequenceDb = 0;
        int cntOneItemsetSeq = 0;
        int cntMultiItemsetSeq = 0;
        int cntSingletonItemset = 0;
        int cntMultiItemItemset = 0;
        int cntRepeatedItemAcrossItemsets = 0;
        int cntDuplicateSequenceRecords = 0;
        int cntSparseStructure = 0;
        int cntDenseStructure = 0;
        int cntAcceptedIExtensionPattern = 0;
        int cntAcceptedSExtensionPattern = 0;
        int cntAcceptedRepeatedItemPattern = 0;
        int cntMultipleEmbeddingsWithinOneSid = 0;
        int cntNonEmptyResultMap = 0;
        int cntEmptyResultMap = 0;

        int maxObservedPatternLength = 0;
        int totalBaselinePatterns = 0;
        int totalBruteForcePatterns = 0;
        int totalBitPrunePatterns = 0;

        for (Trial trial : trials) {
            completedTrials++;
            SequenceDatabase db = trial.database();
            MinimumSupport minSup = trial.minimumSupport();

            // SIDs must be contiguous 1..N
            for (int i = 0; i < db.size(); i++) {
                assertEquals(i + 1, db.records().get(i).sid());
            }

            // Mode tracking
            if (minSup.mode() == MinimumSupportMode.ABSOLUTE) {
                completedAbsoluteConfigs++;
                cntAbsoluteMode++;
            } else if (minSup.mode() == MinimumSupportMode.PERCENT) {
                completedPercentConfigs++;
                cntPercentMode++;
            }

            // Database size tracking
            if (db.size() == 1) {
                cntOneSequenceDb++;
            } else {
                cntMultiSequenceDb++;
            }

            // Structural tracking
            boolean hasDuplicateRecord = false;
            int totalDbLength = 0;
            boolean allRecordsMultiItem = true;

            for (int r = 0; r < db.records().size(); r++) {
                Sequence seq = db.records().get(r).sequence();
                totalDbLength += seq.length();

                if (seq.size() == 1) {
                    cntOneItemsetSeq++;
                } else {
                    cntMultiItemsetSeq++;
                }

                boolean recordHasMultiItem = false;
                for (Itemset is : seq.itemsets()) {
                    if (is.size() == 1) {
                        cntSingletonItemset++;
                    } else {
                        cntMultiItemItemset++;
                        recordHasMultiItem = true;
                    }
                }
                if (!recordHasMultiItem) {
                    allRecordsMultiItem = false;
                }

                // Repeated items across itemsets in the same sequence
                if (seq.size() > 1) {
                    Set<Item> seenItems = new HashSet<>();
                    boolean repeated = false;
                    for (Itemset is : seq.itemsets()) {
                        for (Item itm : is.items()) {
                            if (!seenItems.add(itm)) {
                                repeated = true;
                            }
                        }
                    }
                    if (repeated) {
                        cntRepeatedItemAcrossItemsets++;
                    }
                }

                // Check duplicate sequence records
                for (int r2 = r + 1; r2 < db.records().size(); r2++) {
                    if (seq.equals(db.records().get(r2).sequence())) {
                        hasDuplicateRecord = true;
                    }
                }
            }

            if (hasDuplicateRecord) {
                cntDuplicateSequenceRecords++;
            }
            if (db.size() == 1 && totalDbLength == 1) {
                cntSparseStructure++;
            }
            if (db.size() >= 2 && totalDbLength >= 5 && allRecordsMultiItem) {
                cntDenseStructure++;
            }

            double meanWeight = new ItemWeightCalculator()
                    .calculate(new ItemStatisticsCalculator().calculate(db))
                    .meanWeight().orElseThrow();

            double effectiveThreshold = resolver.resolve(minSup, db.size()).effectiveThreshold();
            assertTrue(Double.isFinite(effectiveThreshold), "Effective threshold must be finite");
            assertTrue(effectiveThreshold > 0.0, "Effective threshold must be strictly positive");

            PrefixTree tree = new PrefixTree();

            // 1. Frozen WSPM_PreTree baseline
            Map<Pattern, Integer> baselineMap = baselineMiner.mine(db, minSup).supports();
            completedBaselineRuns++;
            completedAlgorithmRuns++;
            totalBaselinePatterns += baselineMap.size();

            // 2. Independent brute-force oracle
            Map<Pattern, Integer> bruteForceMap = bruteForceOracle.mine(db, minSup);
            completedBruteForceRuns++;
            completedAlgorithmRuns++;
            totalBruteForcePatterns += bruteForceMap.size();

            // 3. WSPM_BitPrune
            Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(tree, db, minSup);
            completedBitPruneRuns++;
            completedAlgorithmRuns++;
            totalBitPrunePatterns += bitPruneMap.size();

            // PrefixTree postconditions
            assertTrue(tree.isFrozen(), "PrefixTree must be frozen after mining");
            Set<Pattern> nonRootTreePatterns = tree.preOrderPatterns().stream()
                    .filter(p -> !p.isEmpty())
                    .collect(Collectors.toSet());
            assertEquals(bitPruneMap.keySet(), nonRootTreePatterns,
                    "PrefixTree non-root identities must equal BitPrune map keys");

            // Pairwise comparison 1: baseline vs brute force
            boolean bfMatchesBaseline = baselineMap.equals(bruteForceMap);
            completedPairwiseComparisons++;

            // Pairwise comparison 2: baseline vs BitPrune
            boolean bpMatchesBaseline = baselineMap.equals(bitPruneMap);
            completedPairwiseComparisons++;

            // Pairwise comparison 3: brute force vs BitPrune
            boolean bpMatchesBf = bruteForceMap.equals(bitPruneMap);
            completedPairwiseComparisons++;

            if (!bfMatchesBaseline || !bpMatchesBaseline || !bpMatchesBf) {
                unexplainedMismatches++;
                Map<Pattern, Integer> expected = baselineMap;
                Map<Pattern, Integer> actual = !bpMatchesBaseline ? bitPruneMap : bruteForceMap;
                String diag = buildMismatchDiagnostic(
                        trial.trialIndex(), db, minSup, meanWeight, effectiveThreshold,
                        baselineMap, bruteForceMap, bitPruneMap, expected, actual, tree);
                fail(diag);
            }

            // Map content tracking
            if (bitPruneMap.isEmpty()) {
                cntEmptyResultMap++;
            } else {
                cntNonEmptyResultMap++;
            }

            for (var entryPattern : bitPruneMap.entrySet()) {
                Pattern p = entryPattern.getKey();
                int actualSupport = entryPattern.getValue();
                maxObservedPatternLength = Math.max(maxObservedPatternLength, p.length());
                if (p.itemsets().stream().anyMatch(is -> is.size() >= 2)) {
                    cntAcceptedIExtensionPattern++;
                }
                if (p.itemsets().size() >= 2) {
                    cntAcceptedSExtensionPattern++;
                }
                if (p.itemsets().size() >= 2) {
                    Set<Item> seen = new HashSet<>();
                    boolean repeated = false;
                    for (Itemset is : p.itemsets()) {
                        for (Item itm : is.items()) {
                            if (!seen.add(itm)) {
                                repeated = true;
                            }
                        }
                    }
                    if (repeated) {
                        cntAcceptedRepeatedItemPattern++;
                    }
                }

                // True Multiple-Embedding Witness (Section 2)
                int supportingRecordCount = 0;
                boolean hasMultiEmbeddingSid = false;
                int totalEmbeddingSum = 0;

                for (SequenceRecord rec : db.records()) {
                    int embeddingsInRec = countDistinctEmbeddings(p, rec.sequence(), 2);
                    if (embeddingsInRec >= 1) {
                        supportingRecordCount++;
                    }
                    if (embeddingsInRec >= 2) {
                        hasMultiEmbeddingSid = true;
                    }
                    totalEmbeddingSum += embeddingsInRec;
                }

                if (hasMultiEmbeddingSid) {
                    // 1. Pattern is in accepted map
                    // 2. At least one SID has at least two distinct embeddings
                    // 3. Supporting record count calculated
                    // 4. Assert that integer support equals supporting record count
                    assertEquals(supportingRecordCount, actualSupport,
                            "Accepted pattern support must equal supporting record count");
                    // 5. Demonstrate multi-embedding SID contributes 1 support unit, not 1 unit per embedding
                    assertTrue(actualSupport < totalEmbeddingSum,
                            "Support (" + actualSupport + ") must be strictly less than total embeddings (" + totalEmbeddingSum + ")");
                    cntMultipleEmbeddingsWithinOneSid++;
                }
            }
        }

        // Section 11 Summary output
        System.out.println("=== C11.4 FIXED-SEED RANDOMIZED EVIDENCE SUMMARY ===");
        System.out.println("Seed: " + FIXED_SEED);
        System.out.println("Completed trials: " + completedTrials);
        System.out.println("Absolute configurations: " + completedAbsoluteConfigs);
        System.out.println("Percent configurations: " + completedPercentConfigs);
        System.out.println("Baseline maps: " + completedBaselineRuns);
        System.out.println("Brute-force maps: " + completedBruteForceRuns);
        System.out.println("BitPrune maps: " + completedBitPruneRuns);
        System.out.println("Algorithm runs: " + completedAlgorithmRuns);
        System.out.println("Pairwise complete-map comparisons: " + completedPairwiseComparisons);
        System.out.println("Missing patterns: " + missingPatterns);
        System.out.println("Unexpected patterns: " + unexpectedPatterns);
        System.out.println("Support mismatches: " + supportMismatches);
        System.out.println("Unexplained mismatches: " + unexplainedMismatches);
        System.out.println("Total accepted baseline patterns: " + totalBaselinePatterns);
        System.out.println("Total accepted brute-force patterns: " + totalBruteForcePatterns);
        System.out.println("Total accepted BitPrune patterns: " + totalBitPrunePatterns);
        System.out.println("Maximum observed accepted pattern length: " + maxObservedPatternLength);
        System.out.println("Coverage counters: [abs=" + cntAbsoluteMode
                + ", pct=" + cntPercentMode
                + ", 1seq=" + cntOneSequenceDb
                + ", multiSeq=" + cntMultiSequenceDb
                + ", 1itemset=" + cntOneItemsetSeq
                + ", multiItemset=" + cntMultiItemsetSeq
                + ", singleton=" + cntSingletonItemset
                + ", multiItem=" + cntMultiItemItemset
                + ", repeatedItem=" + cntRepeatedItemAcrossItemsets
                + ", dupRecords=" + cntDuplicateSequenceRecords
                + ", sparse=" + cntSparseStructure
                + ", dense=" + cntDenseStructure
                + ", acceptedI=" + cntAcceptedIExtensionPattern
                + ", acceptedS=" + cntAcceptedSExtensionPattern
                + ", acceptedRepeat=" + cntAcceptedRepeatedItemPattern
                + ", multiEmbed=" + cntMultipleEmbeddingsWithinOneSid
                + ", nonEmpty=" + cntNonEmptyResultMap
                + ", empty=" + cntEmptyResultMap + "]");
        System.out.println("======================================================");

        // Required assertions
        assertEquals(TRIAL_COUNT, completedTrials);
        assertEquals(TRIAL_COUNT, completedBaselineRuns);
        assertEquals(TRIAL_COUNT, completedBruteForceRuns);
        assertEquals(TRIAL_COUNT, completedBitPruneRuns);
        assertEquals(TRIAL_COUNT * 3, completedAlgorithmRuns);
        assertEquals(TRIAL_COUNT * 3, completedPairwiseComparisons);
        assertEquals(0, missingPatterns);
        assertEquals(0, unexpectedPatterns);
        assertEquals(0, supportMismatches);
        assertEquals(0, unexplainedMismatches);
        assertEquals(totalBaselinePatterns, totalBruteForcePatterns);
        assertEquals(totalBaselinePatterns, totalBitPrunePatterns);

        // All 18 coverage counters must be > 0
        assertTrue(cntAbsoluteMode > 0, "cntAbsoluteMode must be > 0");
        assertTrue(cntPercentMode > 0, "cntPercentMode must be > 0");
        assertTrue(cntOneSequenceDb > 0, "cntOneSequenceDb must be > 0");
        assertTrue(cntMultiSequenceDb > 0, "cntMultiSequenceDb must be > 0");
        assertTrue(cntOneItemsetSeq > 0, "cntOneItemsetSeq must be > 0");
        assertTrue(cntMultiItemsetSeq > 0, "cntMultiItemsetSeq must be > 0");
        assertTrue(cntSingletonItemset > 0, "cntSingletonItemset must be > 0");
        assertTrue(cntMultiItemItemset > 0, "cntMultiItemItemset must be > 0");
        assertTrue(cntRepeatedItemAcrossItemsets > 0, "cntRepeatedItemAcrossItemsets must be > 0");
        assertTrue(cntDuplicateSequenceRecords > 0, "cntDuplicateSequenceRecords must be > 0");
        assertTrue(cntSparseStructure > 0, "cntSparseStructure must be > 0");
        assertTrue(cntDenseStructure > 0, "cntDenseStructure must be > 0");
        assertTrue(cntAcceptedIExtensionPattern > 0, "cntAcceptedIExtensionPattern must be > 0");
        assertTrue(cntAcceptedSExtensionPattern > 0, "cntAcceptedSExtensionPattern must be > 0");
        assertTrue(cntAcceptedRepeatedItemPattern > 0, "cntAcceptedRepeatedItemPattern must be > 0");
        assertTrue(cntMultipleEmbeddingsWithinOneSid > 0, "cntMultipleEmbeddingsWithinOneSid must be > 0");
        assertTrue(cntNonEmptyResultMap > 0, "cntNonEmptyResultMap must be > 0");
        assertTrue(cntEmptyResultMap > 0, "cntEmptyResultMap must be > 0");
    }

    // =========================================================================
    // 3. Test 2: Deterministic Generation Test
    // =========================================================================

    @Test
    @DisplayName("fixed_seed_generation_is_deterministic")
    void fixed_seed_generation_is_deterministic() {
        List<Trial> run1 = generateTrials(FIXED_SEED, TRIAL_COUNT);
        List<Trial> run2 = generateTrials(FIXED_SEED, TRIAL_COUNT);

        assertEquals(run1.size(), run2.size());
        for (int i = 0; i < run1.size(); i++) {
            Trial t1 = run1.get(i);
            Trial t2 = run2.get(i);
            assertEquals(t1.trialIndex(), t2.trialIndex());
            assertEquals(t1.minimumSupport().mode(), t2.minimumSupport().mode());
            assertEquals(t1.minimumSupport().configuredValue(), t2.minimumSupport().configuredValue());
            assertEquals(t1.database().size(), t2.database().size());
            for (int r = 0; r < t1.database().size(); r++) {
                SequenceRecord r1 = t1.database().records().get(r);
                SequenceRecord r2 = t2.database().records().get(r);
                assertEquals(r1.sid(), r2.sid());
                assertEquals(r1.sequence(), r2.sequence());
            }
            assertEquals(serializeDatabase(t1.database()), serializeDatabase(t2.database()));
        }
    }

    // =========================================================================
    // 4. Test 3: Coverage of Required Structural and Numeric Families
    // =========================================================================

    @Test
    @DisplayName("randomized_catalogue_covers_required_structural_and_numeric_families")
    void randomized_catalogue_covers_required_structural_and_numeric_families() {
        List<Trial> trials = generateTrials(FIXED_SEED, TRIAL_COUNT);
        boolean hasAbs = trials.stream().anyMatch(t -> t.minimumSupport().mode() == MinimumSupportMode.ABSOLUTE);
        boolean hasPct = trials.stream().anyMatch(t -> t.minimumSupport().mode() == MinimumSupportMode.PERCENT);
        boolean has1Seq = trials.stream().anyMatch(t -> t.database().size() == 1);
        boolean hasMultiSeq = trials.stream().anyMatch(t -> t.database().size() > 1);
        boolean hasMultiItem = trials.stream().anyMatch(t -> t.database().records().stream()
                .anyMatch(r -> r.sequence().itemsets().stream().anyMatch(is -> is.size() > 1)));

        assertTrue(hasAbs);
        assertTrue(hasPct);
        assertTrue(has1Seq);
        assertTrue(hasMultiSeq);
        assertTrue(hasMultiItem);
    }

    // =========================================================================
    // 5. Test 4: Result Maps and Prefix Trees Postconditions
    // =========================================================================

    @Test
    @DisplayName("randomized_result_maps_and_prefix_trees_preserve_postconditions")
    void randomized_result_maps_and_prefix_trees_preserve_postconditions() {
        List<Trial> trials = generateTrials(FIXED_SEED, 20); // sample of 20 trials
        for (Trial trial : trials) {
            PrefixTree tree = new PrefixTree();
            Map<Pattern, Integer> map = bitPruneMiner.mineInto(tree, trial.database(), trial.minimumSupport());

            // Immutability
            assertThrows(UnsupportedOperationException.class, () -> map.put(Pattern.singleton(ITEM_1), 1));

            // Frozen tree
            assertTrue(tree.isFrozen());

            // 1:1 non-root tree nodes to map keys
            Set<Pattern> treeKeys = tree.preOrderPatterns().stream().filter(p -> !p.isEmpty())
                    .collect(Collectors.toSet());
            assertEquals(map.keySet(), treeKeys);

            // Repeatability and iteration order
            PrefixTree tree2 = new PrefixTree();
            Map<Pattern, Integer> map2 = bitPruneMiner.mineInto(tree2, trial.database(), trial.minimumSupport());
            assertEquals(map, map2);

            List<String> bitPruneIteration1 = map.entrySet().stream()
                    .map(e -> e.getKey().serialize() + "=" + e.getValue())
                    .toList();
            List<String> bitPruneIteration2 = map2.entrySet().stream()
                    .map(e -> e.getKey().serialize() + "=" + e.getValue())
                    .toList();
            assertEquals(bitPruneIteration1, bitPruneIteration2,
                    "BitPrune result map iteration order must be deterministic and identical");

            Map<Pattern, Integer> base1 = baselineMiner.mine(trial.database(), trial.minimumSupport()).supports();
            Map<Pattern, Integer> base2 = baselineMiner.mine(trial.database(), trial.minimumSupport()).supports();
            List<String> baseIteration1 = base1.entrySet().stream()
                    .map(e -> e.getKey().serialize() + "=" + e.getValue())
                    .toList();
            List<String> baseIteration2 = base2.entrySet().stream()
                    .map(e -> e.getKey().serialize() + "=" + e.getValue())
                    .toList();
            assertEquals(baseIteration1, baseIteration2,
                    "Baseline result map iteration order must be deterministic and identical");

            List<String> treePreOrder1 = tree.preOrderPatterns().stream().map(Pattern::serialize).toList();
            List<String> treePreOrder2 = tree2.preOrderPatterns().stream().map(Pattern::serialize).toList();
            assertEquals(treePreOrder1, treePreOrder2, "PrefixTree pre-order traversal must be identical");
        }
    }

    // =========================================================================
    // 6. Fail-Fast Diagnostics Builder (Section 10)
    // =========================================================================

    static String buildMismatchDiagnostic(
            int trialIndex,
            SequenceDatabase database,
            MinimumSupport minimumSupport,
            double meanWeight,
            double effectiveThreshold,
            Map<Pattern, Integer> baselineMap,
            Map<Pattern, Integer> bruteForceMap,
            Map<Pattern, Integer> bitPruneMap,
            Map<Pattern, Integer> expectedMap,
            Map<Pattern, Integer> actualMap,
            PrefixTree prefixTree) {

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
        sb.append("=== FIXED-SEED RANDOMIZED DIFFERENTIAL MISMATCH ===").append(System.lineSeparator());
        sb.append("task ID: C11.4").append(System.lineSeparator());
        sb.append("fixed seed: ").append(FIXED_SEED).append(System.lineSeparator());
        sb.append("trial index: ").append(trialIndex).append(System.lineSeparator());
        sb.append("complete serialized generated database: ").append(serializeDatabase(database))
                .append(System.lineSeparator());
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
        sb.append("sequence record order and SIDs: ")
                .append(database.records().stream().map(SequenceRecord::sid).toList())
                .append(System.lineSeparator());
        sb.append("minimum-support mode: ").append(minimumSupport.mode()).append(System.lineSeparator());
        sb.append("configured value: ").append(minimumSupport.configuredValue()).append(System.lineSeparator());
        sb.append("resolved effective threshold: ").append(effectiveThreshold).append(System.lineSeparator());
        sb.append("MeanW: ").append(meanWeight).append(System.lineSeparator());
        sb.append("baseline map: ").append(formatMap(baselineMap)).append(System.lineSeparator());
        sb.append("brute-force map: ").append(formatMap(bruteForceMap)).append(System.lineSeparator());
        sb.append("BitPrune map: ").append(formatMap(bitPruneMap)).append(System.lineSeparator());
        sb.append("missing patterns: ").append(missing.stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());
        sb.append("unexpected patterns: ").append(unexpected.stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());
        sb.append("first support mismatch: ").append(firstSupportMismatch).append(System.lineSeparator());
        sb.append("BitPrune PrefixTree pre-order: ")
                .append(prefixTree.preOrderPatterns().stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());

        return sb.toString();
    }

    private static String formatMap(Map<Pattern, Integer> map) {
        if (map == null) {
            return "null";
        }
        return map.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Pattern::serialize)))
                .map(e -> e.getKey().serialize() + "=" + e.getValue())
                .toList()
                .toString();
    }
}
