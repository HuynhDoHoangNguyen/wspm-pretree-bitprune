package vn.edu.wspm.bitprune.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Pattern;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Exhaustive & structural differential safety evidence for C06.2.
 *
 * <p>
 * Validates the {@link BitmapUpperBoundFilter} across three complementary oracle layers:
 * <ul>
 * <li><b>Oracle O-A (Baseline-Relative Conformance):</b> Baseline-relative conformance oracle
 * using the frozen {@link ResolvedMinimumSupport#meetsThreshold(int, double)} predicate.
 * Exhaustive support-interval verification across all sequence counts [0, 8], all candidate
 * cardinalities, and all hypothetical supports in [0, cardinality] is independent of the
 * production filter's monotonicity shortcut. This is not an independent exact-real semantic
 * oracle and not an independent structural oracle.</li>
 * <li><b>Oracle O-B (Exact-Rational Audit):</b> Audit comparing frozen binary64 baseline
 * behavior against exact rational arithmetic, explicitly tracking and reporting
 * rounding discrepancies (including the mandatory 14% × 25 counterexample).</li>
 * <li><b>Oracle O-C (Independent Structural Direct-Scan):</b> Independent structural direct-scan
 * oracle with Set-based candidate intersection and greedy subsequence scanning verifying that
 * {@code ActualSupportingSIDs(P) ⊆ CandidateBitmapSIDs(P)} and
 * {@code support(P) ≤ candidateBitmap.cardinality()}, with deterministic and fixed-seed
 * randomized differential evidence.</li>
 * </ul>
 */
class BitmapUpperBoundFilterDifferentialTest {

    private static final MinimumSupportResolver RESOLVER = new MinimumSupportResolver();
    private static final BitmapUpperBoundFilter FILTER = new BitmapUpperBoundFilter();

    // =========================================================================
    // Test-Local Helper Classes & Rational Arithmetic (Oracle O-B)
    // =========================================================================

    /**
     * Exact rational representation using {@link BigInteger} to compute exact
     * mathematical
     * values without floating-point rounding or binary64 approximation.
     */
    static final class Rational implements Comparable<Rational> {
        private final BigInteger num;
        private final BigInteger den;

        public Rational(long num, long den) {
            this(BigInteger.valueOf(num), BigInteger.valueOf(den));
        }

        public Rational(BigInteger num, BigInteger den) {
            Objects.requireNonNull(num, "numerator must not be null");
            Objects.requireNonNull(den, "denominator must not be null");
            if (den.signum() == 0) {
                throw new ArithmeticException("Denominator must not be zero");
            }
            if (den.signum() < 0) {
                num = num.negate();
                den = den.negate();
            }
            BigInteger gcd = num.gcd(den);
            this.num = num.divide(gcd);
            this.den = den.divide(gcd);
        }

        public static Rational of(long val) {
            return new Rational(val, 1L);
        }

        public static Rational parseDecimal(String decimalStr) {
            String clean = decimalStr.trim();
            if (!clean.contains(".")) {
                return of(Long.parseLong(clean));
            }
            String[] parts = clean.split("\\.");
            String whole = parts[0];
            String frac = parts.length > 1 ? parts[1] : "";
            int scale = frac.length();
            BigInteger unscaled = new BigInteger(whole + frac);
            BigInteger den = BigInteger.TEN.pow(scale);
            return new Rational(unscaled, den);
        }

        public Rational multiply(Rational other) {
            return new Rational(this.num.multiply(other.num), this.den.multiply(other.den));
        }

        public Rational divide(long scalar) {
            return new Rational(this.num, this.den.multiply(BigInteger.valueOf(scalar)));
        }

        @Override
        public int compareTo(Rational other) {
            return this.num.multiply(other.den).compareTo(other.num.multiply(this.den));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof Rational rational))
                return false;
            return num.equals(rational.num) && den.equals(rational.den);
        }

        @Override
        public int hashCode() {
            return 31 * num.hashCode() + den.hashCode();
        }

        @Override
        public String toString() {
            return num + "/" + den;
        }
    }

    enum DisagreementClassification {
        AGREE,
        BASELINE_PRUNE_EXACT_KEEP,
        BASELINE_KEEP_EXACT_PRUNE
    }

    // =========================================================================
    // Test-Local Helper Fixtures & Structural Oracle Methods (Oracle O-C)
    // =========================================================================

    private static Set<Item> distinctItems(Pattern pattern) {
        Set<Item> distinct = new LinkedHashSet<>();
        for (Itemset itemset : pattern.itemsets()) {
            distinct.addAll(itemset.items());
        }
        return distinct;
    }

    private static boolean sequenceContainsItem(Sequence sequence, Item item) {
        for (Itemset itemset : sequence.itemsets()) {
            if (itemset.contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Independent Set-based oracle for candidate supporting SIDs via presence
     * intersection.
     * Does NOT use SidBitmap.and or any production BitPrune logic.
     */
    private static Set<Integer> candidateSidsByPresenceIntersection(SequenceDatabase db, Pattern pattern) {
        Set<Item> distinct = distinctItems(pattern);
        if (distinct.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Integer> result = null;
        for (Item item : distinct) {
            Set<Integer> itemSids = new HashSet<>();
            for (SequenceRecord record : db.records()) {
                if (sequenceContainsItem(record.sequence(), item)) {
                    itemSids.add(record.sid());
                }
            }
            if (result == null) {
                result = new HashSet<>(itemSids);
            } else {
                result.retainAll(itemSids);
            }
        }
        return result != null ? result : Collections.emptySet();
    }

    /**
     * Direct greedy subsequence scan (pure test oracle; not a production verifier).
     * Satisfies:
     * 1. Iterates pattern itemsets in temporal order.
     * 2. Each pattern itemset must be a subset of exactly one database itemset.
     * 3. Selected database itemsets must have strictly increasing indices.
     * 4. Multi-item itemsets require same-itemset inclusion.
     */
    private static boolean directlySupports(Sequence sequence, Pattern pattern) {
        if (pattern.isEmpty()) {
            return true;
        }
        List<Itemset> pItemsets = pattern.itemsets();
        List<Itemset> sItemsets = sequence.itemsets();
        int pIdx = 0;
        for (int sIdx = 0; sIdx < sItemsets.size() && pIdx < pItemsets.size(); sIdx++) {
            Itemset pItemset = pItemsets.get(pIdx);
            Itemset sItemset = sItemsets.get(sIdx);
            if (sItemset.items().containsAll(pItemset.items())) {
                pIdx++;
            }
        }
        return pIdx == pItemsets.size();
    }

    /**
     * Returns the set of distinct actual SIDs whose sequence directly supports the
     * pattern.
     * Single SID counted at most once regardless of multiple embeddings.
     */
    private static Set<Integer> exactSupportingSids(SequenceDatabase db, Pattern pattern) {
        Set<Integer> sids = new HashSet<>();
        for (SequenceRecord record : db.records()) {
            if (directlySupports(record.sequence(), pattern)) {
                sids.add(record.sid());
            }
        }
        return sids;
    }

    /**
     * Converts an independently computed candidate SID Set into a {@link SidBitmap}
     * using the given {@link SidMapping} and {@link SidBitmap.Builder}.
     */
    private static SidBitmap bitmapWithCandidateSids(SidMapping mapping, Set<Integer> candidateSids) {
        SidBitmap.Builder builder = SidBitmap.builder(mapping);
        for (int sid : candidateSids) {
            builder.setSid(sid);
        }
        return builder.build();
    }

    private static SidBitmap bitmapWithCardinality(int sequenceCount, int cardinality) {
        int[] sids = new int[sequenceCount];
        for (int i = 0; i < sequenceCount; i++) {
            sids[i] = 1000 + i;
        }
        List<SequenceRecord> records = new ArrayList<>(sequenceCount);
        for (int sid : sids) {
            records.add(new SequenceRecord(sid, Sequence.of(Itemset.of(new Item(1)))));
        }
        SidMapping mapping = SidMapping.of(new SequenceDatabase(records));
        SidBitmap.Builder builder = SidBitmap.builder(mapping);
        for (int i = 0; i < cardinality; i++) {
            builder.setIndex(i);
        }
        return builder.build();
    }

    /**
     * Asserts that if the production filter prunes, the baseline predicate rejects
     * every
     * hypothetical support s in [0, cardinality].
     */
    private static void assertBaselineIntervalSafety(
            SidBitmap bitmap, double meanWeight, ResolvedMinimumSupport resolved, String context) {
        boolean canPrune = FILTER.canPrune(bitmap, meanWeight, resolved);
        if (canPrune) {
            int cardinality = bitmap.cardinality();
            for (int s = 0; s <= cardinality; s++) {
                assertFalse(
                        resolved.meetsThreshold(s, meanWeight),
                        "[baseline-relative oracle] Monotonicity violation (" + context
                                + "): filter pruned at cardinality=" + cardinality
                                + " but baseline accepts support=" + s);
            }
        }
    }

    // =========================================================================
    // Section 12: Deterministic Named Counterexample Tests (Tests 1–10)
    // =========================================================================

    /**
     * Test 1: Equality: upper bound == threshold => MUST NOT prune.
     */
    @Test
    void equality_upperBoundEqualsThreshold_doesNotPrune() {
        // 2 * 0.5 = 1.0 == 1.0 (exact binary64 representable)
        SidBitmap bitmap = bitmapWithCardinality(2, 2);
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 2);
        double meanWeight = 0.5;

        boolean canPrune = FILTER.canPrune(bitmap, meanWeight, resolved);

        assertFalse(canPrune, "[baseline-relative oracle] Equality (2 * 0.5 == 1.0) must not be pruned");
        assertTrue(resolved.meetsThreshold(2, meanWeight),
                "[baseline-relative oracle] Baseline predicate must accept exact equality");
    }

    /**
     * Test 2: Just below threshold => MUST prune.
     */
    @Test
    void nearEquality_justBelowThreshold_prunes() {
        SidBitmap bitmap = bitmapWithCardinality(2, 1);
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 2);
        double meanWeight = Math.nextDown(1.0);

        boolean canPrune = FILTER.canPrune(bitmap, meanWeight, resolved);

        assertTrue(canPrune, "[baseline-relative oracle] 1 * nextDown(1.0) < 1.0 must be pruned");
        assertBaselineIntervalSafety(bitmap, meanWeight, resolved, "nearEquality_justBelowThreshold");
    }

    /**
     * Test 3: Threshold zero => MUST NOT prune, including empty bitmap.
     */
    @Test
    void thresholdZero_emptyOrNonEmpty_doesNotPrune() {
        ResolvedMinimumSupport resolvedZero = RESOLVER.resolve(MinimumSupport.absolute(0.0), 5);

        SidBitmap emptyBm = bitmapWithCardinality(5, 0);
        assertFalse(FILTER.canPrune(emptyBm, 0.5, resolvedZero),
                "[baseline-relative oracle] Threshold 0.0 with empty bitmap must not prune (no-op policy)");

        SidBitmap nonEmptyBm = bitmapWithCardinality(5, 3);
        assertFalse(FILTER.canPrune(nonEmptyBm, 0.5, resolvedZero),
                "[baseline-relative oracle] Threshold 0.0 with non-empty bitmap must not prune (no-op policy)");
    }

    /**
     * Test 4: Empty bitmap, positive threshold => MUST prune.
     */
    @Test
    void emptyBitmap_positiveThreshold_prunes() {
        SidBitmap emptyBm = bitmapWithCardinality(5, 0);
        ResolvedMinimumSupport resolvedPos = RESOLVER.resolve(MinimumSupport.absolute(1.0), 5);

        boolean canPrune = FILTER.canPrune(emptyBm, 0.5, resolvedPos);

        assertTrue(canPrune, "[baseline-relative oracle] Empty bitmap with positive threshold must prune");
        assertBaselineIntervalSafety(emptyBm, 0.5, resolvedPos, "emptyBitmap_positiveThreshold");
    }

    /**
     * Test 5: Reverse-order temporal false positive.
     * Sequence has (2) then (1); Pattern is <(1)(2)>.
     * Both items present -> Candidate SID set = {100} (cardinality 1).
     * Direct scan -> pattern not matched -> exact support = 0.
     * Cardinality (1) is a loose upper bound for exact support (0).
     */
    @Test
    void reverseOrder_temporalFalsePositive_structuralDirectScan() {
        Item item1 = new Item(1);
        Item item2 = new Item(2);
        SequenceRecord rec = new SequenceRecord(100, Sequence.of(Itemset.of(item2), Itemset.of(item1)));
        SequenceDatabase db = new SequenceDatabase(List.of(rec));
        Pattern pattern = Pattern.of(Itemset.of(item1), Itemset.of(item2));

        Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);
        Set<Integer> exactSids = exactSupportingSids(db, pattern);

        assertEquals(Set.of(100), candidateSids,
                "[structural direct-scan oracle] Candidate presence intersection must include SID 100");
        assertEquals(Collections.emptySet(), exactSids,
                "[structural direct-scan oracle] Reverse temporal order must have 0 exact supporting SIDs");
        assertTrue(candidateSids.containsAll(exactSids),
                "[structural direct-scan oracle] Exact supporting SIDs must be a subset of candidate SIDs");
        assertTrue(exactSids.size() <= candidateSids.size(),
                "[structural direct-scan oracle] exactSupport (0) must be <= candidate cardinality (1)");

        SidMapping mapping = SidMapping.of(db);
        SidBitmap bitmap = bitmapWithCandidateSids(mapping, candidateSids);
        assertEquals(1, bitmap.cardinality());

        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 1);
        double meanWeight = 0.5; // 1 * 0.5 = 0.5 < 1.0 -> prune
        boolean canPrune = FILTER.canPrune(bitmap, meanWeight, resolved);
        assertTrue(canPrune, "[baseline-relative oracle] 1 * 0.5 < 1.0 must prune candidate");
        assertBaselineIntervalSafety(bitmap, meanWeight, resolved, "reverseOrder");
    }

    /**
     * Test 6: Same-itemset boundary false positive.
     * Sequence has (1) and (2) in separate itemsets; Pattern requires same itemset
     * <(1 2)>.
     * Candidate contains SID, but exact support is 0.
     */
    @Test
    void sameItemsetBoundary_falsePositive_structuralDirectScan() {
        Item item1 = new Item(1);
        Item item2 = new Item(2);
        SequenceRecord rec = new SequenceRecord(200, Sequence.of(Itemset.of(item1), Itemset.of(item2)));
        SequenceDatabase db = new SequenceDatabase(List.of(rec));
        Pattern pattern = Pattern.of(Itemset.of(item1, item2));

        Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);
        Set<Integer> exactSids = exactSupportingSids(db, pattern);

        assertEquals(Set.of(200), candidateSids,
                "[structural direct-scan oracle] Both items present -> candidate SID set has 200");
        assertEquals(Collections.emptySet(), exactSids,
                "[structural direct-scan oracle] Items in different itemsets -> exact support is 0");
        assertTrue(candidateSids.containsAll(exactSids));
        assertTrue(exactSids.size() <= candidateSids.size());

        SidMapping mapping = SidMapping.of(db);
        SidBitmap bitmap = bitmapWithCandidateSids(mapping, candidateSids);
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 1);
        assertBaselineIntervalSafety(bitmap, 0.5, resolved, "sameItemsetBoundary");
    }

    /**
     * Test 7: Repeated item <(1)(1)> loose upper bound.
     * Sequence has only one occurrence of (1).
     * Distinct item set is {1}, so candidate bitmap contains SID.
     * But pattern requires item 1 at two distinct temporal positions.
     */
    @Test
    void repeatedItem_looseUpperBound_structuralDirectScan() {
        Item item1 = new Item(1);
        SequenceRecord rec = new SequenceRecord(300, Sequence.of(Itemset.of(item1)));
        SequenceDatabase db = new SequenceDatabase(List.of(rec));
        Pattern pattern = Pattern.of(Itemset.of(item1), Itemset.of(item1));

        Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);
        Set<Integer> exactSids = exactSupportingSids(db, pattern);

        assertEquals(Set.of(300), candidateSids,
                "[structural direct-scan oracle] Distinct items {1} are present -> candidate has SID");
        assertEquals(Collections.emptySet(), exactSids,
                "[structural direct-scan oracle] Sequence has only 1 occurrence -> exact support is 0");
        assertTrue(candidateSids.containsAll(exactSids));
        assertTrue(exactSids.size() <= candidateSids.size());

        SidMapping mapping = SidMapping.of(db);
        SidBitmap bitmap = bitmapWithCandidateSids(mapping, candidateSids);
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 1);
        assertBaselineIntervalSafety(bitmap, 0.5, resolved, "repeatedItem");
    }

    /**
     * Test 8: Multiple embeddings in one sequence counted as 1 distinct SID
     * support.
     * Sequence has (1)(2)(1)(2), pattern <(1)(2)>.
     * Exactly 1 SID support, candidate cardinality 1.
     */
    @Test
    void multipleEmbeddings_distinctSidSupport_structuralDirectScan() {
        Item item1 = new Item(1);
        Item item2 = new Item(2);
        SequenceRecord rec = new SequenceRecord(400, Sequence.of(
                Itemset.of(item1), Itemset.of(item2), Itemset.of(item1), Itemset.of(item2)));
        SequenceDatabase db = new SequenceDatabase(List.of(rec));
        Pattern pattern = Pattern.of(Itemset.of(item1), Itemset.of(item2));

        Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);
        Set<Integer> exactSids = exactSupportingSids(db, pattern);

        assertEquals(Set.of(400), candidateSids);
        assertEquals(Set.of(400), exactSids,
                "[structural direct-scan oracle] Multiple embeddings must contribute exactly 1 to distinct SID count");
        assertEquals(1, exactSids.size());
        assertEquals(1, candidateSids.size());

        SidMapping mapping = SidMapping.of(db);
        SidBitmap bitmap = bitmapWithCandidateSids(mapping, candidateSids);
        assertEquals(1, bitmap.cardinality());
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(1.0), 1);
        assertBaselineIntervalSafety(bitmap, 1.0, resolved, "multipleEmbeddings");
    }

    /**
     * Test 9: Noncontiguous and negative SID mapping handled correctly.
     */
    @Test
    void noncontiguousNegativeSidMapping_structuralDirectScan() {
        Item item1 = new Item(1);
        Item item2 = new Item(2);
        int[] sids = new int[] { -999, -42, 0, 7, 100000 };
        List<SequenceRecord> records = List.of(
                new SequenceRecord(-999, Sequence.of(Itemset.of(item1))),
                new SequenceRecord(-42, Sequence.of(Itemset.of(item2))),
                new SequenceRecord(0, Sequence.of(Itemset.of(item1, item2))),
                new SequenceRecord(7, Sequence.of(Itemset.of(item1), Itemset.of(item2))),
                new SequenceRecord(100000, Sequence.of(Itemset.of(item2), Itemset.of(item1))));
        SequenceDatabase db = new SequenceDatabase(records);
        Pattern pattern = Pattern.of(Itemset.of(item1), Itemset.of(item2));

        Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);
        Set<Integer> exactSids = exactSupportingSids(db, pattern);

        // candidate: 0, 7, 100000 (all have both 1 and 2)
        assertEquals(Set.of(0, 7, 100000), candidateSids,
                "[structural direct-scan oracle] Candidate presence intersection");
        // exact: 7 (temporal (1) then (2)). SID 0 has (1 2) in same itemset; pattern
        // has 2 separate itemsets!
        // SID 100000 has (2) then (1).
        assertEquals(Set.of(7), exactSids,
                "[structural direct-scan oracle] Exact sequential pattern match with separate itemsets");

        assertTrue(candidateSids.containsAll(exactSids));
        assertTrue(exactSids.size() <= candidateSids.size());

        SidMapping mapping = SidMapping.of(db);
        SidBitmap bitmap = bitmapWithCandidateSids(mapping, candidateSids);
        assertEquals(3, bitmap.cardinality());
        assertEquals(db.size(), bitmap.sequenceCount());

        int[] recoveredSids = bitmap.toSupportingSids();
        Set<Integer> recoveredSet = Arrays.stream(recoveredSids).boxed().collect(Collectors.toSet());
        assertEquals(candidateSids, recoveredSet,
                "[structural direct-scan oracle] Recovered SIDs from bitmap must match candidate SIDs");

        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(2.0), db.size());
        assertBaselineIntervalSafety(bitmap, 0.5, resolved, "noncontiguousNegativeSids");
    }

    /**
     * Test 10: Mandatory counterexample: 14% × 25 sequences, cardinality 7, MeanW
     * 0.5.
     * Demonstrates that exact-real and frozen baseline double disagree.
     */
    @Test
    void audit_counterexample_14Percent_25Sequences_exactRealDisagreement() {
        int sequenceCount = 25;
        double percentage = 14.0;
        int cardinality = 7;
        double meanWeight = 0.5;

        // 1. Frozen baseline evaluation
        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.percentage(percentage), sequenceCount);
        double baselineThreshold = resolved.effectiveThreshold();
        assertEquals(3.5000000000000004, baselineThreshold, 1e-15,
                "[baseline-relative oracle] Baseline effective threshold has binary64 rounding artifact");

        SidBitmap bitmap = bitmapWithCardinality(sequenceCount, cardinality);
        boolean baselineCanPrune = FILTER.canPrune(bitmap, meanWeight, resolved);
        assertTrue(baselineCanPrune,
                "[baseline-relative oracle] Filter prunes because 3.5 < 3.5000000000000004 in double arithmetic");

        // 2. Exact-rational arithmetic (Oracle O-B)
        Rational exactPercent = Rational.parseDecimal("14.0");
        Rational exactSeqCount = Rational.of(sequenceCount);
        Rational exactThreshold = exactPercent.multiply(exactSeqCount).divide(100); // 14 * 25 / 100 = 7/2 = 3.5
        assertEquals(new Rational(7, 2), exactThreshold,
                "[exact-rational oracle] Exact mathematical threshold must be 7/2");

        Rational exactMeanW = new Rational(1, 2);
        Rational exactUpperBoundWSUP = Rational.of(cardinality).multiply(exactMeanW); // 7 * 1/2 = 7/2 = 3.5
        assertEquals(new Rational(7, 2), exactUpperBoundWSUP,
                "[exact-rational oracle] Exact upper bound WSUP must be 7/2");

        boolean exactRealAccepts = exactUpperBoundWSUP.compareTo(exactThreshold) >= 0; // 3.5 >= 3.5 is TRUE
        assertTrue(exactRealAccepts,
                "[exact-rational oracle] Exact real arithmetic achieves equality (3.5 == 3.5) and accepts");

        // 3. Classification
        DisagreementClassification classification = (baselineCanPrune && exactRealAccepts)
                ? DisagreementClassification.BASELINE_PRUNE_EXACT_KEEP
                : DisagreementClassification.AGREE;

        assertEquals(DisagreementClassification.BASELINE_PRUNE_EXACT_KEEP, classification,
                "[exact-rational oracle] Known counterexample must be classified as BASELINE_PRUNE_EXACT_KEEP");
    }

    // =========================================================================
    // Section 8 & 12: Exhaustive Baseline-Relative Differential (Oracle O-A, Test
    // 11)
    // =========================================================================

    /**
     * Test 11: Exhaustive baseline interval check covering sequence counts 0–8, all
     * possible
     * cardinalities [0, sequenceCount], all hypothetical supports in [0,
     * cardinality],
     * and complete MeanW and threshold catalogues.
     */
    @Test
    void exhaustiveBaselineInterval_allSizesUpTo8_catalogue() {
        double[] meanWCatalogue = new double[] {
                +0.0,
                -0.0,
                Double.MIN_VALUE,
                0.1,
                0.2,
                0.5,
                Math.nextDown(1.0),
                1.0
        };

        double[] absThresholdCatalogue = new double[] {
                0.0,
                Double.MIN_VALUE,
                0.1,
                0.5,
                1.0,
                Math.nextDown(1.0),
                Math.nextUp(1.0),
                2.0,
                4.0
        };

        double[] percentCatalogue = new double[] { 0.0, 14.0, 50.0, 100.0 };

        long totalEvaluations = 0;
        long pruneDecisions = 0;

        for (int sequenceCount = 0; sequenceCount <= 8; sequenceCount++) {
            for (int cardinality = 0; cardinality <= sequenceCount; cardinality++) {
                SidBitmap bitmap = bitmapWithCardinality(sequenceCount, cardinality);

                for (double meanW : meanWCatalogue) {
                    // 1. Absolute threshold catalogue
                    for (double absThreshold : absThresholdCatalogue) {
                        ResolvedMinimumSupport resolved = RESOLVER.resolve(
                                MinimumSupport.absolute(absThreshold), sequenceCount);

                        boolean expectedCanPrune;
                        if (resolved.effectiveThreshold() == 0.0) {
                            expectedCanPrune = false;
                        } else {
                            boolean allRejected = true;
                            for (int s = 0; s <= cardinality; s++) {
                                if (resolved.meetsThreshold(s, meanW)) {
                                    allRejected = false;
                                    break;
                                }
                            }
                            expectedCanPrune = allRejected;
                        }

                        boolean actualCanPrune = FILTER.canPrune(bitmap, meanW, resolved);
                        assertEquals(expectedCanPrune, actualCanPrune,
                                "[baseline-relative oracle] Exhaustive match failed at seq="
                                        + sequenceCount + " card=" + cardinality
                                        + " meanW=" + meanW + " absThresh=" + absThreshold);

                        if (actualCanPrune) {
                            pruneDecisions++;
                            assertBaselineIntervalSafety(bitmap, meanW, resolved, "exhaustive_abs");
                        }
                        totalEvaluations++;
                    }

                    // 2. Percentage threshold catalogue (only when sequenceCount > 0)
                    if (sequenceCount > 0) {
                        for (double pct : percentCatalogue) {
                            ResolvedMinimumSupport resolved = RESOLVER.resolve(
                                    MinimumSupport.percentage(pct), sequenceCount);

                            boolean expectedCanPrune;
                            if (resolved.effectiveThreshold() == 0.0) {
                                expectedCanPrune = false;
                            } else {
                                boolean allRejected = true;
                                for (int s = 0; s <= cardinality; s++) {
                                    if (resolved.meetsThreshold(s, meanW)) {
                                        allRejected = false;
                                        break;
                                    }
                                }
                                expectedCanPrune = allRejected;
                            }

                            boolean actualCanPrune = FILTER.canPrune(bitmap, meanW, resolved);
                            assertEquals(expectedCanPrune, actualCanPrune,
                                    "[baseline-relative oracle] Exhaustive match failed at seq="
                                            + sequenceCount + " card=" + cardinality
                                            + " meanW=" + meanW + " pct=" + pct);

                            if (actualCanPrune) {
                                pruneDecisions++;
                                assertBaselineIntervalSafety(bitmap, meanW, resolved, "exhaustive_pct");
                            }
                            totalEvaluations++;
                        }
                    }
                }
            }
        }

        assertTrue(totalEvaluations > 0, "Evaluations must be non-zero");
        assertTrue(pruneDecisions > 0, "Must have generated prune decisions");
    }

    // =========================================================================
    // Section 11 & 12: Fixed-Seed 1,200 Randomized Structural Differential (Test
    // 12)
    // =========================================================================

    /**
     * Test 12: Fixed-seed randomized structural differential suite.
     * Completes exactly 1,200 trials with seed 20261029L, comparing candidate
     * bitmaps
     * against independent direct-scan exact support and auditing exact-rational
     * disagreements.
     */
    @Test
    void randomizedStructuralDifferential_fixedSeed1200Trials() {
        final long requiredSeed = 20261029L;
        final int requiredTrials = 1200;
        Random rng = new Random(requiredSeed);

        long completedTrials = 0;
        long structuralComparisons = 0;
        long filterPruneDecisions = 0;
        long falseNegativeCount = 0;
        long thresholdZeroNoOpCount = 0;

        long exactRationalAgreements = 0;
        long baselinePruneExactKeepCount = 0;
        long baselineKeepExactPruneCount = 0;

        for (int trial = 0; trial < requiredTrials; trial++) {
            // 1. Generate sequenceCount in [0, 8]
            int sequenceCount = rng.nextInt(9);

            // 2. Generate distinct SIDs (including negative, zero, positive, noncontiguous)
            Set<Integer> sidSet = new HashSet<>();
            while (sidSet.size() < sequenceCount) {
                int rawSid = (rng.nextInt(200) - 100) * 3 + rng.nextInt(3);
                sidSet.add(rawSid);
            }
            List<Integer> sids = new ArrayList<>(sidSet);
            Collections.sort(sids);

            // 3. Build sequence records
            List<SequenceRecord> records = new ArrayList<>(sequenceCount);
            for (int sid : sids) {
                int itemsetCount = 1 + rng.nextInt(5); // 1 to 5 itemsets
                List<Itemset> itemsets = new ArrayList<>(itemsetCount);
                for (int isIdx = 0; isIdx < itemsetCount; isIdx++) {
                    int itemCount = 1 + rng.nextInt(3); // 1 to 3 items
                    Set<Item> items = new HashSet<>();
                    while (items.size() < itemCount) {
                        int itemId = 1 + rng.nextInt(4); // Item IDs in {1, 2, 3, 4}
                        items.add(new Item(itemId));
                    }
                    itemsets.add(Itemset.of(items.toArray(Item[]::new)));
                }
                records.add(new SequenceRecord(sid, Sequence.of(itemsets.toArray(Itemset[]::new))));
            }
            SequenceDatabase db = new SequenceDatabase(records);

            // 4. Generate random pattern (1 to 3 itemsets, each 1 to 2 items)
            int pItemsetCount = 1 + rng.nextInt(3);
            List<Itemset> pItemsets = new ArrayList<>(pItemsetCount);
            for (int pis = 0; pis < pItemsetCount; pis++) {
                int itemCount = 1 + rng.nextInt(2);
                Set<Item> items = new HashSet<>();
                while (items.size() < itemCount) {
                    int itemId = 1 + rng.nextInt(4);
                    items.add(new Item(itemId));
                }
                pItemsets.add(Itemset.of(items.toArray(Item[]::new)));
            }
            Pattern pattern = Pattern.of(pItemsets.toArray(Itemset[]::new));

            // 5. Compute candidate SIDs by independent Set intersection
            Set<Integer> candidateSids = candidateSidsByPresenceIntersection(db, pattern);

            // 6. Compute exact supporting SIDs by direct greedy subsequence scan
            Set<Integer> exactSids = exactSupportingSids(db, pattern);

            // Structural Invariants (Oracle O-C)
            assertTrue(candidateSids.containsAll(exactSids),
                    "[structural direct-scan oracle] Exact supporting SIDs must be subset of candidate SIDs (trial "
                            + trial + ")");
            int exactSupport = exactSids.size();
            int candidateCardinality = candidateSids.size();
            assertTrue(exactSupport <= candidateCardinality,
                    "[structural direct-scan oracle] exactSupport must be <= candidateCardinality (trial " + trial
                            + ")");

            SidMapping mapping = SidMapping.of(db);
            SidBitmap candidateBitmap = bitmapWithCandidateSids(mapping, candidateSids);
            assertEquals(candidateCardinality, candidateBitmap.cardinality(),
                    "[structural direct-scan oracle] Bitmap cardinality must equal candidate SIDs set size");

            int[] recoveredSids = candidateBitmap.toSupportingSids();
            Set<Integer> recoveredSet = Arrays.stream(recoveredSids).boxed().collect(Collectors.toSet());
            assertEquals(candidateSids, recoveredSet,
                    "[structural direct-scan oracle] Bitmap recovered SIDs must match candidate set");

            // 7. Test configurations: vary MeanW and thresholds
            double[] meanWList = new double[] { 0.0, 0.25, 0.5, 0.8, 1.0 };
            Rational[] meanWRationalList = new Rational[] {
                    Rational.of(0), new Rational(1, 4), new Rational(1, 2), new Rational(4, 5), Rational.of(1)
            };

            for (int mIdx = 0; mIdx < meanWList.length; mIdx++) {
                double meanW = meanWList[mIdx];
                Rational meanWRational = meanWRationalList[mIdx];

                // Test absolute threshold
                double[] absThresholds = { 0.0, 0.5, 1.0, 2.0 };
                String[] absThresholdLexemes = { "0.0", "0.5", "1.0", "2.0" };
                assertEquals(absThresholds.length, absThresholdLexemes.length,
                        "absThresholds and absThresholdLexemes must have identical lengths");

                for (int thresholdIndex = 0; thresholdIndex < absThresholds.length; thresholdIndex++) {
                    double absThresh = absThresholds[thresholdIndex];
                    Rational exactThreshold = Rational.parseDecimal(absThresholdLexemes[thresholdIndex]);

                    ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.absolute(absThresh),
                            sequenceCount);
                    structuralComparisons++;

                    boolean canPrune = FILTER.canPrune(candidateBitmap, meanW, resolved);

                    if (resolved.effectiveThreshold() == 0.0) {
                        assertFalse(canPrune, "[baseline-relative oracle] Threshold 0.0 must be no-op");
                        thresholdZeroNoOpCount++;
                    } else if (canPrune) {
                        filterPruneDecisions++;
                        // Safety check: baseline must reject exact support
                        boolean baselineAcceptsExact = resolved.meetsThreshold(exactSupport, meanW);
                        if (baselineAcceptsExact) {
                            falseNegativeCount++;
                        }
                        assertFalse(baselineAcceptsExact,
                                "[baseline-relative oracle] FALSE NEGATIVE DETECTED: filter pruned but baseline accepts exact support="
                                        + exactSupport + " at trial=" + trial);

                        // Baseline interval safety check
                        assertBaselineIntervalSafety(candidateBitmap, meanW, resolved, "trial=" + trial);
                    }

                    // Oracle O-B exact-rational audit
                    Rational exactUpperBoundWSUP = Rational.of(candidateCardinality).multiply(meanWRational);
                    boolean exactRealRejects = exactUpperBoundWSUP.compareTo(exactThreshold) < 0;

                    if (canPrune == exactRealRejects) {
                        exactRationalAgreements++;
                    } else if (canPrune && !exactRealRejects) {
                        baselinePruneExactKeepCount++;
                    } else {
                        baselineKeepExactPruneCount++;
                    }
                }

                // Test percentage threshold (only if sequenceCount > 0)
                if (sequenceCount > 0) {
                    double[] percentages = new double[] { 0.0, 14.0, 50.0, 100.0 };
                    String[] pctStrings = new String[] { "0.0", "14.0", "50.0", "100.0" };
                    for (int p = 0; p < percentages.length; p++) {
                        double pct = percentages[p];
                        String pctStr = pctStrings[p];
                        ResolvedMinimumSupport resolved = RESOLVER.resolve(MinimumSupport.percentage(pct),
                                sequenceCount);
                        structuralComparisons++;

                        boolean canPrune = FILTER.canPrune(candidateBitmap, meanW, resolved);

                        if (resolved.effectiveThreshold() == 0.0) {
                            assertFalse(canPrune, "[baseline-relative oracle] Percentage threshold 0.0 must be no-op");
                            thresholdZeroNoOpCount++;
                        } else if (canPrune) {
                            filterPruneDecisions++;
                            boolean baselineAcceptsExact = resolved.meetsThreshold(exactSupport, meanW);
                            if (baselineAcceptsExact) {
                                falseNegativeCount++;
                            }
                            assertFalse(baselineAcceptsExact,
                                    "[baseline-relative oracle] FALSE NEGATIVE DETECTED: filter pruned but baseline accepts exact support="
                                            + exactSupport + " at trial=" + trial);

                            assertBaselineIntervalSafety(candidateBitmap, meanW, resolved, "trial=" + trial + "_pct");
                        }

                        // Oracle O-B exact-rational audit
                        Rational exactThreshold = Rational.parseDecimal(pctStr)
                                .multiply(Rational.of(sequenceCount))
                                .divide(100);
                        Rational exactUpperBoundWSUP = Rational.of(candidateCardinality).multiply(meanWRational);
                        boolean exactRealRejects = exactUpperBoundWSUP.compareTo(exactThreshold) < 0;

                        if (canPrune == exactRealRejects) {
                            exactRationalAgreements++;
                        } else if (canPrune && !exactRealRejects) {
                            baselinePruneExactKeepCount++;
                        } else {
                            baselineKeepExactPruneCount++;
                        }
                    }
                }
            }

            completedTrials++;
        }

        assertEquals(requiredTrials, completedTrials, "Must complete exactly 1,200 trials");
        assertEquals(0, falseNegativeCount,
                "[baseline-relative oracle] False-negative count relative to frozen baseline must be 0");
        assertTrue(structuralComparisons > 0, "Structural comparisons must be positive");
        assertTrue(filterPruneDecisions > 0, "Must have generated filter prune decisions");

        // Print differential evidence audit summary
        System.out.println("=== C06.2 FIXED-SEED RANDOMIZED DIFFERENTIAL EVIDENCE AUDIT ===");
        System.out.println("Seed: " + requiredSeed);
        System.out.println("Completed trials: " + completedTrials);
        System.out.println("Total structural comparisons: " + structuralComparisons);
        System.out.println("Filter prune decisions: " + filterPruneDecisions);
        System.out.println("Threshold zero no-op count: " + thresholdZeroNoOpCount);
        System.out.println("Frozen-baseline false negative count: " + falseNegativeCount);
        System.out.println("Exact-rational agreements: " + exactRationalAgreements);
        System.out.println("Baseline prune / Exact keep (rounding disagreements): " + baselinePruneExactKeepCount);
        System.out.println("Baseline keep / Exact prune (rounding disagreements): " + baselineKeepExactPruneCount);
        System.out.println("===============================================================");
    }
}
