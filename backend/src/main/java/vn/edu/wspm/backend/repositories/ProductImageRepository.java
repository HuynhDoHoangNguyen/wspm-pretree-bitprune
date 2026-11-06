package vn.edu.wspm.backend.repositories;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.ProductImageEntity;

public interface ProductImageRepository extends JpaRepository<ProductImageEntity, String> {
    List<ProductImageEntity> findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(
            Collection<String> productIds);
}
