package vn.edu.wspm.backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @NotBlank(message = "categoryId must not be blank")
        @Size(max = 32, message = "categoryId must not exceed 32 characters")
        String categoryId,

        @NotBlank(message = "categoryName must not be blank")
        @Size(max = 128, message = "categoryName must not exceed 128 characters")
        String categoryName,

        @Size(max = 255, message = "description must not exceed 255 characters")
        String description
) {
}

