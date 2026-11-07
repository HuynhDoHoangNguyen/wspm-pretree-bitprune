package vn.edu.wspm.backend.dto.run;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.mappers.MiningPreviewResponseMapper;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.config.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MiningPreviewResponseContractTest {
    @Test void projectsExactPublicSchemaAndAllStatisticsWithoutInternalReferences() {
        var artifact = new ConversionArtifactResult("snapshot", "private-attempt", "private-validation",
                new ConversionArtifactResult.ArtifactFile("private-path", "a".repeat(64), 123),
                new ConversionArtifactResult.ArtifactFile("private-path", "b".repeat(64), 456),
                new ConversionArtifactResult.Statistics(3, 1, 2, 6, 3, 9007199254740993L, 2, 3, 8.0 / 3));
        for (var config : java.util.List.of(MinimumSupport.percentage(14), MinimumSupport.absolute(0.25))) {
            var response = new MiningPreviewResponseMapper().map(artifact, "WSPM_PRETREE", new MinimumSupportResolver().resolve(config, 3));
            var json = JsonMapper.builder().build().valueToTree(response);
            assertEquals(Set.of("snapshotId", "algorithm", "artifactVersion", "sequenceSha256", "mappingSha256", "statistics", "minimumSupport", "executionEligibility"),
                    Set.copyOf(json.propertyNames()));
            assertEquals(9, json.get("statistics").size());
            assertEquals("3", json.at("/statistics/sequenceCount").asString());
            assertEquals("1", json.at("/statistics/singleItemsetSequenceCount").asString());
            assertEquals("2", json.at("/statistics/multiItemsetSequenceCount").asString());
            assertEquals("6", json.at("/statistics/itemsetCount").asString());
            assertEquals("3", json.at("/statistics/distinctItemCount").asString());
            assertEquals("9007199254740993", json.at("/statistics/totalItemOccurrences").asString());
            assertEquals("2", json.at("/statistics/minSequenceLength").asString());
            assertEquals("3", json.at("/statistics/maxSequenceLength").asString());
            assertEquals(8.0 / 3, json.at("/statistics/averageSequenceLength").asDouble());
            assertEquals(Set.of("mode", "configuredValue", "relativeFraction", "effectiveThreshold"), Set.copyOf(json.get("minimumSupport").propertyNames()));
            assertEquals(config.mode() == MinimumSupportMode.ABSOLUTE, json.at("/minimumSupport/relativeFraction").isNull());
            assertTrue(json.at("/minimumSupport/effectiveThreshold").isNumber());
            assertFalse(json.toString().contains("private-")); assertFalse(json.toString().contains("meanW"));
            assertEquals("NOT_EVALUATED", json.get("executionEligibility").asString());
        }
    }
}
