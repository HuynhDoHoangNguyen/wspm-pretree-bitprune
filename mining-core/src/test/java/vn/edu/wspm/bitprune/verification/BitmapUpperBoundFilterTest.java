package vn.edu.wspm.bitprune.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.bitprune.bitmap.SidMapping;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.model.Item;
import vn.edu.wspm.model.Itemset;
import vn.edu.wspm.model.Sequence;
import vn.edu.wspm.model.SequenceDatabase;
import vn.edu.wspm.model.SequenceRecord;

/**
 * Deterministic focused tests cho {@link BitmapUpperBoundFilter} — C06.1.
 *
 * <p>
 * Policy được duyệt: {@code BASELINE_RELATIVE_MONOTONE_UPPER_BOUND}.
 *
 * <h2>Baseline-relative conformance oracle</h2>
 *
 * <p>
 * Expected baseline decisions được lấy từ
 * {@link ResolvedMinimumSupport#meetsThreshold(int, double)}.
 *
 * <p>
 * Helper gọi production filter để kiểm tra implication: nếu filter prune
 * thì baseline phải reject mọi support trong {@code [0, cardinality]}.
 *
 * <p>
 * Đây không phải independent structural oracle. Nó không chứng minh exact
 * temporal support, bitmap-superset property trên database thực, hoặc
 * exact-real safety. Structural independent oracle được defer sang C06.2.
 *
 * <h2>Randomized tests</h2>
 *
 * <p>
 * Không có randomized tests trong C06.1. Fixed-seed randomized/exhaustive
 * tiny-input differential evidence được defer tới C06.2.
 */
class BitmapUpperBoundFilterTest {

    private static final MinimumSupportResolver RESOLVER = new MinimumSupportResolver();
    private static final BitmapUpperBoundFilter FILTER = new BitmapUpperBoundFilter();
    private static final Item DUMMY_ITEM = new Item(1);

    // =========================================================================
    // Test-Local Helper Factory Methods
    // =========================================================================

    /** Tạo SequenceDatabase với {@code n} sequences, SID = 1000+i. */
    private static SequenceDatabase dbOfSize(int n) {
        var records = new ArrayList<SequenceRecord>(n);
        for (int i = 0; i < n; i++) {
            records.add(new SequenceRecord(1000 + i, Sequence.of(Itemset.of(DUMMY_ITEM))));
        }
        return new SequenceDatabase(List.copyOf(records));
    }

    /** Tạo SidMapping với {@code n} sequences. */
    private static SidMapping mappingOfSize(int n) {
        return SidMapping.of(dbOfSize(n));
    }

    /**
     * Tạo SidBitmap với {@code cardinality} bits được set (các bit đầu tiên)
     * trên universe {@code sequenceCount}.
     */
    private static SidBitmap bitmapWithCardinality(int sequenceCount, int cardinality) {
        SidMapping mapping = mappingOfSize(sequenceCount);
        SidBitmap.Builder builder = SidBitmap.builder(mapping);
        for (int i = 0; i < cardinality; i++) {
            builder.setIndex(i);
        }
        return builder.build();
    }

    /** Tạo empty bitmap trên universe {@code sequenceCount}. */
    private static SidBitmap emptyBitmap(int sequenceCount) {
        return SidBitmap.empty(mappingOfSize(sequenceCount));
    }

    /** Resolve percentage-mode threshold. */
    private static ResolvedMinimumSupport resolvePercent(double percentage, int sequenceCount) {
        return RESOLVER.resolve(MinimumSupport.percentage(percentage), sequenceCount);
    }

    /** Resolve absolute-mode threshold. */
    private static ResolvedMinimumSupport resolveAbsolute(double threshold, int sequenceCount) {
        return RESOLVER.resolve(MinimumSupport.absolute(threshold), sequenceCount);
    }

