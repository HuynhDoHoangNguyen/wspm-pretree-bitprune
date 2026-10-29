package vn.edu.wspm.bitprune.verification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.bitprune.index.PatternEndpoints;
import vn.edu.wspm.bitprune.index.VerticalOccurrenceIndex;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.PatternExtensions;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Exhaustive & randomized differential test suite for {@link ExactTemporalVerifier} I-Extension
 * verification (C07.2).
 *
 * <h2>Oracle Classification</h2>
 * <ul>
 *   <li><b>Oracle A (Independent Specification-Level Algebraic Oracle):</b>
 *       Validates same-itemset intersection against boolean position masks over universe
 *       {@code {1, 2, 3, 4, 5, 6}} across all 4,096 combinations of parent feasible endpoints
 *       and extension item occurrence positions, independently proving that
 *       {@code E(P ⊕I x, s) = E(P, s) ∩ Occurrences(x, s)}.</li>
 *   <li><b>Oracle B (Independent Structural Direct-Scan Oracle):</b>
 *       Exhaustive recursive backtracking subsequence scanner reading raw {@link SequenceDatabase}
 *       records directly. For every candidate child pattern, the oracle discovers embeddings
 *       from scratch without calling {@link ExactTemporalVerifier}, without using
 *       {@link VerticalOccurrenceIndex} queries, and without performing two-pointer array joins.</li>
 *   <li><b>Shared Domain Infrastructure:</b>
 *       {@link PatternExtensions} and domain value objects ({@link Item}, {@link Itemset},
 *       {@link Pattern}, {@link Sequence}, {@link SequenceRecord}, {@link SequenceDatabase})
 *       are accepted shared data models, not independent pattern oracles.</li>
 * </ul>
 */
class ExactTemporalVerifierIExtensionDifferentialTest {

    private ExactTemporalVerifier verifier;
    private PatternExtensions patternExtensions;

    @BeforeEach
    void setUp() {
        verifier = new ExactTemporalVerifier();
        patternExtensions = new PatternExtensions();
    }

    // =========================================================================
    // Independent Structural Direct-Scan Oracle (Oracle B)
    // =========================================================================

    /**
     * Direct-scan oracle: computes all feasible 1-based ending itemset positions of
     * {@code pattern} in {@code sequence} via recursive backtracking over raw itemsets.
     */
    private static int[] directScanFeasibleEndpoints(Sequence sequence, Pattern pattern) {
        if (pattern.isEmpty()) {
            return new int[0];
        }
        List<Itemset> patternItemsets = pattern.itemsets();
        List<Itemset> seqItemsets = sequence.itemsets();
        TreeSet<Integer> endpoints = new TreeSet<>();
        findEmbeddings(seqItemsets, patternItemsets, 0, 0, endpoints);

        int[] result = new int[endpoints.size()];
        int idx = 0;
        for (int ep : endpoints) {
            result[idx++] = ep;
        }
        return result;
    }

    private static void findEmbeddings(
            List<Itemset> seqItemsets,
            List<Itemset> patternItemsets,
            int patternIndex,
            int minSeqIndex,
            TreeSet<Integer> endpoints) {

        Itemset targetItemset = patternItemsets.get(patternIndex);
        boolean isLastItemset = (patternIndex == patternItemsets.size() - 1);

        for (int i = minSeqIndex; i < seqItemsets.size(); i++) {
            if (containsAllItems(seqItemsets.get(i), targetItemset)) {
                if (isLastItemset) {
                    endpoints.add(i + 1); // 1-based itemset position
                } else {
                    findEmbeddings(seqItemsets, patternItemsets, patternIndex + 1, i + 1, endpoints);
                }
            }
        }
    }

    private static boolean containsAllItems(Itemset sequenceItemset, Itemset patternItemset) {
        for (Item item : patternItemset.items()) {
            if (!sequenceItemset.contains(item)) {
                return false;
            }
        }
        return true;
    }

    /** Encapsulates oracle results for an entire sequence database in record order. */
    private static final class OracleScanResult {
        final Map<Integer, int[]> sidToEndpoints;
        final int[] supportingSids;
        final List<PatternEndpoints> endpointStates;

        OracleScanResult(
                Map<Integer, int[]> sidToEndpoints,
                int[] supportingSids,
                List<PatternEndpoints> endpointStates) {
            this.sidToEndpoints = sidToEndpoints;
            this.supportingSids = supportingSids;
            this.endpointStates = endpointStates;
        }
    }

