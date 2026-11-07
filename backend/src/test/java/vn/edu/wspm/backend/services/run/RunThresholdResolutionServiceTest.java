package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;
import vn.edu.wspm.config.ResolvedMinimumSupport;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Actual core resolver, no mocked threshold computation. */
class RunThresholdResolutionServiceTest {
    static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final RunThresholdResolutionService service = new RunThresholdResolutionService(FACTORY.getValidator());

    @AfterAll static void closeFactory() { FACTORY.close(); }

    record Case(RunCreateRequest request, int count) {}

    static Stream<Case> configurations() {
        return Stream.of("WSPM_PRETREE", "WSPM_BITPRUNE").flatMap(algorithm ->
                Stream.of("PERCENT", "ABSOLUTE").flatMap(mode ->
                        Stream.of(0.0, -0.0, Double.MIN_VALUE, 0.25, 14.0, 100.0).flatMap(value ->
                                Stream.of(1, 7, Integer.MAX_VALUE).map(count ->
                                        new Case(new RunCreateRequest(ID, algorithm, mode, value), count)))));
    }

    @ParameterizedTest @MethodSource("configurations")
    void exactlyMatchesAllCoreResultFields(Case input) {
        var request = input.request();
        var expected = new MinimumSupportResolver().resolve(new MinimumSupport(
                request.minsupValue(), MinimumSupportMode.valueOf(request.minsupMode())), input.count());
        assertSameResolution(expected, service.resolve(request, input.count()));
    }

    @Test void preservesTableOneUnroundedThreshold() {
        var result = service.resolve(request("PERCENT", 14.0), 7);
        assertEquals(0.14, result.relativeMinimumSupport().orElseThrow());
        assertEquals(0.9800000000000001, result.effectiveThreshold());
        assertNotEquals(1.0, result.effectiveThreshold());
    }

    @Test void absoluteThresholdIsFractionalUnscaledAndHasNoRelativeFraction() {
        for (int count : List.of(0, 1, 7, Integer.MAX_VALUE)) {
            var result = service.resolve(request("ABSOLUTE", 0.25), count);
            assertEquals(0.25, result.effectiveThreshold());
            assertTrue(result.relativeMinimumSupport().isEmpty());
        }
        assertEquals(Double.MAX_VALUE, service.resolve(request("ABSOLUTE", Double.MAX_VALUE), 7).effectiveThreshold());
    }

    @ParameterizedTest @ValueSource(doubles = {0.0, -0.0, 14.0, 100.0})
    void percentEmptyDatabaseRetainsCoreRejection(double value) {
        var expected = assertThrows(IllegalArgumentException.class,
                () -> new MinimumSupportResolver().resolve(MinimumSupport.percentage(value), 0));
        var actual = assertThrows(IllegalArgumentException.class, () -> service.resolve(request("PERCENT", value), 0));
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test void resolutionKeepsZeroAndPositiveUnderflowWithoutApprovingExecution() {
        assertEquals(0.0, service.resolve(request("PERCENT", Double.MIN_VALUE), 7).effectiveThreshold());
        for (String mode : List.of("PERCENT", "ABSOLUTE")) {
            assertEquals(0L, Double.doubleToRawLongBits(service.resolve(request(mode, 0.0), 7).effectiveThreshold()));
            assertEquals(Long.MIN_VALUE, Double.doubleToRawLongBits(service.resolve(request(mode, -0.0), 7).effectiveThreshold()));
        }
        assertEquals(0.0, service.resolve(request("ABSOLUTE", 0.0), 0).effectiveThreshold());
    }

    @ParameterizedTest @ValueSource(longs = {-1, Long.MIN_VALUE, 2147483648L, Long.MAX_VALUE})
    void rejectsInvalidInternalCountsBeforeNarrowing(long count) {
        for (String mode : List.of("PERCENT", "ABSOLUTE")) {
            assertThrows(IllegalArgumentException.class, () -> service.resolve(request(mode, 10.0), count));
        }
    }

    static Stream<RunCreateRequest> invalidRequests() {
        return Stream.of(
                new RunCreateRequest(null, "WSPM_PRETREE", "PERCENT", 10.0),
                new RunCreateRequest(ID.toUpperCase(java.util.Locale.ROOT), "WSPM_PRETREE", "PERCENT", 10.0),
                new RunCreateRequest(ID, null, "PERCENT", 10.0),
                new RunCreateRequest(ID, "OTHER", "PERCENT", 10.0),
                new RunCreateRequest(ID, "WSPM_PRETREE", null, 10.0),
                new RunCreateRequest(ID, "WSPM_PRETREE", "percent", 10.0),
                new RunCreateRequest(ID, "WSPM_PRETREE", "PERCENT", null),
                request("PERCENT", -1.0), request("PERCENT", Math.nextUp(100.0)),
                request("PERCENT", Double.NaN), request("ABSOLUTE", Double.POSITIVE_INFINITY),
                request("ABSOLUTE", Double.NEGATIVE_INFINITY), request("ABSOLUTE", -Double.MIN_VALUE));
    }

    @ParameterizedTest @MethodSource("invalidRequests")
    void directCallersCannotBypassDtoValidation(RunCreateRequest request) {
        var error = assertThrows(ApplicationException.class, () -> service.resolve(request, 7));
        assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode());
        assertFalse(error.details().isEmpty());
        assertTrue(error.details().stream().allMatch(d -> d.message().equals("Supply a valid value.")));
    }

    @Test void nullRequestIsSafeValidationFailureAndMultipleDetailsAreSorted() {
        var missing = assertThrows(ApplicationException.class, () -> service.resolve(null, 7));
        assertEquals(ErrorCode.VALIDATION_FAILED, missing.errorCode());
        assertEquals("request", missing.details().getFirst().field());
        var error = assertThrows(ApplicationException.class,
                () -> service.resolve(new RunCreateRequest(null, null, null, null), 7));
        assertEquals(List.of("algorithm", "minsupMode", "minsupValue", "snapshotId"),
                error.details().stream().map(d -> d.field()).toList());
    }

    private static RunCreateRequest request(String mode, double value) {
        return new RunCreateRequest(ID, "WSPM_PRETREE", mode, value);
    }

    static void assertSameResolution(ResolvedMinimumSupport expected, ResolvedMinimumSupport actual) {
        assertEquals(expected.mode(), actual.mode());
        assertEquals(expected.numberOfSequences(), actual.numberOfSequences());
        assertEquals(Double.doubleToRawLongBits(expected.configuredValue()), Double.doubleToRawLongBits(actual.configuredValue()));
        assertEquals(Double.doubleToRawLongBits(expected.effectiveThreshold()), Double.doubleToRawLongBits(actual.effectiveThreshold()));
        assertEquals(expected.relativeMinimumSupport().isPresent(), actual.relativeMinimumSupport().isPresent());
        if (expected.relativeMinimumSupport().isPresent()) {
            assertEquals(Double.doubleToRawLongBits(expected.relativeMinimumSupport().orElseThrow()),
                    Double.doubleToRawLongBits(actual.relativeMinimumSupport().orElseThrow()));
        }
    }
}
