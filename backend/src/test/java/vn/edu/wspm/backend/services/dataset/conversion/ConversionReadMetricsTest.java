package vn.edu.wspm.backend.services.dataset.conversion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.ConversionResponseMapper;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import tools.jackson.databind.json.JsonMapper;

/** Deterministic scope tests: mocked dependencies advance one shared clock, not elapsed wall time. */
class ConversionReadMetricsTest {
    static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    final AtomicLong clock = new AtomicLong();
    final SnapshotConversionRepository repository = mock(SnapshotConversionRepository.class);
    final ConversionArtifactCodec codec = mock(ConversionArtifactCodec.class);
    final ConversionArtifactStorage storage = mock(ConversionArtifactStorage.class);
    final RawSnapshotStorage raw = mock(RawSnapshotStorage.class);
    final DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity();
    final ConversionArtifactResult.Statistics stats = new ConversionArtifactResult.Statistics(1, 1, 0, 1, 1, 1, 1, 1, 1);
    final ConversionArtifactResult result = new ConversionArtifactResult(ID, ID, "hash",
            new ConversionArtifactResult.ArtifactFile("sequence", "hash", 1),
            new ConversionArtifactResult.ArtifactFile("mapping", "hash", 1), stats);
    final ConversionReadService service = new ConversionReadService(repository, codec, storage, raw,
            new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 1), new ConversionResponseMapper(), JsonMapper.builder().build());
    @BeforeEach void setup() throws Exception {
        snapshot.setConversionReportJson(Map.of("provenance", Map.of("rawSizeBytes", "1"), "conversion",
                Map.of("version", ConversionArtifactCodec.VERSION, "policies", ConversionArtifactCodec.POLICIES,
                        "startedAt", "2026-11-07T00:00:00Z", "completedAt", "2026-11-07T00:00:01Z")));
        when(repository.read(ID)).thenAnswer(call -> { clock.addAndGet(1000); return snapshot; });
        when(repository.completed(snapshot)).thenReturn(result);
        when(raw.verifiedBytes(any(), any(), any(), any())).thenAnswer(call -> { clock.addAndGet(11); return new byte[]{1}; });
        when(storage.read(eq(ID), eq(ID), eq("sequences.spmf"), any())).thenAnswer(call -> { clock.addAndGet(13); return new byte[]{2}; });
        when(storage.read(eq(ID), eq(ID), eq("mapping.json"), any())).thenAnswer(call -> { clock.addAndGet(17); return new byte[]{3}; });
        when(codec.verify(any(), any())).thenAnswer(call -> { clock.addAndGet(10000); return new ConversionArtifactCodec.Verified(stats, null); });
    }
    @Test void sumsOnlyThreeExistingVerifiedReadsExcludingDbAndParsing() throws Exception {
        var measured = service.verifiedMeasured(ID, clock::get);
        assertEquals(41, measured.datasetIoNanos()); assertEquals(12041, clock.get());
        assertArrayEquals(new byte[]{2}, measured.bundle().sequenceBytes());
        verify(repository, times(2)).read(ID); verify(raw).verifiedBytes(any(), any(), any(), any());
        verify(storage, times(2)).read(any(), any(), any(), any()); verify(codec).verify(any(), any());
    }
    @Test void failedVerifiedReadDoesNotPublishMetricsAndReleasesCapacity() throws Exception {
        when(storage.read(eq(ID), eq(ID), eq("sequences.spmf"), any())).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        assertThrows(ApplicationException.class, () -> service.verifiedMeasured(ID, clock::get));
        when(storage.read(eq(ID), eq(ID), eq("sequences.spmf"), any())).thenReturn(new byte[]{2});
        assertNotNull(service.verifiedMeasured(ID, clock::get));
    }
    @Test void changedPublicationStillRejectsMeasuredRead() {
        when(repository.completed(snapshot)).thenReturn(result, new ConversionArtifactResult(ID, "changed", "hash", result.sequence(), result.mapping(), stats));
        assertThrows(ApplicationException.class, () -> service.verifiedMeasured(ID, clock::get));
    }
}
