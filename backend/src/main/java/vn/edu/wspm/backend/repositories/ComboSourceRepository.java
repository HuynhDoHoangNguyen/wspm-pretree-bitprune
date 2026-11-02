package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ComboSourceEntity;
import vn.edu.wspm.backend.entities.ComboSourceId;

public interface ComboSourceRepository
        extends JpaRepository<ComboSourceEntity, ComboSourceId> {

    List<ComboSourceEntity> findByComboId(String comboId);
}

