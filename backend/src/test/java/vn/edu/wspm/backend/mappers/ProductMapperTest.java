package vn.edu.wspm.backend.mappers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;
import vn.edu.wspm.backend.entities.ProductEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductMapperTest {
    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test void mapsHistoricalPrecisionAndCategoryIdWithoutChangingTheEntity() {
        var category = new CategoryEntity("category-1", "Accessories");
        var price = new BigDecimal("9007199254740993.25");
        var entity = new ProductEntity("product-1", "Chuột", category, "DISCONTINUED", price);
        assertEquals(new ProductResponse("product-1", "Chuột", "category-1", "DISCONTINUED", price),
                mapper.toResponse(entity));
        assertSame(category, entity.getCategory());
        assertEquals(price, entity.getPrice());
    }

    @Test void preservesNullableFieldsAndNullSource() {
        assertNull(mapper.toResponse(null));
        assertEquals(new ProductResponse("product-2", null, null, null, null),
                mapper.toResponse(new ProductEntity("product-2", null, null, null, null)));
    }
}
