package vn.edu.wspm.bitprune.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;

class BitPruneRunMetadataTest {

    private final MinimumSupportResolver resolver = new MinimumSupportResolver();

    @Test
    @DisplayName("Class must be an immutable Java record")
    void classIsRecord() {
        assertTrue(BitPruneRunMetadata.class.isRecord());
    }

    @Test
    @DisplayName("Valid empty database metadata construction")
    void validEmptyDatabaseMetadata() {
        ResolvedMinimumSupport minsup = resolver.resolve(MinimumSupport.absolute(1.0), 0);
        var metadata = new BitPruneRunMetadata(0, OptionalDouble.empty(), minsup);

        assertEquals(0, metadata.distinctItemCount());
        assertTrue(metadata.globalMeanWeight().isEmpty());
        assertEquals(minsup, metadata.minimumSupport());
        assertEquals(0, metadata.minimumSupport().numberOfSequences());
    }

    @Test
    @DisplayName("Valid Table 1 metadata construction")
    void validTableOneMetadata() {
        ResolvedMinimumSupport minsup = resolver.resolve(MinimumSupport.percentage(14.0), 7);
        double meanW = 12.0 / 66.0;
        var metadata = new BitPruneRunMetadata(8, OptionalDouble.of(meanW), minsup);

        assertEquals(8, metadata.distinctItemCount());
        assertTrue(metadata.globalMeanWeight().isPresent());
        assertEquals(meanW, metadata.globalMeanWeight().getAsDouble(), 1e-9);
        assertEquals(minsup, metadata.minimumSupport());
        assertEquals(7, metadata.minimumSupport().numberOfSequences());
        assertEquals(0.98, metadata.minimumSupport().effectiveThreshold(), 1e-9);
    }

    @Test
    @DisplayName("Null optional MeanW or null ResolvedMinimumSupport must be rejected")
    void nullArgumentsRejected() {
        ResolvedMinimumSupport minsup = resolver.resolve(MinimumSupport.percentage(14.0), 7);
        assertThrows(NullPointerException.class,
                () -> new BitPruneRunMetadata(8, null, minsup));
        assertThrows(NullPointerException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.of(0.2), null));
    }

    @Test
    @DisplayName("Negative distinct item count must be rejected")
    void negativeDistinctItemCountRejected() {
        ResolvedMinimumSupport minsup = resolver.resolve(MinimumSupport.percentage(14.0), 7);
        var error = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(-1, OptionalDouble.of(0.2), minsup));
        assertTrue(error.getMessage().contains("distinctItemCount must not be negative"));
    }

    @Test
    @DisplayName("NaN, infinite, or negative MeanW must be rejected")
    void invalidMeanWRejected() {
        ResolvedMinimumSupport minsup = resolver.resolve(MinimumSupport.percentage(14.0), 7);

        assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.of(Double.NaN), minsup));
        assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.of(Double.POSITIVE_INFINITY), minsup));
        assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.of(Double.NEGATIVE_INFINITY), minsup));
        assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.of(-0.0001), minsup));
    }

    @Test
    @DisplayName("Zero-sequence metadata with non-zero distinct item count or present MeanW must be rejected")
    void invalidZeroSequenceCombinationsRejected() {
        ResolvedMinimumSupport minsup0 = resolver.resolve(MinimumSupport.absolute(1.0), 0);

        // Zero sequences but positive distinct count
        var err1 = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(5, OptionalDouble.empty(), minsup0));
        assertTrue(err1.getMessage().contains("zero-sequence metadata must have distinctItemCount == 0"));

        // Zero sequences but present MeanW
        var err2 = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(0, OptionalDouble.of(0.2), minsup0));
        assertTrue(err2.getMessage().contains("zero-sequence metadata must have empty globalMeanWeight"));
    }

    @Test
    @DisplayName("Positive-sequence metadata with zero distinct count or empty MeanW must be rejected")
    void invalidPositiveSequenceCombinationsRejected() {
        ResolvedMinimumSupport minsupPositive = resolver.resolve(MinimumSupport.percentage(14.0), 7);

        // Positive sequences but zero distinct items
        var err1 = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(0, OptionalDouble.of(0.2), minsupPositive));
        assertTrue(err1.getMessage().contains("positive-sequence metadata must have positive distinctItemCount"));

        // Positive sequences but empty MeanW
        var err2 = assertThrows(IllegalArgumentException.class,
                () -> new BitPruneRunMetadata(8, OptionalDouble.empty(), minsupPositive));
        assertTrue(err2.getMessage().contains("positive-sequence metadata must have present globalMeanWeight"));
    }

    @Test
    @DisplayName("Record equality, hashCode, and toString include all fields")
    void equalityHashCodeAndToString() {
        ResolvedMinimumSupport minsup1 = resolver.resolve(MinimumSupport.percentage(14.0), 7);
        ResolvedMinimumSupport minsup2 = resolver.resolve(MinimumSupport.percentage(20.0), 7);

        var m1 = new BitPruneRunMetadata(8, OptionalDouble.of(0.18182), minsup1);
        var m2 = new BitPruneRunMetadata(8, OptionalDouble.of(0.18182), minsup1);
        var mDiffCount = new BitPruneRunMetadata(9, OptionalDouble.of(0.18182), minsup1);
        var mDiffMeanW = new BitPruneRunMetadata(8, OptionalDouble.of(0.25), minsup1);
        var mDiffMinsup = new BitPruneRunMetadata(8, OptionalDouble.of(0.18182), minsup2);

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertEquals(m1, m1);

        assertNotEquals(m1, null);
        assertNotEquals(m1, "different type");
        assertNotEquals(m1, mDiffCount);
        assertNotEquals(m1, mDiffMeanW);
        assertNotEquals(m1, mDiffMinsup);

        String str = m1.toString();
        assertNotNull(str);
        assertTrue(str.contains("BitPruneRunMetadata"));
        assertTrue(str.contains("distinctItemCount=8"));
        assertTrue(str.contains("globalMeanWeight="));
        assertTrue(str.contains("minimumSupport="));
    }
}

