package vn.edu.wspm.backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(String productId, String productName, String categoryId,
        String productStatus, BigDecimal price, String imageUrl, LocalDateTime createdAt,
        List<Image> images) {
    public ProductDetailResponse { images = List.copyOf(images); }
    public record Image(String imageId, String imageUrl, String altText, Integer displayOrder) {}
}
