package vn.edu.wspm.backend.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(String productId, String productName, String categoryId,
        String productStatus, BigDecimal price, String imageUrl, LocalDateTime createdAt,
        List<Image> images, Information information) {
    public ProductDetailResponse { images = List.copyOf(images); }
    public ProductDetailResponse(String productId, String productName, String categoryId,
            String productStatus, BigDecimal price, String imageUrl, LocalDateTime createdAt, List<Image> images) {
        this(productId, productName, categoryId, productStatus, price, imageUrl, createdAt, images, null);
    }
    public record Information(String sku, String categoryName, String shortDescription,
            String description, String currencyCode) {}
    public record Image(String imageId, String imageUrl, String altText, Integer displayOrder) {}
}
