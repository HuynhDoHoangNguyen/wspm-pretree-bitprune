package vn.edu.wspm.backend.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import vn.edu.wspm.backend.dto.dataset.SnapshotDetailResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;

import java.util.Map;
import java.util.Set;

/** Pure public projection. Storage access and acquisition/validation decisions belong to services. */
@Mapper(config = BackendMapperConfig.class)
public abstract class SnapshotResponseMapper {
    @Mapping(target = "snapshotId", source = "snapshot.snapshotId")
    @Mapping(target = "sourceName", source = "snapshot.sourceName")
    @Mapping(target = "sourceType", constant = "CSV")
    @Mapping(target = "validationStatus", constant = "PENDING")
    @Mapping(target = "conversionStatus", constant = "NOT_STARTED")
    @Mapping(target = "statisticsStatus", constant = "NOT_COMPUTED")
    @Mapping(target = "rawSha256", source = "rawSha256")
    @Mapping(target = "rawSizeBytes", source = "rawSizeBytes")
    @Mapping(target = "sequenceCount", ignore = true)
    @Mapping(target = "fromTime", ignore = true)
    @Mapping(target = "toTime", ignore = true)
    @Mapping(target = "createdAt", source = "snapshot.createdAt")
    public abstract DatasetSnapshotResponse acquisition(DatasetSnapshotEntity snapshot, String rawSha256, long rawSizeBytes);

    @Mapping(target = "snapshotId", source = "snapshot.snapshotId")
    @Mapping(target = "sourceType", source = "metadata.sourceType")
    @Mapping(target = "sourceName", source = "metadata.sourceName")
    @Mapping(target = "validationStatus", source = "metadata.validationStatus")
    @Mapping(target = "conversionStatus", source = "metadata.conversionStatus")
    @Mapping(target = "statisticsStatus", source = "metadata.statisticsStatus")
    @Mapping(target = "rawSha256", source = "metadata.rawSha256")
    @Mapping(target = "rawSizeBytes", source = "metadata.rawSizeBytes")
    @Mapping(target = "sequenceCount", source = "metadata.sequenceCount")
    @Mapping(target = "provenance", source = "metadata.provenance")
    @Mapping(target = "rawAvailability", source = "rawAvailability")
    @Mapping(target = "fromTime", source = "snapshot.fromTime")
    @Mapping(target = "toTime", source = "snapshot.toTime")
    @Mapping(target = "createdAt", source = "snapshot.createdAt")
    public abstract SnapshotDetailResponse detail(DatasetSnapshotEntity snapshot, Metadata metadata, String rawAvailability);

    /** Sanitized view shared by the service's availability probe and the final public mapping. */
    public record Metadata(String sourceType, String sourceName, String validationStatus,
            String conversionStatus, String statisticsStatus, String rawSha256, String rawSizeBytes,
            String sequenceCount, SnapshotDetailResponse.Provenance provenance) {}

    public Metadata metadata(DatasetSnapshotEntity snapshot) {
        var report = snapshot.getConversionReportJson();
        Map<?, ?> provenance = report != null && report.get("provenance") instanceof Map<?, ?> map ? map : Map.of();
        String statistics = known(text(report, "statisticsStatus"), "NOT_COMPUTED", "COMPUTED");
        String count = "COMPUTED".equals(statistics) && snapshot.getSequenceCount() != null && snapshot.getSequenceCount() >= 0
                ? snapshot.getSequenceCount().toString() : null;
        String size = text(provenance, "rawSizeBytes");
        size = size != null && size.matches("[1-9][0-9]*") ? size : null;
        String hash = snapshot.getRawSha256();
        hash = hash != null && hash.matches("[0-9a-f]{64}") ? hash : null;
        return new Metadata(known(snapshot.getSourceType(), "CSV", "DATABASE"), safeName(snapshot.getSourceName()),
                known(snapshot.getValidationStatus(), "PENDING", "VALID", "INVALID", "LEGACY_UNVERIFIED"),
                known(text(report, "conversionStatus"), "NOT_STARTED", "RUNNING", "SUCCEEDED", "FAILED"), statistics, hash, size, count,
                new SnapshotDetailResponse.Provenance(known(text(report, "contractVersion"), "wdm-order-csv-v1"),
                        known(text(provenance, "acquisition"), "CSV_UPLOAD", "DATABASE_EXPORT"),
                        known(text(provenance, "sourceTimeZone"), "UTC"), known(text(provenance, "timeField"), "ordered_at"),
                        known(text(provenance, "statusPolicy"), "COMPLETED_CURRENT_AT_ACQUISITION")));
    }

    private static String text(Map<?, ?> map, String key) {
        return map != null && map.get(key) instanceof String text ? text : null;
    }
    private static String known(String value, String... allowed) {
        return value != null && Set.of(allowed).contains(value) ? value : null;
    }
    private static String safeName(String value) {
        return value != null && !value.isBlank() && value.length() <= 255 && !value.contains("/") && !value.contains("\\")
                && !value.contains(":") && value.codePoints().noneMatch(Character::isISOControl) ? value : null;
    }
}
