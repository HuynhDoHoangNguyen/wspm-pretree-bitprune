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
import java.util.Objects;
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
 * Task C11.3 — Bounded Exhaustive Differential Correctness.
 *
 * <p>
 * Constructs and executes a finite, deterministic, fully enumerated catalogue
 * of tiny sequence
 * databases over universe U = {1, 2}. Compares complete
 * {@code Map<Pattern, Integer>} outputs
 * across frozen WSPM_PreTree baseline, independent
 * BruteForceWeightedPatternOracle, and WSPM_BitPrune.
 * </p>
 */
class BitPruneBoundedExhaustiveDifferentialTest {

    private final WspmPreTreeMiner baselineMiner = new WspmPreTreeMiner();
    private final BruteForceWeightedPatternOracle bruteForceOracle = new BruteForceWeightedPatternOracle();
    private final BitPruneMiner bitPruneMiner = new BitPruneMiner();
    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    // Canonical item universe and itemsets: U = {1, 2}
    static final Item ITEM_1 = new Item(1);
    static final Item ITEM_2 = new Item(2);

    static final Itemset I0 = Itemset.of(ITEM_1);
    static final Itemset I1 = Itemset.of(ITEM_2);
    static final Itemset I2 = Itemset.of(ITEM_1, ITEM_2);

    static final List<Itemset> CANONICAL_ITEMSETS = List.of(I0, I1, I2);

    record CatalogueEntry(
            int databaseIndex,
            List<Integer> sequenceIndices,
            SequenceDatabase database) {
    }

    // =========================================================================
    // 1. Catalogue Generation (Section 7)
    // =========================================================================

    static List<Sequence> generateSequenceCatalogue() {
        List<Sequence> sequences = new ArrayList<>(12);
        // 1. All length-one sequences in canonical itemset order:
        for (Itemset itemset : CANONICAL_ITEMSETS) {
            sequences.add(Sequence.of(itemset));
        }
        // 2. All length-two sequences in lexicographic Cartesian-product order:
        for (Itemset first : CANONICAL_ITEMSETS) {
            for (Itemset second : CANONICAL_ITEMSETS) {
                sequences.add(Sequence.of(first, second));
            }
        }
        return List.copyOf(sequences);
    }

