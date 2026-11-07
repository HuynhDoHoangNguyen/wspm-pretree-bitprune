package vn.edu.wspm.backend.services.mining;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.config.MinimumSupport;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Controlled compatibility fixture, not production WDM-to-TV2 wiring or a publication witness. */
class WdmArtifactCompatibilityTest {
    @Test void nonDenseWdmIdsAndSourceBytesSurviveThePreparedConsumerWithoutReconversion() {
        byte[] sequence = "12 27 -1 -2\n12 -1 27 -1 -2\n".getBytes(StandardCharsets.UTF_8);
        // Hand-authored WDM V1 document, independent of the production mapping serializer.
        byte[] mapping = ("{\"formatVersion\":\"CONVERSION_ARTIFACT_V1\",\"policyVersion\":\"GLOBAL_PRODUCT_ITEM_INT_V1\","
                + "\"items\":[{\"itemId\":12,\"productId\":\"product-A\"},{\"itemId\":27,\"productId\":\"product-B\"}]}\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] originalSequence = sequence.clone(), originalMapping = mapping.clone();
        String sequenceHash = ConversionArtifactCodec.sha256(sequence), mappingHash = ConversionArtifactCodec.sha256(mapping);
        var codec = new ConversionArtifactCodec(JsonMapper.builder().build(), new ValidationLimits(16384, 100, 8388608, 16777216, 30000, 1));
        assertEquals(2, codec.verify(sequence, mapping).statistics().distinctItems());

        // Historical labels are supplied by this fixture. WDM mapping alone does not supply them.
        var dictionary = Map.of(12, new PreparedSnapshotMiner.DictionaryItem("product-A", "Historical A", "SKU-A"),
                27, new PreparedSnapshotMiner.DictionaryItem("product-B", "Historical B", "SKU-B"));
        var input = new PreparedSnapshotMiner.Input("fixture", "a".repeat(64), sequence, sequenceHash,
                "CONVERSION_ARTIFACT_V1", "CONTROLLED_TEST_REVISION", dictionary, MinimumSupport.absolute(.4));
        var result = new PreparedSnapshotMiner().mine(input);
        assertEquals(Map.of("<(12)>", 2, "<(27)>", 2, "<(12 27)>", 1, "<(12)(27)>", 1), result.canonicalSupports());
        assertEquals("product-A", result.input().dictionary().get(12).productId());
        assertEquals("product-B", result.input().dictionary().get(27).productId());
        assertArrayEquals(originalSequence, result.input().spmf());
        assertArrayEquals(originalSequence, sequence);
        assertArrayEquals(originalMapping, mapping);
        assertEquals(sequenceHash, ConversionArtifactCodec.sha256(sequence));
        assertEquals(mappingHash, ConversionArtifactCodec.sha256(mapping));
        assertFalse(result.publishBlockers().isEmpty());
    }
}
