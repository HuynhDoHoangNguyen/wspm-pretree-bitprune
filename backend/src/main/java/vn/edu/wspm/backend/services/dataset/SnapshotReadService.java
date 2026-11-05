package vn.edu.wspm.backend.services.dataset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.dataset.SnapshotDetailResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SnapshotReadService {
    private final DatasetSnapshotRepository repository;
    private final RawSnapshotStorage storage;

    public SnapshotReadService(DatasetSnapshotRepository repository, RawSnapshotStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    public SnapshotDetailResponse detail(String id) {
        var entity = find(id);
        var report = entity.getConversionReportJson();
        Map<?, ?> provenance = report != null && report.get("provenance") instanceof Map<?, ?> map ? map : Map.of();
        String statistics = known(text(report, "statisticsStatus"), "NOT_COMPUTED", "COMPUTED");
        String count = "COMPUTED".equals(statistics) && entity.getSequenceCount() != null && entity.getSequenceCount() >= 0
                ? entity.getSequenceCount().toString() : null;
        String size = text(provenance, "rawSizeBytes");
        size = size != null && size.matches("[1-9][0-9]*") ? size : null;
        String hash = entity.getRawSha256();
        hash = hash != null && hash.matches("[0-9a-f]{64}") ? hash : null;
        String availability = storage.availability(id, entity.getRawFilePath(), hash, size);
        log.debug("Snapshot detail read: snapshotId={}, rawAvailability={}", id, availability);
        return new SnapshotDetailResponse(id, known(entity.getSourceType(), "CSV", "DATABASE"), safeName(entity.getSourceName()),
                known(entity.getValidationStatus(), "PENDING", "VALID", "INVALID", "LEGACY_UNVERIFIED"),
                known(text(report, "conversionStatus"), "NOT_STARTED"), statistics, hash, size, count,
                entity.getFromTime(), entity.getToTime(), entity.getCreatedAt(), availability,
                new SnapshotDetailResponse.Provenance(known(text(report, "contractVersion"), "wdm-order-csv-v1"),
                        known(text(provenance, "acquisition"), "CSV_UPLOAD", "DATABASE_EXPORT"),
                        known(text(provenance, "sourceTimeZone"), "UTC"), known(text(provenance, "timeField"), "ordered_at"),
                        known(text(provenance, "statusPolicy"), "COMPLETED_CURRENT_AT_ACQUISITION")));
    }

    public byte[] raw(String id) {
        var entity = find(id);
        Map<?, ?> report = entity.getConversionReportJson();
        Map<?, ?> provenance = report != null && report.get("provenance") instanceof Map<?, ?> map ? map : Map.of();
        if (entity.getRawFilePath() == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        try {
            byte[] bytes = storage.verifiedBytes(id, entity.getRawFilePath(), entity.getRawSha256(), text(provenance, "rawSizeBytes"));
            log.info("Snapshot raw download verified: snapshotId={}, sizeBytes={}", id, bytes.length);
            return bytes;
        } catch (NoSuchFileException missing) {
            throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        } catch (IOException | IllegalArgumentException failure) {
            var safe = new IllegalStateException("Raw artifact read failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Snapshot raw download unavailable: snapshotId={}", id, safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Raw artifact could not be verified or read.");
        }
    }

    private DatasetSnapshotEntity find(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw ApplicationException.invalid("snapshotId", "Supply a canonical snapshot UUID.");
        }
        return repository.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
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