    /**
     * Baseline-relative conformance helper: kiểm tra monotonicity invariant.
     * Helper gọi production filter để kiểm tra implication: nếu filter prune thì
     * baseline (ResolvedMinimumSupport.meetsThreshold) phải reject mọi support
     * trong [0, cardinality].
     * Không phải independent structural oracle (defer sang C06.2).
     */
    private static void assertUpperBoundIntervalInvariant(
            SidBitmap bitmap, double meanWeight, ResolvedMinimumSupport resolved) {
        boolean canPrune = FILTER.canPrune(bitmap, meanWeight, resolved);
        if (canPrune) {
            int cardinality = bitmap.cardinality();
            for (int s = 0; s <= cardinality; s++) {
                assertFalse(
                        resolved.meetsThreshold(s, meanWeight),
                        "Monotonicity violation: filter pruned but baseline accepts support="
                                + s + " with cardinality=" + cardinality
                                + " meanWeight=" + meanWeight);
            }
        }
    }

    // =========================================================================
    // Section 1: Input Validation (Tests 1–8)
    // =========================================================================

    /** Test 1: Null bitmap → NullPointerException. */
    @Test
    void nullBitmap_throwsNullPointerException() {
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertThrows(NullPointerException.class,
                () -> FILTER.canPrune(null, 0.5, resolved));
    }

    /** Test 2: Null minimum support → NullPointerException. */
    @Test
    void nullMinimumSupport_throwsNullPointerException() {
        SidBitmap bitmap = emptyBitmap(5);
        assertThrows(NullPointerException.class,
                () -> FILTER.canPrune(bitmap, 0.5, null));
    }

