package vn.edu.wspm.backend.dto.run;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RunCreateRequestValidationTest {
    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll static void closeFactory() { FACTORY.close(); }

    static Stream<RunCreateRequest> validRequests() {
        return Stream.of("WSPM_PRETREE", "WSPM_BITPRUNE").flatMap(algorithm ->
                Stream.of("PERCENT", "ABSOLUTE").flatMap(mode ->
                        Stream.of(0.0, -0.0, Double.MIN_VALUE, 0.125, 10.0, 100.0)
                                .map(value -> new RunCreateRequest(ID, algorithm, mode, value))));
    }

    @ParameterizedTest @MethodSource("validRequests")
    void acceptsConfiguredValuesWithoutApprovingExecution(RunCreateRequest request) {
        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @Test void acceptsFiniteAbsoluteValuesAboveSequenceCountsWithoutLookingUpData() {
        assertTrue(fields(new RunCreateRequest(ID, "WSPM_PRETREE", "ABSOLUTE", Double.MAX_VALUE)).isEmpty());
    }

    @Test void requiresAllFourFields() {
        assertEquals(Set.of("snapshotId", "algorithm", "minsupMode", "minsupValue"),
                fields(new RunCreateRequest(null, null, null, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "550E8400-E29B-41D4-A716-446655440000", "1-1-1-1-1",
            " 550e8400-e29b-41d4-a716-446655440000", "550e8400-e29b-41d4-a716-446655440000\n"})
    void rejectsNonCanonicalSnapshotIds(String id) {
        assertEquals(Set.of("snapshotId"), fields(new RunCreateRequest(id, "WSPM_PRETREE", "PERCENT", 10.0)));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "wspm_pretree", "OTHER", "WSPM_PRETREE "})
    void rejectsInvalidAlgorithms(String algorithm) {
        assertEquals(Set.of("algorithm"), fields(new RunCreateRequest(ID, algorithm, "PERCENT", 10.0)));
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "percent", "RELATIVE", "PERCENT "})
    void rejectsInvalidModes(String mode) {
        assertEquals(Set.of("minsupMode"), fields(new RunCreateRequest(ID, "WSPM_PRETREE", mode, 10.0)));
    }

    static Stream<Double> invalidValues() {
        return Stream.of(-Double.MIN_VALUE, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
    }

    @ParameterizedTest @MethodSource("invalidValues")
    void rejectsNegativeAndNonFiniteValuesInBothModes(double value) {
        for (String mode : Set.of("PERCENT", "ABSOLUTE")) {
            assertEquals(Set.of("minsupValue"), fields(new RunCreateRequest(ID, "WSPM_PRETREE", mode, value)));
        }
    }

    @Test void percentUpperBoundaryIsInclusiveWithoutEpsilon() {
        assertTrue(fields(new RunCreateRequest(ID, "WSPM_PRETREE", "PERCENT", 100.0)).isEmpty());
        assertEquals(Set.of("minsupValue"), fields(new RunCreateRequest(ID, "WSPM_PRETREE", "PERCENT", Math.nextUp(100.0))));
    }

    private static Set<String> fields(RunCreateRequest request) {
        return VALIDATOR.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
