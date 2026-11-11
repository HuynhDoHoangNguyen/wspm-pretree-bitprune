package vn.edu.wspm.backend.dto.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @NotBlank(message = "categoryId must not be blank")
        @Size(max = 32, message = "categoryId must not exceed 32 characters")
        String categoryId,

        @NotBlank(message = "productName must not be blank")
        @Size(max = 255, message = "productName must not exceed 255 characters")
        String productName,

        @NotNull(message = "unitPrice must not be null")
        @Positive(message = "unitPrice must be greater than zero")
        BigDecimal unitPrice,

        Boolean isActive,

        @NotNull(message = "weight must not be null")
        @Positive(message = "weight must be greater than zero")
        Double weight
) {
}

