package vn.edu.wspm.backend.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(
        String orderId,
        String productId,
        BigDecimal unitPrice,
        Integer quantity
) {
}

