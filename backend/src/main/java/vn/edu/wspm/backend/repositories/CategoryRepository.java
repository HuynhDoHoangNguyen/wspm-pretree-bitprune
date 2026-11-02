package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.CategoryEntity;

public interface CategoryRepository
        extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findAllByOrderByCategoryNameAsc();
}

