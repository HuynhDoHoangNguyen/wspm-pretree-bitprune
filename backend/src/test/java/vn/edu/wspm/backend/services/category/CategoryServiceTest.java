package vn.edu.wspm.backend.services.category;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;
import vn.edu.wspm.backend.mappers.CategoryMapper;
import vn.edu.wspm.backend.repositories.CategoryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("findAll returns mapped category response list")
    void shouldFindAllCategories() {
        CategoryEntity entity = new CategoryEntity("cat-1", "Books");
        CategoryResponse response = new CategoryResponse("cat-1", "Books");

        given(categoryRepository.findAllByOrderByCategoryNameAsc()).willReturn(List.of(entity));
        given(categoryMapper.toResponse(entity)).willReturn(response);

        List<CategoryResponse> result = categoryService.findAll();

        assertEquals(1, result.size());
        assertEquals("cat-1", result.get(0).categoryId());
        assertEquals("Books", result.get(0).categoryName());
    }
}

