package vn.edu.wspm.backend.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ItemMappingEntity;

public interface ItemMappingRepository
        extends JpaRepository<ItemMappingEntity, String> {

    Optional<ItemMappingEntity> findByItemId(Integer itemId);
}

