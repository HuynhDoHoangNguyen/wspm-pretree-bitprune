package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ComboEntity;

public interface ComboRepository
        extends JpaRepository<ComboEntity, String> {

    List<ComboEntity> findByBatch_Run_RunId(String runId);

    List<ComboEntity> findByComboStatus(String comboStatus);

    List<ComboEntity> findByComboStatusOrderBySupportDescWeightDesc(String comboStatus);
}

