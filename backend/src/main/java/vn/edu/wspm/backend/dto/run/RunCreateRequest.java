package vn.edu.wspm.backend.dto.run;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record RunCreateRequest(
        @Pattern(regexp = "WSPM_PRETREE|WSPM_BITPRUNE", message = "algorithm must be WSPM_PRETREE or WSPM_BITPRUNE")
        String algorithm,

        @NotNull(message = "minSup must not be null")
        @Positive(message = "minSup must be greater than zero")
        Double minSup,

        @NotNull(message = "minWeight must not be null")
        @Positive(message = "minWeight must be greater than zero")
        Double minWeight
) {
}

