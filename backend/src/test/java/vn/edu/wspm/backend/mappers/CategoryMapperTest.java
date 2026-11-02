package vn.edu.wspm.backend.mappers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapper();

    @Test
    @DisplayName("toResponse maps all CategoryEntity attributes")
    void shouldMapEntityToResponse() {
        CategoryEntity entity = new CategoryEntity("cat-1", "Fashion");
        CategoryResponse response = mapper.toResponse(entity);

        assertEquals("cat-1", response.categoryId());
        assertEquals("Fashion", response.categoryName());
    }
}

