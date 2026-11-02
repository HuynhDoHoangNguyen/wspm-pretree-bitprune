package vn.edu.wspm.backend.dto.combo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ComboUpdateRequest(
        @NotBlank(message = "comboStatus must not be blank")
        @Pattern(regexp = "PENDING|APPROVED|REJECTED", message = "comboStatus must be PENDING, APPROVED, or REJECTED")
        String comboStatus,

        @Size(max = 255, message = "comboName must not exceed 255 characters")
        String comboName,

        @Positive(message = "comboPrice must be greater than zero")
        BigDecimal comboPrice
) {
}

