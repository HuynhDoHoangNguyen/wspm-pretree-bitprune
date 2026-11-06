package vn.edu.wspm.backend.services.dataset;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.dataset.SnapshotDetailResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import vn.edu.wspm.backend.mappers.SnapshotResponseMapper;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotReadService {
    private final DatasetSnapshotRepository repository;
    private final RawSnapshotStorage storage;
    private final SnapshotResponseMapper mapper;

    public SnapshotDetailResponse detail(String id) {
        var entity = find(id);
        var metadata = mapper.metadata(entity);
        String availability = storage.availability(id, entity.getRawFilePath(), metadata.rawSha256(), metadata.rawSizeBytes());
        log.debug("Snapshot detail read: snapshotId={}, rawAvailability={}", id, availability);
        return mapper.detail(entity, metadata, availability);
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
}
