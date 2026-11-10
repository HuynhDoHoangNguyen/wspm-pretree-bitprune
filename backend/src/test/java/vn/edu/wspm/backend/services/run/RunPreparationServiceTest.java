package vn.edu.wspm.backend.services.run;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.run.engine.CoreMiningEngineAdapter;
import vn.edu.wspm.config.MinimumSupport;

/** Actual bounded codec/parser, core calculators/resolver/gate; mocked storage publication boundary. */
class RunPreparationServiceTest {
    static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    final ValidationLimits limits = new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 2);
    final ConversionArtifactCodec codec = new ConversionArtifactCodec(JsonMapper.builder().build(), limits);
    final ConversionReadService reads = mock(ConversionReadService.class);
    final SnapshotConversionRepository snapshots = mock(SnapshotConversionRepository.class);
    final ConversionReadService.VerifiedBundle bundle = mock(ConversionReadService.VerifiedBundle.class);
    final byte[] sequence = "1 2 -1 1 -1 -2\n1 -1 2 -1 -2\n1 2 -1 -2\n".getBytes(StandardCharsets.UTF_8);
    final byte[] mapping = codec.mappingBytes(new ProductItemDictionary(Map.of("p1", 1, "p2", 2)));
    RunPreparationService service;
    ConversionArtifactResult publication;
    DatasetSnapshotEntity snapshot;
    @AfterAll static void close() { FACTORY.close(); }
    @BeforeEach void setup() {
        publication = new ConversionArtifactResult(ID, ID, "a".repeat(64),
                new ConversionArtifactResult.ArtifactFile("private-sequence", ConversionArtifactCodec.sha256(sequence), sequence.length),
                new ConversionArtifactResult.ArtifactFile("private-mapping", ConversionArtifactCodec.sha256(mapping), mapping.length),
                codec.verify(sequence, mapping).statistics());
        snapshot = new DatasetSnapshotEntity(); snapshot.setSnapshotId(ID); snapshot.setSourceType("CSV");
        snapshot.setSourceName("synthetic"); snapshot.setRawFilePath(ID + "/raw.csv"); snapshot.setRawSha256("b".repeat(64));
        when(reads.verified(ID)).thenReturn(bundle); when(bundle.result()).thenReturn(publication);
        when(bundle.sequenceBytes()).thenReturn(sequence); when(bundle.mappingBytes()).thenReturn(mapping);
        when(snapshots.read(ID)).thenReturn(snapshot); when(snapshots.completed(snapshot)).thenReturn(publication);
        service = new RunPreparationService(FACTORY.getValidator(), reads, codec, snapshots,
                new RunThresholdResolutionService(FACTORY.getValidator()), new RunConfigurationValidityGate(), limits);
    }
    RunCreateRequest request(String algorithm, String mode, double value) { return new RunCreateRequest(ID, algorithm, mode, value); }

    @Test void measuredPreparationPropagatesWorkerIoWithoutCallingUnmeasuredRead() {
        java.util.function.LongSupplier ticker = () -> 123;
        when(reads.verifiedMeasured(ID, ticker)).thenReturn(new ConversionReadService.MeasuredBundle(bundle, 7654321));
        var measured = service.prepareMeasured(request("WSPM_PRETREE", "ABSOLUTE", .1), ticker);
        assertEquals(7654321, measured.datasetIoNanos()); assertEquals(3, measured.input().engineRequest().database().size());
        verify(reads).verifiedMeasured(ID, ticker); verify(reads, never()).verified(anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"WSPM_PRETREE", "WSPM_BITPRUNE"})
    void weightsAndResolvedValuesMatchRealEngineWithoutMiningDuringPreparation(String algorithm) {
        for (String mode : new String[]{"PERCENT", "ABSOLUTE"}) {
            var prepared = service.prepare(request(algorithm, mode, 14));
            var actual = new CoreMiningEngineAdapter(new RunConfigurationValidityGate()).execute(prepared.engineRequest());
            assertEquals(actual.globalMeanWeight(), prepared.weights().meanWeight().orElseThrow());
            assertEquals(.5, prepared.weights().meanWeight().orElseThrow());
            assertEquals(4.0 / 7, prepared.weights().maxWeight().orElseThrow());
            assertEquals(3.0 / 7, prepared.weights().minWeight().orElseThrow());
            assertEquals(Double.doubleToRawLongBits(actual.minimumSupport().effectiveThreshold()),
                    Double.doubleToRawLongBits(prepared.minimumSupport().effectiveThreshold()));
            assertEquals(3, prepared.engineRequest().database().size()); assertSame(publication, prepared.binding().publication());
            assertFalse(prepared.toString().contains("private")); assertFalse(prepared.binding().toString().contains("raw.csv"));
        }
    }
    @ParameterizedTest @ValueSource(doubles = {0, -0.0, Double.MIN_VALUE})
    void percentZeroAndUnderflowRemainUnsupported(double value) {
        var failure = assertThrows(ApplicationException.class, () -> service.prepare(request("WSPM_PRETREE", "PERCENT", value)));
        assertEquals(ErrorCode.UNSUPPORTED_MINING_CONFIGURATION, failure.errorCode());
    }
    @Test void tinyAbsoluteRemainsPositive() {
        var prepared = service.prepare(request("WSPM_BITPRUNE", "ABSOLUTE", Double.MIN_VALUE));
        assertEquals(Double.MIN_VALUE, prepared.minimumSupport().effectiveThreshold());
    }
    @Test void malformedRequestsFailBeforeArtifactAccess() {
        assertThrows(ApplicationException.class, () -> service.prepare(null));
        assertThrows(ApplicationException.class, () -> service.prepare(request("OTHER", "PERCENT", 14)));
        verifyNoInteractions(reads, snapshots);
    }
    @Test void rejectsMalformedSequenceAndInconsistentStatistics() {
        when(bundle.sequenceBytes()).thenReturn("-2\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.prepare(request("WSPM_PRETREE", "ABSOLUTE", 1))).errorCode());
        when(bundle.sequenceBytes()).thenReturn(sequence);
        var wrong = new ConversionArtifactResult(publication.snapshotId(), publication.attemptId(), publication.validationSha256(),
                publication.sequence(), publication.mapping(), new ConversionArtifactResult.Statistics(4, 1, 3, 5, 2, 7, 2, 3, 1.75));
        when(bundle.result()).thenReturn(wrong);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.prepare(request("WSPM_PRETREE", "ABSOLUTE", 1))).errorCode());
    }
    @Test void publicationChangeBetweenVerifiedReadAndMetadataReadIsRejected() {
        var wrong = new ConversionArtifactResult(ID, "another-attempt", publication.validationSha256(),
                publication.sequence(), publication.mapping(), publication.statistics());
        when(snapshots.completed(snapshot)).thenReturn(wrong);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> service.prepare(request("WSPM_PRETREE", "ABSOLUTE", 1))).errorCode());
    }
    @Test void readFailuresRetainErrorClassificationAndCapacityIsReleased() {
        doThrow(new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND)).when(reads).verified(ID);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(ApplicationException.class,
                () -> service.prepare(request("WSPM_PRETREE", "ABSOLUTE", 1))).errorCode());
        doReturn(bundle).when(reads).verified(ID);
        assertNotNull(service.prepare(request("WSPM_PRETREE", "ABSOLUTE", 1)));
    }
}
