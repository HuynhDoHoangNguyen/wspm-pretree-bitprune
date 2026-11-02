package vn.edu.wspm.backend.dto.order;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(
        @NotBlank(message = "orderId must not be blank")
        @Size(max = 32, message = "orderId must not exceed 32 characters")
        String orderId,

        @NotBlank(message = "customerId must not be blank")
        @Size(max = 32, message = "customerId must not exceed 32 characters")
        String customerId,

        LocalDateTime orderTime,

        @Size(max = 16, message = "orderStatus must not exceed 16 characters")
        String orderStatus,

        @NotEmpty(message = "items list must not be empty")
        List<@Valid OrderItemCreateRequest> items
) {
}

