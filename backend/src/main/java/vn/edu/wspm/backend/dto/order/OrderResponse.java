package vn.edu.wspm.backend.dto.order;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        LocalDateTime orderTime,
        String orderStatus,
        List<OrderItemResponse> items
) {
}

