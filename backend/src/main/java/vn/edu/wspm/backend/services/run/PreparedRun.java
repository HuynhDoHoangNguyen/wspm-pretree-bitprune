package vn.edu.wspm.backend.services.run;

import java.util.Objects;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.backend.services.run.engine.MiningEngineRequest;
import vn.edu.wspm.config.ResolvedMinimumSupport;
import vn.edu.wspm.statistics.ItemWeightResult;

/** Internal immutable handoff, never serialized or logged as a payload. No file bytes retained. */
public record PreparedRun(MiningEngineRequest engineRequest, ResolvedMinimumSupport minimumSupport,
                          ItemWeightResult weights, Binding binding) {
    public PreparedRun {
        Objects.requireNonNull(engineRequest); Objects.requireNonNull(minimumSupport);
        Objects.requireNonNull(weights); Objects.requireNonNull(binding);
        if (engineRequest.database().size() != minimumSupport.numberOfSequences()
                || binding.publication().statistics().sequenceCount() != minimumSupport.numberOfSequences()
                || engineRequest.minimumSupport().mode() != minimumSupport.mode()
                || Double.doubleToRawLongBits(engineRequest.minimumSupport().configuredValue())
                    != Double.doubleToRawLongBits(minimumSupport.configuredValue())
                || minimumSupport.effectiveThreshold() <= 0 || !Double.isFinite(minimumSupport.effectiveThreshold())
                || weights.meanWeight().isEmpty() || weights.maxWeight().isEmpty() || weights.minWeight().isEmpty()) {
            throw new IllegalArgumentException("Prepared input is inconsistent");
        }
    }
    public record Binding(ConversionArtifactResult publication, String sourceType, String sourceName,
                          String rawReference, String rawSha256) {
        public Binding {
            Objects.requireNonNull(publication); Objects.requireNonNull(sourceType); Objects.requireNonNull(sourceName);
            Objects.requireNonNull(rawReference); Objects.requireNonNull(rawSha256);
        }
        @Override public String toString() { return "RunBinding[snapshotId=" + publication.snapshotId() + "]"; }
    }
    @Override public String toString() { return "PreparedRun[snapshotId=" + binding.publication().snapshotId() + "]"; }
}
