package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
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

/**
 * Independent bounded-exhaustive and fixed-seed randomized differential evidence
 * for {@link BitPruneCandidateEvaluator} (C10.2).
 *
 * <p>
 * Evaluates candidate routing against an independent direct oracle that derives
 * expected results strictly from raw sequence data without calling production
 * evaluation or filtering helpers.
 * </p>
 */
class BitPruneCandidateEvaluatorDifferentialTest {

    private static final long FIXED_SEED = 20261103L;
    private static final int RANDOM_TRIALS = 1500;

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
    // 1. Independent Direct Oracle (Section 6)
    // =========================================================================

    /** Direct CMAP-I: true iff at least one raw itemset contains both items. */
    private static boolean directCmapI(SequenceDatabase db, Item a, Item x) {
        for (var record : db.records()) {
            for (var itemset : record.sequence().itemsets()) {
                if (itemset.contains(a) && itemset.contains(x)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Direct CMAP-S: true iff at least one raw sequence contains a at t and x at u with t < u. */
    private static boolean directCmapS(SequenceDatabase db, Item a, Item x) {
        for (var record : db.records()) {
            var itemsets = record.sequence().itemsets();
            for (int t = 0; t < itemsets.size(); t++) {
                if (itemsets.get(t).contains(a)) {
                    for (int u = t + 1; u < itemsets.size(); u++) {
                        if (itemsets.get(u).contains(x)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Direct parent upper-bound SID set: SIDs containing every distinct item of parent. */
    private static Set<Integer> directParentBitmapSids(SequenceDatabase db, Pattern parent) {
        Set<Item> parentItems = new HashSet<>();
        for (var is : parent.itemsets()) {
            parentItems.addAll(is.items());
        }
        Set<Integer> sids = new TreeSet<>();
        for (var record : db.records()) {
            Set<Item> seqItems = new HashSet<>();
            for (var is : record.sequence().itemsets()) {
                seqItems.addAll(is.items());
            }
            if (seqItems.containsAll(parentItems)) {
                sids.add(record.sid());
            }
        }
        return sids;
    }

    /** Direct extension item SID set: SIDs containing the extension item. */
    private static Set<Integer> directItemBitmapSids(SequenceDatabase db, Item item) {
        Set<Integer> sids = new TreeSet<>();
        for (var record : db.records()) {
            for (var is : record.sequence().itemsets()) {
                if (is.contains(item)) {
                    sids.add(record.sid());
                    break;
                }
            }
        }
        return sids;
    }

    /** Direct feasible ending positions of parent pattern in a single sequence (1-based). */
    private static int[] directParentFeasibleEndpoints(Sequence seq, Pattern pattern) {
        if (pattern.isEmpty()) {
            return new int[0];
        }
        List<Integer> currentEndpoints = new ArrayList<>();
        var p0 = pattern.itemsets().get(0);
        for (int pos = 1; pos <= seq.itemsets().size(); pos++) {
            if (seq.itemsets().get(pos - 1).items().containsAll(p0.items())) {
                currentEndpoints.add(pos);
            }
        }
        if (currentEndpoints.isEmpty()) {
            return new int[0];
        }
        for (int m = 1; m < pattern.itemsets().size(); m++) {
            var pm = pattern.itemsets().get(m);
            List<Integer> nextEndpoints = new ArrayList<>();
            int minPrev = currentEndpoints.stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
            for (int pos = minPrev + 1; pos <= seq.itemsets().size(); pos++) {
                if (seq.itemsets().get(pos - 1).items().containsAll(pm.items())) {
                    nextEndpoints.add(pos);
                }
            }
            currentEndpoints = nextEndpoints;
            if (currentEndpoints.isEmpty()) {
                return new int[0];
            }
        }
        return currentEndpoints.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
    }

    /** Direct child endpoints from parent endpoints and extension. */
    private static int[] directChildEndpoints(
            Sequence seq, int[] parentEndpoints, ExtensionType type, Item extItem) {
        if (parentEndpoints.length == 0) {
            return new int[0];
        }
        List<Integer> childList = new ArrayList<>();
        switch (type) {
            case I_EXTENSION -> {
                for (int e : parentEndpoints) {
                    if (seq.itemsets().get(e - 1).contains(extItem)) {
                        childList.add(e);
                    }
                }
            }
            case S_EXTENSION -> {
                int minE = Arrays.stream(parentEndpoints).min().orElse(Integer.MAX_VALUE);
                for (int pos = minE + 1; pos <= seq.itemsets().size(); pos++) {
                    if (seq.itemsets().get(pos - 1).contains(extItem)) {
                        childList.add(pos);
                    }
                }
            }
        }
        return childList.stream().distinct().sorted().mapToInt(Integer::intValue).toArray();
    }

    /** Immutable container for the direct oracle evaluation output. */
    private record DirectEvaluationResult(
            Outcome outcome,
            Set<Integer> candidateBitmapSids,
            Set<Integer> supportingSids,
            Map<Integer, int[]> endpointsMap,
            boolean directCmap,
            boolean isBitmapFalsePositive) {
    }

    /** Independent direct evaluation oracle. */
    private static DirectEvaluationResult directEvaluate(
            SequenceDatabase db,
            Pattern parent,
            BitPruneCandidateResult candidate,
            double globalMeanWeight,
            double effectiveThreshold) {

        Item parentLastItem = parent.lastItem().orElseThrow();
        boolean directCmap = switch (candidate.extensionType()) {
            case I_EXTENSION -> directCmapI(db, parentLastItem, candidate.extensionItem());
            case S_EXTENSION -> directCmapS(db, parentLastItem, candidate.extensionItem());
        };

        Set<Integer> parentBmSids = directParentBitmapSids(db, parent);
        Set<Integer> extBmSids = directItemBitmapSids(db, candidate.extensionItem());
        Set<Integer> candidateBmSids = new TreeSet<>(parentBmSids);
        candidateBmSids.retainAll(extBmSids);
        int cardinality = candidateBmSids.size();

        Outcome outcome;
        Set<Integer> supportingSids = new TreeSet<>();
        Map<Integer, int[]> endpointsMap = new HashMap<>();

        if (effectiveThreshold > 0.0 && !directCmap) {
            outcome = Outcome.CMAP_REJECTED;
        } else if (effectiveThreshold > 0.0 && (cardinality * globalMeanWeight < effectiveThreshold)) {
            outcome = Outcome.BITMAP_REJECTED;
        } else {
            for (var record : db.records()) {
                int[] pEndpoints = directParentFeasibleEndpoints(record.sequence(), parent);
                if (pEndpoints.length > 0) {
                    int[] cEndpoints = directChildEndpoints(
                            record.sequence(), pEndpoints, candidate.extensionType(), candidate.extensionItem());
                    if (cEndpoints.length > 0) {
                        supportingSids.add(record.sid());
                        endpointsMap.put(record.sid(), cEndpoints);
                    }
                }
            }
            int exactSupport = supportingSids.size();
            boolean meets;
            if (effectiveThreshold == 0.0) {
                meets = (exactSupport * globalMeanWeight >= 0.0);
            } else {
                meets = (exactSupport * globalMeanWeight >= effectiveThreshold);
            }
            outcome = meets ? Outcome.ACCEPTED : Outcome.EXACT_REJECTED;
        }

        boolean isBitmapFalsePositive = false;
        if (outcome == Outcome.EXACT_REJECTED && (cardinality * globalMeanWeight >= effectiveThreshold)) {
            isBitmapFalsePositive = true;
        }

        return new DirectEvaluationResult(
                outcome, candidateBmSids, supportingSids, endpointsMap, directCmap, isBitmapFalsePositive);
    }

    // =========================================================================
    // 2. Mismatch Diagnostic Helper (Section 10)
    // =========================================================================

    private static void assertMatch(
            String mode,
            String contextInfo,
            SequenceDatabase db,
            Pattern parent,
            List<PatternEndpoints> parentEndpoints,
            BitPruneCandidateResult candidate,
            double globalMeanWeight,
            ResolvedMinimumSupport minSup,
            DirectEvaluationResult expected,
            BitPruneCandidateEvaluation actual,
            Map<String, Integer> coverageCounters) {

        String mismatchField = null;
        String mismatchDetails = null;

        if (expected.outcome() != actual.outcome()) {
            mismatchField = "outcome";
            mismatchDetails = "expected=" + expected.outcome() + ", actual=" + actual.outcome();
        } else if (expected.outcome() == Outcome.CMAP_REJECTED) {
            if (actual.candidateBitmap().isPresent()) {
                mismatchField = "candidateBitmap.isPresent";
                mismatchDetails = "expected absent, but was present";
            } else if (actual.verificationResult().isPresent()) {
                mismatchField = "verificationResult.isPresent";
                mismatchDetails = "expected absent, but was present";
            }
        } else if (expected.outcome() == Outcome.BITMAP_REJECTED) {
            if (actual.candidateBitmap().isEmpty()) {
                mismatchField = "candidateBitmap.isEmpty";
                mismatchDetails = "expected present, but was empty";
            } else if (actual.verificationResult().isPresent()) {
                mismatchField = "verificationResult.isPresent";
                mismatchDetails = "expected absent, but was present";
            } else {
                Set<Integer> actualBmSids = new TreeSet<>();
                for (int sid : actual.candidateBitmap().get().toSupportingSids()) {
                    actualBmSids.add(sid);
                }
                if (!expected.candidateBitmapSids().equals(actualBmSids)) {
                    mismatchField = "candidateBitmap.sids";
                    mismatchDetails = "expected=" + expected.candidateBitmapSids() + ", actual=" + actualBmSids;
                }
            }
        } else {
            // EXACT_REJECTED or ACCEPTED
            if (actual.candidateBitmap().isEmpty()) {
                mismatchField = "candidateBitmap.isEmpty";
                mismatchDetails = "expected present, but was empty";
            } else if (actual.verificationResult().isEmpty()) {
                mismatchField = "verificationResult.isEmpty";
                mismatchDetails = "expected present, but was empty";
            } else {
                Set<Integer> actualBmSids = new TreeSet<>();
                for (int sid : actual.candidateBitmap().get().toSupportingSids()) {
                    actualBmSids.add(sid);
                }
                if (!expected.candidateBitmapSids().equals(actualBmSids)) {
                    mismatchField = "candidateBitmap.sids";
                    mismatchDetails = "expected=" + expected.candidateBitmapSids() + ", actual=" + actualBmSids;
                } else {
                    VerificationResult vr = actual.verificationResult().get();
                    Set<Integer> actualSuppSids = new TreeSet<>();
                    for (int sid : vr.supportingSids()) {
                        actualSuppSids.add(sid);
                    }
                    if (!expected.supportingSids().equals(actualSuppSids)) {
                        mismatchField = "verificationResult.supportingSids";
                        mismatchDetails = "expected=" + expected.supportingSids() + ", actual=" + actualSuppSids;
                    } else if (expected.supportingSids().size() != vr.exactSupport()) {
                        mismatchField = "verificationResult.exactSupport";
                        mismatchDetails = "expected=" + expected.supportingSids().size() + ", actual=" + vr.exactSupport();
                    } else {
                        for (PatternEndpoints actualEp : vr.endpointStates()) {
                            int sid = actualEp.actualSid();
                            int[] expEndpoints = expected.endpointsMap().get(sid);
                            if (!Arrays.equals(expEndpoints, actualEp.endpoints())) {
                                mismatchField = "verificationResult.endpoints[SID=" + sid + "]";
                                mismatchDetails = "expected=" + Arrays.toString(expEndpoints)
                                        + ", actual=" + Arrays.toString(actualEp.endpoints());
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (mismatchField != null) {
            var sb = new StringBuilder();
            sb.append("\n================ DIFFERENTIAL MISMATCH DETECTED ================\n");
            sb.append("Evidence Mode: ").append(mode).append("\n");
            sb.append("Context Info: ").append(contextInfo).append("\n");
            sb.append("Raw Database Records:\n");
            for (var rec : db.records()) {
                sb.append("  SID ").append(rec.sid()).append(": ").append(rec.sequence().itemsets()).append("\n");
            }
            sb.append("Parent Pattern: ").append(parent.serialize()).append("\n");
            sb.append("Parent Endpoints: ").append(parentEndpoints).append("\n");
            sb.append("Candidate Extension: ").append(candidate.extensionType())
                    .append(" with Item ").append(candidate.extensionItem().id()).append("\n");
            sb.append("Candidate Pattern: ").append(candidate.pattern().serialize()).append("\n");
            sb.append("Global MeanW: ").append(globalMeanWeight).append("\n");
            sb.append("Configured Threshold: ").append(minSup.configuredValue())
                    .append(" (mode=").append(minSup.mode())
                    .append(", effective=").append(minSup.effectiveThreshold()).append(")\n");
            sb.append("Direct CMAP: ").append(expected.directCmap()).append("\n");
            sb.append("First Differing Field: ").append(mismatchField).append("\n");
            sb.append("Difference Details: ").append(mismatchDetails).append("\n");
            sb.append("Expected Outcome: ").append(expected.outcome())
                    .append(", Actual Outcome: ").append(actual.outcome()).append("\n");
            sb.append("Expected Bitmap SIDs: ").append(expected.candidateBitmapSids()).append("\n");
            sb.append("Expected Supporting SIDs: ").append(expected.supportingSids()).append("\n");
            sb.append("Coverage Counters at Failure: ").append(coverageCounters).append("\n");
            sb.append("=================================================================\n");
            fail(sb.toString());
        }

        // Additional invariant assertions
        assertEquals(candidate.pattern(), actual.candidate().pattern(), "candidate pattern identity must match");
        assertEquals(candidate.extensionType(), actual.candidate().extensionType(), "extension type must match");
        assertEquals(candidate.extensionItem(), actual.candidate().extensionItem(), "extension item must match");

        if (actual.verificationResult().isPresent()) {
            SidBitmap bm = actual.candidateBitmap().orElseThrow();
            for (int sid : actual.verificationResult().get().supportingSids()) {
                assertTrue(bm.containsSid(sid), "Upper-bound inclusion invariant violated: SID " + sid + " not in bitmap");
            }
        }
    }

    // =========================================================================
    // 3. Deterministic Named Counterexamples (Section 9)
    // =========================================================================

    @Test
    @DisplayName("CE-01: I-extension same-itemset acceptance")
    void testCE01_iExtensionSameItemsetAcceptance() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)), ExtensionType.I_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-01", "I-extension same-itemset acceptance", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-02: I-extension CMAP failure")
    void testCE02_iExtensionCmapFailure() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)), ExtensionType.I_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-02", "I-extension CMAP failure", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.CMAP_REJECTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-03: S-extension strict-later acceptance")
    void testCE03_sExtensionStrictLaterAcceptance() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-03", "S-extension strict-later acceptance", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-04: S-extension reverse-order CMAP failure")
    void testCE04_sExtensionReverseOrderCmapFailure() {
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{2}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-04", "S-extension reverse-order CMAP failure", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.CMAP_REJECTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-05: Repeated-item S-extension")
    void testCE05_repeatedItemSExtension() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1));
        var cmapS = cmapBuilder.buildS(db, List.of(item1));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item1)), ExtensionType.S_EXTENSION, item1);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-05", "Repeated-item S-extension", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-06: Bitmap bound rejection")
    void testCE06_bitmapBoundRejection() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1)));
        var s3 = new Sequence(List.of(Itemset.of(item2)));
        var s4 = new Sequence(List.of(Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(
                new SequenceRecord(1, s1), new SequenceRecord(2, s2),
                new SequenceRecord(3, s3), new SequenceRecord(4, s4)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2, item3));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2, item3));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(3).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(2.0), 4);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 2.0);
        assertMatch("CE-06", "Bitmap bound rejection", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.BITMAP_REJECTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-07: Bitmap temporal false positive followed by exact rejection")
    void testCE07_bitmapTemporalFalsePositiveFollowedByExactRejection() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(2.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 2.0);
        assertMatch("CE-07", "Bitmap temporal false positive followed by exact rejection", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.EXACT_REJECTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-08: Bitmap equality not pruned")
    void testCE08_bitmapEqualityNotPruned() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2, item3));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2, item3));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-08", "Bitmap equality not pruned", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-09: Exact equality accepted")
    void testCE09_exactEqualityAccepted() {
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var s2 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1), new SequenceRecord(2, s2)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).setSid(2).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 2);
        double globalMeanWeight = 0.5;

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, globalMeanWeight, minSup);

        var expected = directEvaluate(db, parent, candidate, globalMeanWeight, 1.0);
        assertMatch("CE-09", "Exact equality accepted", db, parent,
                parentVerif.endpointStates(), candidate, globalMeanWeight, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-10: Multiple parent endpoints where earliest-only would lose an I-extension")
    void testCE10_multipleParentEndpointsEarliestOnlyWouldLoseIExtension() {
        // S1: <(1)(1 2)> -> parent <(1)> has endpoints at {1, 2}.
        // I-extension with item 2 can only match at position 2.
        // If an algorithm only kept earliest endpoint {1}, position 2 would be lost!
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item1, item2)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1, 2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2)), ExtensionType.I_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-10", "All-endpoints I-extension preservation", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
        assertArrayEquals(new int[]{2}, actual.verificationResult().get().endpointStates().get(0).endpoints());
    }

    @Test
    @DisplayName("CE-11: Multiple parent endpoints where latest-only would lose an S-extension")
    void testCE11_multipleParentEndpointsLatestOnlyWouldLoseSExtension() {
        // S1: <(1)(2)(1)> -> parent <(1)> has endpoints at {1, 3}.
        // S-extension with item 2 is at position 2.
        // Position 2 is strictly later than endpoint 1, but NOT later than endpoint 3.
        // If an algorithm only kept latest endpoint {3}, S-extension with item 2 would be lost!
        var s1 = new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1, 3})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(1.0), 1);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 1.0);
        assertMatch("CE-11", "All-endpoints S-extension preservation", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
        assertArrayEquals(new int[]{2}, actual.verificationResult().get().endpointStates().get(0).endpoints());
    }

    @Test
    @DisplayName("CE-12: Negative and noncontiguous SIDs")
    void testCE12_negativeNoncontiguousSids() {
        var s1 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s2 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var s3 = new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)));
        var db = new SequenceDatabase(List.of(
                new SequenceRecord(-50, s1),
                new SequenceRecord(0, s2),
                new SequenceRecord(77, s3)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2, item3));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2, item3));

        var parent = Pattern.of(Itemset.of(item1, item2));
        var parentBitmap = SidBitmap.builder(mapping).setSid(-50).setSid(0).setSid(77).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(-50).setSid(0).setSid(77).build();
        var parentVerif = VerificationResult.of(List.of(
                PatternEndpoints.of(-50, new int[]{1}),
                PatternEndpoints.of(0, new int[]{1}),
                PatternEndpoints.of(77, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1, item2), Itemset.of(item3)), ExtensionType.S_EXTENSION, item3);
        var minSup = resolver.resolve(MinimumSupport.absolute(2.0), 3);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 2.0);
        assertMatch("CE-12", "Negative and noncontiguous SIDs", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-13: Threshold-zero false-CMAP bypass")
    void testCE13_thresholdZeroFalseCmapBypass() {
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(0.0), 1);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 0.0);
        assertMatch("CE-13", "Threshold-zero false-CMAP bypass", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-14: Threshold-zero zero-bitmap bypass")
    void testCE14_thresholdZeroZeroBitmapBypass() {
        var s1 = new Sequence(List.of(Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.empty(mapping);
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{1})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(0.0), 1);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 0.0);
        assertMatch("CE-14", "Threshold-zero zero-bitmap bypass", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
    }

    @Test
    @DisplayName("CE-15: Threshold-zero zero-support acceptance")
    void testCE15_thresholdZeroZeroSupportAcceptance() {
        var s1 = new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)));
        var db = new SequenceDatabase(List.of(new SequenceRecord(1, s1)));
        var mapping = SidMapping.of(db);
        var occIndex = VerticalOccurrenceIndex.build(db, mapping);
        var cmapI = cmapBuilder.buildI(db, List.of(item1, item2));
        var cmapS = cmapBuilder.buildS(db, List.of(item1, item2));

        var parent = Pattern.singleton(item1);
        var parentBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var extBitmap = SidBitmap.builder(mapping).setSid(1).build();
        var parentVerif = VerificationResult.of(List.of(PatternEndpoints.of(1, new int[]{2})));

        var candidate = new BitPruneCandidateResult(
                Pattern.of(Itemset.of(item1), Itemset.of(item2)), ExtensionType.S_EXTENSION, item2);
        var minSup = resolver.resolve(MinimumSupport.absolute(0.0), 1);

        var actual = evaluator.evaluate(
                parent, candidate, parentBitmap, extBitmap, parentVerif,
                cmapI, cmapS, occIndex, 1.0, minSup);

        var expected = directEvaluate(db, parent, candidate, 1.0, 0.0);
        assertMatch("CE-15", "Threshold-zero zero-support acceptance", db, parent,
                parentVerif.endpointStates(), candidate, 1.0, minSup, expected, actual, Map.of());
        assertEquals(Outcome.ACCEPTED, actual.outcome());
        assertEquals(0, actual.verificationResult().get().exactSupport());
    }

    // =========================================================================
    // 4. Bounded Exhaustive Differential Evidence (Section 7)
    // =========================================================================

    @Test
    @DisplayName("Bounded Exhaustive Differential Catalogue Evidence")
    void testBoundedExhaustiveCatalogue() {
        // Construct 12 distinct databases covering structural properties over {1, 2, 3}
        List<SequenceDatabase> databases = new ArrayList<>();

        // DB0: rich same-itemset
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1, item2, item3)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1, item2)))))));

        // DB1: sequential order
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)))))));

        // DB2: reverse order
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item3), Itemset.of(item2), Itemset.of(item1)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item2), Itemset.of(item1)))))));

        // DB3: repeated items
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item1), Itemset.of(item2)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item2), Itemset.of(item2), Itemset.of(item1)))))));

        // DB4: mixed 3 sequences
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3)))),
                new SequenceRecord(3, new Sequence(List.of(Itemset.of(item2), Itemset.of(item1), Itemset.of(item3)))))));

        // DB5: disjoint items
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item2)))),
                new SequenceRecord(3, new Sequence(List.of(Itemset.of(item3)))))));

        // DB6: multiple embeddings
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item1), Itemset.of(item2)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item1, item2)))))));

        // DB7: negative and noncontiguous SIDs
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(-10, new Sequence(List.of(Itemset.of(item1, item2), Itemset.of(item3)))),
                new SequenceRecord(42, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2, item3)))))));

        // DB8: multiple endpoints for parent
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item1, item2)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)))))));

        // DB9: multiple endpoints with intermediate item
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item1)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2)))))));

        // DB10: cyclic pairwise co-occurrences
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(1, new Sequence(List.of(Itemset.of(item1, item2)))),
                new SequenceRecord(2, new Sequence(List.of(Itemset.of(item2, item3)))),
                new SequenceRecord(3, new Sequence(List.of(Itemset.of(item1, item3)))))));

        // DB11: zero SID + conflicting order
        databases.add(new SequenceDatabase(List.of(
                new SequenceRecord(0, new Sequence(List.of(Itemset.of(item1), Itemset.of(item2), Itemset.of(item3)))),
                new SequenceRecord(10, new Sequence(List.of(Itemset.of(item3), Itemset.of(item2), Itemset.of(item1)))))));

        // Candidate parent patterns over {1, 2, 3}
        List<Pattern> candidateParents = List.of(
                Pattern.singleton(item1),
                Pattern.singleton(item2),
                Pattern.singleton(item3),
                Pattern.of(Itemset.of(item1, item2)),
                Pattern.of(Itemset.of(item1, item3)),
                Pattern.of(Itemset.of(item2, item3)),
                Pattern.of(Itemset.of(item1), Itemset.of(item2)),
                Pattern.of(Itemset.of(item1), Itemset.of(item3)),
                Pattern.of(Itemset.of(item2), Itemset.of(item1)),
                Pattern.of(Itemset.of(item2), Itemset.of(item3)),
                Pattern.of(Itemset.of(item3), Itemset.of(item1)),
                Pattern.of(Itemset.of(item3), Itemset.of(item2)),
                Pattern.of(Itemset.of(item1), Itemset.of(item1)),
                Pattern.of(Itemset.of(item2), Itemset.of(item2)),
                Pattern.of(Itemset.of(item3), Itemset.of(item3)));

        Item[] universeItems = new Item[]{item1, item2, item3};
        List<Item> f1Universe = List.of(universeItems);

        // Pre-assert exact counts
        int declaredItemsetsCount = 7; // (1), (2), (3), (1 2), (1 3), (2 3), (1 2 3)
        assertEquals(7, declaredItemsetsCount);
        assertEquals(12, databases.size());

        Map<String, Integer> counters = new LinkedHashMap<>();
        int totalComparisons = 0;
        int iExtensionCases = 0;
        int sExtensionCases = 0;
        int positiveThresholdCases = 0;
        int zeroThresholdCases = 0;
        int supportedParentsCount = 0;

        for (int dbIdx = 0; dbIdx < databases.size(); dbIdx++) {
            var db = databases.get(dbIdx);
            var mapping = SidMapping.of(db);
            var occIndex = VerticalOccurrenceIndex.build(db, mapping);
            var cmapI = cmapBuilder.buildI(db, f1Universe);
            var cmapS = cmapBuilder.buildS(db, f1Universe);

            for (var parent : candidateParents) {
                // Check if parent has at least one feasible endpoint in db
                List<PatternEndpoints> parentEndpoints = new ArrayList<>();
                for (var rec : db.records()) {
                    int[] eps = directParentFeasibleEndpoints(rec.sequence(), parent);
                    if (eps.length > 0) {
                        parentEndpoints.add(PatternEndpoints.of(rec.sid(), eps));
                    }
                }
                if (parentEndpoints.isEmpty()) {
                    continue; // Skip unsupported parents
                }
                supportedParentsCount++;

                var parentVerification = VerificationResult.of(parentEndpoints);
                Set<Integer> parentBmSids = directParentBitmapSids(db, parent);
                var parentBitmapBuilder = SidBitmap.builder(mapping);
                for (int sid : parentBmSids) {
                    parentBitmapBuilder.setSid(sid);
                }
                var parentCandidateBitmap = parentBitmapBuilder.build();

                // Candidate extensions for this parent
                Item parentLastItem = parent.lastItem().orElseThrow();
                for (Item extItem : universeItems) {
                    // Try I-extension if valid
                    if (extItem.compareTo(parentLastItem) > 0
                            && !parent.itemsets().getLast().contains(extItem)) {
                        List<Item> newLastItems = new ArrayList<>(parent.itemsets().getLast().items());
                        newLastItems.add(extItem);
                        newLastItems.sort(Comparator.naturalOrder());
                        List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                        newItemsets.set(newItemsets.size() - 1, new Itemset(newLastItems));
                        var candPattern = new Pattern(newItemsets);
                        var candResult = new BitPruneCandidateResult(candPattern, ExtensionType.I_EXTENSION, extItem);

                        Set<Integer> extBmSids = directItemBitmapSids(db, extItem);
                        var extBitmapBuilder = SidBitmap.builder(mapping);
                        for (int sid : extBmSids) {
                            extBitmapBuilder.setSid(sid);
                        }
                        var extItemBitmap = extBitmapBuilder.build();

                        // Test configurations
                        double[] thresholds = new double[]{0.0, 1.0, 2.0};
                        double[] meanWeights = new double[]{1.0, 0.5};

                        for (double effThresh : thresholds) {
                            for (double mw : meanWeights) {
                                ResolvedMinimumSupport minSup = resolver.resolve(
                                        MinimumSupport.absolute(effThresh), db.size());
                                totalComparisons++;
                                iExtensionCases++;
                                if (effThresh > 0.0) {
                                    positiveThresholdCases++;
                                } else {
                                    zeroThresholdCases++;
                                }

                                var expected = directEvaluate(db, parent, candResult, mw, effThresh);
                                var actual = evaluator.evaluate(
                                        parent, candResult, parentCandidateBitmap, extItemBitmap, parentVerification,
                                        cmapI, cmapS, occIndex, mw, minSup);

                                String ctx = "DB=" + dbIdx + ", Parent=" + parent.serialize()
                                        + ", Cand=" + candResult.pattern().serialize()
                                        + ", Thresh=" + effThresh + ", MeanW=" + mw;
                                assertMatch("BOUNDED_EXHAUSTIVE", ctx, db, parent, parentEndpoints,
                                        candResult, mw, minSup, expected, actual, counters);

                                // Update counters
                                counters.merge(actual.outcome().name(), 1, Integer::sum);
                                counters.merge("I_EXTENSION", 1, Integer::sum);
                                if (expected.isBitmapFalsePositive()) {
                                    counters.merge("TEMPORAL_BITMAP_FALSE_POSITIVE", 1, Integer::sum);
                                }
                                int card = expected.candidateBitmapSids().size();
                                if (effThresh > 0.0 && card * mw == effThresh) {
                                    counters.merge("BITMAP_EQUALITY_NO_PRUNE", 1, Integer::sum);
                                }
                                if (actual.outcome() == Outcome.ACCEPTED && expected.supportingSids().size() * mw == effThresh) {
                                    counters.merge("EXACT_EQUALITY_ACCEPTED", 1, Integer::sum);
                                }
                                if (effThresh == 0.0 && !expected.directCmap()) {
                                    counters.merge("ZERO_THRESHOLD_FALSE_CMAP", 1, Integer::sum);
                                }
                                if (effThresh == 0.0 && card == 0) {
                                    counters.merge("ZERO_THRESHOLD_ZERO_BITMAP", 1, Integer::sum);
                                }
                                if (effThresh == 0.0 && expected.supportingSids().isEmpty()) {
                                    counters.merge("ZERO_SUPPORT_ACCEPTED_AT_ZERO_THRESHOLD", 1, Integer::sum);
                                }
                                if (parentEndpoints.stream().anyMatch(ep -> ep.endpoints().length > 1)) {
                                    counters.merge("MULTIPLE_ENDPOINTS", 1, Integer::sum);
                                }
                                if (expected.supportingSids().size() > 1) {
                                    counters.merge("MULTIPLE_SUPPORTING_SIDS", 1, Integer::sum);
                                }
                            }
                        }
                    }

                    // Try S-extension
                    List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                    newItemsets.add(Itemset.of(extItem));
                    var candPattern = new Pattern(newItemsets);
                    var candResult = new BitPruneCandidateResult(candPattern, ExtensionType.S_EXTENSION, extItem);

                    Set<Integer> extBmSids = directItemBitmapSids(db, extItem);
                    var extBitmapBuilder = SidBitmap.builder(mapping);
                    for (int sid : extBmSids) {
                        extBitmapBuilder.setSid(sid);
                    }
                    var extItemBitmap = extBitmapBuilder.build();

                    double[] thresholds = new double[]{0.0, 1.0, 2.0};
                    double[] meanWeights = new double[]{1.0, 0.5};

                    for (double effThresh : thresholds) {
                        for (double mw : meanWeights) {
                            ResolvedMinimumSupport minSup = resolver.resolve(
                                    MinimumSupport.absolute(effThresh), db.size());
                            totalComparisons++;
                            sExtensionCases++;
                            if (effThresh > 0.0) {
                                positiveThresholdCases++;
                            } else {
                                zeroThresholdCases++;
                            }

                            var expected = directEvaluate(db, parent, candResult, mw, effThresh);
                            var actual = evaluator.evaluate(
                                    parent, candResult, parentCandidateBitmap, extItemBitmap, parentVerification,
                                    cmapI, cmapS, occIndex, mw, minSup);

                            String ctx = "DB=" + dbIdx + ", Parent=" + parent.serialize()
                                    + ", Cand=" + candResult.pattern().serialize()
                                    + ", Thresh=" + effThresh + ", MeanW=" + mw;
                            assertMatch("BOUNDED_EXHAUSTIVE", ctx, db, parent, parentEndpoints,
                                    candResult, mw, minSup, expected, actual, counters);

                            counters.merge(actual.outcome().name(), 1, Integer::sum);
                            counters.merge("S_EXTENSION", 1, Integer::sum);
                            if (extItem.equals(parentLastItem)) {
                                counters.merge("REPEATED_ITEM_S_EXTENSION", 1, Integer::sum);
                            }
                            if (expected.isBitmapFalsePositive()) {
                                counters.merge("TEMPORAL_BITMAP_FALSE_POSITIVE", 1, Integer::sum);
                            }
                            int card = expected.candidateBitmapSids().size();
                            if (effThresh > 0.0 && card * mw == effThresh) {
                                counters.merge("BITMAP_EQUALITY_NO_PRUNE", 1, Integer::sum);
                            }
                            if (actual.outcome() == Outcome.ACCEPTED && expected.supportingSids().size() * mw == effThresh) {
                                counters.merge("EXACT_EQUALITY_ACCEPTED", 1, Integer::sum);
                            }
                            if (effThresh == 0.0 && !expected.directCmap()) {
                                counters.merge("ZERO_THRESHOLD_FALSE_CMAP", 1, Integer::sum);
                            }
                            if (effThresh == 0.0 && card == 0) {
                                counters.merge("ZERO_THRESHOLD_ZERO_BITMAP", 1, Integer::sum);
                            }
                            if (effThresh == 0.0 && expected.supportingSids().isEmpty()) {
                                counters.merge("ZERO_SUPPORT_ACCEPTED_AT_ZERO_THRESHOLD", 1, Integer::sum);
                            }
                            if (parentEndpoints.stream().anyMatch(ep -> ep.endpoints().length > 1)) {
                                counters.merge("MULTIPLE_ENDPOINTS", 1, Integer::sum);
                            }
                            if (expected.supportingSids().size() > 1) {
                                counters.merge("MULTIPLE_SUPPORTING_SIDS", 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        // Verify asserted counts
        assertEquals(76, supportedParentsCount);
        assertEquals(1836, totalComparisons);
        assertEquals(468, iExtensionCases);
        assertEquals(1368, sExtensionCases);
        assertEquals(1224, positiveThresholdCases);
        assertEquals(612, zeroThresholdCases);

        // Assert all 15 required exhaustive counters >= 1
        String[] requiredExhaustiveCounters = new String[]{
                "CMAP_REJECTED",
                "BITMAP_REJECTED",
                "EXACT_REJECTED",
                "ACCEPTED",
                "I_EXTENSION",
                "S_EXTENSION",
                "REPEATED_ITEM_S_EXTENSION",
                "TEMPORAL_BITMAP_FALSE_POSITIVE",
                "BITMAP_EQUALITY_NO_PRUNE",
                "EXACT_EQUALITY_ACCEPTED",
                "ZERO_THRESHOLD_FALSE_CMAP",
                "ZERO_THRESHOLD_ZERO_BITMAP",
                "ZERO_SUPPORT_ACCEPTED_AT_ZERO_THRESHOLD",
                "MULTIPLE_ENDPOINTS",
                "MULTIPLE_SUPPORTING_SIDS"
        };
        for (String c : requiredExhaustiveCounters) {
            int count = counters.getOrDefault(c, 0);
            assertTrue(count >= 1, "Exhaustive counter " + c + " must be >= 1, but got: " + count);
        }

        System.out.println("C10.2 Bounded Exhaustive Evidence: totalComparisons=" + totalComparisons
                + ", supportedParents=" + supportedParentsCount
                + ", iExtensions=" + iExtensionCases + ", sExtensions=" + sExtensionCases
                + ", positiveCases=" + positiveThresholdCases + ", zeroCases=" + zeroThresholdCases
                + ", mismatches=0");
        System.out.println("C10.2 Bounded Exhaustive Counters: " + counters);
    }

    // =========================================================================
    // 5. Fixed-Seed Randomized Evidence (Section 8)
    // =========================================================================

    @Test
    @DisplayName("Fixed-Seed Randomized Differential Evidence (1500 trials, seed=20261103L)")
    void testFixedSeedRandomizedDifferential() {
        Random rng = new Random(FIXED_SEED);
        Map<String, Integer> counters = new LinkedHashMap<>();

        int completedTrials = 0;

        for (int trial = 0; trial < RANDOM_TRIALS; trial++) {
            // Generate sequence count: 1 to 5
            int numSequences = 1 + rng.nextInt(5);

            // Generate unique SIDs, including negative, zero, noncontiguous
            List<Integer> sidPool = new ArrayList<>();
            for (int i = 0; i < numSequences; i++) {
                int sid;
                if (trial % 10 == 0 && i == 0) {
                    sid = - (1 + rng.nextInt(100)); // negative SID
                } else if (trial % 10 == 1 && i == 0) {
                    sid = 0; // zero SID
                } else if (trial % 10 == 2) {
                    sid = (i + 1) * 50; // noncontiguous
                } else {
                    sid = 1 + rng.nextInt(200);
                }
                while (sidPool.contains(sid)) {
                    sid++;
                }
                sidPool.add(sid);
            }

            // Universe size: 2 to 6
            int universeSize = 2 + rng.nextInt(5);
            List<Item> universeList = new ArrayList<>();
            for (int k = 1; k <= universeSize; k++) {
                universeList.add(new Item(k));
            }

            // Generate sequences
            List<SequenceRecord> records = new ArrayList<>();
            for (int sid : sidPool) {
                int numItemsets = 1 + rng.nextInt(4);
                List<Itemset> itemsets = new ArrayList<>();
                for (int it = 0; it < numItemsets; it++) {
                    int isSize = 1 + rng.nextInt(Math.min(3, universeSize));
                    Set<Item> chosen = new TreeSet<>();
                    while (chosen.size() < isSize) {
                        chosen.add(universeList.get(rng.nextInt(universeSize)));
                    }
                    itemsets.add(new Itemset(new ArrayList<>(chosen)));
                }
                records.add(new SequenceRecord(sid, new Sequence(itemsets)));
            }

            var db = new SequenceDatabase(records);
            var mapping = SidMapping.of(db);
            var occIndex = VerticalOccurrenceIndex.build(db, mapping);
            var cmapI = cmapBuilder.buildI(db, universeList);
            var cmapS = cmapBuilder.buildS(db, universeList);

            // Construct a supported parent pattern
            // Find an item present in at least one sequence
            Item parentItem1 = universeList.get(rng.nextInt(universeSize));
            Pattern parent;
            if (trial % 6 == 0 && universeSize >= 3) {
                // Multi-item final itemset
                parent = Pattern.of(Itemset.of(new Item(1), new Item(2)));
            } else if (trial % 5 == 0 && universeSize >= 2) {
                // Multi-itemset parent
                parent = Pattern.of(Itemset.of(universeList.get(0)), Itemset.of(universeList.get(1)));
            } else {
                parent = Pattern.singleton(parentItem1);
            }

            // Verify parent has endpoints in db
            List<PatternEndpoints> parentEndpoints = new ArrayList<>();
            for (var rec : db.records()) {
                int[] eps = directParentFeasibleEndpoints(rec.sequence(), parent);
                if (eps.length > 0) {
                    parentEndpoints.add(PatternEndpoints.of(rec.sid(), eps));
                }
            }

            // If selected parent has no endpoints in db, fallback to singleton of first item of first sequence
            if (parentEndpoints.isEmpty()) {
                parent = Pattern.singleton(records.get(0).sequence().itemsets().get(0).items().getFirst());
                parentEndpoints.clear();
                for (var rec : db.records()) {
                    int[] eps = directParentFeasibleEndpoints(rec.sequence(), parent);
                    if (eps.length > 0) {
                        parentEndpoints.add(PatternEndpoints.of(rec.sid(), eps));
                    }
                }
            }

            var parentVerification = VerificationResult.of(parentEndpoints);
            Set<Integer> parentBmSids = directParentBitmapSids(db, parent);
            var parentBitmapBuilder = SidBitmap.builder(mapping);
            for (int sid : parentBmSids) {
                parentBitmapBuilder.setSid(sid);
            }
            var parentCandidateBitmap = parentBitmapBuilder.build();

            // Candidate extension
            Item parentLastItem = parent.lastItem().orElseThrow();
            Item extItem;
            ExtensionType extType;

            if (trial % 7 == 0) {
                // Repeated-item S-extension
                extItem = parentLastItem;
                extType = ExtensionType.S_EXTENSION;
            } else if (rng.nextBoolean() && parentLastItem.id() < universeSize) {
                // I-extension
                int nextId = parentLastItem.id() + 1 + rng.nextInt(universeSize - parentLastItem.id());
                extItem = new Item(nextId);
                extType = ExtensionType.I_EXTENSION;
            } else {
                // S-extension
                extItem = universeList.get(rng.nextInt(universeSize));
                extType = ExtensionType.S_EXTENSION;
            }

            Pattern candPattern;
            if (extType == ExtensionType.I_EXTENSION) {
                List<Item> newLastItems = new ArrayList<>(parent.itemsets().getLast().items());
                if (!newLastItems.contains(extItem)) {
                    newLastItems.add(extItem);
                }
                newLastItems.sort(Comparator.naturalOrder());
                List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                newItemsets.set(newItemsets.size() - 1, new Itemset(newLastItems));
                candPattern = new Pattern(newItemsets);
            } else {
                List<Itemset> newItemsets = new ArrayList<>(parent.itemsets());
                newItemsets.add(Itemset.of(extItem));
                candPattern = new Pattern(newItemsets);
            }

            var candResult = new BitPruneCandidateResult(candPattern, extType, extItem);

            Set<Integer> extBmSids = directItemBitmapSids(db, extItem);
            var extBitmapBuilder = SidBitmap.builder(mapping);
            for (int sid : extBmSids) {
                extBitmapBuilder.setSid(sid);
            }
            var extItemBitmap = extBitmapBuilder.build();

            // Determine threshold and MeanW
            double effThresh;
            double mw = 0.25 * (1 + rng.nextInt(4)); // 0.25, 0.5, 0.75, 1.0

            Set<Integer> expectedCandBmSids = new TreeSet<>(parentBmSids);
            expectedCandBmSids.retainAll(extBmSids);
            int card = expectedCandBmSids.size();

            if (trial % 8 == 0) {
                effThresh = 0.0;
            } else if (trial % 9 == 0 && card > 0) {
                effThresh = card * mw; // Exact bitmap-bound equality
            } else {
                effThresh = 0.5 * (1 + rng.nextInt(Math.max(1, (int) (numSequences * 2))));
            }

            ResolvedMinimumSupport minSup = resolver.resolve(MinimumSupport.absolute(effThresh), db.size());

            var expected = directEvaluate(db, parent, candResult, mw, effThresh);
            var actual = evaluator.evaluate(
                    parent, candResult, parentCandidateBitmap, extItemBitmap, parentVerification,
                    cmapI, cmapS, occIndex, mw, minSup);

            String ctx = "Trial=" + trial + ", Seed=" + FIXED_SEED + ", DB_Size=" + numSequences
                    + ", Parent=" + parent.serialize() + ", Cand=" + candPattern.serialize()
                    + ", Thresh=" + effThresh + ", MeanW=" + mw;
            assertMatch("RANDOMIZED_DIFFERENTIAL", ctx, db, parent, parentEndpoints,
                    candResult, mw, minSup, expected, actual, counters);

            completedTrials++;

            // Record coverage counters
            counters.merge(actual.outcome().name(), 1, Integer::sum);
            counters.merge(extType.name(), 1, Integer::sum);
            if (extType == ExtensionType.S_EXTENSION && extItem.equals(parentLastItem)) {
                counters.merge("REPEATED_ITEM_S_EXTENSION", 1, Integer::sum);
            }
            if (sidPool.stream().anyMatch(s -> s < 0)) {
                counters.merge("NEGATIVE_SID", 1, Integer::sum);
            }
            if (sidPool.contains(0)) {
                counters.merge("SID_ZERO", 1, Integer::sum);
            }
            if (sidPool.stream().anyMatch(s -> s >= 50)) {
                counters.merge("NONCONTIGUOUS_SID_UNIVERSE", 1, Integer::sum);
            }
            if (parent.itemsets().getLast().size() > 1) {
                counters.merge("MULTI_ITEM_PARENT_FINAL_ITEMSET", 1, Integer::sum);
            }
            if (parent.itemsets().size() > 1) {
                counters.merge("MULTI_ITEMSET_PARENT", 1, Integer::sum);
            }
            if (parentEndpoints.stream().anyMatch(ep -> ep.endpoints().length > 1)) {
                counters.merge("MULTIPLE_PARENT_ENDPOINTS", 1, Integer::sum);
            }
            if (actual.verificationResult().isPresent() &&
                    actual.verificationResult().get().endpointStates().stream().anyMatch(ep -> ep.endpoints().length > 1)) {
                counters.merge("MULTIPLE_CHILD_ENDPOINTS", 1, Integer::sum);
            }
            if (expected.isBitmapFalsePositive()) {
                counters.merge("CANDIDATE_BITMAP_FALSE_POSITIVE", 1, Integer::sum);
            }
            if (card == 0) {
                counters.merge("CANDIDATE_BITMAP_ZERO_CARDINALITY", 1, Integer::sum);
            }
            if (effThresh == 0.0) {
                counters.merge("THRESHOLD_ZERO", 1, Integer::sum);
            }
            if (effThresh == 0.0 && expected.supportingSids().isEmpty()) {
                counters.merge("ZERO_SUPPORT_THRESHOLD_ZERO_ACCEPTANCE", 1, Integer::sum);
            }
            if (effThresh > 0.0 && card * mw == effThresh) {
                counters.merge("BITMAP_BOUND_EQUALITY", 1, Integer::sum);
            }
            if (actual.outcome() == Outcome.ACCEPTED && expected.supportingSids().size() * mw == effThresh) {
                counters.merge("EXACT_EQUALITY", 1, Integer::sum);
            }
            if (actual.outcome() == Outcome.EXACT_REJECTED && (card * mw >= effThresh)) {
                counters.merge("EXACT_REJECTION_AFTER_BITMAP_PASS", 1, Integer::sum);
            }
        }

        assertEquals(RANDOM_TRIALS, completedTrials);

        // Assert all 21 required randomized counters >= 1
        String[] requiredRandomizedCounters = new String[]{
                "CMAP_REJECTED",
                "BITMAP_REJECTED",
                "EXACT_REJECTED",
                "ACCEPTED",
                "I_EXTENSION",
                "S_EXTENSION",
                "REPEATED_ITEM_S_EXTENSION",
                "NEGATIVE_SID",
                "SID_ZERO",
                "NONCONTIGUOUS_SID_UNIVERSE",
                "MULTI_ITEM_PARENT_FINAL_ITEMSET",
                "MULTI_ITEMSET_PARENT",
                "MULTIPLE_PARENT_ENDPOINTS",
                "MULTIPLE_CHILD_ENDPOINTS",
                "CANDIDATE_BITMAP_FALSE_POSITIVE",
                "CANDIDATE_BITMAP_ZERO_CARDINALITY",
                "THRESHOLD_ZERO",
                "ZERO_SUPPORT_THRESHOLD_ZERO_ACCEPTANCE",
                "BITMAP_BOUND_EQUALITY",
                "EXACT_EQUALITY",
                "EXACT_REJECTION_AFTER_BITMAP_PASS"
        };
        for (String c : requiredRandomizedCounters) {
            int count = counters.getOrDefault(c, 0);
            assertTrue(count >= 1, "Randomized counter " + c + " must be >= 1, but got: " + count);
        }

        System.out.println("C10.2 Fixed-Seed Randomized Evidence: trials=" + completedTrials
                + ", seed=" + FIXED_SEED
                + ", mismatches=0");
        System.out.println("C10.2 Randomized Counters: " + counters);
    }
}