    /**
     * Scans {@code database} in record order to compute the complete oracle result for
     * {@code pattern}.
     */
    private static OracleScanResult scanDatabase(SequenceDatabase database, Pattern pattern) {
        Map<Integer, int[]> map = new LinkedHashMap<>();
        List<PatternEndpoints> states = new ArrayList<>();
        List<Integer> sids = new ArrayList<>();

        for (SequenceRecord record : database.records()) {
            int[] endpoints = directScanFeasibleEndpoints(record.sequence(), pattern);
            if (endpoints.length > 0) {
                map.put(record.sid(), endpoints);
                states.add(PatternEndpoints.of(record.sid(), endpoints));
                sids.add(record.sid());
            }
        }

        int[] sidsArr = sids.stream().mapToInt(Integer::intValue).toArray();
        return new OracleScanResult(
                Collections.unmodifiableMap(map),
                sidsArr,
                Collections.unmodifiableList(states));
    }

    // =========================================================================
    // Diagnostic Formatting Helpers
    // =========================================================================

    private static String formatSequence(Sequence sequence) {
        StringBuilder sb = new StringBuilder("<");
        for (Itemset itemset : sequence.itemsets()) {
            sb.append('(');
            for (int i = 0; i < itemset.items().size(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(itemset.items().get(i).id());
            }
            sb.append(')');
        }
        return sb.append('>').toString();
    }

    private static String formatDatabase(SequenceDatabase database) {
        StringBuilder sb = new StringBuilder("[");
        List<SequenceRecord> records = database.records();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append(", ");
            SequenceRecord rec = records.get(i);
            sb.append("rec(").append(rec.sid()).append(", ").append(formatSequence(rec.sequence())).append(")");
        }
        return sb.append("]").toString();
    }

