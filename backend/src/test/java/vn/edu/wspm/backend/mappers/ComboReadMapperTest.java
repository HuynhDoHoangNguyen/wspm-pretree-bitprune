package vn.edu.wspm.backend.mappers;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;

class ComboReadMapperTest {
    @Test void keepsTemporalPositionsQuantitiesMoneyPrecisionAndNullImage() throws Exception {
        var batch = new BatchRow("b", "r", "s", "WSPM_PRETREE", "SUPPORT_THEN_PATTERN_WEIGHT_V1", "stored", 4, BigInteger.TEN);
        var combo = new ComboRow("c", "Combo", "SAME_ORDER_BUNDLE", "PUBLISHED", "reason", null, null, null,
                "9007199254740993", "<(1 2)>", BigInteger.ONE, .1, .8, "VND",
                new BigDecimal("9007199254740993.99"), new BigDecimal("0.00"), "NONE", null, null);
        var later = new ItemRow("c", 2, 1, 3, false, false, BigDecimal.TEN, "q", "Q", "q", null, null, BigDecimal.ONE);
        var first = new ItemRow("c", 1, 2, 2, true, true, new BigDecimal("10.00"), "p", "P", "p", "cat", "/uploads/products/p.jpg", BigDecimal.ONE);
        var result = new ComboReadMapper().detail(combo, batch, List.of(later, first), Instant.EPOCH);
        assertEquals(List.of(1L, 2L), result.items().stream().map(i -> i.itemsetOrder()).toList());
        assertEquals(2, result.items().getFirst().itemOrder());
        assertEquals(3, result.items().get(1).quantity());
        assertNull(result.items().get(1).product().imageUrl());
        assertNull(result.pricing().discountValue());
        assertEquals("9007199254740993.99", result.pricing().referenceOriginalPrice());
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(result.pricing()));
        assertTrue(json.get("referenceOriginalPrice").isString());
        assertEquals("0.00", json.get("referenceSalePrice").asString());
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
    }
}
