package vn.edu.wspm.backend.mappers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import vn.edu.wspm.backend.dto.dataset.SnapshotDetailResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure projection fixtures, not acquisition/conversion eligibility evidence. */
class SnapshotResponseMapperTest {
    private final SnapshotResponseMapper mapper = Mappers.getMapper(SnapshotResponseMapper.class);
    private static final Instant NOW = Instant.parse("2026-11-06T01:02:03.123456Z");

    private DatasetSnapshotEntity snapshot() {
        var snapshot = new DatasetSnapshotEntity();
        snapshot.setSnapshotId("snapshot-1"); snapshot.setSourceType("DATABASE"); snapshot.setSourceName("đơn hàng.csv");
        snapshot.setValidationStatus("VALID"); snapshot.setSequenceCount(9007199254740993L);
        snapshot.setRawSha256("a".repeat(64)); snapshot.setRawFilePath("private/raw.csv");
        snapshot.setFromTime(NOW); snapshot.setToTime(NOW); snapshot.setCreatedAt(NOW);
        snapshot.setConversionReportJson(Map.of("contractVersion", "wdm-order-csv-v1", "conversionStatus", "NOT_STARTED",
                "statisticsStatus", "NOT_COMPUTED", "validation", Map.of("manifest", "private-manifest"),
                "provenance", Map.of("rawSizeBytes", "42", "acquisition", "DATABASE_EXPORT", "sourceTimeZone", "UTC",
                        "timeField", "ordered_at", "statusPolicy", "COMPLETED_CURRENT_AT_ACQUISITION")));
        return snapshot;
    }

    @Test void acquisitionUsesReceiptBindingAndExplicitAcquisitionDefaultsNotDerivedPlaceholders() {
        var snapshot = snapshot();
        var response = mapper.acquisition(snapshot, "b".repeat(64), 9007199254740993L);
        assertEquals(new DatasetSnapshotResponse("snapshot-1", "CSV", "đơn hàng.csv", "PENDING", "NOT_STARTED",
                "NOT_COMPUTED", "b".repeat(64), "9007199254740993", null, null, null, NOW), response);
        assertEquals(9007199254740993L, snapshot.getSequenceCount());
        assertEquals("a".repeat(64), snapshot.getRawSha256());
    }

    @Test void detailPreservesSafeMetadataAndHidesInternalReportAndUncomputedCount() {
        var snapshot = snapshot();
        var root = snapshot.getConversionReportJson();
        var metadata = mapper.metadata(snapshot);
        var response = mapper.detail(snapshot, metadata, "AVAILABLE");
        assertEquals(new SnapshotDetailResponse("snapshot-1", "DATABASE", "đơn hàng.csv", "VALID", "NOT_STARTED", "NOT_COMPUTED",
                "a".repeat(64), "42", null, NOW, NOW, NOW, "AVAILABLE",
                new SnapshotDetailResponse.Provenance("wdm-order-csv-v1", "DATABASE_EXPORT", "UTC", "ordered_at",
                        "COMPLETED_CURRENT_AT_ACQUISITION")), response);
        String json = JsonMapper.builder().build().writeValueAsString(response);
        assertFalse(json.contains("private")); assertFalse(json.contains("manifest"));
        assertSame(root, snapshot.getConversionReportJson());
    }

    @Test void unknownUnsafeAndWrongTypedMetadataRemainNullAndNeverChangeState() {
        var snapshot = snapshot(); snapshot.setSourceType("UNKNOWN"); snapshot.setSourceName("C:\\private\\test.csv");
        snapshot.setValidationStatus("NEW_STATE"); snapshot.setRawSha256("private-invalid-sha");
        snapshot.setConversionReportJson(Map.of("contractVersion", "future", "conversionStatus", "RUNNING",
                "statisticsStatus", "future", "provenance", Map.of("rawSizeBytes", 42, "sourceTimeZone", "private-zone")));
        var metadata = mapper.metadata(snapshot);
        assertNull(metadata.sourceType()); assertNull(metadata.sourceName()); assertNull(metadata.validationStatus());
        assertNull(metadata.conversionStatus()); assertNull(metadata.statisticsStatus()); assertNull(metadata.sequenceCount());
        assertNull(metadata.rawSha256()); assertNull(metadata.rawSizeBytes());
        assertEquals(new SnapshotDetailResponse.Provenance(null, null, null, null, null), metadata.provenance());
        assertEquals("NEW_STATE", snapshot.getValidationStatus());
        snapshot.setConversionReportJson(null);
        assertNull(mapper.metadata(snapshot).sequenceCount());
    }

    @Test void computedCountsKeepExactStringAndRejectNegativeOrNoncanonicalMetadata() {
        var snapshot = snapshot(); snapshot.setConversionReportJson(Map.of("statisticsStatus", "COMPUTED",
                "provenance", Map.of("rawSizeBytes", "042")));
        assertEquals("9007199254740993", mapper.metadata(snapshot).sequenceCount());
        assertNull(mapper.metadata(snapshot).rawSizeBytes());
        snapshot.setSequenceCount(-1L); assertNull(mapper.metadata(snapshot).sequenceCount());
        snapshot.setSequenceCount(null); assertNull(mapper.metadata(snapshot).sequenceCount());
    }
}
