
package vn.edu.wspm.backend.dto.catalog;

import java.math.BigDecimal;

public record ProductResponse(
        String productId,
        String productName,
        String categoryId,
        String productStatus,
        BigDecimal price
) {
}
