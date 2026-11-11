package vn.edu.wspm.backend.dto.run;

/** Supported configuration at a verified read; no run reservation or execution authorization. */
public record RunConfigurationValidationResponse(String snapshotId, String algorithm, String artifactVersion,
        String sequenceSha256, String mappingSha256, String sequenceCount,
        MinimumSupport minimumSupport, String configurationStatus) {
    public record MinimumSupport(String mode, double configuredValue, Double relativeFraction, double effectiveThreshold) {}
    @Override public String toString() { return "RunConfigurationValidationResponse[snapshotId=" + snapshotId + "]"; }
}
