
package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ProductEntity;

public interface ProductRepository
        extends JpaRepository<ProductEntity, String> {

    List<ProductEntity> findAllByOrderByProductNameAsc();
}
