package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversionArtifactCodecTest {
    private final ConversionArtifactCodec codec = new ConversionArtifactCodec(JsonMapper.builder().build(),
            new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 2));
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    @Test void dictionaryHasExactVersionedCanonicalBytesAndNumericOrder() {
        var dict = new ProductItemDictionary(Map.of("p2", 27, "p1", 12));
        assertArrayEquals(bytes("{\"formatVersion\":\"CONVERSION_ARTIFACT_V1\",\"policyVersion\":\"GLOBAL_PRODUCT_ITEM_INT_V1\",\"items\":[{\"itemId\":12,\"productId\":\"p1\"},{\"itemId\":27,\"productId\":\"p2\"}]}\n"), codec.mappingBytes(dict));
        assertArrayEquals(codec.mappingBytes(dict), codec.mappingBytes(new ProductItemDictionary(Map.of("p1", 12, "p2", 27))));
    }
    @Test void unicodeQuotesAndNewlinesAreEscapedWithoutChangingIdentity() {
        byte[] mapping = codec.mappingBytes(new ProductItemDictionary(Map.of("p\"é\n😀", Integer.MAX_VALUE)));
        assertEquals(1, codec.verify(bytes("2147483647 -1 -2\n"), mapping).statistics().distinctItems());
    }
    @Test void statisticsMeasureItemsNotOrdersOrSourceQuantity() {
        byte[] mapping = codec.mappingBytes(new ProductItemDictionary(Map.of("p", 1, "q", 2, "r", 3)));
        var actual = codec.verify(bytes("1 2 -1 1 -1 -2\n3 -1 -2\n"), mapping);
        assertEquals(new ConversionArtifactResult.Statistics(2, 1, 1, 3, 3, 4, 1, 3, 2.0), actual.statistics());
    }
    @Test void missingExtraDuplicateOrNoncanonicalMappingCannotVerify() {
        byte[] valid = codec.mappingBytes(new ProductItemDictionary(Map.of("p", 1)));
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("2 -1 -2\n"), valid));
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("1 -1 -2\n"), codec.mappingBytes(new ProductItemDictionary(Map.of("p", 1, "q", 2)))));
        String json = new String(valid, StandardCharsets.UTF_8);
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("1 -1 -2\n"), bytes(json.replace("\"itemId\":1", "\"itemId\":0"))));
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("1 -1 -2\n"), bytes(json.replace("\"items\":[", "\"items\":[{\"itemId\":2,\"productId\":\"p\"},"))));
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("1 -1 -2\n"), bytes(" " + json)));
        assertThrows(ApplicationException.class, () -> codec.verify(bytes("1 -1 -2\n"), bytes(json.replace("ARTIFACT_V1", "ARTIFACT_V2"))));
    }
    @Test void malformedEmptyZeroAndOversizedTokenStreamsFailClosed() {
        byte[] mapping = codec.mappingBytes(new ProductItemDictionary(Map.of("p", 1)));
        for (String spmf : new String[]{"", "1 -1", "0 -1 -2\n", "1 1 -1 -2\n", "999999999999999 -1 -2\n", "1\t-1 -2\n"}) {
            assertThrows(ApplicationException.class, () -> codec.verify(bytes(spmf), mapping), spmf);
        }
        var tiny = new ConversionArtifactCodec(JsonMapper.builder().build(), new ValidationLimits(100, 1, 1000, 1000, 1000, 1));
        assertThrows(ApplicationException.class, () -> tiny.verify(bytes("1 -1 1 -1 -2\n"), mapping));
    }
    @Test void sha256UsesExactBytesAndLowercaseHex() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ConversionArtifactCodec.sha256(new byte[0]));
        assertNotEquals(ConversionArtifactCodec.sha256(bytes("a")), ConversionArtifactCodec.sha256(bytes("a\n")));
    }
}
