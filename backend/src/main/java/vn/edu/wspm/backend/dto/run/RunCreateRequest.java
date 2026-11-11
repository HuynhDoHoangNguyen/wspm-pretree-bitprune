package vn.edu.wspm.backend.dto.run;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Configured input only; validation does not establish mining execution eligibility. */
@ValidRunMinimumSupport
public record RunCreateRequest(
        @NotNull
        @Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String snapshotId,

        @NotNull
        @Pattern(regexp = "WSPM_PRETREE|WSPM_BITPRUNE", message = "algorithm must be WSPM_PRETREE or WSPM_BITPRUNE")
        String algorithm,

        @NotNull
        @Pattern(regexp = "PERCENT|ABSOLUTE")
        String minsupMode,

        @NotNull
        Double minsupValue
) {
}

