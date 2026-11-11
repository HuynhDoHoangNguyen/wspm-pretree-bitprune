package vn.edu.wspm.backend.services.run;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.mappers.MiningPreviewResponseMapper;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.config.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiningPreviewServiceTest {
    private static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final ConversionReadService reads = mock(ConversionReadService.class);
    private final RunThresholdResolutionService thresholds = spy(new RunThresholdResolutionService(FACTORY.getValidator()));
    private final MiningPreviewService service = new MiningPreviewService(FACTORY.getValidator(), reads,
            thresholds, new MiningPreviewResponseMapper());
    @AfterAll static void close() { FACTORY.close(); }

    private ConversionArtifactResult publication(String id, long count) {
        return new ConversionArtifactResult(id, "private-attempt", "private-validation-hash",
                new ConversionArtifactResult.ArtifactFile("private-sequence-path", "a".repeat(64), 10),
                new ConversionArtifactResult.ArtifactFile("private-mapping-path", "b".repeat(64), 20),
                new ConversionArtifactResult.Statistics(count, 1, 2, 6, 3, 8, 2, 3, 8.0 / 3));
    }
    private void verified(ConversionArtifactResult result) {
        var bundle = mock(ConversionReadService.VerifiedBundle.class);
        when(bundle.result()).thenReturn(result); when(reads.verified(ID)).thenReturn(bundle);
    }
    private RunCreateRequest request(String algorithm, String mode, double value) {
        return new RunCreateRequest(ID, algorithm, mode, value);
    }

    @ParameterizedTest @ValueSource(strings = {"WSPM_PRETREE", "WSPM_BITPRUNE"})
    void bindsOneVerifiedPublicationToRealResolver(String algorithm) {
        verified(publication(ID, 3));
        for (String mode : java.util.List.of("PERCENT", "ABSOLUTE")) {
            var input = request(algorithm, mode, 14);
            var actual = service.preview(input);
            var expected = new MinimumSupportResolver().resolve(new MinimumSupport(14, MinimumSupportMode.valueOf(mode)), 3);
            assertEquals(ID, actual.snapshotId()); assertEquals(algorithm, actual.algorithm());
            assertEquals("3", actual.statistics().sequenceCount());
            assertEquals("a".repeat(64), actual.sequenceSha256()); assertEquals("b".repeat(64), actual.mappingSha256());
            assertEquals(expected.mode().name(), actual.minimumSupport().mode());
            assertEquals(Double.doubleToRawLongBits(expected.configuredValue()), Double.doubleToRawLongBits(actual.minimumSupport().configuredValue()));
            assertEquals(Double.doubleToRawLongBits(expected.effectiveThreshold()), Double.doubleToRawLongBits(actual.minimumSupport().effectiveThreshold()));
            if (mode.equals("ABSOLUTE")) assertNull(actual.minimumSupport().relativeFraction());
            else assertEquals(expected.relativeMinimumSupport().orElseThrow(), actual.minimumSupport().relativeFraction());
            assertEquals("NOT_EVALUATED", actual.executionEligibility());
            verify(thresholds).resolve(input, 3);
        }
        verify(reads, times(2)).verified(ID); verifyNoMoreInteractions(reads);
    }

    @ParameterizedTest @ValueSource(doubles = {0.0, -0.0, Double.MIN_VALUE})
    void zeroAndUnderflowAreInformational(double configured) {
        verified(publication(ID, 3));
        var actual = service.preview(request("WSPM_PRETREE", "PERCENT", configured));
        assertEquals(0.0, Math.abs(actual.minimumSupport().effectiveThreshold()));
        assertEquals("NOT_EVALUATED", actual.executionEligibility());
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, 2147483648L, Long.MAX_VALUE})
    void rejectsInconsistentVerifiedCountBeforeResolver(long count) {
        verified(publication(ID, count));
        var error = assertThrows(ApplicationException.class, () -> service.preview(request("WSPM_PRETREE", "PERCENT", 14)));
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, error.errorCode()); verifyNoInteractions(thresholds);
    }

    @Test void rejectsAnotherSnapshotBeforeResolver() {
        verified(publication("00000000-0000-0000-0000-000000000001", 3));
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.preview(request("WSPM_PRETREE", "PERCENT", 14))).errorCode());
        verifyNoInteractions(thresholds);
    }

    @Test void invalidDirectCallersCannotAccessData() {
        for (var input : java.util.Arrays.asList(null, new RunCreateRequest(null, null, null, null), request("OTHER", "PERCENT", 14))) {
            assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(ApplicationException.class, () -> service.preview(input)).errorCode());
        }
        verifyNoInteractions(reads, thresholds);
    }

    @ParameterizedTest @ValueSource(strings = {"RESOURCE_NOT_FOUND", "INVALID_RESOURCE_STATE", "SERVICE_UNAVAILABLE"})
    void verifierFailuresArePreservedWithoutResolving(String code) {
        var expected = new ApplicationException(ErrorCode.valueOf(code)); when(reads.verified(ID)).thenThrow(expected);
        assertSame(expected, assertThrows(ApplicationException.class, () -> service.preview(request("WSPM_PRETREE", "PERCENT", 14))));
        verifyNoInteractions(thresholds);
    }
}
