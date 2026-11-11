package vn.edu.wspm.backend.dto.product;

import java.math.BigDecimal;

public record ProductResponse(
        String productId,
        String productName,
        String categoryId,
        String productStatus,
        BigDecimal price,
        String imageUrl,
        java.time.LocalDateTime createdAt
) {
    public ProductResponse(String productId, String productName, String categoryId,
            String productStatus, BigDecimal price) {
        this(productId, productName, categoryId, productStatus, price, null, null);
    }
}

