package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.run.RunConfigurationValidationResponse;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.config.ResolvedMinimumSupport;

/** Pure public projection after verification, resolution and the operational scope gate. */
@Component
public class RunConfigurationValidationResponseMapper {
    public RunConfigurationValidationResponse map(ConversionArtifactResult publication, String algorithm, ResolvedMinimumSupport resolved) {
        return new RunConfigurationValidationResponse(publication.snapshotId(), algorithm, ConversionArtifactCodec.VERSION,
                publication.sequence().sha256(), publication.mapping().sha256(), Long.toString(publication.statistics().sequenceCount()),
                new RunConfigurationValidationResponse.MinimumSupport(resolved.mode().name(), resolved.configuredValue(),
                        resolved.relativeMinimumSupport().isPresent() ? resolved.relativeMinimumSupport().getAsDouble() : null,
                        resolved.effectiveThreshold()), "SUPPORTED");
    }
}