    static List<CatalogueEntry> generateDatabaseCatalogue(List<Sequence> sequences) {
        List<CatalogueEntry> databases = new ArrayList<>(156);
        int dbIndex = 0;
        // 1. All 12 one-sequence databases in sequence-catalogue order
        for (int i = 0; i < sequences.size(); i++) {
            Sequence seq = sequences.get(i);
            SequenceRecord record = new SequenceRecord(1, seq);
            SequenceDatabase db = new SequenceDatabase(List.of(record));
            databases.add(new CatalogueEntry(dbIndex++, List.of(i), db));
        }
        // 2. All 144 two-sequence ordered pairs in lexicographic Cartesian-product
        // order
        for (int i = 0; i < sequences.size(); i++) {
            Sequence seq1 = sequences.get(i);
            for (int j = 0; j < sequences.size(); j++) {
                Sequence seq2 = sequences.get(j);
                SequenceRecord rec1 = new SequenceRecord(1, seq1);
                SequenceRecord rec2 = new SequenceRecord(2, seq2);
                SequenceDatabase db = new SequenceDatabase(List.of(rec1, rec2));
                databases.add(new CatalogueEntry(dbIndex++, List.of(i, j), db));
            }
        }
        return List.copyOf(databases);
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

    // =========================================================================
    // 2. Main Test: Bounded Exhaustive Differential Equality (312 configurations)
    // =========================================================================

    @Test
    @DisplayName("bounded_exhaustive_three_oracle_complete_map_equality")
    void bounded_exhaustive_three_oracle_complete_map_equality() {
        List<Sequence> sequences = generateSequenceCatalogue();
        List<CatalogueEntry> databases = generateDatabaseCatalogue(sequences);

        assertEquals(12, sequences.size());
        assertEquals(156, databases.size());

        List<MinimumSupport> configs = List.of(
                MinimumSupport.absolute(0.25),
                MinimumSupport.percentage(25.0));

        int completedDatabases = 0;
        int completedConfigs = 0;
        int completedBaselineRuns = 0;
        int completedBruteForceRuns = 0;
        int completedBitPruneRuns = 0;
        int completedAlgorithmRuns = 0;
        int completedPairwiseComparisons = 0;

        int missingPatterns = 0;
        int unexpectedPatterns = 0;
        int supportMismatches = 0;
        int unexplainedMismatches = 0;

        // Coverage witness counters
        int witnessOneItemsetSeq = 0;
        int witnessTwoItemsetSeq = 0;
        int witnessMultiItemItemset = 0;
        int witnessRepeatedItemAcrossItemsets = 0;
        int witnessRepeatedDenseItemsets = 0;
        int witnessDuplicateSeqInDb = 0;
        int witnessSameItemsetPattern = 0;
        int witnessSequentialPattern = 0;
        int witnessIExtensionPattern = 0;
        int witnessSExtensionPattern = 0;
        int witnessMultipleEmbeddingsWithinOneSid = 0;
        int witnessSupportFromOneSid = 0;
        int witnessSupportFromTwoSids = 0;
        int witnessPositiveAbsoluteMode = 0;
        int witnessPositivePercentMode = 0;
        int witnessInclusiveWeightedThresholdEquality = 0;

        int maxObservedPatternLength = 0;
        int totalBaselinePatterns = 0;
        int totalBruteForcePatterns = 0;
        int totalBitPrunePatterns = 0;

        int globalConfigIndex = 0;

        for (CatalogueEntry entry : databases) {
            completedDatabases++;
            SequenceDatabase db = entry.database();

            // Track sequence structural witnesses
            for (SequenceRecord rec : db.records()) {
                Sequence seq = rec.sequence();
                if (seq.size() == 1) {
                    witnessOneItemsetSeq++;
                }
                if (seq.size() == 2) {
                    witnessTwoItemsetSeq++;
                }
                for (Itemset is : seq.itemsets()) {
                    if (is.size() > 1) {
                        witnessMultiItemItemset++;
                    }
                }
                if (seq.size() == 2) {
                    Itemset is1 = seq.itemsets().get(0);
                    Itemset is2 = seq.itemsets().get(1);
                    boolean hasSharedItem = is1.items().stream().anyMatch(is2.items()::contains);
                    if (hasSharedItem) {
                        witnessRepeatedItemAcrossItemsets++;
                        witnessMultipleEmbeddingsWithinOneSid++;
                    }
                    if (is1.equals(I2) && is2.equals(I2)) {
                        witnessRepeatedDenseItemsets++;
                    }
                }
            }
            if (db.size() == 2 && db.records().get(0).sequence().equals(db.records().get(1).sequence())) {
                witnessDuplicateSeqInDb++;
            }

            double meanWeight = new ItemWeightCalculator()
                    .calculate(new ItemStatisticsCalculator().calculate(db))
                    .meanWeight().orElseThrow();

            for (MinimumSupport minSup : configs) {
                int currentConfigIndex = globalConfigIndex++;
                completedConfigs++;

                if (minSup.mode() == MinimumSupportMode.ABSOLUTE) {
                    witnessPositiveAbsoluteMode++;
                } else if (minSup.mode() == MinimumSupportMode.PERCENT) {
                    witnessPositivePercentMode++;
                }

                double effectiveThreshold = resolver.resolve(minSup, db.size()).effectiveThreshold();
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

                // PrefixTree invariant checks
                assertTrue(tree.isFrozen(), "PrefixTree must be frozen after mining");
                Set<Pattern> nonRootTreePatterns = tree.preOrderPatterns().stream()
                        .filter(p -> !p.isEmpty())
                        .collect(Collectors.toSet());
                assertEquals(bitPruneMap.keySet(), nonRootTreePatterns,
                        "PrefixTree non-root identities must equal BitPrune map keys");

                // Check immutability
                assertThrows(UnsupportedOperationException.class,
                        () -> bitPruneMap.put(Pattern.singleton(ITEM_1), 1),
                        "BitPrune result map must be unmodifiable");

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
                            entry.databaseIndex(), currentConfigIndex, entry.sequenceIndices(), db,
                            minSup, meanWeight, effectiveThreshold,
                            baselineMap, bruteForceMap, bitPruneMap, expected, actual, tree);
                    fail(diag);
                }

                // Pattern-level witnesses and metrics
                for (var entryPattern : bitPruneMap.entrySet()) {
                    Pattern p = entryPattern.getKey();
                    int supp = entryPattern.getValue();
                    maxObservedPatternLength = Math.max(maxObservedPatternLength, p.length());

                    if (p.itemsets().size() == 1 && p.itemsets().get(0).size() >= 2) {
                        witnessSameItemsetPattern++;
                    }
                    if (p.itemsets().size() >= 2) {
                        witnessSequentialPattern++;
                    }
                    if (p.itemsets().stream().anyMatch(is -> is.size() >= 2)) {
                        witnessIExtensionPattern++;
                    }
                    if (p.itemsets().size() >= 2) {
                        witnessSExtensionPattern++;
                    }
                    if (supp == 1) {
                        witnessSupportFromOneSid++;
                    }
                    if (supp == 2) {
                        witnessSupportFromTwoSids++;
                    }
                    double weightedSupport = supp * meanWeight;
                    if (Double.compare(weightedSupport, effectiveThreshold) == 0) {
                        witnessInclusiveWeightedThresholdEquality++;
                    }
                }
            }
        }

