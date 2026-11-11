package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.cmap.CmapBuilder;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.bitprune.mining.BitPruneCandidateEvaluation.Outcome;
import vn.edu.wspm.bitprune.verification.VerificationResult;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;
import vn.edu.wspm.tree.ExtensionType;

class BitPruneCmapCountRoutingTest {
    private static final Item A = new Item(1), B = new Item(2), C = new Item(3);

    private static SequenceDatabase db(String... sequences) {
        var records = new ArrayList<SequenceRecord>();
        int sid = -5;
        for (String sequence : sequences) {
            var sets = new ArrayList<Itemset>();
            for (String set : sequence.split(" ")) {
                sets.add(new Itemset(set.chars().mapToObj(ch -> new Item(ch - 'a' + 1)).toList()));
            }
            records.add(new SequenceRecord(sid, new Sequence(sets)));
            sid += 17;
        }
        return new SequenceDatabase(records);
    }

    // Independent embedding enumeration: retain every possible last itemset.
    private static void collectEndpoints(List<Itemset> pattern, List<Itemset> source,
            int pi, int nextPosition, java.util.Set<Integer> endpoints) {
        for (int pos = nextPosition; pos < source.size(); pos++) {
            if (pattern.get(pi).items().stream().allMatch(source.get(pos)::contains)) {
                if (pi == pattern.size() - 1) endpoints.add(pos + 1);
                else collectEndpoints(pattern, source, pi + 1, pos + 1, endpoints);
            }
        }
    }

    private static SidBitmap itemBitmap(SequenceDatabase database, SidMapping mapping, Item item) {
        var b = SidBitmap.builder(mapping);
        for (var record : database.records()) {
            if (record.sequence().itemsets().stream().anyMatch(set -> set.contains(item))) {
                b.setSid(record.sid());
            }
        }
        return b.build();
    }

    private record Pair(BitPruneCandidateEvaluation diagnostic, BitPruneHotEvaluation hot) {}

    private static Pair evaluateBoth(SequenceDatabase database, Pattern parent,
            ExtensionType type, Item item, double mean, double threshold) {
        var mapping = SidMapping.of(database);
        var bitmap = SidBitmap.builder(mapping);
        var endpoints = new ArrayList<PatternEndpoints>();
        for (var record : database.records()) {
            // Same loose all-items bitmap invariant as production candidate bitmaps.
            boolean allItems = parent.itemsets().stream().flatMap(set -> set.items().stream())
                    .allMatch(x -> record.sequence().itemsets().stream().anyMatch(set -> set.contains(x)));
            if (allItems) bitmap.setSid(record.sid());
            var positions = new java.util.TreeSet<Integer>();
            collectEndpoints(parent.itemsets(), record.sequence().itemsets(), 0, 0, positions);
            if (!positions.isEmpty()) endpoints.add(PatternEndpoints.of(record.sid(),
                    positions.stream().mapToInt(Integer::intValue).toArray()));
        }
        var cb = new CmapBuilder();
        var ci = cb.buildI(database, List.of(A, B, C));
        var cs = cb.buildS(database, List.of(A, B, C));
        var index = VerticalOccurrenceIndex.build(database, mapping);
        var min = new MinimumSupportResolver().resolve(MinimumSupport.absolute(threshold),
                database.records().size());
        var extensions = new PatternExtensions();
        var child = type == ExtensionType.I_EXTENSION
                ? extensions.iExtend(parent, item) : extensions.sExtend(parent, item);
        var candidate = new BitPruneCandidateResult(child, type, item);
        var vr = VerificationResult.of(endpoints);
        var bm = bitmap.build();
        var ext = itemBitmap(database, mapping, item);
        var evaluator = new BitPruneCandidateEvaluator();
        // Exercise both validation/exception paths even when either one throws.
        BitPruneCandidateEvaluation diagnostic = null;
        BitPruneHotEvaluation hot = null;
        IllegalArgumentException diagnosticError = null, hotError = null;
        try { diagnostic = evaluator.evaluate(parent, candidate, bm, ext, vr, ci, cs, index, mean, min); }
        catch (IllegalArgumentException e) { diagnosticError = e; }
        try { hot = evaluator.evaluatePreparedExtension(parent, type, item, bm, ext, vr, ci, cs, index, mean, min); }
        catch (IllegalArgumentException e) { hotError = e; }
        assertEquals(diagnosticError == null, hotError == null, "exception parity");
        if (diagnosticError != null) throw diagnosticError;
        assertEquals(diagnostic.outcome(), hot.outcome(), "routing parity");
        BitPruneCandidateEvaluator.validateParentInclusion(bm, vr);
        var validated = evaluator.evaluateValidatedParentExtension(
                parent, type, item, bm, ext, vr, ci, cs, index, mean, min);
        assertEquals(hot.outcome(), validated.outcome(), "validated-parent routing parity");
        if (diagnostic.outcome() == Outcome.ACCEPTED) {
            assertEquals(hot.acceptedVerification(), validated.acceptedVerification());
            assertArrayEquals(hot.acceptedBitmap().toSupportingSids(),
                    validated.acceptedBitmap().toSupportingSids());
            var expected = diagnostic.verificationResult().orElseThrow();
            assertEquals(expected.exactSupport(), hot.acceptedVerification().exactSupport());
            assertArrayEquals(expected.supportingSids(), hot.acceptedVerification().supportingSids());
            assertArrayEquals(diagnostic.candidateBitmap().orElseThrow().toSupportingSids(),
                    hot.acceptedBitmap().toSupportingSids());
            var actual = hot.acceptedVerification().endpointStates();
            assertEquals(expected.endpointStates().size(), actual.size());
            for (int k = 0; k < actual.size(); k++) {
                assertEquals(expected.endpointStates().get(k).actualSid(), actual.get(k).actualSid());
                assertArrayEquals(expected.endpointStates().get(k).endpoints(), actual.get(k).endpoints());
            }
        }
        return new Pair(diagnostic, hot);
    }

