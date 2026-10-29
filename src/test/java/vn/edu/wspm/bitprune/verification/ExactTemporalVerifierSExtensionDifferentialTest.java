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
 * Exhaustive & randomized differential test suite for {@link ExactTemporalVerifier} S-Extension
 * verification (C08.2).
 *
 * <h2>Oracle Classification & Empirical Evidence Scope</h2>
 * <ul>
 *   <li><b>Oracle A (Independent Specification-Level Algebraic Oracle):</b>
 *       Exhaustively validates, within the bounded six-position catalogue, strictly-later endpoint
 *       matching against boolean position masks over universe {@code {1, 2, 3, 4, 5, 6}} across all
 *       4,096 combinations of parent feasible endpoints and extension item occurrence positions,
 *       confirming that {@code E(P ⊕S x, s) = {p ∈ Occurrences(x, s) | ∃ e ∈ E(P, s) : e < p}}
 *       for the entire bounded universe {@code {1, ..., 6}}.</li>
 *   <li><b>Oracle B (Independent Structural Direct-Scan Oracle) &amp; Randomized Trials:</b>
 *       Exhaustive recursive backtracking subsequence scanner reading raw {@link SequenceDatabase}
 *       records directly (Layer B: 2,800 sequences, 61 parents, 183 pairs) and Layer D fixed-seed
 *       randomized trials (1,200 trials with seed 20261031L). These layers provide independent
 *       differential evidence of correctness over the tested spaces.</li>
 *   <li><b>Evidence Limitation:</b>
 *       This empirical differential evidence confirms 0 mismatches over the specified bounded
 *       exhaustive and fixed-seed randomized spaces; it is not a formal proof for infinite sequence
 *       spaces or arbitrary sequence lengths, and does not replace a general mathematical proof.</li>
 *   <li><b>Shared Domain Infrastructure:</b>
 *       {@link PatternExtensions} and domain value objects ({@link Item}, {@link Itemset},
 *       {@link Pattern}, {@link Sequence}, {@link SequenceRecord}, {@link SequenceDatabase})
 *       are accepted shared data models, not independent pattern oracles.</li>
 * </ul>
 */
class ExactTemporalVerifierSExtensionDifferentialTest {

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
                VerificationResult actual = verifier.verifySExtension(parent, parentStates, extItem, index);