        // Output Deterministic Evidence Summary
        System.out.println("=== C11.3 BOUNDED EXHAUSTIVE EVIDENCE SUMMARY ===");
        System.out.println("Item universe: 2");
        System.out.println("Canonical itemsets: 3");
        System.out.println("Sequences: " + sequences.size());
        System.out.println("Databases: " + completedDatabases);
        System.out.println("One-sequence databases: 12");
        System.out.println("Two-sequence databases: 144");
        System.out.println("Configurations: " + completedConfigs);
        System.out.println("Baseline maps: " + completedBaselineRuns);
        System.out.println("Brute-force maps: " + completedBruteForceRuns);
        System.out.println("BitPrune maps: " + completedBitPruneRuns);
        System.out.println("Algorithm runs: " + completedAlgorithmRuns);
        System.out.println("Pairwise complete-map comparisons: " + completedPairwiseComparisons);
        System.out.println("Missing patterns: " + missingPatterns);
        System.out.println("Unexpected patterns: " + unexpectedPatterns);
        System.out.println("Support mismatches: " + supportMismatches);
        System.out.println("Unexplained mismatches: " + unexplainedMismatches);
        System.out.println("Maximum observed accepted pattern length: " + maxObservedPatternLength);
        System.out.println("Total accepted baseline patterns across configurations: " + totalBaselinePatterns);
        System.out.println("Total accepted brute-force patterns across configurations: " + totalBruteForcePatterns);
        System.out.println("Total accepted BitPrune patterns across configurations: " + totalBitPrunePatterns);
        System.out.println("====================================================");

        // Required contract assertions
        assertEquals(156, completedDatabases);
        assertEquals(312, completedConfigs);
        assertEquals(312, completedBaselineRuns);
        assertEquals(312, completedBruteForceRuns);
        assertEquals(312, completedBitPruneRuns);
        assertEquals(936, completedAlgorithmRuns);
        assertEquals(936, completedPairwiseComparisons);
        assertEquals(0, missingPatterns);
        assertEquals(0, unexpectedPatterns);
        assertEquals(0, supportMismatches);
        assertEquals(0, unexplainedMismatches);
        assertEquals(totalBaselinePatterns, totalBruteForcePatterns);
        assertEquals(totalBaselinePatterns, totalBitPrunePatterns);