    @Test
    void positivePairCountsBelowWeightedThresholdRejectBeforeBitmapForBothExtensions() {
        for (var type : ExtensionType.values()) {
            var database = type == ExtensionType.I_EXTENSION ? db("ab", "a", "b") : db("a b", "a", "b");
            // Actual item weights are both 0.5, singleton supports 2, pair support 1.
            var result = evaluateBoth(database, Pattern.singleton(A), type, B, 0.5, 0.75);
            assertEquals(Outcome.CMAP_REJECTED, result.diagnostic().outcome());
            assertTrue(result.diagnostic().candidateBitmap().isEmpty());
            assertTrue(result.diagnostic().verificationResult().isEmpty());
        }
    }

    @Test
    void equalityAndAdjacentDoubleThresholdsPreserveInclusiveAcceptance() {
        for (var type : ExtensionType.values()) {
            var database = type == ExtensionType.I_EXTENSION
                    ? db("ab", "ab", "a", "b") : db("a b", "a b", "a", "b");
            // Synthetic method-level MeanW=0.25; pair count=2. Not a database weight claim.
            for (double threshold : new double[]{Math.nextDown(0.5), 0.5, Math.nextUp(0.5)}) {
                var result = evaluateBoth(database, Pattern.singleton(A), type, B, 0.25, threshold);
                assertEquals(threshold <= 0.5 ? Outcome.ACCEPTED : Outcome.CMAP_REJECTED,
                        result.diagnostic().outcome());
            }
        }
    }

    @Test
    void longerPatternPassesPairBoundButFailsBitmap() {
        var result = evaluateBoth(db("a b c", "a b", "b c"),
                Pattern.of(Itemset.of(A), Itemset.of(B)), ExtensionType.S_EXTENSION,
                C, (3.0 / 7 + 2.0 / 7) / 2, 0.5);
        assertEquals(Outcome.BITMAP_REJECTED, result.diagnostic().outcome());
        assertEquals(1, result.diagnostic().candidateBitmap().orElseThrow().cardinality());
        assertTrue(result.diagnostic().verificationResult().isEmpty());
    }

    @Test
    void pairAndBitmapPassButFullTemporalPatternDoesNotExist() {
        var result = evaluateBoth(db("a b c", "ab", "b c"),
                Pattern.of(Itemset.of(A, B)), ExtensionType.S_EXTENSION,
                C, (3.0 / 7 + 2.0 / 7) / 2, 0.25);
        assertEquals(Outcome.EXACT_REJECTED, result.diagnostic().outcome());
        assertEquals(0, result.diagnostic().verificationResult().orElseThrow().exactSupport());
    }

    @Test
    void zeroThresholdBypassesCmapWithoutResolvingR6() {
        for (double zero : new double[]{0.0, -0.0}) {
            var result = evaluateBoth(db("a", "b"), Pattern.singleton(A),
                    ExtensionType.S_EXTENSION, B, 0.5, zero);
            assertNotEquals(Outcome.CMAP_REJECTED, result.diagnostic().outcome());
            assertTrue(result.diagnostic().verificationResult().isPresent());
        }
    }

    @Test
    void invalidMeanAndOverflowPropagateForBothEvaluatorPaths() {
        for (double mean : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.1, Double.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> evaluateBoth(db("ab", "ab"),
                    Pattern.singleton(A), ExtensionType.I_EXTENSION, B, mean, 0.5));
        }
    }

    @Test
    void parentValidationRejectsAbsentAndUnknownSidsBeforeTrustingState() {
        var database = db("a", "b");
        var mapping = SidMapping.of(database);
        var bitmap = itemBitmap(database, mapping, A);
        var absent = VerificationResult.of(List.of(PatternEndpoints.of(12, new int[]{1})));
        var unknown = VerificationResult.of(List.of(PatternEndpoints.of(999, new int[]{1})));
        assertThrows(IllegalArgumentException.class,
                () -> BitPruneCandidateEvaluator.validateParentInclusion(bitmap, absent));
        assertThrows(IllegalArgumentException.class,
                () -> BitPruneCandidateEvaluator.validateParentInclusion(bitmap, unknown));
        assertThrows(NullPointerException.class,
                () -> BitPruneCandidateEvaluator.validateParentInclusion(null, absent));
    }
}
