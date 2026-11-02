package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.dto.catalog.ProductResponse;
import vn.edu.wspm.backend.entities.ProductEntity;

@Component
public class ProductMapper {

    public ProductResponse toResponse(ProductEntity product) {
        return new ProductResponse(
                product.getProductId(),
                product.getProductName(),
                product.getCategoryId(),
                product.getProductStatus(),
                product.getPrice()
        );
    }
}
