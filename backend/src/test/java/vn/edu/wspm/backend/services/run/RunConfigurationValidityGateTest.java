package vn.edu.wspm.backend.services.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.config.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunConfigurationValidityGateTest {
    private final RunConfigurationValidityGate gate = new RunConfigurationValidityGate();
    @ParameterizedTest @ValueSource(doubles = {Double.MIN_VALUE, 0.25, 1, 100, Double.MAX_VALUE})
    void returnsSamePositiveResolutionWithoutFloorEpsilonOrIntegerCap(double value) {
        var resolved = new MinimumSupportResolver().resolve(MinimumSupport.absolute(value), 3);
        assertSame(resolved, gate.requireSupported(resolved));
    }
    @ParameterizedTest @ValueSource(doubles = {0.0, -0.0})
    void rejectsBothSignedZerosInBothModes(double value) {
        for (var mode : MinimumSupportMode.values()) {
            var resolved = new MinimumSupportResolver().resolve(new MinimumSupport(value, mode), 3);
            var error = assertThrows(ApplicationException.class, () -> gate.requireSupported(resolved));
            assertEquals(422, error.status().value()); assertEquals(ErrorCode.UNSUPPORTED_MINING_CONFIGURATION, error.errorCode());
            assertEquals("minsupValue", error.details().getFirst().field());
            assertEquals("ZERO_EFFECTIVE_THRESHOLD", error.details().getFirst().code());
        }
    }
    @Test void positiveConfiguredPercentCanUnderflowAndMustFail() {
        var resolved = new MinimumSupportResolver().resolve(MinimumSupport.percentage(Double.MIN_VALUE), 3);
        assertTrue(resolved.configuredValue() > 0); assertEquals(0.0, resolved.effectiveThreshold());
        assertThrows(ApplicationException.class, () -> gate.requireSupported(resolved));
    }
    @Test void positivePercentReturnsSameCoreObjectAndBits() {
        var resolved = new MinimumSupportResolver().resolve(MinimumSupport.percentage(14), 7);
        assertSame(resolved, gate.requireSupported(resolved));
        assertEquals(Double.doubleToRawLongBits(0.9800000000000001), Double.doubleToRawLongBits(resolved.effectiveThreshold()));
    }
    @Test void nullAndMalformedInternalResolutionsAreNotUserScopeErrors() {
        assertThrows(NullPointerException.class, () -> gate.requireSupported(null));
        var empty = new MinimumSupportResolver().resolve(MinimumSupport.absolute(1), 0);
        assertThrows(IllegalArgumentException.class, () -> gate.requireSupported(empty));
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            var invalid = mock(ResolvedMinimumSupport.class); when(invalid.numberOfSequences()).thenReturn(3);
            when(invalid.effectiveThreshold()).thenReturn(value);
            assertThrows(IllegalArgumentException.class, () -> gate.requireSupported(invalid));
        }
    }
}