    private static String formatExpectedMap(Map<Integer, int[]> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, int[]> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(entry.getKey()).append("=").append(Arrays.toString(entry.getValue()));
        }
        return sb.append("}").toString();
    }

    private static boolean isNoncontiguous(Set<Integer> sids) {
        if (sids.size() < 2) {
            return false;
        }
        List<Integer> sorted = new ArrayList<>(sids);
        Collections.sort(sorted);
        for (int i = 1; i < sorted.size(); i++) {
            long gap = (long) sorted.get(i) - (long) sorted.get(i - 1);
            if (gap != 1L) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Differential Comparison Assertions
    // =========================================================================

    private static void assertVerificationMatch(
            String context,
            VerificationResult actual,
            Map<Integer, int[]> expectedEndpointsMap,
            int[] expectedSupportingSids) {

        // 1. exactSupport matches distinct supporting SID count
        assertEquals(
                expectedSupportingSids.length,
                actual.exactSupport(),
                () -> context + String.format(
                        "%n  exactSupport mismatch: expected=%d, actual=%d",
                        expectedSupportingSids.length, actual.exactSupport()));

        // 2. supportingSids matches exact array and ordering
        assertArrayEquals(
                expectedSupportingSids,
                actual.supportingSids(),
                () -> context + String.format(
                        "%n  supportingSids mismatch: expected=%s, actual=%s",
                        Arrays.toString(expectedSupportingSids), Arrays.toString(actual.supportingSids())));

        // 3. endpointStates size matches
        assertEquals(
                expectedSupportingSids.length,
                actual.endpointStates().size(),
                () -> context + String.format(
                        "%n  endpointStates size mismatch: expected=%d, actual=%d",
                        expectedSupportingSids.length, actual.endpointStates().size()));

        // 4. Per-SID endpoints match element-by-element in deterministic order
        for (int i = 0; i < actual.endpointStates().size(); i++) {
            final int index = i;
            PatternEndpoints ep = actual.endpointStates().get(i);
            int sid = ep.actualSid();
            assertEquals(
                    expectedSupportingSids[i],
                    sid,
                    () -> context + String.format(
                        "%n  SID ordering mismatch at index %d: expected SID=%d, actual SID=%d",
                        index, expectedSupportingSids[index], sid));

            int[] expectedEps = expectedEndpointsMap.get(sid);
            assertNotNull(
                    expectedEps,
                    () -> context + String.format(
                        "%n  Unexpected SID in actual result: %d", sid));
            assertArrayEquals(
                    expectedEps,
                    ep.endpoints(),
                    () -> context + String.format(
                        "%n  Endpoints mismatch for SID %d: expected=%s, actual=%s",
                        sid, Arrays.toString(expectedEps), Arrays.toString(ep.endpoints())));
        }
    }

    // =========================================================================
    // Shared Builders
    // =========================================================================

    private static Item item(int id) {
        return new Item(id);
    }

    private static Itemset itemset(Item... items) {
        return Itemset.of(items);
    }

    private static Sequence seq(Itemset... itemsets) {
        return Sequence.of(itemsets);
    }

    private static SequenceRecord rec(int sid, Sequence sequence) {
        return new SequenceRecord(sid, sequence);
    }

    private static SequenceDatabase db(SequenceRecord... records) {
        return new SequenceDatabase(Arrays.asList(records));
    }

    private static VerticalOccurrenceIndex buildIndex(SequenceDatabase db) {
        return VerticalOccurrenceIndex.build(db, SidMapping.of(db));
    }

    // =========================================================================
    // Layer A: Exhaustive Endpoint-Mask Catalogue (4,096 comparisons)
    // =========================================================================

    @Test
    void testLayerA_exhaustiveEndpointMaskCatalogue() {
        Item parentItem = item(1);
        Item extItem = item(2);
        Item fillerItem = item(3);

        Pattern parent = Pattern.singleton(parentItem);

        int endpointMaskComparisons = 0;
        int endpointMaskMismatches = 0;
        int testSid = -999; // Non-default negative SID

        for (int maskA = 0; maskA < 64; maskA++) {
            for (int maskB = 0; maskB < 64; maskB++) {
                endpointMaskComparisons++;

                // Build sequence with 6 itemsets corresponding to positions 1..6
                List<Itemset> itemsets = new ArrayList<>(6);
                for (int pos = 1; pos <= 6; pos++) {
                    int bit = 1 << (pos - 1);
                    boolean hasParent = (maskA & bit) != 0;
                    boolean hasExt = (maskB & bit) != 0;

                    List<Item> items = new ArrayList<>();
                    if (hasParent) items.add(parentItem);
                    if (hasExt) items.add(extItem);
                    if (!hasParent && !hasExt) items.add(fillerItem);

                    itemsets.add(new Itemset(items));
                }

                Sequence seq = new Sequence(itemsets);
                SequenceDatabase database = new SequenceDatabase(List.of(new SequenceRecord(testSid, seq)));
                VerticalOccurrenceIndex index = buildIndex(database);

                // Parent endpoints from mask A
                List<Integer> pEps = new ArrayList<>();
                for (int pos = 1; pos <= 6; pos++) {
                    if ((maskA & (1 << (pos - 1))) != 0) {
                        pEps.add(pos);
                    }
                }

                List<PatternEndpoints> parentStates;
                if (pEps.isEmpty()) {
                    parentStates = List.of();
                } else {
                    int[] eps = pEps.stream().mapToInt(Integer::intValue).toArray();
                    parentStates = List.of(PatternEndpoints.of(testSid, eps));
                }

                // SUT Execution
                VerificationResult actual = verifier.verifyIExtension(parent, parentStates, extItem, index);

                // Oracle A Expected: A ∩ B
                int intersectionMask = maskA & maskB;
                List<Integer> expectedEps = new ArrayList<>();
                for (int pos = 1; pos <= 6; pos++) {
                    if ((intersectionMask & (1 << (pos - 1))) != 0) {
                        expectedEps.add(pos);
                    }
                }

                // Verification with reproducible mismatch diagnostics
                if (expectedEps.isEmpty()) {
                    if (actual.exactSupport() != 0
                            || !actual.endpointStates().isEmpty()
                            || actual.supportingSids().length != 0) {
                        endpointMaskMismatches++;
                        String diagnostic = String.format(
                                "Layer A Mismatch at maskA=0x%02X (bin=%s), maskB=0x%02X (bin=%s):%n"
                                        + "  Sequence fixture: %s%n"
                                        + "  Expected endpoints: %s%n"
                                        + "  Actual exactSupport: %d%n"
                                        + "  Actual supportingSids: %s%n"
                                        + "  Actual endpointStates: %s",
                                maskA, Integer.toBinaryString(maskA), maskB, Integer.toBinaryString(maskB),
                                formatSequence(seq),
                                expectedEps,
                                actual.exactSupport(),
                                Arrays.toString(actual.supportingSids()),
                                actual.endpointStates());
                        assertEquals(0, actual.exactSupport(), () -> diagnostic);
                        assertTrue(actual.endpointStates().isEmpty(), () -> diagnostic);
                        assertEquals(0, actual.supportingSids().length, () -> diagnostic);
                    }
                } else {
                    int[] expectedArr = expectedEps.stream().mapToInt(Integer::intValue).toArray();
                    boolean match = (actual.exactSupport() == 1)
                            && (actual.endpointStates().size() == 1)
                            && (actual.endpointStates().get(0).actualSid() == testSid)
                            && Arrays.equals(actual.endpointStates().get(0).endpoints(), expectedArr)
                            && Arrays.equals(actual.supportingSids(), new int[]{testSid});

                    if (!match) {
                        endpointMaskMismatches++;
                        String diagnostic = String.format(
                                "Layer A Mismatch at maskA=0x%02X (bin=%s), maskB=0x%02X (bin=%s):%n"
                                        + "  Sequence fixture: %s%n"
                                        + "  Expected endpoints: %s%n"
                                        + "  Actual exactSupport: %d%n"
                                        + "  Actual supportingSids: %s%n"
                                        + "  Actual endpointStates: %s",
                                maskA, Integer.toBinaryString(maskA), maskB, Integer.toBinaryString(maskB),
                                formatSequence(seq),
                                Arrays.toString(expectedArr),
                                actual.exactSupport(),
                                Arrays.toString(actual.supportingSids()),
                                actual.endpointStates());
                        assertEquals(1, actual.exactSupport(), () -> diagnostic);
                        assertEquals(1, actual.endpointStates().size(), () -> diagnostic);
                        assertEquals(testSid, actual.endpointStates().get(0).actualSid(), () -> diagnostic);
                        assertArrayEquals(expectedArr, actual.endpointStates().get(0).endpoints(), () -> diagnostic);
                        assertArrayEquals(new int[]{testSid}, actual.supportingSids(), () -> diagnostic);
                    }
                }
            }
        }

        System.out.println("C07.2 Layer A: endpointMaskComparisons=" + endpointMaskComparisons);
        System.out.println("C07.2 Layer A: endpointMaskMismatches=" + endpointMaskMismatches);

        assertEquals(4096, endpointMaskComparisons, "Must execute exactly 4,096 mask comparisons");
        assertEquals(0, endpointMaskMismatches, "Exhaustive mask mismatches must be zero");
    }

    // =========================================================================
    // Layer B: Exhaustive Structural Catalogue (2,800 sequences, 121 pairs)
    // =========================================================================

    @Test
    void testLayerB_exhaustiveStructuralCatalogue() {
        Item i1 = item(1);
        Item i2 = item(2);
        Item i3 = item(3);

        List<Itemset> baseItemsets = List.of(
                itemset(i1),
                itemset(i2),
                itemset(i3),
                itemset(i1, i2),
                itemset(i1, i3),
                itemset(i2, i3),
                itemset(i1, i2, i3));

        // Generate exactly 2,800 sequences: 7 + 7^2 + 7^3 + 7^4
        List<SequenceRecord> records = new ArrayList<>(2800);
        int sidCounter = -1000; // Covers negative, zero, and positive SIDs

        // Length 1: 7
        for (Itemset s1 : baseItemsets) {
            records.add(new SequenceRecord(sidCounter++, seq(s1)));
        }
        // Length 2: 49
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                records.add(new SequenceRecord(sidCounter++, seq(s1, s2)));
            }
        }
        // Length 3: 343
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                for (Itemset s3 : baseItemsets) {
                    records.add(new SequenceRecord(sidCounter++, seq(s1, s2, s3)));
                }
            }
        }
        // Length 4: 2401
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                for (Itemset s3 : baseItemsets) {
                    for (Itemset s4 : baseItemsets) {
                        records.add(new SequenceRecord(sidCounter++, seq(s1, s2, s3, s4)));
                    }
                }
            }
        }

        assertEquals(2800, records.size(), "Catalogue must contain exactly 2,800 sequences");
        SequenceDatabase database = new SequenceDatabase(records);
        VerticalOccurrenceIndex index = buildIndex(database);

        // Generate canonical parent/extension pairs: 1..3 itemsets, length <= 4, ext > lastItem
        List<Map.Entry<Pattern, Item>> canonicalPairs = new ArrayList<>();
        List<Item> universeItems = List.of(i1, i2, i3);

        // 1 itemset
        for (Itemset s1 : baseItemsets) {
            Pattern p = Pattern.of(s1);
            if (p.length() <= 4) {
                int last = p.lastItem().orElseThrow().id();
                for (Item ext : universeItems) {
                    if (ext.id() > last) {
                        canonicalPairs.add(Map.entry(p, ext));
                    }
                }
            }
        }
        // 2 itemsets
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                Pattern p = Pattern.of(s1, s2);
                if (p.length() <= 4) {
                    int last = p.lastItem().orElseThrow().id();
                    for (Item ext : universeItems) {
                        if (ext.id() > last) {
                            canonicalPairs.add(Map.entry(p, ext));
                        }
                    }
                }
            }
        }
        // 3 itemsets
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                for (Itemset s3 : baseItemsets) {
                    Pattern p = Pattern.of(s1, s2, s3);
                    if (p.length() <= 4) {
                        int last = p.lastItem().orElseThrow().id();
                        for (Item ext : universeItems) {
                            if (ext.id() > last) {
                                canonicalPairs.add(Map.entry(p, ext));
                            }
                        }
                    }
                }
            }
        }

        int structuralSequenceCount = database.size();
        int structuralPatternPairCount = canonicalPairs.size();
        int structuralComparisons = 0;
        int structuralMismatches = 0;
        int exhaustiveCatalogueZeroSupportCases = 0;
        int dedicatedStructuralZeroSupportCases = 0;
        int multipleEndpointCases = 0;
        int multipleSidCases = 0;

        for (Map.Entry<Pattern, Item> entry : canonicalPairs) {
            structuralComparisons++;
            Pattern parent = entry.getKey();
            Item ext = entry.getValue();
            Pattern child = patternExtensions.iExtend(parent, ext);

            // Oracle scan for parent endpoints
            OracleScanResult parentScan = scanDatabase(database, parent);

            // SUT Execution
            VerificationResult actual = verifier.verifyIExtension(
                    parent, parentScan.endpointStates, ext, index);

            // Oracle scan for child pattern
            OracleScanResult childScan = scanDatabase(database, child);

            // Context with catalogue identity and exact pattern/extension info
            String context = String.format(
                    "Catalogue=%s, parent=%s, ext=%d, child=%s",
                    "EXHAUSTIVE_U3_LENGTH_1_TO_4_2800",
                    parent.serialize(),
                    ext.id(),
                    child.serialize());

            // Verify equivalence with reproducible mismatch diagnostics
            try {
                assertVerificationMatch(
                        context,
                        actual,
                        childScan.sidToEndpoints,
                        childScan.supportingSids);
            } catch (AssertionError e) {
                structuralMismatches++;
                throw e;
            }

            if (actual.exactSupport() == 0) {
                exhaustiveCatalogueZeroSupportCases++;
            } else if (actual.exactSupport() > 1) {
                multipleSidCases++;
            }

            for (PatternEndpoints ep : actual.endpointStates()) {
                if (ep.endpoints().length > 1) {
                    multipleEndpointCases++;
                    break;
                }
            }
        }

        // Dedicated zero-support test on single sequence from the catalogue
        SequenceDatabase singleSeqDb = db(rec(-1000, seq(itemset(i1))));
        VerticalOccurrenceIndex singleSeqIdx = buildIndex(singleSeqDb);
        Pattern p1 = Pattern.singleton(i1);
        OracleScanResult p1Scan = scanDatabase(singleSeqDb, p1);
        VerificationResult actualZero = verifier.verifyIExtension(p1, p1Scan.endpointStates, i2, singleSeqIdx);
        assertEquals(0, actualZero.exactSupport(), "I-extension of <(1)> with 2 in <(1)> must yield exactSupport=0");
        dedicatedStructuralZeroSupportCases++;

        System.out.println("C07.2 Layer B: structuralSequenceCount=" + structuralSequenceCount);
        System.out.println("C07.2 Layer B: structuralPatternPairCount=" + structuralPatternPairCount);
        System.out.println("C07.2 Layer B: structuralComparisons=" + structuralComparisons);
        System.out.println("C07.2 Layer B: structuralMismatches=" + structuralMismatches);
        System.out.println("C07.2 Layer B: exhaustiveCatalogueZeroSupportCases=" + exhaustiveCatalogueZeroSupportCases);
        System.out.println("C07.2 Layer B: dedicatedStructuralZeroSupportCases=" + dedicatedStructuralZeroSupportCases);
        System.out.println("C07.2 Layer B: multipleEndpointCases=" + multipleEndpointCases);
        System.out.println("C07.2 Layer B: multipleSidCases=" + multipleSidCases);

        assertEquals(2800, structuralSequenceCount);
        assertEquals(121, structuralPatternPairCount);
        assertEquals(121, structuralComparisons);
        assertEquals(0, structuralMismatches);
        assertEquals(0, exhaustiveCatalogueZeroSupportCases,
                "Exhaustive catalogue must have 0 zero-support cases as sequence 2,800 contains witness for all 121 pairs");
        assertTrue(dedicatedStructuralZeroSupportCases > 0,
                "Dedicated structural zero-support case must be > 0");
        assertTrue(multipleEndpointCases > 0, "Must observe multiple-endpoint cases");
        assertTrue(multipleSidCases > 0, "Must observe multiple-SID support cases");
    }

    // =========================================================================
    // Layer C: Named Counterexamples and Boundary Verification
    // =========================================================================

    @Test
    void testNamedCase01_sameSidExtensionBeforeEndpoint() {
        // Sequence: <(2)(1)>, extension item 2 is strictly BEFORE parent endpoint at pos 2
        var database = db(rec(1, seq(itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{2}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase02_sameSidExtensionAfterEndpoint() {
        // Sequence: <(1)(2)>, extension item 2 is strictly AFTER parent endpoint at pos 1
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase03_counterexampleCE01() {
        // CE-01 from C01.2 §14: Sequence <(1)(1 2)>, parent <(1)> has endpoints {1, 2}
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase04_multipleParentEndpointsAllSurvive() {
        // Sequence: <(1 2)(3)(1 2)>, positions 1 and 3 both match I-extension
        var database = db(rec(10, seq(itemset(item(1), item(2)), itemset(item(3)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(10, new int[]{1, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1, 3}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase05_multipleEmbeddingsSameSidSupportIsOne() {
        // Sequence: <(1)(1)(1 2)>, multiple parent prefix embeddings in same SID
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2, 3}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport(), "Distinct SID support must be 1 regardless of embeddings");
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase06_twoSidsSameItemPresenceDifferentPositions() {
        // SID 1: <(1 2)>, SID 2: <(1)(2)>. Both contain items 1 and 2, but only SID 1 matches I-extension.
        var database = db(
                rec(1, seq(itemset(item(1), item(2)))),
                rec(2, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertEquals(1, result.endpointStates().get(0).actualSid());
    }

    @Test
    void testNamedCase07_actualSidsNegativeZeroPositiveNoncontiguous() {
        var database = db(
                rec(-500, seq(itemset(item(1), item(2)))),
                rec(0, seq(itemset(item(1), item(2)))),
                rec(777, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(
                PatternEndpoints.of(-500, new int[]{1}),
                PatternEndpoints.of(0, new int[]{1}),
                PatternEndpoints.of(777, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertEquals(3, result.exactSupport());
        assertArrayEquals(new int[]{-500, 0, 777}, result.supportingSids());
    }

    @Test
    void testNamedCase08_extensionItemAbsent() {
        var database = db(rec(1, seq(itemset(item(1)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifyIExtension(parent, parentStates, item(99), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase09_parentStateEmpty() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var result = verifier.verifyIExtension(parent, List.of(), item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase10_multiItemsetParentMultiItemFinalItemset() {
        // Parent: <(1 2)(3 4)>, Extension: 5 => Child: <(1 2)(3 4 5)>
        var database = db(
                rec(10, seq(itemset(item(1), item(2)), itemset(item(3), item(4), item(5)))),
                rec(20, seq(itemset(item(1), item(2)), itemset(item(3), item(4)), itemset(item(3), item(4), item(5)))));
        var index = buildIndex(database);

        var parent = Pattern.of(itemset(item(1), item(2)), itemset(item(3), item(4)));
        var parentScan = scanDatabase(database, parent);

        var result = verifier.verifyIExtension(parent, parentScan.endpointStates, item(5), index);
        assertEquals(2, result.exactSupport());
        assertArrayEquals(new int[]{10, 20}, result.supportingSids());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(1).endpoints());
    }

    @Test
    void testNamedCase11_repeatedItemsAcrossItemsets() {
        // Pattern: <(1)(1)>, Extension: 2 => Child: <(1)(1 2)>
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.of(itemset(item(1)), itemset(item(1)));
        var parentScan = scanDatabase(database, parent);

        var result = verifier.verifyIExtension(parent, parentScan.endpointStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase12_deterministicOrderAndNoDuplicateSid() {
        var ep1 = PatternEndpoints.of(5, new int[]{1});
        var ep2 = PatternEndpoints.of(1, new int[]{1});
        var ep3 = PatternEndpoints.of(9, new int[]{1});

        var database = db(
                rec(5, seq(itemset(item(1), item(2)))),
                rec(1, seq(itemset(item(1), item(2)))),
                rec(9, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);

        var parent = Pattern.singleton(item(1));
        var parentStates = List.of(ep1, ep2, ep3);

        var result = verifier.verifyIExtension(parent, parentStates, item(2), index);
        assertArrayEquals(new int[]{5, 1, 9}, result.supportingSids(), "Output SID order must match input order exactly");

        // Verify no duplicate SID
        Set<Integer> uniqueSids = new HashSet<>();
        for (int sid : result.supportingSids()) {
            assertTrue(uniqueSids.add(sid), "No duplicate SIDs permitted");
        }
    }

    // =========================================================================
    // Layer D: Fixed-Seed Randomized Structural Differential (1,200 trials)
    // =========================================================================

    @Test
    void testLayerD_fixedSeedRandomizedStructuralDifferential() {
        long seed = 20261030L;
        Random rand = new Random(seed);
        int trials = 1200;

        int completedTrials = 0;
        int mismatches = 0;
        int zeroSupportCases = 0;
        int positiveSupportCases = 0;
        int multipleEndpointCases = 0;
        int multipleSidCases = 0;
        int sameSidDifferentPositionCases = 0;
        int negativeSidCases = 0;
        int sidZeroCases = 0;
        int noncontiguousSidCases = 0;

        for (int trial = 0; trial < trials; trial++) {
            // Generate sequence database: 1..8 sequences
            int seqCount = 1 + rand.nextInt(8);
            List<SequenceRecord> records = new ArrayList<>(seqCount);
            Set<Integer> usedSids = new HashSet<>();

            boolean hasNegative = false;
            boolean hasZero = false;

            for (int s = 0; s < seqCount; s++) {
                int sid;
                do {
                    sid = rand.nextInt(2001) - 1000;
                } while (!usedSids.add(sid));

                if (sid < 0) hasNegative = true;
                if (sid == 0) hasZero = true;

                int itemsetCount = 1 + rand.nextInt(6);
                List<Itemset> itemsets = new ArrayList<>(itemsetCount);

                for (int it = 0; it < itemsetCount; it++) {
                    int itemCount = 1 + rand.nextInt(3);
                    Set<Integer> itemIds = new TreeSet<>();
                    while (itemIds.size() < itemCount) {
                        itemIds.add(1 + rand.nextInt(6));
                    }
                    List<Item> items = new ArrayList<>(itemIds.size());
                    for (int id : itemIds) {
                        items.add(new Item(id));
                    }
                    itemsets.add(new Itemset(items));
                }
                records.add(new SequenceRecord(sid, new Sequence(itemsets)));
            }

            if (hasNegative) negativeSidCases++;
            if (hasZero) sidZeroCases++;
            if (isNoncontiguous(usedSids)) noncontiguousSidCases++;

            SequenceDatabase database = new SequenceDatabase(records);

            // Generate canonical parent pattern: 1..3 itemsets, length <= 4
            int pItemsetCount = 1 + rand.nextInt(3);
            List<Itemset> pItemsets = new ArrayList<>(pItemsetCount);
            int totalLen = 0;

            for (int pi = 0; pi < pItemsetCount; pi++) {
                boolean isLast = (pi == pItemsetCount - 1);
                int maxAllowedItem = isLast ? 5 : 6;
                int maxItems = Math.min(2, 4 - totalLen - (pItemsetCount - 1 - pi));
                if (maxItems < 1) maxItems = 1;
                int count = 1 + rand.nextInt(maxItems);
                Set<Integer> itemIds = new TreeSet<>();
                while (itemIds.size() < count) {
                    itemIds.add(1 + rand.nextInt(maxAllowedItem));
                }
                List<Item> items = new ArrayList<>(itemIds.size());
                for (int id : itemIds) {
                    items.add(new Item(id));
                }
                pItemsets.add(new Itemset(items));
                totalLen += count;
            }

            Pattern parent = new Pattern(pItemsets);
            int lastItemId = parent.lastItem().orElseThrow().id();

            // Canonical I-extension item: must be strictly > lastItemId
            int extId = lastItemId + 1 + rand.nextInt(6 - lastItemId);
            Item extensionItem = new Item(extId);
            Pattern child = patternExtensions.iExtend(parent, extensionItem);

            // 1. Oracle scan for parent endpoints
            OracleScanResult parentScan = scanDatabase(database, parent);

            // 2. Build SUT index & run SUT
            VerticalOccurrenceIndex index = buildIndex(database);
            VerificationResult actual = verifier.verifyIExtension(
                    parent, parentScan.endpointStates, extensionItem, index);

            // 3. Oracle scan for child pattern
            OracleScanResult childScan = scanDatabase(database, child);

            // 4. Failure context with rich reproducible diagnostic details
            String context = String.format(
                    "Trial=%d, seed=%dL%n"
                            + "  Database: %s%n"
                            + "  Parent: %s%n"
                            + "  Extension item: %d%n"
                            + "  Child: %s%n"
                            + "  Expected SID/endpoint map: %s%n"
                            + "  Actual supporting SIDs: %s%n"
                            + "  Actual endpoint states: %s",
                    trial, seed,
                    formatDatabase(database),
                    parent.serialize(),
                    extensionItem.id(),
                    child.serialize(),
                    formatExpectedMap(childScan.sidToEndpoints),
                    Arrays.toString(actual.supportingSids()),
                    actual.endpointStates());

            // 5. Verify match with reproducible mismatch diagnostics
            try {
                assertVerificationMatch(
                        context,
                        actual,
                        childScan.sidToEndpoints,
                        childScan.supportingSids);
            } catch (AssertionError e) {
                mismatches++;
                throw e;
            }

            // 6. Update coverage counters
            if (actual.exactSupport() == 0) {
                zeroSupportCases++;
            } else {
                positiveSupportCases++;
            }

            if (actual.exactSupport() > 1) {
                multipleSidCases++;
            }

            for (PatternEndpoints ep : actual.endpointStates()) {
                if (ep.endpoints().length > 1) {
                    multipleEndpointCases++;
                    break;
                }
            }

            // Check same-SID-different-position: parent supported, extension item present in SID, but child not supported
            for (PatternEndpoints parentEp : parentScan.endpointStates) {
                int sid = parentEp.actualSid();
                int[] occ = index.positions(extensionItem, sid);
                if (occ.length > 0 && !childScan.sidToEndpoints.containsKey(sid)) {
                    sameSidDifferentPositionCases++;
                    break;
                }
            }

            completedTrials++;
        }

        System.out.println("C07.2 Layer D: seed=" + seed);
        System.out.println("C07.2 Layer D: completedTrials=" + completedTrials);
        System.out.println("C07.2 Layer D: mismatches=" + mismatches);
        System.out.println("C07.2 Layer D: zeroSupportCases=" + zeroSupportCases);
        System.out.println("C07.2 Layer D: positiveSupportCases=" + positiveSupportCases);
        System.out.println("C07.2 Layer D: multipleEndpointCases=" + multipleEndpointCases);
        System.out.println("C07.2 Layer D: multipleSidCases=" + multipleSidCases);
        System.out.println("C07.2 Layer D: sameSidDifferentPositionCases=" + sameSidDifferentPositionCases);
        System.out.println("C07.2 Layer D: negativeSidCases=" + negativeSidCases);
        System.out.println("C07.2 Layer D: sidZeroCases=" + sidZeroCases);
        System.out.println("C07.2 Layer D: noncontiguousSidCases=" + noncontiguousSidCases);

        assertEquals(20261030L, seed);
        assertEquals(1200, completedTrials);
        assertEquals(0, mismatches);
        assertTrue(zeroSupportCases > 0, "zeroSupportCases must be > 0");
        assertTrue(positiveSupportCases > 0, "positiveSupportCases must be > 0");
        assertTrue(multipleEndpointCases > 0, "multipleEndpointCases must be > 0");
        assertTrue(multipleSidCases > 0, "multipleSidCases must be > 0");
        assertTrue(sameSidDifferentPositionCases > 0, "sameSidDifferentPositionCases must be > 0");
        assertTrue(negativeSidCases > 0, "negativeSidCases must be > 0");
        assertTrue(sidZeroCases > 0, "sidZeroCases must be > 0");
        assertTrue(noncontiguousSidCases > 0, "noncontiguousSidCases must be > 0");
    }
}
