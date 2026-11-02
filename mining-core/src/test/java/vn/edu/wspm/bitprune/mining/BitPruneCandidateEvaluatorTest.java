package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation.Outcome;
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
import vn.edu.wspm.tree.ExtensionType;
import vn.edu.wspm.tree.PrefixTree;

/**
 * Deterministic correctness and invariant tests for {@link BitPruneCandidateEvaluator} (C10.1).
 *
 * <p>
 * Covers all 22 required named tests from the C10.1 contract.
 * Expected values are derived directly from tiny raw sequence fixtures.
 * </p>
 */
class BitPruneCandidateEvaluatorTest {

    private BitPruneCandidateEvaluator evaluator;
    private MinimumSupportResolver resolver;
    private CmapBuilder cmapBuilder;

    private final Item item1 = new Item(1);
    private final Item item2 = new Item(2);
    private final Item item3 = new Item(3);

    @BeforeEach
    void setUp() {
        evaluator = new BitPruneCandidateEvaluator();
        resolver = new MinimumSupportResolver();
        cmapBuilder = new CmapBuilder();
    }

    // =========================================================================
    // Fixture Helpers
    // =========================================================================

    private static SequenceDatabase createDb(SequenceRecord... records) {
        return new SequenceDatabase(List.of(records));
    }

    private static SidBitmap buildItemBitmap(SequenceDatabase db, SidMapping mapping, Item item) {
        var builder = SidBitmap.builder(mapping);
        for (var rec : db.records()) {
            for (var is : rec.sequence().itemsets()) {
                if (is.contains(item)) {
                    builder.setSid(rec.sid());
                    break;
                }
            }
        }
        return builder.build();
    }

    // =========================================================================
    // Required Named Tests (1 to 22)
    // =========================================================================

    @Test
    @DisplayName("1. valid I-extension accepted")
    void test01_validIExtensionAccepted() {
        // S1: <(1 2)>, S2: <(1 2)>
        var s1 = new Sequence(List.of(Itemset.of(item1, item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)),
                ExtensionType.I_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(2, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(2, result.verificationResult().get().exactSupport());
        assertArrayEquals(new int[]{1, 2}, result.verificationResult().get().supportingSids());
    }

    @Test
    @DisplayName("2. valid S-extension accepted")
    void test02_validSExtensionAccepted() {
        // S1: <(1)(2)>, S2: <(1)(2)>
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(2, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(2, result.verificationResult().get().exactSupport());
        assertArrayEquals(new int[]{1, 2}, result.verificationResult().get().supportingSids());
    }

    @Test
    @DisplayName("3. repeated-item S-extension accepted")
    void test03_repeatedItemSExtensionAccepted() {
        // S1: <(1)(1)>, S2: <(1)(1)>
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item1);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item1)),
                ExtensionType.S_EXTENSION,
                item1);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(2, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(2, result.verificationResult().get().exactSupport());
        // Child endpoints are at position 2
        assertEquals(2, result.verificationResult().get().endpointStates().get(0).endpoints()[0]);
    }

