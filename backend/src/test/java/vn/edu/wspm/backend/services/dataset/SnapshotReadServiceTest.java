package vn.edu.wspm.backend.services.dataset;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotReadServiceTest {
    @TempDir Path root;
    DatasetSnapshotRepository repository;
    RawSnapshotStorage storage;
    SnapshotReadService service;
    DatasetSnapshotEntity entity;
    RawSnapshotStorage.StoredRaw raw;

    @BeforeEach
    void setup() throws Exception {
        repository = mock(DatasetSnapshotRepository.class);
        storage = new RawSnapshotStorage(root.toString(), new MultipartConfigElement("", 1024, 2048, 0));
        service = new SnapshotReadService(repository, storage);
        raw = storage.save(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        entity = new DatasetSnapshotEntity();
        entity.setSnapshotId(raw.snapshotId()); entity.setSourceType("CSV"); entity.setSourceName("sample.csv");
        entity.setRawFilePath(raw.reference()); entity.setRawSha256(raw.sha256());
        entity.setValidationStatus("PENDING"); entity.setSequenceCount(0L); entity.setCreatedAt(Instant.parse("2026-11-05T00:00:00Z"));
        entity.setConversionReportJson(Map.of("conversionStatus", "NOT_STARTED", "statisticsStatus", "NOT_COMPUTED",
                "contractVersion", "wdm-order-csv-v1", "provenance", Map.of("rawSizeBytes", "3", "acquisition", "CSV_UPLOAD")));
        when(repository.findById(raw.snapshotId())).thenReturn(Optional.of(entity));
    }

    @Test
    void detailHidesPlaceholderAndReadPreservesExactBytesAndMetadata() throws Exception {
        var before = entity.getConversionReportJson();
        var detail = service.detail(raw.snapshotId());
        assertEquals("AVAILABLE", detail.rawAvailability());
        assertNull(detail.sequenceCount()); assertNull(detail.provenance().sourceTimeZone());
        assertEquals("CSV_UPLOAD", detail.provenance().acquisition());
        assertEquals(raw.sha256(), detail.rawSha256());
        assertArrayEquals(new byte[]{1, 2, 3}, service.raw(raw.snapshotId()));
        assertSame(before, entity.getConversionReportJson());
        assertEquals(raw.reference(), entity.getRawFilePath());
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve(raw.reference())));
    }

    @Test
    void missingAndUnknownMetadataNeverBecomesValidOrComputed() {
        entity.setConversionReportJson(Map.of("statisticsStatus", "secret-path", "conversionStatus", "secret-path",
                "provenance", Map.of("rawSizeBytes", 3, "sourceTimeZone", "secret-path")));
        entity.setSourceName("/private/secret.csv");
        var detail = service.detail(raw.snapshotId());
        assertNull(detail.statisticsStatus()); assertNull(detail.conversionStatus());
        assertNull(detail.sequenceCount()); assertNull(detail.rawSizeBytes()); assertNull(detail.sourceName());
        assertNull(detail.provenance().sourceTimeZone());
        assertEquals("UNAVAILABLE", detail.rawAvailability());
        entity.setConversionReportJson(null);
        assertNull(service.detail(raw.snapshotId()).sequenceCount());
    }

    @Test
    void countIsOnlyExposedWithExplicitComputedStatus() {
        entity.setConversionReportJson(Map.of("statisticsStatus", "COMPUTED"));
        entity.setSequenceCount(9007199254740993L);
        assertEquals("9007199254740993", service.detail(raw.snapshotId()).sequenceCount());
    }

    @Test
    void missingArtifactKeepsDetailAndRawReturns404() throws Exception {
        Files.delete(root.resolve(raw.reference())); // Only this test's own temporary file.
        assertEquals("MISSING", service.detail(raw.snapshotId()).rawAvailability());
        assertEquals(404, assertThrows(ApplicationException.class, () -> service.raw(raw.snapshotId())).status().value());
    }

    @Test
    void sameSizeTamperIsRejectedBeforeReturningAnyBytes() throws Exception {
        Files.write(root.resolve(raw.reference()), new byte[]{3, 2, 1});
        assertEquals("AVAILABLE", service.detail(raw.snapshotId()).rawAvailability()); // Probe is not a hash attestation.
        assertEquals(503, assertThrows(ApplicationException.class, () -> service.raw(raw.snapshotId())).status().value());
        assertEquals(raw.sha256(), entity.getRawSha256());
        assertArrayEquals(new byte[]{3, 2, 1}, Files.readAllBytes(root.resolve(raw.reference())));
    }

    @Test
    void traversalAbsoluteAndForeignReferencesCannotBeRead() throws Exception {
        for (String reference : new String[]{"../outside.csv", root.resolve(raw.reference()).toString(),
                "11111111-2222-4333-8444-555555555555/raw.csv"}) {
            entity.setRawFilePath(reference);
            assertEquals("UNAVAILABLE", service.detail(raw.snapshotId()).rawAvailability());
            assertEquals(503, assertThrows(ApplicationException.class, () -> service.raw(raw.snapshotId())).status().value());
        }
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(root.resolve(raw.reference())));
    }

    @Test
    void malformedIdDoesNotQueryRepositoryAndUnknownIdIs404() {
        clearInvocations(repository);
        for (String id : new String[]{"bad", "../raw.csv", "1-1-1-1-1"}) {
            assertEquals(400, assertThrows(ApplicationException.class, () -> service.detail(id)).status().value());
        }
        verifyNoInteractions(repository);
        String missing = "11111111-2222-4333-8444-555555555555";
        assertEquals(404, assertThrows(ApplicationException.class, () -> service.detail(missing)).status().value());
    }

    @Test
    void readsNeverCreateStorageAndEnforceSizeMetadataAndBounds() throws Exception {
        Path absent = root.resolve("absent");
        var empty = new RawSnapshotStorage(absent.toString(), new MultipartConfigElement("", 1024, 2048, 0));
        assertEquals("MISSING", empty.availability(raw.snapshotId(), raw.reference(), raw.sha256(), "3"));
        assertFalse(Files.exists(absent));
        for (String size : new String[]{"2", "4", "1025", "9223372036854775808", "0", "not-a-count"}) {
            assertThrows(IOException.class, () -> storage.verifiedBytes(raw.snapshotId(), raw.reference(), raw.sha256(), size));
        }
    }
}
