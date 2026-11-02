package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.PatternItemEntity;
import vn.edu.wspm.backend.entities.PatternItemId;

public interface PatternItemRepository
        extends JpaRepository<PatternItemEntity, PatternItemId> {

    List<PatternItemEntity> findByPatternIdOrderByItemsetOrderAscItemOrderAsc(Long patternId);
}

