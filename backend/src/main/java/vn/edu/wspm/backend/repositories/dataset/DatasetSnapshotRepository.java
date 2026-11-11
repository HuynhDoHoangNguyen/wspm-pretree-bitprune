package vn.edu.wspm.backend.repositories.dataset;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;

import java.util.Optional;

/** Acquisition-only persistence: no generic save/merge/update/delete surface. */
@Repository
public class DatasetSnapshotRepository {
    @PersistenceContext private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(DatasetSnapshotEntity snapshot) {
        entityManager.persist(snapshot);
        entityManager.flush();
    }

    @Transactional(readOnly = true)
    public Optional<DatasetSnapshotEntity> findById(String id) {
        return Optional.ofNullable(entityManager.find(DatasetSnapshotEntity.class, id));
    }
}
