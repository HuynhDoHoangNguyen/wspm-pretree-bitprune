
package vn.edu.wspm.backend.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.wspm.backend.entities.ProductEntity;

public interface ProductRepository
        extends JpaRepository<ProductEntity, String> {

    List<ProductEntity> findAllByOrderByProductNameAsc();

    List<ProductEntity> findAllByOrderByCreatedAtDescProductIdAsc();

    /** Candidates only: database collation can match more than exact Java strings. */
    @Query("select p.productId from ProductEntity p where p.productId in :ids")
    List<String> findReferenceCandidates(@Param("ids") Collection<String> ids);
}
