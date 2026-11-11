package vn.edu.wspm.backend.dto.order;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderItemCreateRequest(
        @NotBlank(message = "productId must not be blank")
        @Size(max = 32, message = "productId must not exceed 32 characters")
        String productId,

        @NotNull(message = "unitPrice must not be null")
        @Positive(message = "unitPrice must be greater than zero")
        BigDecimal unitPrice,

        @NotNull(message = "quantity must not be null")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {
}

