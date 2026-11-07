package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import vn.edu.wspm.backend.web.wdm.RunRequestReader;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.config.MinimumSupportMode;
import vn.edu.wspm.config.MinimumSupportResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** In-process reader/service/core contract; no HTTP, Spring context, DB or miner. */
class RunThresholdResolutionContractTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final RunRequestReader reader = new RunRequestReader(FACTORY.getValidator());
    private final RunThresholdResolutionService service = new RunThresholdResolutionService(FACTORY.getValidator());

    @AfterAll static void closeFactory() { FACTORY.close(); }

    record Case(String algorithm, String mode, String number, int count) {}

    static Stream<Case> requests() {
        return Stream.of("WSPM_PRETREE", "WSPM_BITPRUNE").flatMap(algorithm ->
                Stream.of("PERCENT", "ABSOLUTE").flatMap(mode ->
                        Stream.of("0", "-0.0", "0.25", "14", "100", "4.9e-324", "1e-999")
                                .flatMap(number -> Stream.of(1, 7, Integer.MAX_VALUE)
                                        .map(count -> new Case(algorithm, mode, number, count)))));
    }

    @ParameterizedTest @MethodSource("requests")
    void readerThroughServicePreservesCoreResolution(Case input) throws IOException {
        var request = reader.read(stream(json(input.algorithm(), input.mode(), input.number())));
        var expected = new MinimumSupportResolver().resolve(new MinimumSupport(
                Double.parseDouble(input.number()), MinimumSupportMode.valueOf(input.mode())), input.count());
        RunThresholdResolutionServiceTest.assertSameResolution(expected, service.resolve(request, input.count()));
    }

    @Test void readerDoesNotAcceptClientSequenceCountOrLegacyInputs() {
        for (String field : java.util.List.of("numberOfSequences", "sequenceCount", "minSup", "minWeight", "effectiveThreshold")) {
            var error = assertThrows(ApplicationException.class,
                    () -> reader.read(stream(json("WSPM_PRETREE", "PERCENT", "14").replace("}", ",\"" + field + "\":7}"))));
            assertEquals(ErrorCode.VALIDATION_FAILED, error.errorCode());
        }
    }

    private static String json(String algorithm, String mode, String value) {
        return "{\"snapshotId\":\"" + RunThresholdResolutionServiceTest.ID + "\",\"algorithm\":\""
                + algorithm + "\",\"minsupMode\":\"" + mode + "\",\"minsupValue\":" + value + "}";
    }

    private static ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