        // Assert all 16 coverage witnesses > 0
        assertTrue(witnessOneItemsetSeq > 0, "witnessOneItemsetSeq must be > 0");
        assertTrue(witnessTwoItemsetSeq > 0, "witnessTwoItemsetSeq must be > 0");
        assertTrue(witnessMultiItemItemset > 0, "witnessMultiItemItemset must be > 0");
        assertTrue(witnessRepeatedItemAcrossItemsets > 0, "witnessRepeatedItemAcrossItemsets must be > 0");
        assertTrue(witnessRepeatedDenseItemsets > 0, "witnessRepeatedDenseItemsets must be > 0");
        assertTrue(witnessDuplicateSeqInDb > 0, "witnessDuplicateSeqInDb must be > 0");
        assertTrue(witnessSameItemsetPattern > 0, "witnessSameItemsetPattern must be > 0");
        assertTrue(witnessSequentialPattern > 0, "witnessSequentialPattern must be > 0");
        assertTrue(witnessIExtensionPattern > 0, "witnessIExtensionPattern must be > 0");
        assertTrue(witnessSExtensionPattern > 0, "witnessSExtensionPattern must be > 0");
        assertTrue(witnessMultipleEmbeddingsWithinOneSid > 0, "witnessMultipleEmbeddingsWithinOneSid must be > 0");
        assertTrue(witnessSupportFromOneSid > 0, "witnessSupportFromOneSid must be > 0");
        assertTrue(witnessSupportFromTwoSids > 0, "witnessSupportFromTwoSids must be > 0");
        assertTrue(witnessPositiveAbsoluteMode > 0, "witnessPositiveAbsoluteMode must be > 0");
        assertTrue(witnessPositivePercentMode > 0, "witnessPositivePercentMode must be > 0");
        assertTrue(witnessInclusiveWeightedThresholdEquality > 0,
                "witnessInclusiveWeightedThresholdEquality must be > 0");
    }

    // =========================================================================
    // 3. Specific Required Named Tests (Section 11)
    // =========================================================================

    @Test
    @DisplayName("catalogue_has_exactly_three_itemsets")
    void catalogue_has_exactly_three_itemsets() {
        assertEquals(3, CANONICAL_ITEMSETS.size());
        assertEquals(I0, CANONICAL_ITEMSETS.get(0));
        assertEquals(I1, CANONICAL_ITEMSETS.get(1));
        assertEquals(I2, CANONICAL_ITEMSETS.get(2));

        Set<String> itemsetNames = CANONICAL_ITEMSETS.stream()
                .map(is -> is.items().stream().map(Item::id).map(String::valueOf).collect(Collectors.joining(" ")))
                .collect(Collectors.toSet());
        assertEquals(Set.of("1", "2", "1 2"), itemsetNames);
    }

    @Test
    @DisplayName("sequence_catalogue_has_exactly_twelve_unique_sequences")
    void sequence_catalogue_has_exactly_twelve_unique_sequences() {
        List<Sequence> sequences = generateSequenceCatalogue();
        assertEquals(12, sequences.size());

        Set<String> serialized = sequences.stream()
                .map(BitPruneBoundedExhaustiveDifferentialTest::serializeSequence)
                .collect(Collectors.toSet());
        assertEquals(12, serialized.size(), "All 12 sequences must have unique serializations");

        // First 3 are length-one
        for (int i = 0; i < 3; i++) {
            assertEquals(1, sequences.get(i).size());
        }
        // Remaining 9 are length-two
        for (int i = 3; i < 12; i++) {
            assertEquals(2, sequences.get(i).size());
        }
    }

    @Test
    @DisplayName("database_catalogue_has_exactly_156_unique_databases")
    void database_catalogue_has_exactly_156_unique_databases() {
        List<Sequence> sequences = generateSequenceCatalogue();
        List<CatalogueEntry> databases = generateDatabaseCatalogue(sequences);

        assertEquals(156, databases.size());

        long oneSeqDbs = databases.stream().filter(e -> e.database().size() == 1).count();
        long twoSeqDbs = databases.stream().filter(e -> e.database().size() == 2).count();
        assertEquals(12, oneSeqDbs);
        assertEquals(144, twoSeqDbs);

        Set<String> serializedDbs = databases.stream()
                .map(e -> serializeDatabase(e.database()))
                .collect(Collectors.toSet());
        assertEquals(156, serializedDbs.size(), "All 156 databases must be uniquely serialized");
    }

    @Test
    @DisplayName("every_generated_database_uses_contiguous_sids")
    void every_generated_database_uses_contiguous_sids() {
        List<Sequence> sequences = generateSequenceCatalogue();
        List<CatalogueEntry> databases = generateDatabaseCatalogue(sequences);

        for (CatalogueEntry entry : databases) {
            SequenceDatabase db = entry.database();
            for (int i = 0; i < db.size(); i++) {
                assertEquals(i + 1, db.records().get(i).sid(),
                        "Database " + entry.databaseIndex() + " record " + i + " must have contiguous SID 1..N");
            }
        }
    }

    @Test
    @DisplayName("bounded_catalogue_covers_i_and_s_pattern_identity")
    void bounded_catalogue_covers_i_and_s_pattern_identity() {
        // Pattern <(1 2)> vs Pattern <(1)(2)>
        Pattern iPattern = Pattern.of(Itemset.of(ITEM_1, ITEM_2));
        Pattern sPattern = Pattern.of(Itemset.of(ITEM_1), Itemset.of(ITEM_2));

        assertNotEquals(iPattern, sPattern);
        assertEquals("<(1 2)>", iPattern.serialize());
        assertEquals("<(1)(2)>", sPattern.serialize());

        // Both are validly discovered in a database containing S11: <(1 2)(1 2)>
        Sequence seqS11 = Sequence.of(I2, I2);
        SequenceDatabase db = new SequenceDatabase(List.of(new SequenceRecord(1, seqS11)));
        MinimumSupport minSup = MinimumSupport.absolute(0.25);

        Map<Pattern, Integer> map = bitPruneMiner.mineInto(new PrefixTree(), db, minSup);
        assertTrue(map.containsKey(iPattern), "Must discover same-itemset pattern <(1 2)>");
        assertTrue(map.containsKey(sPattern), "Must discover sequential pattern <(1)(2)>");
        assertEquals(1, map.get(iPattern));
        assertEquals(1, map.get(sPattern));
    }

    @Test
    @DisplayName("repeated_items_and_multiple_embeddings_count_once_per_sid")
    void repeated_items_and_multiple_embeddings_count_once_per_sid() {
        // In a database containing SID 1: <(1)(1)>, item 1 occurs twice within SID 1.
        // SPMF sequence support counts SID occurrence at most once per sequence.
        Sequence seq = Sequence.of(I0, I0);
        SequenceDatabase db = new SequenceDatabase(List.of(new SequenceRecord(1, seq)));
        MinimumSupport minSup = MinimumSupport.absolute(0.25);

        Map<Pattern, Integer> baselineMap = baselineMiner.mine(db, minSup).supports();
        Map<Pattern, Integer> bruteForceMap = bruteForceOracle.mine(db, minSup);
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(new PrefixTree(), db, minSup);

        Pattern p1 = Pattern.singleton(ITEM_1);
        Pattern p11 = Pattern.of(I0, I0);

        assertEquals(1, baselineMap.get(p1));
        assertEquals(1, bruteForceMap.get(p1));
        assertEquals(1, bitPruneMap.get(p1));

        assertEquals(1, baselineMap.get(p11));
        assertEquals(1, bruteForceMap.get(p11));
        assertEquals(1, bitPruneMap.get(p11));
    }

    @Test
    @DisplayName("exact_threshold_equality_is_retained")
    void exact_threshold_equality_is_retained() {
        // Database: SID 1: <(1)>, SID 2: <(2)>
        // Item 1: count=1, seqSupport=1, sumLen=1
        // Item 2: count=1, seqSupport=1, sumLen=1
        // Database size=2, totalDatabaseLength=2
        // MaxW = 1.0 / 2 = 0.5, MinW = 1.0 / (2 * 1) = 0.5 -> weight = 0.5 for both
        // MeanW = 0.500000000000
        // Percentage 25.0% on size 2 -> effective threshold = 0.500000000000
        // Support of pattern <(1)> is 1 -> weighted support = 1 * 0.50 = 0.500000000000
        // Exact equality: weightedSupport == effectiveThreshold
        // Inclusive condition: weightedSupport >= effectiveThreshold is true (delta ==
        // 0.0)
        SequenceDatabase db = new SequenceDatabase(List.of(
                new SequenceRecord(1, Sequence.of(I0)),
                new SequenceRecord(2, Sequence.of(I1))));
        MinimumSupport minSup = MinimumSupport.percentage(25.0);

        double meanWeight = new ItemWeightCalculator()
                .calculate(new ItemStatisticsCalculator().calculate(db))
                .meanWeight().orElseThrow();
        double effectiveThreshold = resolver.resolve(minSup, db.size()).effectiveThreshold();
        double weightedSupport = 1 * meanWeight;

        assertEquals(0, Double.compare(weightedSupport, effectiveThreshold),
                "weightedSupport must exactly equal effectiveThreshold with Double.compare == 0");

        Map<Pattern, Integer> baselineMap = baselineMiner.mine(db, minSup).supports();
        Map<Pattern, Integer> bruteForceMap = bruteForceOracle.mine(db, minSup);
        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(new PrefixTree(), db, minSup);

        Pattern p1 = Pattern.singleton(ITEM_1);
        Pattern p2 = Pattern.singleton(ITEM_2);

        assertTrue(baselineMap.containsKey(p1), "Baseline must retain pattern at exact threshold equality");
        assertTrue(bruteForceMap.containsKey(p1), "Brute force must retain pattern at exact threshold equality");
        assertTrue(bitPruneMap.containsKey(p1), "BitPrune must retain pattern at exact threshold equality");

        assertTrue(baselineMap.containsKey(p2));
        assertTrue(bruteForceMap.containsKey(p2));
        assertTrue(bitPruneMap.containsKey(p2));

        assertEquals(baselineMap, bruteForceMap);
        assertEquals(baselineMap, bitPruneMap);
    }

    @Test
    @DisplayName("catalogue_generation_is_deterministic")
    void catalogue_generation_is_deterministic() {
        List<Sequence> seq1 = generateSequenceCatalogue();
        List<Sequence> seq2 = generateSequenceCatalogue();
        assertEquals(seq1, seq2);

        List<CatalogueEntry> db1 = generateDatabaseCatalogue(seq1);
        List<CatalogueEntry> db2 = generateDatabaseCatalogue(seq2);
        assertEquals(db1.size(), db2.size());
        for (int i = 0; i < db1.size(); i++) {
            assertEquals(db1.get(i).databaseIndex(), db2.get(i).databaseIndex());
            assertEquals(db1.get(i).sequenceIndices(), db2.get(i).sequenceIndices());
            assertEquals(db1.get(i).database().size(), db2.get(i).database().size());
            for (int r = 0; r < db1.get(i).database().size(); r++) {
                assertEquals(db1.get(i).database().records().get(r).sid(),
                        db2.get(i).database().records().get(r).sid());
                assertEquals(db1.get(i).database().records().get(r).sequence(),
                        db2.get(i).database().records().get(r).sequence());
            }
        }
    }

    @Test
    @DisplayName("returned_maps_are_immutable")
    void returned_maps_are_immutable() {
        SequenceDatabase db = new SequenceDatabase(List.of(new SequenceRecord(1, Sequence.of(I0))));
        MinimumSupport minSup = MinimumSupport.absolute(0.25);

        Map<Pattern, Integer> bitPruneMap = bitPruneMiner.mineInto(new PrefixTree(), db, minSup);
        Map<Pattern, Integer> baselineMap = baselineMiner.mine(db, minSup).supports();

        assertThrows(UnsupportedOperationException.class, () -> bitPruneMap.put(Pattern.singleton(ITEM_1), 999));
        assertThrows(UnsupportedOperationException.class, () -> baselineMap.put(Pattern.singleton(ITEM_1), 999));
    }

    @Test
    @DisplayName("mismatch_diagnostic_contains_reproduction_fields")
    void mismatch_diagnostic_contains_reproduction_fields() {
        SequenceDatabase db = new SequenceDatabase(List.of(new SequenceRecord(1, Sequence.of(I0))));
        MinimumSupport minSup = MinimumSupport.absolute(0.25);

        String diag = buildMismatchDiagnostic(
                0, 1, List.of(0), db, minSup, 1.0, 0.25,
                Map.of(Pattern.singleton(ITEM_1), 1),
                Map.of(Pattern.singleton(ITEM_1), 1),
                Map.of(Pattern.singleton(ITEM_1), 1),
                Map.of(Pattern.singleton(ITEM_1), 1),
                Map.of(Pattern.singleton(ITEM_1), 1),
                new PrefixTree());

        assertTrue(diag.contains("Database catalogue index: 0"));
        assertTrue(diag.contains("Configuration index: 1"));
        assertTrue(diag.contains("Sequence-catalogue indices used by the database: [0]"));
        assertTrue(diag.contains("Raw database in SPMF-like form:"));
        assertTrue(diag.contains("SID and record order: [1]"));
        assertTrue(diag.contains("Minimum-support mode: ABSOLUTE"));
        assertTrue(diag.contains("Configured minimum support: 0.25"));
        assertTrue(diag.contains("Number of sequences: 1"));
        assertTrue(diag.contains("Effective threshold: 0.25"));
        assertTrue(diag.contains("MeanW: 1.0"));
        assertTrue(diag.contains("Baseline complete map:"));
        assertTrue(diag.contains("Brute-force complete map:"));
        assertTrue(diag.contains("BitPrune complete map:"));
        assertTrue(diag.contains("Missing patterns:"));
        assertTrue(diag.contains("Unexpected patterns:"));
        assertTrue(diag.contains("First support mismatch:"));
        assertTrue(diag.contains("BitPrune PrefixTree pre-order:"));
        assertTrue(diag
                .contains("Catalogue bounds: Universe={1,2}, Itemsets=3, Sequences=12, Databases=156, Configs=312"));
        assertTrue(diag.contains("Seed: N/A — deterministic bounded catalogue"));
    }

    // =========================================================================
    // 4. Fail-Fast Diagnostics Builder (Section 13)
    // =========================================================================

    static String buildMismatchDiagnostic(
            int dbIndex,
            int configIndex,
            List<Integer> seqIndices,
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
        sb.append("=== BOUNDED EXHAUSTIVE DIFFERENTIAL MISMATCH ===").append(System.lineSeparator());
        sb.append("Database catalogue index: ").append(dbIndex).append(System.lineSeparator());
        sb.append("Configuration index: ").append(configIndex).append(System.lineSeparator());
        sb.append("Sequence-catalogue indices used by the database: ").append(seqIndices)
                .append(System.lineSeparator());
        sb.append("Raw database in SPMF-like form:").append(System.lineSeparator());
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
        sb.append("SID and record order: ")
                .append(database.records().stream().map(SequenceRecord::sid).toList())
                .append(System.lineSeparator());
        sb.append("Minimum-support mode: ").append(minimumSupport.mode()).append(System.lineSeparator());
        sb.append("Configured minimum support: ").append(minimumSupport.configuredValue()).append(System.lineSeparator());
        sb.append("Number of sequences: ").append(database.size()).append(System.lineSeparator());
        sb.append("Effective threshold: ").append(effectiveThreshold).append(System.lineSeparator());
        sb.append("MeanW: ").append(meanWeight).append(System.lineSeparator());
        sb.append("Baseline complete map: ").append(formatMap(baselineMap)).append(System.lineSeparator());
        sb.append("Brute-force complete map: ").append(formatMap(bruteForceMap)).append(System.lineSeparator());
        sb.append("BitPrune complete map: ").append(formatMap(bitPruneMap)).append(System.lineSeparator());
        sb.append("Missing patterns: ").append(missing.stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());
        sb.append("Unexpected patterns: ").append(unexpected.stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());
        sb.append("First support mismatch: ").append(firstSupportMismatch).append(System.lineSeparator());
        sb.append("BitPrune PrefixTree pre-order: ")
                .append(prefixTree.preOrderPatterns().stream().map(Pattern::serialize).toList())
                .append(System.lineSeparator());
        sb.append("Catalogue bounds: Universe={1,2}, Itemsets=3, Sequences=12, Databases=156, Configs=312")
                .append(System.lineSeparator());
        sb.append("Seed: N/A — deterministic bounded catalogue").append(System.lineSeparator());

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
