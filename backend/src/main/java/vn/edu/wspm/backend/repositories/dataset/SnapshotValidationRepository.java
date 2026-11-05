package vn.edu.wspm.backend.repositories.dataset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Dedicated publication boundary; acquisition repository remains acquisition-only. */
@Repository
public class SnapshotValidationRepository {
    @PersistenceContext private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Optional<DatasetSnapshotEntity> find(String id) {
        return Optional.ofNullable(entityManager.find(DatasetSnapshotEntity.class, id));
    }

    /** Short, first-writer-wins transaction: report and status commit together. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public DatasetSnapshotEntity publish(String id, String expectedHash, String expectedReference,
            Map<String, Object> expectedRoot, Map<String, Object> report, String status) {
        var entity = entityManager.find(DatasetSnapshotEntity.class, id, LockModeType.PESSIMISTIC_WRITE,
                Map.of("jakarta.persistence.lock.timeout", 0));
        if (entity == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var root = entity.getConversionReportJson();
        if (root != null && root.containsKey("validation")) return entity; // service verifies winner binding
        if (!"PENDING".equals(entity.getValidationStatus()) || !Objects.equals(expectedHash, entity.getRawSha256())
                || !Objects.equals(expectedReference, entity.getRawFilePath()) || !Objects.equals(expectedRoot, root)
                || entity.getSequenceFilePath() != null || entity.getMappingFilePath() != null) {
            throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        }
        if (!"VALID".equals(status) && !"INVALID".equals(status)) throw new IllegalArgumentException("Invalid publication status");
        var updated = new LinkedHashMap<>(root);
        updated.put("validation", report);
        entity.setConversionReportJson(updated);
        entity.setValidationStatus(status);
        entityManager.flush();
        return entity;
    }
}
