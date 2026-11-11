package vn.edu.wspm.backend.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerCreateRequest(
        @NotBlank(message = "customerId must not be blank")
        @Size(max = 32, message = "customerId must not exceed 32 characters")
        String customerId,

        @NotBlank(message = "customerName must not be blank")
        @Size(max = 128, message = "customerName must not exceed 128 characters")
        String customerName,

        @Email(message = "email must be a valid email format")
        @Size(max = 128, message = "email must not exceed 128 characters")
        String email,

        @Size(max = 32, message = "phone must not exceed 32 characters")
        String phone
) {
}