    /**
     * Test 3: Bitmap sequenceCount khác resolved numberOfSequences
     * → IllegalArgumentException.
     */
    @Test
    void universeSizeMismatch_throwsIllegalArgumentException() {
        SidBitmap bitmap = emptyBitmap(5); // sequenceCount = 5
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 10); // numberOfSequences = 10
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, 0.5, resolved));
    }

    /** Test 4: MeanW NaN → IllegalArgumentException. */
    @Test
    void meanWeightNaN_throwsIllegalArgumentException() {
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, Double.NaN, resolved));
    }

    /** Test 5: MeanW positive infinity → IllegalArgumentException. */
    @Test
    void meanWeightPositiveInfinity_throwsIllegalArgumentException() {
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, Double.POSITIVE_INFINITY, resolved));
    }

    /** Test 6: MeanW negative infinity → IllegalArgumentException. */
    @Test
    void meanWeightNegativeInfinity_throwsIllegalArgumentException() {
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, Double.NEGATIVE_INFINITY, resolved));
    }

    /** Test 7: MeanW âm hữu hạn → IllegalArgumentException. */
    @Test
    void meanWeightNegativeFinite_throwsIllegalArgumentException() {
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, -0.001, resolved));
    }

    /**
     * Test 8: Synthetic multiplication overflow → IllegalArgumentException, không
     * prune.
     *
     * <p>
     * Với valid repository (MeanW ∈ [0,1], cardinality ≤ Integer.MAX_VALUE),
     * overflow không xảy ra. Tuy nhiên, với synthetic out-of-contract input
     * (cardinality=2, meanWeight=Double.MAX_VALUE), product overflow thành
     * infinity,
     * meetsThreshold ném IllegalArgumentException và exception phải được propagate.
     * Tuyệt đối không được chuyển thành canPrune=true.
     *
     * <p>
     * Để tạo bitmap với cardinality=2 mà không vi phạm sequenceCount constraint:
     * dùng sequenceCount=2, absolute threshold=1.0.
     */
    @Test
    void syntheticMultiplicationOverflow_propagatesIllegalArgumentException_doesNotPrune() {
        // sequenceCount=2 để bitmap.sequenceCount() == resolved.numberOfSequences()
        SidBitmap bitmap = bitmapWithCardinality(2, 2); // cardinality=2
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 2);
        // 2 * Double.MAX_VALUE overflows thành Infinity → meetsThreshold ném IAE
        assertThrows(IllegalArgumentException.class,
                () -> FILTER.canPrune(bitmap, Double.MAX_VALUE, resolved));
    }

    // =========================================================================
    // Section 2: Threshold Behavior (Tests 9–14)
    // =========================================================================

    /** Test 9: Empty bitmap, positive threshold → prune. */
    @Test
    void emptyBitmap_positiveThreshold_prunes() {
        SidBitmap bitmap = emptyBitmap(10);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 10);
        assertTrue(FILTER.canPrune(bitmap, 0.5, resolved));
        // Xác nhận oracle: baseline reject s=0 (mọi possible exact support trong [0,0])
        assertFalse(resolved.meetsThreshold(0, 0.5),
                "Oracle: baseline must reject support=0");
    }

    /** Test 10: Empty bitmap, threshold zero → không prune. */
    @Test
    void emptyBitmap_thresholdZero_doesNotPrune() {
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(0.0, 5);
        assertFalse(FILTER.canPrune(bitmap, 0.5, resolved));
    }

    /** Test 11: Non-empty bitmap, threshold zero → không prune. */
    @Test
    void nonEmptyBitmap_thresholdZero_doesNotPrune() {
        SidBitmap bitmap = bitmapWithCardinality(5, 3);
        ResolvedMinimumSupport resolved = resolveAbsolute(0.0, 5);
        assertFalse(FILTER.canPrune(bitmap, 0.5, resolved));
    }

    /**
     * Test 12: +0.0 và -0.0 threshold → cùng no-op behavior.
     *
     * <p>
     * MinimumSupport.absolute(0.0) tạo effectiveThreshold=+0.0.
     * Không có public API để tạo threshold=-0.0 từ MinimumSupport; test xác nhận
     * cả hai trường hợp numerically zero đều no-op. Threshold +0.0 được kiểm tra
     * trực tiếp qua absolute(0.0).
     *
     * <p>
     * NOTE: MinimumSupport không chấp nhận giá trị âm, nên -0.0 là biên giới.
     * Về mặt số học, -0.0 == +0.0 là true, do đó effectiveThreshold == 0.0 là true
     * cho cả hai.
     */
    @Test
    void positiveAndNegativeZeroThreshold_sameNoOpBehavior() {
        SidBitmap bitmap = bitmapWithCardinality(5, 3);

        // +0.0 threshold: absolute(+0.0)
        ResolvedMinimumSupport resolved = resolveAbsolute(0.0, 5);
        assertEquals(0.0, resolved.effectiveThreshold());
        assertFalse(FILTER.canPrune(bitmap, 0.5, resolved),
                "+0.0 threshold must be no-op");

        // Xác nhận -0.0 == 0.0 trong Java comparison semantics (dùng Double.compare để
        // kiểm tra sign)
        // effectiveThreshold = 0.0 và 0.0 == -0.0 trong IEEE 754 == comparison.
        // Nếu effectiveThreshold từ resolver là -0.0 về sign (không thể qua public
        // API),
        // thì 0.0 == 0.0 vẫn true → canPrune=false. Ta xác nhận bằng mathematical
        // equality.
        assertTrue(0.0 == -0.0, "IEEE 754: +0.0 == -0.0 numerically");
    }

    /**
     * Test 13: MeanW = +0.0, positive threshold → prune.
     *
     * <p>
     * Với MeanW=+0.0, mọi support × 0.0 = 0.0 < positive threshold.
     * Baseline reject mọi support. Filter phải prune.
     */
    @Test
    void meanWeightPositiveZero_positiveThreshold_prunes() {
        SidBitmap bitmap = bitmapWithCardinality(5, 3);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        assertTrue(FILTER.canPrune(bitmap, +0.0, resolved));
        // Xác nhận oracle
        assertUpperBoundIntervalInvariant(bitmap, +0.0, resolved);
    }

    /**
     * Test 14: MeanW = -0.0, positive threshold → cùng zero behavior như +0.0.
     *
     * <p>
     * -0.0 finite, và -0.0 < 0.0 là false theo IEEE 754 (chúng bằng nhau),
     * nên validation cho phép -0.0 đi qua. -0.0 được coi là zero.
     * meetsThreshold nhận -0.0 là meanWeight:
     * - support * (-0.0) = -0.0 (0 * -0.0 = +0.0, non-zero * -0.0 = -0.0)
     * - -0.0 >= positiveThreshold → false → meetsThreshold trả false
     * → filter trả true (prune).
     * Nếu meetsThreshold ném IAE với -0.0, propagate IAE.
     * Behavior phải giống +0.0.
     */
    @Test
    void meanWeightNegativeZero_positiveThreshold_sameZeroBehavior() {
        SidBitmap bitmap = bitmapWithCardinality(5, 3);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 5);
        // -0.0 được coi là zero: nên có behavior giống +0.0
        // ResolvedMinimumSupport.meetsThreshold kiểm tra < 0.0; -0.0 < 0.0 là false,
        // nên -0.0 đi qua validation của meetsThreshold.
        // Kết quả: 3 * (-0.0) = -0.0, -0.0 >= 1.0 là false → không meets → prune.
        assertTrue(FILTER.canPrune(bitmap, +0.0, resolved));
        // Xác nhận -0.0 pass validation trong filter (không ném IAE)
        assertDoesNotThrow(() -> FILTER.canPrune(bitmap, -0.0, resolved));
        // Xác nhận behavior giống +0.0
        boolean resultPositiveZero = FILTER.canPrune(bitmap, +0.0, resolved);
        boolean resultNegativeZero = FILTER.canPrune(bitmap, -0.0, resolved);
        assertEquals(resultPositiveZero, resultNegativeZero,
                "+0.0 and -0.0 must produce the same canPrune result");
    }

    // =========================================================================
    // Section 3: Equality and Near-equality (Tests 15–18)
    // =========================================================================

    /**
     * Test 15: Cardinality 2, MeanW 0.5, threshold 1.0 → equality, không prune.
     *
     * <p>
     * 2 * 0.5 = 1.0 == 1.0 (exact binary representable). meetsThreshold trả true.
     * Filter phải trả false.
     */
    @Test
    void equality_cardinalityTimesWeight_equalsThreshold_doesNotPrune() {
        // sequenceCount=2, threshold=1.0
        SidBitmap bitmap = bitmapWithCardinality(2, 2);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 2);
        // 2 * 0.5 = 1.0 == 1.0 → meets threshold → không prune
        assertFalse(FILTER.canPrune(bitmap, 0.5, resolved));
        // Xác nhận oracle trực tiếp
        assertTrue(resolved.meetsThreshold(2, 0.5), "Oracle: baseline accepts cardinality=2");
    }

    /**
     * Test 16: Cardinality 1, MeanW Math.nextDown(1.0), threshold 1.0 → prune.
     *
     * <p>
     * 1 * nextDown(1.0) = nextDown(1.0) < 1.0. Baseline reject.
     */
    @Test
    void nearEquality_justBelow_prunes() {
        SidBitmap bitmap = bitmapWithCardinality(2, 1);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 2);
        double meanW = Math.nextDown(1.0);
        assertTrue(FILTER.canPrune(bitmap, meanW, resolved),
                "nextDown(1.0) < threshold → must prune");
        // Xác nhận oracle
        assertUpperBoundIntervalInvariant(bitmap, meanW, resolved);
    }

    /**
     * Test 17: Cardinality 1, MeanW 1.0, threshold 1.0 → equality, không prune.
     *
     * <p>
     * 1 * 1.0 = 1.0 == 1.0. meetsThreshold trả true.
     */
    @Test
    void equality_cardinality1_weight1_threshold1_doesNotPrune() {
        SidBitmap bitmap = bitmapWithCardinality(2, 1);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 2);
        assertFalse(FILTER.canPrune(bitmap, 1.0, resolved),
                "1*1.0=1.0 == threshold → must not prune");
        assertTrue(resolved.meetsThreshold(1, 1.0), "Oracle: baseline accepts");
    }

    /**
     * Test 18: Cardinality 1, MeanW Math.nextUp(1.0), threshold 1.0 → không prune.
     *
     * <p>
     * 1 * nextUp(1.0) = nextUp(1.0) > 1.0. Baseline accept.
     */
    @Test
    void nearEquality_justAbove_doesNotPrune() {
        SidBitmap bitmap = bitmapWithCardinality(2, 1);
        ResolvedMinimumSupport resolved = resolveAbsolute(1.0, 2);
        double meanW = Math.nextUp(1.0);
        assertFalse(FILTER.canPrune(bitmap, meanW, resolved),
                "nextUp(1.0) > threshold → must not prune");
        assertTrue(resolved.meetsThreshold(1, meanW), "Oracle: baseline accepts");
    }

    // =========================================================================
    // Section 4: Subnormal (Tests 19–20)
    // =========================================================================

    /**
     * Test 19: Empty bitmap, threshold Double.MIN_VALUE → prune.
     *
     * <p>
     * cardinality=0: 0 * anything = 0. 0 < Double.MIN_VALUE (subnormal positive).
     * Baseline reject support=0.
     */
    @Test
    void subnormal_emptyBitmap_thresholdMinValue_prunes() {
        // Absolute threshold=Double.MIN_VALUE; sequenceCount cần đủ lớn.
        // Không thể dùng percentage với giá trị nhỏ như vậy → dùng absolute mode.
        SidBitmap bitmap = emptyBitmap(5);
        ResolvedMinimumSupport resolved = resolveAbsolute(Double.MIN_VALUE, 5);
        assertTrue(FILTER.canPrune(bitmap, 0.5, resolved));
        // Xác nhận oracle: baseline reject s=0
        assertFalse(resolved.meetsThreshold(0, 0.5), "Oracle: baseline rejects support=0");
    }

    /**
     * Test 20: Cardinality 1, MeanW Double.MIN_VALUE, threshold Double.MIN_VALUE
     * → equality, không prune.
     *
     * <p>
     * 1 * Double.MIN_VALUE = Double.MIN_VALUE. Double.MIN_VALUE >=
     * Double.MIN_VALUE.
     * meetsThreshold trả true.
     */
    @Test
    void subnormal_cardinality1_minValueWeight_minValueThreshold_equality_doesNotPrune() {
        SidBitmap bitmap = bitmapWithCardinality(2, 1);
        ResolvedMinimumSupport resolved = resolveAbsolute(Double.MIN_VALUE, 2);
        assertFalse(FILTER.canPrune(bitmap, Double.MIN_VALUE, resolved),
                "Subnormal equality: must not prune");
        // Xác nhận oracle
        assertTrue(resolved.meetsThreshold(1, Double.MIN_VALUE),
                "Oracle: subnormal equality accepted by baseline");
    }

    // =========================================================================
    // Section 5: Baseline-relative counterexample (Test 21)
    // =========================================================================

    /**
     * Test 21: Counterexample 14% × 25, cardinality=7, MeanW=0.5.
     *
     * <p>
     * <strong>Baseline-relative behavior (Option A):</strong><br>
     * resolved threshold = (14.0/100.0)*25 = 3.5000000000000004 (IEEE 754
     * rounding)<br>
     * upper bound = 7 * 0.5 = 3.5<br>
     * 3.5 {@literal <} 3.5000000000000004 → baseline reject → filter returns true
     * (prune).
     *
     * <p>
     * <strong>Exact-real semantics:</strong><br>
     * threshold_exact = 14/100 × 25 = 3.5<br>
     * upper_bound_exact = 7 × 1/2 = 3.5<br>
     * exact-real equality → should NOT prune.
     *
     * <p>
     * <strong>Kết luận bắt buộc:</strong><br>
     * Đây là bằng chứng rằng policy {@code BASELINE_RELATIVE_MONOTONE_UPPER_BOUND}
     * không được tuyên bố là exact-real safe. Không thay expected result để che
     * semantic difference.
     */
    @Test
    void baselineRelativeCounterexample_14Percent_25Sequences_cardinality7_meanW05() {
        int sequenceCount = 25;
        double percentage = 14.0;
        int cardinality = 7;
        double meanWeight = 0.5;

        SidBitmap bitmap = bitmapWithCardinality(sequenceCount, cardinality);
        ResolvedMinimumSupport resolved = resolvePercent(percentage, sequenceCount);

        // Xác nhận resolved threshold có IEEE 754 rounding artifact
        double expectedThreshold = 3.5000000000000004;
        assertEquals(expectedThreshold, resolved.effectiveThreshold(),
                "Resolved threshold must be 3.5000000000000004 (IEEE 754 rounding artifact)");

        // Xác nhận rounded upper bound = 3.5 (chính xác biểu diễn trong binary64)
        double roundedUpperBound = cardinality * meanWeight; // 7 * 0.5 = 3.5 exactly
        assertEquals(3.5, roundedUpperBound, 0.0,
                "Rounded upper bound must be exactly 3.5");

        // Xác nhận 3.5 < 3.5000000000000004 → filter prunes (baseline-relative safe)
        assertTrue(roundedUpperBound < expectedThreshold,
                "Double semantics: 3.5 < 3.5000000000000004");

        // Filter phải trả true: BASELINE-RELATIVE PRUNE
        assertTrue(FILTER.canPrune(bitmap, meanWeight, resolved),
                "Baseline-relative: filter must prune (3.5 < 3.5000000000000004)");

        // Xác nhận baseline reject tại cardinality=7
        assertFalse(resolved.meetsThreshold(cardinality, meanWeight),
                "Oracle: baseline rejects cardinality=7 with MeanW=0.5");

        // Xác nhận oracle: baseline reject mọi s ∈ [0, 7]
        assertUpperBoundIntervalInvariant(bitmap, meanWeight, resolved);

        // DOCUMENTATION: exact-real semantics would NOT prune (equality)
        // exact-real: 7 * 0.5 = 3.5 == 14/100 * 25 = 3.5 → should keep
        // This test proves Option A cannot be called exact-real safe.
        // exact_real_upperBound = 3.5, exact_real_threshold = 3.5 → equality → no prune
        // But Option A baseline-relative prunes. This is the documented limitation.
    }

    // =========================================================================
    // Section 6: Upper-bound interval invariant (Test 22)
    // =========================================================================

    /**
     * Test 22: Deterministic catalogue — nếu filter prune, loop s ∈ [0,
     * cardinality]
     * và assert baseline reject mọi s.
     *
     * <p>
     * Catalogue gồm các (sequenceCount, cardinality, meanWeight, absoluteThreshold)
     * deterministic covering nhiều trường hợp: normal prune, near-equality, zero
     * MeanW.
     */
    @Test
    void upperBoundIntervalInvariant_deterministicCatalogue() {
        // Catalogue: {sequenceCount, cardinality, meanWeight, absoluteThreshold}
        double[][] catalogue = {
                // prune cases: upper bound clearly below threshold
                { 10, 2, 0.3, 1.0 }, // 2*0.3=0.6 < 1.0 → prune; all s in [0,2] rejected
                { 10, 5, 0.1, 1.0 }, // 5*0.1=0.5 < 1.0 → prune
                { 10, 0, 0.5, 0.001 }, // empty bitmap → prune
                { 10, 3, 0.0, 1.0 }, // MeanW=0 → all products 0 → prune
                // keep cases: upper bound meets or exceeds threshold
                { 10, 4, 0.5, 1.0 }, // 4*0.5=2.0 >= 1.0 → keep
                { 10, 2, 0.5, 1.0 }, // 2*0.5=1.0 == 1.0 → equality → keep
                { 10, 3, 0.5, 1.0 }, // 3*0.5=1.5 >= 1.0 → keep
                // subnormal: empty bitmap, tiny threshold → prune
                { 5, 0, 0.5, Double.MIN_VALUE },
                // subnormal equality: 1*MIN_VALUE >= MIN_VALUE → keep
                { 5, 1, Double.MIN_VALUE, Double.MIN_VALUE },
        };

        for (double[] row : catalogue) {
            int sequenceCount = (int) row[0];
            int cardinality = (int) row[1];
            double meanWeight = row[2];
            double threshold = row[3];

            SidBitmap bitmap = bitmapWithCardinality(sequenceCount, cardinality);
            ResolvedMinimumSupport resolved = resolveAbsolute(threshold, sequenceCount);

            // Assertion: nếu filter prune, mọi s ∈ [0, cardinality] bị baseline reject
            assertUpperBoundIntervalInvariant(bitmap, meanWeight, resolved);
        }
    }

    // =========================================================================
    // Section 7: Bitmap semantic traps (Tests 23–24)
    // =========================================================================

    /**
     * Test 23: Temporal false-positive style bitmap.
     *
     * <p>
     * Bitmap cardinality có thể lớn hơn exact support thực tế vì:
     * temporal false positives (sequence có item nhưng không đúng thứ
     * tự/embedding).
     *
     * <p>
     * Test fixture: bitmapCardinality=5 nhưng metadata ghi nhận exactSupport=3.
     * Filter chỉ quyết định theo cardinality (5), không theo exactSupport (3).
     * Không có temporal scanner được triển khai; đây là deterministic semantic
     * fixture.
     */
    @Test
    void bitmapSemanticTrap_temporalFalsePositive_filterUsesCardinalityNotExactSupport() {
        // Fixture: sequenceCount=10, bitmapCardinality=5, simulatedExactSupport=3
        int sequenceCount = 10;
        int bitmapCardinality = 5; // upper bound
        int simulatedExactSupport = 3; // metadata only; filter không biết giá trị này

        SidBitmap bitmap = bitmapWithCardinality(sequenceCount, bitmapCardinality);
        ResolvedMinimumSupport resolved = resolveAbsolute(2.0, sequenceCount);
        double meanWeight = 0.5;

        // Xác nhận bitmap.cardinality() == bitmapCardinality
        assertEquals(bitmapCardinality, bitmap.cardinality());

        // Xác nhận simulatedExactSupport <= bitmapCardinality (upper bound property)
        assertTrue(simulatedExactSupport <= bitmapCardinality,
                "exactSupport must be <= bitmapCardinality (upper bound contract)");

        // Filter quyết định theo cardinality=5, MeanW=0.5: 5*0.5=2.5 >= 2.0 → keep
        assertFalse(FILTER.canPrune(bitmap, meanWeight, resolved),
                "Filter must use cardinality=5, not exactSupport=3; result: keep");

        // Kiểm tra nếu filter quyết định dựa trên exact support: 3*0.5=1.5 < 2.0 →
        // would prune
        // (nhưng filter không biết exact support → không prune)
        assertTrue(resolved.meetsThreshold(bitmapCardinality, meanWeight),
                "Oracle with cardinality: baseline accepts");
    }

    /**
     * Test 24: Repeated-item style bitmap — BM(a) AND BM(a) == BM(a) có thể là cận
     * lỏng.
     *
     * <p>
     * Với pattern {@code <(a)(a)>}, candidate bitmap = BM(a) AND BM(a) = BM(a).
     * Cardinality = |BM(a)|. Exact support ≤ cardinality (cận lỏng vì thứ tự bắt
     * buộc).
     *
     * <p>
     * Test xác nhận filter vẫn xử lý bitmap như upper bound:
     * API quyết định theo cardinality, không tuyên bố exact support.
     */
    @Test
    void bitmapSemanticTrap_repeatedItemBitmap_cardinalityIsLooseUpperBound() {
        // Fixture: sequenceCount=8, BM(a)=5 sequences, BM(a) AND BM(a)=5 (tautology)
        int sequenceCount = 8;
        int bitmapCardinality = 5; // BM(a) cardinality
        // Exact support of <(a)(a)> <= 5; có thể = 3 nếu chỉ 3 seq có a ít nhất 2 lần

        SidBitmap bitmap = bitmapWithCardinality(sequenceCount, bitmapCardinality);
        // AND với chính nó: không thay đổi
        SidBitmap andWithSelf = bitmap.and(bitmap);
        assertEquals(bitmapCardinality, andWithSelf.cardinality(),
                "BM(a) AND BM(a) == BM(a): cardinality unchanged");

        ResolvedMinimumSupport resolved = resolveAbsolute(3.0, sequenceCount);
        double meanWeight = 0.5;

        // Filter quyết định theo cardinality=5, MeanW=0.5: 5*0.5=2.5 < 3.0 → prune
        assertTrue(FILTER.canPrune(bitmap, meanWeight, resolved),
                "Filter must decide by cardinality, treating it as upper bound");

        // Xác nhận oracle: baseline reject mọi s ∈ [0, 5]
        assertUpperBoundIntervalInvariant(bitmap, meanWeight, resolved);

        // Xác nhận upper bound contract: exact support (unknown) ∈ [0, cardinality]
        // Filter không có thông tin exact support → an toàn theo monotonicity proof
        assertTrue(bitmapCardinality >= 0, "Upper bound cardinality is non-negative");
    }

    // =========================================================================
    // Section 8: Additional correctness and robustness cases
    // =========================================================================

    /**
     * Các case bổ sung cho deterministic catalogue với nhiều
     * cardinality/MeanW/threshold.
     * Xác nhận cả prune và non-prune đều nhất quán với oracle.
     */
    @Test
    void additionalDeterministicCases_pruneAndKeep_consistentWithOracle() {
        // Các trường hợp bổ sung
        // {sequenceCount, cardinality, meanWeight, absoluteThreshold, expectedPrune}
        Object[][] cases = {
                // Clearly prune
                { 20, 0, 0.5, 1.0, true }, // empty bitmap
                { 20, 1, 0.1, 1.0, true }, // 1*0.1=0.1 < 1.0
                { 20, 3, 0.2, 1.0, true }, // 3*0.2=0.6 < 1.0
                // Clearly keep
                { 20, 5, 0.5, 1.0, false }, // 5*0.5=2.5 >= 1.0
                { 20, 10, 0.5, 1.0, false }, // 10*0.5=5.0 >= 1.0
                // Zero threshold: always no-op
                { 20, 0, 0.5, 0.0, false },
                { 20, 5, 0.0, 0.0, false },
        };

        for (Object[] row : cases) {
            int sequenceCount = (int) row[0];
            int cardinality = (int) row[1];
            double meanWeight = (double) row[2];
            double threshold = (double) row[3];
            boolean expectedPrune = (boolean) row[4];

            SidBitmap bitmap = bitmapWithCardinality(sequenceCount, cardinality);
            ResolvedMinimumSupport resolved = resolveAbsolute(threshold, sequenceCount);

            boolean actual = FILTER.canPrune(bitmap, meanWeight, resolved);
            assertEquals(expectedPrune, actual,
                    "Case: seq=" + sequenceCount + " card=" + cardinality
                            + " mw=" + meanWeight + " th=" + threshold);

            // Nếu prune, xác nhận interval invariant
            assertUpperBoundIntervalInvariant(bitmap, meanWeight, resolved);
        }
    }
}
