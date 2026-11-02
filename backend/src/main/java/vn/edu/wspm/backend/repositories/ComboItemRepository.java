package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ComboItemEntity;
import vn.edu.wspm.backend.entities.ComboItemId;

public interface ComboItemRepository
        extends JpaRepository<ComboItemEntity, ComboItemId> {

    List<ComboItemEntity> findByComboIdOrderByItemsetOrderAscItemOrderAsc(String comboId);
}

