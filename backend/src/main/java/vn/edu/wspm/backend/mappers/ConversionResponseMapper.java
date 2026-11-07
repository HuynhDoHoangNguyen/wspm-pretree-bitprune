package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.dataset.ConversionResponse;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;

import java.time.Instant;

/** Pure explicit projection of already validated metadata; no entity/report or filesystem access. */
@Component
public class ConversionResponseMapper {
    public ConversionResponse pending(String id, String state, Instant started, Instant failed, String failureCode) {
        return new ConversionResponse(id, state, "NOT_COMPUTED", started, null, failed, failureCode,
                started == null ? null : ConversionArtifactCodec.VERSION, null, null, null);
    }
    public ConversionResponse completed(ConversionArtifactResult result, Instant started, Instant completed) {
        var s = result.statistics();
        String base = "/api/v1/admin/dataset-snapshots/" + result.snapshotId() + "/artifacts/";
        return new ConversionResponse(result.snapshotId(), "SUCCEEDED", "COMPUTED", started, completed, null, null,
                ConversionArtifactCodec.VERSION, new ConversionResponse.Statistics(Long.toString(s.sequenceCount()),
                Long.toString(s.singleItemsetSequences()), Long.toString(s.multiItemsetSequences()), Long.toString(s.itemsetCount()),
                Long.toString(s.distinctItems()), Long.toString(s.totalItemOccurrences()), Integer.toString(s.minSequenceLength()),
                Integer.toString(s.maxSequenceLength()), s.averageSequenceLength()),
                new ConversionResponse.Artifact(result.sequence().sha256(), Long.toString(result.sequence().sizeBytes()), base + "sequence"),
                new ConversionResponse.Artifact(result.mapping().sha256(), Long.toString(result.mapping().sizeBytes()), base + "mapping"));
    }
}
