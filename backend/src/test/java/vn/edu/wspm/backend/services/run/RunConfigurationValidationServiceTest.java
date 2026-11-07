package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.mappers.RunConfigurationValidationResponseMapper;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.config.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunConfigurationValidationServiceTest {
    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversionReadService reads = mock(ConversionReadService.class);
    private final RunThresholdResolutionService thresholds = spy(new RunThresholdResolutionService(FACTORY.getValidator()));
    private final RunConfigurationValidityGate gate = spy(new RunConfigurationValidityGate());
    private final RunConfigurationValidationService service = new RunConfigurationValidationService(FACTORY.getValidator(), reads,
            thresholds, gate, new RunConfigurationValidationResponseMapper());
    @AfterAll static void close() { FACTORY.close(); }
    private void verified(String id, long count) {
        var result = new ConversionArtifactResult(id, "private-attempt", "private-hash",
                new ConversionArtifactResult.ArtifactFile("private-path", "a".repeat(64), 10),
                new ConversionArtifactResult.ArtifactFile("private-path", "b".repeat(64), 20),
                new ConversionArtifactResult.Statistics(count, 1, 2, 6, 3, 8, 2, 3, 8.0 / 3));
        var bundle = mock(ConversionReadService.VerifiedBundle.class); when(bundle.result()).thenReturn(result);
        when(reads.verified(ID)).thenReturn(bundle);
    }
    private RunCreateRequest input(String algorithm, String mode, double value) { return new RunCreateRequest(ID, algorithm, mode, value); }
    @ParameterizedTest @ValueSource(strings = {"WSPM_PRETREE", "WSPM_BITPRUNE"})
    void bindsVerifiedSnapshotAndResolvesThenGatesWithoutChangingNumbers(String algorithm) {
        verified(ID, 3);
        for (String mode : List.of("PERCENT", "ABSOLUTE")) {
            var request = input(algorithm, mode, 14); var actual = service.validate(request);
            var expected = new MinimumSupportResolver().resolve(new MinimumSupport(14, MinimumSupportMode.valueOf(mode)), 3);
            assertEquals(ID, actual.snapshotId()); assertEquals(algorithm, actual.algorithm());
            assertEquals("3", actual.sequenceCount()); assertEquals("SUPPORTED", actual.configurationStatus());
            assertEquals("a".repeat(64), actual.sequenceSha256()); assertEquals("b".repeat(64), actual.mappingSha256());
            assertEquals(Double.doubleToRawLongBits(expected.effectiveThreshold()), Double.doubleToRawLongBits(actual.minimumSupport().effectiveThreshold()));
            assertEquals(expected.configuredValue(), actual.minimumSupport().configuredValue());
            if (mode.equals("ABSOLUTE")) assertNull(actual.minimumSupport().relativeFraction());
            else assertEquals(expected.relativeMinimumSupport().orElseThrow(), actual.minimumSupport().relativeFraction());
        }
        var order = inOrder(reads, thresholds, gate);
        order.verify(reads).verified(ID); order.verify(thresholds).resolve(input(algorithm, "PERCENT", 14), 3);
        order.verify(gate).requireSupported(any());
        order.verify(reads).verified(ID); order.verify(thresholds).resolve(input(algorithm, "ABSOLUTE", 14), 3);
        order.verify(gate).requireSupported(any()); verifyNoMoreInteractions(reads);
    }
    @ParameterizedTest @ValueSource(doubles = {0.0, -0.0, Double.MIN_VALUE})
    void gateRejectionHappensAfterVerificationAndResolution(double value) {
        verified(ID, 3);
        assertEquals(ErrorCode.UNSUPPORTED_MINING_CONFIGURATION, assertThrows(ApplicationException.class,
                () -> service.validate(input("WSPM_PRETREE", "PERCENT", value))).errorCode());
        var order = inOrder(reads, thresholds, gate); order.verify(reads).verified(ID);
        order.verify(thresholds).resolve(any(), eq(3L)); order.verify(gate).requireSupported(any());
    }
    @ParameterizedTest @ValueSource(longs = {-1, 0, 2147483648L})
    void metadataCountFailurePrecedesZeroGate(long count) {
        verified(ID, count);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.validate(input("WSPM_PRETREE", "PERCENT", 0))).errorCode());
        verifyNoInteractions(thresholds, gate);
    }
    @Test void mismatchedSnapshotAndInvalidDirectCallersNeverReachGate() {
        for (var request : java.util.Arrays.asList(null, new RunCreateRequest(null, null, null, null))) {
            assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(ApplicationException.class, () -> service.validate(request)).errorCode());
        }
        verifyNoInteractions(reads, thresholds, gate);
        verified("00000000-0000-0000-0000-000000000001", 3);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.validate(input("WSPM_PRETREE", "ABSOLUTE", 0))).errorCode());
        verifyNoInteractions(thresholds, gate);
    }
    @ParameterizedTest @ValueSource(strings = {"RESOURCE_NOT_FOUND", "INVALID_RESOURCE_STATE", "SERVICE_UNAVAILABLE"})
    void preservesVerifierErrorsEvenForConfiguredZero(String code) {
        var expected = new ApplicationException(ErrorCode.valueOf(code)); when(reads.verified(ID)).thenThrow(expected);
        assertSame(expected, assertThrows(ApplicationException.class, () -> service.validate(input("WSPM_PRETREE", "ABSOLUTE", 0))));
        verifyNoInteractions(thresholds, gate);
    }
}
