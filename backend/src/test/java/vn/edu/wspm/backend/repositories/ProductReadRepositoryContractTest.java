package vn.edu.wspm.backend.repositories;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.PartTree;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.entities.ProductImageEntity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Parses real derived query contracts without starting JPA or touching any database. */
class ProductReadRepositoryContractTest {
    @Test void latestUsesTimestampDescendingAndIdAscendingForTies() {
        var tree = new PartTree("findAllByOrderByCreatedAtDescProductIdAsc", ProductEntity.class);
        assertEquals(List.of(Sort.Order.desc("createdAt"), Sort.Order.asc("productId")), tree.getSort().toList());
    }
    @Test void imageBatchFiltersByProductAndUsesStableImageOrder() {
        var tree = new PartTree("findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc", ProductImageEntity.class);
        assertEquals("product.productId", tree.getParts().iterator().next().getProperty().toDotPath());
        assertEquals(List.of(Sort.Order.asc("displayOrder"), Sort.Order.asc("imageId")), tree.getSort().toList());
    }
}
