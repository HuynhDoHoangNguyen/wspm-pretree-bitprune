package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.PatternEntity;

public interface PatternRepository
        extends JpaRepository<PatternEntity, Long> {

    List<PatternEntity> findByRun_RunIdOrderBySupportDescWeightDesc(String runId);
}