    @Test
    @DisplayName("4. I-extension CMAP rejection at positive threshold")
    void test04_iExtensionCmapRejectionAtPositiveThreshold() {
        // S1: <(1)(2)>, S2: <(1)(2)> -> CMAP-I(1, 2) is false
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)),
                ExtensionType.I_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.CMAP_REJECTED, result.outcome());
        assertTrue(result.candidateBitmap().isEmpty());
        assertTrue(result.verificationResult().isEmpty());
    }

    @Test
    @DisplayName("5. S-extension CMAP rejection at positive threshold")
    void test05_sExtensionCmapRejectionAtPositiveThreshold() {
        // S1: <(2)(1)>, S2: <(2)(1)> -> CMAP-S(1, 2) is false
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{2}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.CMAP_REJECTED, result.outcome());
        assertTrue(result.candidateBitmap().isEmpty());
        assertTrue(result.verificationResult().isEmpty());
    }

    @Test
    @DisplayName("6. bitmap rejection after CMAP pass")
    void test06_bitmapRejectionAfterCmapPass() {
        // 4 sequences:
        // S1: <(1)(2)> (has 1 and 2, so CMAP-S(1, 2) is true)
        // S2: <(1)>    (only 1)
        // S3: <(2)>    (only 2)
        // S4: <(3)>    (neither)
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1)));
        var s3 = new Sequence(List.of(Itemset.of(item2)));
        var s4 = new Sequence(List.of(Itemset.of(item3)));
        var db = createDb(
                new SequenceRecord(1, s1),
                new SequenceRecord(2, s2),
                new SequenceRecord(3, s3),
                new SequenceRecord(4, s4));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2, item3);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1); // SIDs 1, 2 (cardinality 2)
        var extBitmap = buildItemBitmap(db, mapping, item2);    // SIDs 1, 3 (cardinality 2)
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        // candidateBitmap = {1} (cardinality 1).
        // minSup requires threshold 2.0. cardinality 1 * meanW 1.0 = 1.0 < 2.0 -> pruned!
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(2.0), 4);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.BITMAP_REJECTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(1, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isEmpty());
    }

    @Test
    @DisplayName("7. temporal false positive passes bitmap but is exact-rejected")
    void test07_temporalFalsePositivePassesBitmapButIsExactRejected() {
        // S1: <(1)(2)>, S2: <(2)(1)>
        // Both sequences contain items 1 and 2, so candidateBitmap has cardinality 2.
        // CMAP-S(1, 2) is true from S1.
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1); // SIDs 1, 2
        var extBitmap = buildItemBitmap(db, mapping, item2);    // SIDs 1, 2
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        // Threshold = 2.0. candidateBitmap cardinality = 2 -> upper bound 2.0 >= 2.0 (passes bitmap filter)
        // Exact verification: only S1 has 1 before 2 -> exactSupport = 1.
        // 1 * 1.0 < 2.0 -> EXACT_REJECTED.
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(2.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.EXACT_REJECTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(2, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(1, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("8. equality at bitmap bound is not pruned")
    void test08_equalityAtBitmapBoundIsNotPruned() {
        // S1: <(1)(2)>, S2: <(3)>
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item3)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2, item3);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1); // SID 1 (cardinality 1)
        var extBitmap = buildItemBitmap(db, mapping, item2);    // SID 1 (cardinality 1)
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        // Cardinality = 1. meanW = 1.0. threshold = 1.0.
        // Equality at bound: 1 * 1.0 == 1.0 -> must not be pruned!
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(1, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(1, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("9. exact equality is accepted")
    void test09_exactEqualityIsAccepted() {
        // S1: <(1)(2)>, S2: <(1)(2)>
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        // exactSupport = 2. meanWeight = 0.5. threshold = 1.0.
        // 2 * 0.5 = 1.0 == 1.0 (exact equality) -> ACCEPTED.
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 0.5;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertEquals(2, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("10. exact support below threshold is exact-rejected")
    void test10_exactSupportBelowThresholdIsExactRejected() {
        // S1: <(1)(2)>, S2: <(2)(1)>, S3: <(3)>
        // Candidate bitmap has SIDs {1, 2} (cardinality 2)
        // Upper bound: 2 * 0.5 = 1.0 >= 1.0 -> passes bitmap filter!
        // Exact verification: S1 matches, S2 fails temporal order -> exactSupport = 1.
        // Final condition: 1 * 0.5 = 0.5 < 1.0 -> EXACT_REJECTED.
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var s3 = new Sequence(List.of(Itemset.of(item3)));
        var db = createDb(
                new SequenceRecord(1, s1),
                new SequenceRecord(2, s2),
                new SequenceRecord(3, s3));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2, item3);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1); // {1, 2}
        var extBitmap = buildItemBitmap(db, mapping, item2);    // {1, 2}
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 3);
        double globalMeanWeight = 0.5;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.EXACT_REJECTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(1, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("11. zero threshold bypasses false CMAP")
    void test11_zeroThresholdBypassesFalseCmap() {
        // S1: <(2)(1)>, S2: <(2)(1)> -> CMAP-S(1, 2) is false
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        assertFalse(cmapS.contains(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{2}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(0.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        // Does NOT return CMAP_REJECTED; reaches exact verification and gets ACCEPTED because threshold is 0.0
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertTrue(result.verificationResult().isPresent());
    }

    @Test
    @DisplayName("12. zero threshold bypasses zero-cardinality bitmap pruning")
    void test12_zeroThresholdBypassesZeroCardinalityBitmapPruning() {
        // S1: <(1)>, S2: <(1)> -> item 2 does not appear
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2); // empty bitmap, cardinality 0
        assertEquals(0, extBitmap.cardinality());

        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(0.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        // Does NOT return BITMAP_REJECTED
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.candidateBitmap().isPresent());
        assertEquals(0, result.candidateBitmap().get().cardinality());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(0, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("13. zero-support zero-threshold candidate reaches exact verification")
    void test13_zeroSupportZeroThresholdCandidateReachesExactVerification() {
        // S1: <(2)(1)>, S2: <(2)(1)> -> pattern <(1)(2)> has support 0
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{2}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(0.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(0, result.verificationResult().get().exactSupport());
    }

    @Test
    @DisplayName("14. negative/noncontiguous actual SIDs")
    void test14_negativeNoncontiguousActualSids() {
        // SIDs: -100, 0, 42
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s3 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var db = createDb(
                new SequenceRecord(-100, s1),
                new SequenceRecord(0, s2),
                new SequenceRecord(42, s3));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2, item3);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.of(Itemset.of(item1, item2));
        var parentBitmap = buildItemBitmap(db, mapping, item1).and(buildItemBitmap(db, mapping, item2));
        var extBitmap = buildItemBitmap(db, mapping, item3);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(-100, new int[]{1}),
                PatternEndpoints.of(0, new int[]{1}),
                PatternEndpoints.of(42, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2), Itemset.of(item3)),
                ExtensionType.S_EXTENSION,
                item3);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(2.0), 3);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertTrue(result.verificationResult().isPresent());
        assertEquals(3, result.verificationResult().get().exactSupport());
        assertArrayEquals(new int[]{-100, 0, 42}, result.verificationResult().get().supportingSids());
    }

    @Test
    @DisplayName("15. candidate bitmap contains every exact supporting SID")
    void test15_candidateBitmapContainsEveryExactSupportingSid() {
        // Standard check: verify that every verified SID is in candidate bitmap
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 1.0;

        BitPruneCandidateEvaluation result = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        var candidateBm = result.candidateBitmap().orElseThrow();
        var verif = result.verificationResult().orElseThrow();
        for (int sid : verif.supportingSids()) {
            assertTrue(candidateBm.containsSid(sid));
        }

        // Now test the assertion failure: if extensionItemBitmap artificially lacks SID 1,
        // then candidateBitmap will lack SID 1, but verifier will find it -> IllegalStateException!
        var corruptedExtBitmap = SidBitmap.builder(mapping).setSid(2).build(); // lacks SID 1
        assertThrows(IllegalStateException.class, () ->
                evaluator.evaluate(
                        parent, candidate, parentBitmap, corruptedExtBitmap, parentVerif,
                        cmapI, cmapS, occIndex, globalMeanWeight, minSup));
    }

    @Test
    @DisplayName("16. malformed candidate fails before tier routing")
    void test16_malformedCandidateFailsBeforeTierRouting() {
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2, item3);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{2})));
        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        // Malformed candidate 1: candidate pattern does not match expected S-extension of parent
        var mismatchedPatternCandidate = new BitPruneCandidateResult(
                Pattern.singleton(item3), // Expected <(1)(2)>
                ExtensionType.S_EXTENSION,
                item2);

        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, mismatchedPatternCandidate, parentBitmap, extBitmap, parentVerif,
                        cmapI, cmapS, occIndex, 1.0, minSup));

        // Malformed candidate 2: I-extension item <= parent last item (1 <= 1)
        var invalidIExtCandidate = new BitPruneCandidateResult(
                Pattern.singleton(item1),
                ExtensionType.I_EXTENSION,
                item1);

        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, invalidIExtCandidate, parentBitmap, extBitmap, parentVerif,
                        cmapI, cmapS, occIndex, 1.0, minSup));

        // Malformed candidate 3: empty parent
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        Pattern.empty(), mismatchedPatternCandidate, parentBitmap, extBitmap, parentVerif,
                        cmapI, cmapS, occIndex, 1.0, minSup));
    }

    @Test
    @DisplayName("17. mismatched SID universes fail fast")
    void test17_mismatchedSidUniversesFailFast() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item2)));
        var db1 = createDb(new SequenceRecord(1, s1));
        var db2 = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));

        var mapping1 = SidMapping.of(db1);
        var mapping2 = SidMapping.of(db2);
        var occIndex = VerticalOccurrenceIndex.build(db1, mapping1);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db1, f1);
        var cmapS = cmapBuilder.buildS(db1, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db1, mapping1, item1);
        var extBitmapDiffMapping = buildItemBitmap(db2, mapping2, item2);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup1 = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        // Different mapping between parent and extension bitmap
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, candidate, parentBitmap, extBitmapDiffMapping, parentVerif,
                        cmapI, cmapS, occIndex, 1.0, minSup1));

        // Universe size does not match minSup.numberOfSequences()
        ResolvedMinimumSupport minSup2 = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        var extBitmapSameMapping = buildItemBitmap(db1, mapping1, item2);
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, candidate, parentBitmap, extBitmapSameMapping, parentVerif,
                        cmapI, cmapS, occIndex, 1.0, minSup2));
    }

    @Test
    @DisplayName("18. parent verification SID outside parent bitmap fails fast")
    void test18_parentVerificationSidOutsideParentBitmapFailsFast() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1), new SequenceRecord(2, s2));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        // parentBitmap only has SID 1 set
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = buildItemBitmap(db, mapping, item2);

        // parentVerif has SID 2, which is not set in parentBitmap
        var parentVerifUnsetBit = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, candidate, parentBitmap, extBitmap, parentVerifUnsetBit,
                        cmapI, cmapS, occIndex, 1.0, minSup));

        // parentVerif has SID 999 which does not exist in bitmap universe
        var parentVerifUnknownSid = VerificationResult.of(List.of(
                PatternEndpoints.of(999, new int[]{1})));

        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(
                        parent, candidate, parentBitmap, extBitmap, parentVerifUnknownSid,
                        cmapI, cmapS, occIndex, 1.0, minSup));
    }

    @Test
    @DisplayName("19. null validation")
    void test19_nullValidation() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = createDb(new SequenceRecord(1, s1));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                ExtensionType.S_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);
        double globalMeanWeight = 1.0;

        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(null, candidate, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, null, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, null, extBitmap, parentVerif, cmapI, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, null, parentVerif, cmapI, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, null, cmapI, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, null, cmapS, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, cmapI, null, occIndex, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, null, globalMeanWeight, minSup));
        assertThrows(NullPointerException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, occIndex, globalMeanWeight, null));

        // Non-finite or negative globalMeanWeight
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, occIndex, Double.NaN, minSup));
        assertThrows(IllegalArgumentException.class, () ->
                evaluator.evaluate(parent, candidate, parentBitmap, extBitmap, parentVerif, cmapI, cmapS, occIndex, -0.1, minSup));

        // Result null checks
        assertThrows(NullPointerException.class, () ->
                new BitPruneCandidateEvaluation(null, Outcome.ACCEPTED, Optional.of(parentBitmap), Optional.of(parentVerif)));
        assertThrows(NullPointerException.class, () ->
                new BitPruneCandidateEvaluation(candidate, null, Optional.of(parentBitmap), Optional.of(parentVerif)));
        assertThrows(NullPointerException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.ACCEPTED, null, Optional.of(parentVerif)));
        assertThrows(NullPointerException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.ACCEPTED, Optional.of(parentBitmap), null));
    }

    @Test
    @DisplayName("20. all inputs remain unchanged")
    void test20_allInputsRemainUnchanged() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = createDb(new SequenceRecord(1, s1));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)),
                ExtensionType.I_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        // Snapshots before
        String parentStr = parent.serialize();
        int parentBmCard = parentBitmap.cardinality();
        int extBmCard = extBitmap.cardinality();
        int parentVerifSupport = parentVerif.exactSupport();
        int[] parentEndpoints = parentVerif.endpointStates().get(0).endpoints().clone();

        evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        // Assert unchanged
        assertEquals(parentStr, parent.serialize());
        assertEquals(parentBmCard, parentBitmap.cardinality());
        assertEquals(extBmCard, extBitmap.cardinality());
        assertEquals(parentVerifSupport, parentVerif.exactSupport());
        assertArrayEquals(parentEndpoints, parentVerif.endpointStates().get(0).endpoints());
    }

    @Test
    @DisplayName("21. result invariant rejects illegal Optional/outcome combinations")
    void test21_resultInvariantRejectsIllegalOptionalOutcomeCombinations() {
        var candidate = new BitPruneCandidateResult(
                Pattern.singleton(item1),
                ExtensionType.S_EXTENSION,
                item1);
        var mapping = SidMapping.of(createDb(new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1))))));
        var bitmap = SidBitmap.empty(mapping);
        var verif = VerificationResult.of(List.of());

        // CMAP_REJECTED requires absent bitmap and absent verification
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.CMAP_REJECTED, Optional.of(bitmap), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.CMAP_REJECTED, Optional.empty(), Optional.of(verif)));

        // BITMAP_REJECTED requires present bitmap and absent verification
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.BITMAP_REJECTED, Optional.empty(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.BITMAP_REJECTED, Optional.of(bitmap), Optional.of(verif)));

        // EXACT_REJECTED requires present bitmap and present verification
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.EXACT_REJECTED, Optional.empty(), Optional.of(verif)));
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.EXACT_REJECTED, Optional.of(bitmap), Optional.empty()));

        // ACCEPTED requires present bitmap and present verification
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.ACCEPTED, Optional.empty(), Optional.of(verif)));
        assertThrows(IllegalArgumentException.class, () ->
                new BitPruneCandidateEvaluation(candidate, Outcome.ACCEPTED, Optional.of(bitmap), Optional.empty()));

        // Valid combinations succeed
        assertNotNull(BitPruneCandidateEvaluation.cmapRejected(candidate));
        assertNotNull(BitPruneCandidateEvaluation.bitmapRejected(candidate, bitmap));
        assertNotNull(BitPruneCandidateEvaluation.exactRejected(candidate, bitmap, verif));
        assertNotNull(BitPruneCandidateEvaluation.accepted(candidate, bitmap, verif));
    }

    @Test
    @DisplayName("22. evaluator performs no PrefixTree mutation")
    void test22_evaluatorPerformsNoPrefixTreeMutation() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = createDb(new SequenceRecord(1, s1));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var f1 = List.of(item1, item2);
        var cmapI = cmapBuilder.buildI(db, f1);
        var cmapS = cmapBuilder.buildS(db, f1);

        var parent = Pattern.singleton(item1);
        var parentBitmap = buildItemBitmap(db, mapping, item1);
        var extBitmap = buildItemBitmap(db, mapping, item2);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)),
                ExtensionType.I_EXTENSION,
                item2);

        ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        PrefixTree tree = new PrefixTree();
        int initialSize = tree.nodeCount();
        var root = tree.root();

        evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        assertEquals(initialSize, tree.nodeCount());
        assertEquals(root, tree.root());
        assertTrue(root.children().isEmpty());
    }
}