                // Oracle A Expected: nested specification scan {p in B | exists e in A: e < p}
                List<Integer> expectedEps = new ArrayList<>();
                for (int pos = 1; pos <= 6; pos++) {
                    if ((maskB & (1 << (pos - 1))) != 0) {
                        boolean hasStrictlyEarlierParent = false;
                        for (int pEp : pEps) {
                            if (pEp < pos) {
                                hasStrictlyEarlierParent = true;
                                break;
                            }
                        }
                        if (hasStrictlyEarlierParent) {
                            expectedEps.add(pos);
                        }
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

        System.out.println("C08.2 Layer A: endpointMaskComparisons=" + endpointMaskComparisons);
        System.out.println("C08.2 Layer A: endpointMaskMismatches=" + endpointMaskMismatches);

        assertEquals(4096, endpointMaskComparisons, "Must execute exactly 4,096 mask comparisons");
        assertEquals(0, endpointMaskMismatches, "Exhaustive mask mismatches must be zero");
    }

    // =========================================================================
    // Layer B: Exhaustive Structural Catalogue (2,800 sequences, 61 parents, 183 pairs)
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

        List<Itemset> singletons = List.of(
                itemset(i1),
                itemset(i2),
                itemset(i3));

        // Generate exactly 2,800 sequences: 7 + 7^2 + 7^3 + 7^4
        List<SequenceRecord> records = new ArrayList<>(2800);
        int sidCounter = -1000; // Covers negative, zero, and positive SIDs: -1000 to 1799

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

        // Generate exactly 61 parent patterns:
        // 1 itemset: 7
        // 2 itemsets with total length <= 3: 27
        // 3 singleton itemsets: 27
        List<Pattern> parentPatterns = new ArrayList<>(61);

        // 1 itemset (7)
        for (Itemset s1 : baseItemsets) {
            parentPatterns.add(Pattern.of(s1));
        }

        // 2 itemsets with total length <= 3 (27: 9 + 9 + 9)
        for (Itemset s1 : baseItemsets) {
            for (Itemset s2 : baseItemsets) {
                if (s1.items().size() + s2.items().size() <= 3) {
                    parentPatterns.add(Pattern.of(s1, s2));
                }
            }
        }

        // 3 singleton itemsets (27: 3 * 3 * 3)
        for (Itemset s1 : singletons) {
            for (Itemset s2 : singletons) {
                for (Itemset s3 : singletons) {
                    parentPatterns.add(Pattern.of(s1, s2, s3));
                }
            }
        }

        assertEquals(61, parentPatterns.size(), "Catalogue must contain exactly 61 parent patterns");

        // 61 parents * 3 extension items {1, 2, 3} = 183 pairs
        List<Item> universeItems = List.of(i1, i2, i3);
        List<Map.Entry<Pattern, Item>> pairs = new ArrayList<>(183);
        for (Pattern p : parentPatterns) {
            for (Item ext : universeItems) {
                pairs.add(Map.entry(p, ext));
            }
        }
        assertEquals(183, pairs.size(), "Catalogue must contain exactly 183 parent/extension pairs");

        int structuralComparisons = 0;
        int structuralMismatches = 0;
        int exhaustiveCatalogueZeroSupportCases = 0;
        int repeatedExtensionPairs = 0;
        int extensionSmallerThanParentLastPairs = 0;
        int extensionEqualToParentLastPairs = 0;
        int multipleEndpointCases = 0;
        int multipleSidCases = 0;

        for (Map.Entry<Pattern, Item> entry : pairs) {
            Pattern parent = entry.getKey();
            Item extItem = entry.getValue();
            structuralComparisons++;

            int lastItemId = parent.lastItem().orElseThrow().id();
            if (extItem.id() < lastItemId) {
                extensionSmallerThanParentLastPairs++;
            } else if (extItem.id() == lastItemId) {
                extensionEqualToParentLastPairs++;
            }

            boolean isRepeated = false;
            for (Itemset is : parent.itemsets()) {
                if (is.contains(extItem)) {
                    isRepeated = true;
                    break;
                }
            }
            if (isRepeated) {
                repeatedExtensionPairs++;
            }

            // 1. Oracle scan for parent
            OracleScanResult parentScan = scanDatabase(database, parent);

            // 2. SUT verification
            VerificationResult actual = verifier.verifySExtension(
                    parent, parentScan.endpointStates, extItem, index);

            // 3. Oracle scan for child pattern
            Pattern child = patternExtensions.sExtend(parent, extItem);
            OracleScanResult childScan = scanDatabase(database, child);

            if (childScan.supportingSids.length == 0) {
                exhaustiveCatalogueZeroSupportCases++;
            }
            if (childScan.supportingSids.length > 1) {
                multipleSidCases++;
            }

            for (int[] eps : childScan.sidToEndpoints.values()) {
                if (eps.length > 1) {
                    multipleEndpointCases++;
                    break;
                }
            }

            // 4. Comparison with catalogue context
            String context = String.format(
                    "Catalogue: EXHAUSTIVE_S_U3_LENGTH_1_TO_4_2800%n"
                            + "  Parent: %s%n"
                            + "  Extension: %d%n"
                            + "  Child: %s%n"
                            + "  Expected SID/endpoint map: %s%n"
                            + "  Actual supporting SIDs: %s%n"
                            + "  Actual endpoint states: %s",
                    parent.serialize(),
                    extItem.id(),
                    child.serialize(),
                    formatExpectedMap(childScan.sidToEndpoints),
                    Arrays.toString(actual.supportingSids()),
                    actual.endpointStates());

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
        }

        // Dedicated structural zero-support fixture
        int dedicatedStructuralZeroSupportCases = 0;
        SequenceDatabase zeroDb = db(
                rec(1, seq(itemset(i1, i2))),
                rec(2, seq(itemset(i2), itemset(i1))));
        VerticalOccurrenceIndex zeroIndex = buildIndex(zeroDb);
        Pattern zeroParent = Pattern.of(itemset(i1));
        OracleScanResult zeroParentScan = scanDatabase(zeroDb, zeroParent);
        VerificationResult zeroActual = verifier.verifySExtension(
                zeroParent, zeroParentScan.endpointStates, i2, zeroIndex);
        Pattern zeroChild = patternExtensions.sExtend(zeroParent, i2);
        OracleScanResult zeroChildScan = scanDatabase(zeroDb, zeroChild);

        assertVerificationMatch(
                "DedicatedStructuralZeroSupportFixture",
                zeroActual,
                zeroChildScan.sidToEndpoints,
                zeroChildScan.supportingSids);
        if (zeroActual.exactSupport() == 0) {
            dedicatedStructuralZeroSupportCases++;
        }

        System.out.println("C08.2 Layer B: structuralSequenceCount=" + records.size());
        System.out.println("C08.2 Layer B: structuralParentPatternCount=" + parentPatterns.size());
        System.out.println("C08.2 Layer B: structuralPairCount=" + pairs.size());
        System.out.println("C08.2 Layer B: structuralComparisons=" + structuralComparisons);
        System.out.println("C08.2 Layer B: structuralMismatches=" + structuralMismatches);
        System.out.println("C08.2 Layer B: exhaustiveCatalogueZeroSupportCases=" + exhaustiveCatalogueZeroSupportCases);
        System.out.println("C08.2 Layer B: dedicatedStructuralZeroSupportCases=" + dedicatedStructuralZeroSupportCases);
        System.out.println("C08.2 Layer B: repeatedExtensionPairs=" + repeatedExtensionPairs);
        System.out.println("C08.2 Layer B: extensionSmallerThanParentLastPairs=" + extensionSmallerThanParentLastPairs);
        System.out.println("C08.2 Layer B: extensionEqualToParentLastPairs=" + extensionEqualToParentLastPairs);
        System.out.println("C08.2 Layer B: multipleEndpointCases=" + multipleEndpointCases);
        System.out.println("C08.2 Layer B: multipleSidCases=" + multipleSidCases);

        assertEquals(2800, records.size());
        assertEquals(61, parentPatterns.size());
        assertEquals(183, pairs.size());
        assertEquals(183, structuralComparisons);
        assertEquals(0, structuralMismatches);
        assertEquals(0, exhaustiveCatalogueZeroSupportCases, "Exhaustive catalogue has witness for every pattern");
        assertTrue(dedicatedStructuralZeroSupportCases > 0, "Dedicated structural zero-support fixture must be verified");
        assertTrue(repeatedExtensionPairs > 0, "repeatedExtensionPairs must be > 0");
        assertTrue(extensionSmallerThanParentLastPairs > 0, "extensionSmallerThanParentLastPairs must be > 0");
        assertTrue(extensionEqualToParentLastPairs > 0, "extensionEqualToParentLastPairs must be > 0");
        assertTrue(multipleEndpointCases > 0, "multipleEndpointCases must be > 0");
        assertTrue(multipleSidCases > 0, "multipleSidCases must be > 0");
    }

