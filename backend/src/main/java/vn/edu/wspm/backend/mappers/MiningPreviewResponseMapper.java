package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.run.MiningPreviewResponse;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.config.ResolvedMinimumSupport;

/** Pure closed projection; the caller supplies one verified publication and its resolved threshold. */
@Component
public class MiningPreviewResponseMapper {
    public MiningPreviewResponse map(ConversionArtifactResult publication, String algorithm, ResolvedMinimumSupport threshold) {
        var s = publication.statistics();
        return new MiningPreviewResponse(publication.snapshotId(), algorithm, ConversionArtifactCodec.VERSION,
                publication.sequence().sha256(), publication.mapping().sha256(),
                new MiningPreviewResponse.Statistics(Long.toString(s.sequenceCount()), Long.toString(s.singleItemsetSequences()),
                        Long.toString(s.multiItemsetSequences()), Long.toString(s.itemsetCount()), Long.toString(s.distinctItems()),
                        Long.toString(s.totalItemOccurrences()), Integer.toString(s.minSequenceLength()), Integer.toString(s.maxSequenceLength()),
                        s.averageSequenceLength()),
                new MiningPreviewResponse.MinimumSupport(threshold.mode().name(), threshold.configuredValue(),
                        threshold.relativeMinimumSupport().isPresent() ? threshold.relativeMinimumSupport().getAsDouble() : null,
                        threshold.effectiveThreshold()), "NOT_EVALUATED");
    }
}
