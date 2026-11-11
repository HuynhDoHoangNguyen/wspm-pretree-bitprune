package vn.edu.wspm.backend.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;

class CategoryMapperTest {

    private final CategoryMapper mapper =
            org.mapstruct.factory.Mappers.getMapper(CategoryMapper.class);

    @Test
    @DisplayName("toResponse maps all CategoryEntity attributes")
    void shouldMapEntityToResponse() {
        CategoryEntity entity = new CategoryEntity("cat-1", "Fashion");
        CategoryResponse response = mapper.toResponse(entity);

        assertEquals("cat-1", response.categoryId());
        assertEquals("Fashion", response.categoryName());
    }
}