    // =========================================================================
    // Layer C: Named Counterexamples & Edge Cases (16 Named Cases)
    // =========================================================================

    @Test
    void testNamedCase01_basicStrictlyLaterMatch() {
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase02_sameItemsetOnlyRejection() {
        var database = db(rec(1, seq(itemset(item(1), item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase03_extensionStrictlyBeforeParentRejection() {
        var database = db(rec(1, seq(itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{2}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase04_reverseOrderRejection() {
        var database = db(rec(42, seq(itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(42, new int[]{2}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase05_repeatedItemExtension() {
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2}));

        var result = verifier.verifySExtension(parent, parentStates, item(1), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase06_extensionItemSmallerThanParentLastItem() {
        var database = db(rec(1, seq(itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(2)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(1), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase07_namedCounterexampleCE02() {
        // S = <(1), (2), (1)>, P = <(1)>, E(P, S) = {1, 3}, ext = 2 => E(P ⊕S 2, S) = {2}
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var allEndpoints = List.of(PatternEndpoints.of(1, new int[]{1, 3}));

        var result = verifier.verifySExtension(parent, allEndpoints, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2}, result.endpointStates().get(0).endpoints());

        // Latest-only compression causes false negative (support 0)
        var latestOnly = List.of(PatternEndpoints.of(1, new int[]{3}));
        var flawedResult = verifier.verifySExtension(parent, latestOnly, item(2), index);
        assertEquals(0, flawedResult.exactSupport());
    }

    @Test
    void testNamedCase08_multipleParentEndpointsOnlyEarlyAllowsChild() {
        // Parent endpoints: {2, 5}. Extension occurs at 3. 2 < 3 allows child, 5 < 3 does not.
        var database = db(rec(1, seq(
                itemset(item(3)),
                itemset(item(1)),
                itemset(item(2)),
                itemset(item(3)),
                itemset(item(1)),
                itemset(item(3)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{2, 5}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase09_multipleExtensionPositionsAllPreserved() {
        var database = db(rec(1, seq(
                itemset(item(1)),
                itemset(item(2)),
                itemset(item(3)),
                itemset(item(2)),
                itemset(item(3)),
                itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2, 4, 6}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase10_childReachableFromMultipleParentsRecordedOnce() {
        var database = db(rec(1, seq(
                itemset(item(1)),
                itemset(item(1)),
                itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 2}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase11_multipleEmbeddingsExactSupportRemainsOne() {
        var database = db(rec(1, seq(
                itemset(item(1)),
                itemset(item(2)),
                itemset(item(1)),
                itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1, 3}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{2, 4}, result.endpointStates().get(0).endpoints());
    }

    @Test
    void testNamedCase12_actualSidsNegativeZeroPositiveNoncontiguous() {
        var database = db(
                rec(-500, seq(itemset(item(1)), itemset(item(2)))),
                rec(0, seq(itemset(item(1)), itemset(item(2)))),
                rec(777, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(
                PatternEndpoints.of(-500, new int[]{1}),
                PatternEndpoints.of(0, new int[]{1}),
                PatternEndpoints.of(777, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(3, result.exactSupport());
        assertArrayEquals(new int[]{-500, 0, 777}, result.supportingSids());
    }

    @Test
    void testNamedCase13_similarItemPresenceDifferentTemporalSupport() {
        // SID 1: <(1), (2)> (forward, matches S-extension)
        // SID 2: <(1 2)> (same itemset, rejects S-extension)
        // SID 3: <(2), (1)> (reverse order, rejects S-extension)
        var database = db(
                rec(1, seq(itemset(item(1)), itemset(item(2)))),
                rec(2, seq(itemset(item(1), item(2)))),
                rec(3, seq(itemset(item(2)), itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(
                PatternEndpoints.of(1, new int[]{1}),
                PatternEndpoints.of(2, new int[]{1}),
                PatternEndpoints.of(3, new int[]{2}));

        var result = verifier.verifySExtension(parent, parentStates, item(2), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{1}, result.supportingSids());
    }

    @Test
    void testNamedCase14_extensionItemAbsent() {
        var database = db(rec(1, seq(itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));
        var parentStates = List.of(PatternEndpoints.of(1, new int[]{1}));

        var result = verifier.verifySExtension(parent, parentStates, item(99), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase15_emptyParentStateList() {
        var database = db(rec(1, seq(itemset(item(1)), itemset(item(2)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1)));

        var result = verifier.verifySExtension(parent, List.of(), item(2), index);
        assertEquals(0, result.exactSupport());
        assertTrue(result.endpointStates().isEmpty());
    }

    @Test
    void testNamedCase16_multiItemsetParentAndRepeatedItem() {
        // Parent: <(1 2), (1)>, Extension: 1 => Child: <(1 2), (1), (1)>
        var database = db(rec(1, seq(
                itemset(item(1), item(2)),
                itemset(item(1)),
                itemset(item(1)))));
        var index = buildIndex(database);
        var parent = Pattern.of(itemset(item(1), item(2)), itemset(item(1)));
        var parentScan = scanDatabase(database, parent);

        var result = verifier.verifySExtension(parent, parentScan.endpointStates, item(1), index);
        assertEquals(1, result.exactSupport());
        assertArrayEquals(new int[]{3}, result.endpointStates().get(0).endpoints());
    }

    // =========================================================================
    // Layer D: Fixed-Seed Randomized Structural Differential (1,200 trials)
    // =========================================================================

    @Test
    void testLayerD_fixedSeedRandomizedStructuralDifferential() {
        long seed = 20261031L;
        Random rand = new Random(seed);
        int trials = 1200;

        int completedTrials = 0;
        int mismatches = 0;
        int zeroSupportCases = 0;
        int positiveSupportCases = 0;
        int repeatedExtensionCases = 0;
        int extensionSmallerThanParentLastCases = 0;
        int extensionEqualToParentLastCases = 0;
        int sameItemsetOnlyNonmatchCases = 0;
        int reverseOrderNonmatchCases = 0;
        int multipleParentEndpointCases = 0;
        int multipleChildEndpointCases = 0;
        int multipleSidCases = 0;
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
                    if (rand.nextInt(30) == 0) {
                        sid = 0;
                    } else {
                        sid = rand.nextInt(2001) - 1000;
                    }
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

            // Generate parent pattern: 1..3 itemsets, total length <= 4
            int pItemsetCount = 1 + rand.nextInt(3);
            List<Itemset> pItemsets = new ArrayList<>(pItemsetCount);
            int totalLen = 0;

            for (int pi = 0; pi < pItemsetCount; pi++) {
                int maxItems = Math.min(3, 4 - totalLen - (pItemsetCount - 1 - pi));
                if (maxItems < 1) maxItems = 1;
                int count = 1 + rand.nextInt(maxItems);
                Set<Integer> itemIds = new TreeSet<>();
                while (itemIds.size() < count) {
                    itemIds.add(1 + rand.nextInt(6));
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

            // S-extension item chosen independently from {1..6}
            int extId = 1 + rand.nextInt(6);
            Item extensionItem = new Item(extId);
            Pattern child = patternExtensions.sExtend(parent, extensionItem);

            // Check parent relationship counters
            boolean isRepeated = false;
            for (Itemset is : parent.itemsets()) {
                if (is.contains(extensionItem)) {
                    isRepeated = true;
                    break;
                }
            }
            if (isRepeated) repeatedExtensionCases++;
            if (extId < lastItemId) extensionSmallerThanParentLastCases++;
            if (extId == lastItemId) extensionEqualToParentLastCases++;

            // 1. Oracle scan for parent endpoints
            OracleScanResult parentScan = scanDatabase(database, parent);

            // 2. Build SUT index & run SUT
            VerticalOccurrenceIndex index = buildIndex(database);
            VerificationResult actual = verifier.verifySExtension(
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

            for (PatternEndpoints ep : parentScan.endpointStates) {
                if (ep.endpoints().length > 1) {
                    multipleParentEndpointCases++;
                    break;
                }
            }

            for (PatternEndpoints ep : actual.endpointStates()) {
                if (ep.endpoints().length > 1) {
                    multipleChildEndpointCases++;
                    break;
                }
            }

            // Check sameItemsetOnlyNonmatch and reverseOrderNonmatch
            boolean trialHasSameItemsetOnly = false;
            boolean trialHasReverseOrder = false;

            for (PatternEndpoints parentEp : parentScan.endpointStates) {
                int sid = parentEp.actualSid();
                int[] occ = index.positions(extensionItem, sid);
                boolean childSupportedInSid = childScan.sidToEndpoints.containsKey(sid);

                if (occ.length > 0 && !childSupportedInSid) {
                    boolean hasSame = false;
                    for (int ep : parentEp.endpoints()) {
                        for (int p : occ) {
                            if (ep == p) {
                                hasSame = true;
                                break;
                            }
                        }
                    }
                    if (hasSame) trialHasSameItemsetOnly = true;

                    boolean allBefore = true;
                    int minParent = parentEp.endpoints()[0];
                    for (int p : occ) {
                        if (p >= minParent) {
                            allBefore = false;
                            break;
                        }
                    }
                    if (allBefore) trialHasReverseOrder = true;
                }
            }

            if (trialHasSameItemsetOnly) sameItemsetOnlyNonmatchCases++;
            if (trialHasReverseOrder) reverseOrderNonmatchCases++;

            completedTrials++;
        }

        System.out.println("C08.2 Layer D: seed=" + seed);
        System.out.println("C08.2 Layer D: completedTrials=" + completedTrials);
        System.out.println("C08.2 Layer D: mismatches=" + mismatches);
        System.out.println("C08.2 Layer D: zeroSupportCases=" + zeroSupportCases);
        System.out.println("C08.2 Layer D: positiveSupportCases=" + positiveSupportCases);
        System.out.println("C08.2 Layer D: repeatedExtensionCases=" + repeatedExtensionCases);
        System.out.println("C08.2 Layer D: extensionSmallerThanParentLastCases=" + extensionSmallerThanParentLastCases);
        System.out.println("C08.2 Layer D: extensionEqualToParentLastCases=" + extensionEqualToParentLastCases);
        System.out.println("C08.2 Layer D: sameItemsetOnlyNonmatchCases=" + sameItemsetOnlyNonmatchCases);
        System.out.println("C08.2 Layer D: reverseOrderNonmatchCases=" + reverseOrderNonmatchCases);
        System.out.println("C08.2 Layer D: multipleParentEndpointCases=" + multipleParentEndpointCases);
        System.out.println("C08.2 Layer D: multipleChildEndpointCases=" + multipleChildEndpointCases);
        System.out.println("C08.2 Layer D: multipleSidCases=" + multipleSidCases);
        System.out.println("C08.2 Layer D: negativeSidCases=" + negativeSidCases);
        System.out.println("C08.2 Layer D: sidZeroCases=" + sidZeroCases);
        System.out.println("C08.2 Layer D: noncontiguousSidCases=" + noncontiguousSidCases);

        assertEquals(20261031L, seed);
        assertEquals(1200, completedTrials);
        assertEquals(0, mismatches);
        assertTrue(zeroSupportCases > 0, "zeroSupportCases must be > 0");
        assertTrue(positiveSupportCases > 0, "positiveSupportCases must be > 0");
        assertTrue(repeatedExtensionCases > 0, "repeatedExtensionCases must be > 0");
        assertTrue(extensionSmallerThanParentLastCases > 0, "extensionSmallerThanParentLastCases must be > 0");
        assertTrue(extensionEqualToParentLastCases > 0, "extensionEqualToParentLastCases must be > 0");
        assertTrue(sameItemsetOnlyNonmatchCases > 0, "sameItemsetOnlyNonmatchCases must be > 0");
        assertTrue(reverseOrderNonmatchCases > 0, "reverseOrderNonmatchCases must be > 0");
        assertTrue(multipleParentEndpointCases > 0, "multipleParentEndpointCases must be > 0");
        assertTrue(multipleChildEndpointCases > 0, "multipleChildEndpointCases must be > 0");
        assertTrue(multipleSidCases > 0, "multipleSidCases must be > 0");
        assertTrue(negativeSidCases > 0, "negativeSidCases must be > 0");
        assertTrue(sidZeroCases > 0, "sidZeroCases must be > 0");
        assertTrue(noncontiguousSidCases > 0, "noncontiguousSidCases must be > 0");
    }
}

