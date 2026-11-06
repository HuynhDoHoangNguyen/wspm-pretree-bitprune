package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.mappers.ConversionResponseMapper;
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.csv.OrderCleaningPipeline;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversionReadServiceTest {
    private static final String ID = "00000000-0000-0000-0000-000000000001", ATTEMPT = "00000000-0000-0000-0000-000000000002";
    private static final String TIME = "2026-11-06T00:00:00.000000Z", HASH = "a".repeat(64);
    private final JsonMapper json = JsonMapper.builder().build();
    private final ValidationLimits limits = new ValidationLimits(1000, 100, 10000, 10000, 30000, 1);
    private final ConversionArtifactCodec codec = new ConversionArtifactCodec(json, limits);
    private final SnapshotConversionRepository repository = mock(SnapshotConversionRepository.class);
    private final ConversionArtifactStorage storage = mock(ConversionArtifactStorage.class);
    private final RawSnapshotStorage raw = mock(RawSnapshotStorage.class);
    private final ConversionReadService reads = new ConversionReadService(repository, codec, storage, raw, limits, new ConversionResponseMapper(), json);
    private DatasetSnapshotEntity snapshot;
    private ValidationReport report;

    @BeforeEach void fixture() {
        snapshot = new DatasetSnapshotEntity(); snapshot.setSnapshotId(ID); snapshot.setSourceType("CSV");
        snapshot.setRawFilePath(ID + "/raw.csv"); snapshot.setRawSha256(HASH); snapshot.setSequenceCount(0L); snapshot.setValidationStatus("VALID");
        report = new ValidationReport(ValidationReport.VERSION, "wdm-order-csv-v1", OrderCleaningPipeline.POLICY_VERSION,
                ID, HASH, "4", TIME, "VALID", "READY", true, null, List.of(), List.of());
        var root = new LinkedHashMap<String, Object>();
        root.put("contractVersion", "wdm-order-csv-v1"); root.put("conversionStatus", "NOT_STARTED"); root.put("statisticsStatus", "NOT_COMPUTED");
        root.put("provenance", Map.of("rawSizeBytes", "4")); root.put("validation", json.convertValue(report, Map.class));
        snapshot.setConversionReportJson(root);
        when(repository.read(ID)).thenReturn(snapshot);
    }

    private void state(String state) {
        snapshot.getConversionReportJson().put("conversionStatus", state);
        var op = new LinkedHashMap<String, Object>(Map.of("version", ConversionArtifactCodec.VERSION, "policies", ConversionArtifactCodec.POLICIES,
                "attemptId", ATTEMPT, "validationSha256", codec.validationHash(report), "startedAt", TIME));
        if (state.equals("FAILED")) { op.put("failureCode", "CONVERSION_FAILED"); op.put("failedAt", TIME); }
        if (state.equals("SUCCEEDED")) { op.put("completedAt", TIME); snapshot.getConversionReportJson().put("statisticsStatus", "COMPUTED"); }
        snapshot.getConversionReportJson().put("conversion", op);
    }
    private ConversionArtifactResult published() throws Exception {
        state("SUCCEEDED");
        byte[] sequence = "1 -1 -2\n".getBytes(StandardCharsets.UTF_8), mapping = codec.mappingBytes(new ProductItemDictionary(Map.of("p", 1)));
        var result = new ConversionArtifactResult(ID, ATTEMPT, codec.validationHash(report),
                new ConversionArtifactResult.ArtifactFile("private-sequence", ConversionArtifactCodec.sha256(sequence), sequence.length),
                new ConversionArtifactResult.ArtifactFile("private-mapping", ConversionArtifactCodec.sha256(mapping), mapping.length),
                new ConversionArtifactResult.Statistics(1, 1, 0, 1, 1, 1, 1, 1, 1));
        when(repository.completed(any())).thenReturn(result);
        when(raw.verifiedBytes(anyString(), anyString(), anyString(), anyString())).thenReturn(new byte[4]);
        when(storage.read(ID, ATTEMPT, "sequences.spmf", result.sequence())).thenReturn(sequence);
        when(storage.read(ID, ATTEMPT, "mapping.json", result.mapping())).thenReturn(mapping);
        return result;
    }
    private void noWrites() {
        verify(repository, never()).claim(any(), anyString()); verify(repository, never()).publish(any(), any());
        verify(repository, never()).fail(any());
    }

    @Test void notStartedStateNeedsNoArtifactIoAndDoesNotMutateMetadata() throws Exception {
        String before = json.writeValueAsString(snapshot.getConversionReportJson());
        var response = reads.status(ID);
        assertEquals("NOT_STARTED", response.conversionStatus()); assertNull(response.statistics()); assertNull(response.sequence());
        assertEquals(before, json.writeValueAsString(snapshot.getConversionReportJson()));
        verifyNoInteractions(raw, storage); noWrites();
    }
    @Test void runningAndFailedExposeOnlyWhitelistedFieldsWithoutIoOrWrites() throws Exception {
        for (String state : List.of("RUNNING", "FAILED")) {
            state(state); String before = json.writeValueAsString(snapshot.getConversionReportJson());
            var response = reads.status(ID);
            assertEquals(state, response.conversionStatus()); assertNotNull(response.startedAt()); assertNull(response.completedAt());
            assertEquals(state.equals("FAILED") ? "CONVERSION_FAILED" : null, response.failureCode());
            assertEquals(before, json.writeValueAsString(snapshot.getConversionReportJson()));
            String body = json.writeValueAsString(response); assertFalse(body.contains(ATTEMPT)); assertFalse(body.contains("validationSha256"));
        }
        verifyNoInteractions(raw, storage); noWrites();
    }
    @Test void forgedPendingStateOrUnknownFailureCodeIsRejectedWithoutIo() {
        state("RUNNING"); snapshot.getConversionReportJson().put("statisticsStatus", "COMPUTED");
        assertEquals(409, assertThrows(ApplicationException.class, () -> reads.status(ID)).status().value());
        snapshot.getConversionReportJson().put("statisticsStatus", "NOT_COMPUTED"); state("FAILED");
        @SuppressWarnings("unchecked") var op = (Map<String, Object>) snapshot.getConversionReportJson().get("conversion");
        op.put("failureCode", "private-exception");
        assertEquals(409, assertThrows(ApplicationException.class, () -> reads.status(ID)).status().value());
        verifyNoInteractions(raw, storage); noWrites();
    }
    @Test void verifiedBundleOwnsBytesAndSafeProjectionHasOnlyProtectedApiUrls() throws Exception {
        var expected = published(); var bundle = reads.verified(ID);
        assertEquals(expected, bundle.result());
        byte[] copy = bundle.sequenceBytes(); copy[0] = '9';
        assertEquals("1 -1 -2\n", new String(bundle.sequenceBytes(), StandardCharsets.UTF_8));
        assertEquals("/api/v1/admin/dataset-snapshots/" + ID + "/artifacts/sequence", bundle.response().sequence().downloadUrl());
        String body = json.writeValueAsString(bundle.response());
        assertFalse(body.contains("private")); assertFalse(body.contains(ATTEMPT)); assertFalse(body.contains("validationSha256"));
        assertEquals("1", bundle.response().statistics().sequenceCount());
        noWrites(); verify(storage, never()).save(anyString(), anyString(), any(), any());
    }
    @Test void missingArtifactFailsWithNoStateChangeAndNoPartialBundle() throws Exception {
        var result = published(); when(storage.read(ID, ATTEMPT, "sequences.spmf", result.sequence())).thenThrow(new IOException("private path"));
        String before = json.writeValueAsString(snapshot.getConversionReportJson());
        var failure = assertThrows(ApplicationException.class, () -> reads.verified(ID));
        assertEquals(503, failure.status().value()); assertFalse(failure.getMessage().contains("private"));
        assertEquals(before, json.writeValueAsString(snapshot.getConversionReportJson())); noWrites();
    }
    @Test void changedPublicationBetweenIoAndFreshReadIsRejected() throws Exception {
        var result = published();
        var changed = new ConversionArtifactResult(ID, ATTEMPT, "b".repeat(64), result.sequence(), result.mapping(), result.statistics());
        when(repository.completed(any())).thenReturn(result, changed);
        assertEquals(409, assertThrows(ApplicationException.class, () -> reads.verified(ID)).status().value()); noWrites();
    }
    @Test void malformedIdDoesNotReachRepositoryAndDbOutageDoesNotBecomeMissingSnapshot() {
        assertEquals(400, assertThrows(ApplicationException.class, () -> reads.status("bad")).status().value());
        verifyNoInteractions(repository);
        when(repository.read(ID)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private connection"));
        assertEquals(503, assertThrows(ApplicationException.class, () -> reads.status(ID)).status().value()); noWrites();
    }
}
