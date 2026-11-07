package vn.edu.wspm.backend.mappers;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConversionResponseMapperTest {
    private final ConversionResponseMapper mapper = new ConversionResponseMapper();
    @Test void completedProjectionKeepsExactStringsAndDropsPrivateMetadata() {
        var result = new ConversionArtifactResult("id", "private-attempt", "private-validation",
                new ConversionArtifactResult.ArtifactFile("private-path", "a".repeat(64), 9007199254740993L),
                new ConversionArtifactResult.ArtifactFile("private-path", "b".repeat(64), 42),
                new ConversionArtifactResult.Statistics(9007199254740993L, 1, 2, 3, 4, 5, 1, 3, 4.0 / 3));
        var response = mapper.completed(result, Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        var json = JsonMapper.builder().build(); var tree = json.readTree(json.writeValueAsBytes(response));
        assertEquals("9007199254740993", tree.get("statistics").get("sequenceCount").asString());
        assertTrue(tree.get("statistics").get("sequenceCount").isString());
        assertTrue(tree.get("statistics").get("averageSequenceLength").isNumber());
        assertEquals("9007199254740993", response.sequence().sizeBytes());
        assertFalse(json.writeValueAsString(response).contains("private"));
        assertEquals("SUCCEEDED", response.conversionStatus()); assertEquals("COMPUTED", response.statisticsStatus());
    }
    @Test void pendingProjectionHasNoResultsOrDownloadLinks() {
        var response = mapper.pending("id", "FAILED", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), "CONVERSION_FAILED");
        assertNull(response.statistics()); assertNull(response.sequence()); assertNull(response.mapping()); assertNull(response.completedAt());
        assertEquals("CONVERSION_FAILED", response.failureCode()); assertEquals("NOT_COMPUTED", response.statisticsStatus());
    }
}
