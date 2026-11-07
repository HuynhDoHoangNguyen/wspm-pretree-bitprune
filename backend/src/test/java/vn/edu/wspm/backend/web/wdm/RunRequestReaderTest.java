package vn.edu.wspm.backend.web.wdm;

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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class RunRequestReaderTest {
    static final String BODY = """
            {"snapshotId":"550e8400-e29b-41d4-a716-446655440000","algorithm":"WSPM_BITPRUNE","minsupMode":"PERCENT","minsupValue":10.0}
            """.strip();
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final RunRequestReader reader = new RunRequestReader(FACTORY.getValidator());

    @AfterAll static void closeFactory() { FACTORY.close(); }

    @Test void requestHasExactlyTheAgreedFields() {
        assertEquals(Set.of("snapshotId", "algorithm", "minsupMode", "minsupValue"),
                Arrays.stream(RunCreateRequest.class.getRecordComponents()).map(c -> c.getName()).collect(Collectors.toSet()));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-0.0", "0.125", "1e-10", "1e-999", "10", "100"})
    void preservesConfiguredDoubleWithoutScalingOrIntegerRounding(String number) throws IOException {
        for (String algorithm : Set.of("WSPM_PRETREE", "WSPM_BITPRUNE")) {
            for (String mode : Set.of("PERCENT", "ABSOLUTE")) {
                RunCreateRequest input = read(BODY.replace("10.0", number)
                        .replace("WSPM_BITPRUNE", algorithm).replace("PERCENT", mode));
                assertEquals(Double.doubleToRawLongBits(Double.parseDouble(number)),
                        Double.doubleToRawLongBits(input.minsupValue()));
                assertEquals(algorithm, input.algorithm());
                assertEquals(mode, input.minsupMode());
                assertEquals("550e8400-e29b-41d4-a716-446655440000", input.snapshotId());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"minSup", "minWeight", "effectiveThreshold", "meanW", "artifactPath", "status"})
    void rejectsLegacyAndOtherUnknownFieldsEvenAlongsideValidFields(String field) {
        assertInvalid(BODY.replace("}", ",\"" + field + "\":1}"), ErrorCode.VALIDATION_FAILED);
    }

    static Stream<String> invalidShapes() {
        Stream<String> roots = Stream.of("", "null", "[]", "1", "\"text\"");
        Stream<String> required = Stream.of("snapshotId", "algorithm", "minsupMode", "minsupValue")
                .flatMap(field -> {
                    String member = switch (field) {
                        case "snapshotId" -> "\"snapshotId\":\"550e8400-e29b-41d4-a716-446655440000\"";
                        case "algorithm" -> "\"algorithm\":\"WSPM_BITPRUNE\"";
                        case "minsupMode" -> "\"minsupMode\":\"PERCENT\"";
                        default -> "\"minsupValue\":10.0";
                    };
                    return Stream.of(BODY.replace(member + ",", "").replace("," + member, ""),
                            BODY.replace(member, "\"" + field + "\":null"),
                            BODY.replace(member, "\"" + field + "\":true"),
                            BODY.replace(member, "\"" + field + "\":[]"),
                            BODY.replace(member, "\"" + field + "\":{}"),
                            BODY.replace(member, "\"" + field + "\":"
                                    + (field.equals("minsupValue") ? "\"10\"" : "10")));
                });
        return Stream.concat(roots, required);
    }

    @ParameterizedTest @MethodSource("invalidShapes")
    void rejectsMissingNullAndWrongTypes(String json) {
        assertInvalid(json, ErrorCode.VALIDATION_FAILED);
    }

    @ParameterizedTest @ValueSource(strings = {"\"10\"", "-1", "100.00001"})
    void rejectsNumericCoercionAndOutOfRangePercent(String number) {
        assertInvalid(BODY.replace("10.0", number), ErrorCode.VALIDATION_FAILED);
    }

    static Stream<String> malformedJson() {
        return Stream.of("{", "}", BODY + " {}", BODY + " null", "/*comment*/" + BODY,
                BODY.replace("10.0", "NaN"), BODY.replace("10.0", "Infinity"),
                BODY.replace("}", ",\"minsupValue\":20}"),
                BODY.replace("}", ",\"algorithm\":\"WSPM_PRETREE\"}"));
    }

    @ParameterizedTest @MethodSource("malformedJson")
    void rejectsMalformedDuplicateAndTrailingInput(String json) {
        assertInvalid(json, ErrorCode.MALFORMED_REQUEST);
    }

    @Test void validatesDtoConstraintsAfterStrictShapeProjection() {
        ApplicationException error = assertInvalid(BODY.replace("WSPM_BITPRUNE", "wspm_bitprune")
                .replace("PERCENT", "RELATIVE"), ErrorCode.VALIDATION_FAILED);
        assertEquals(java.util.List.of("algorithm", "minsupMode"), error.details().stream().map(d -> d.field()).toList());
    }

    @Test void rejectsNumbersThatOverflowDouble() {
        assertInvalid(BODY.replace("10.0", "1e999"), ErrorCode.VALIDATION_FAILED);
    }

    private RunCreateRequest read(String json) throws IOException {
        return reader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private ApplicationException assertInvalid(String json, ErrorCode code) {
        ApplicationException error = assertThrows(ApplicationException.class, () -> read(json));
        assertEquals(code, error.errorCode());
        return error;
    }
}
