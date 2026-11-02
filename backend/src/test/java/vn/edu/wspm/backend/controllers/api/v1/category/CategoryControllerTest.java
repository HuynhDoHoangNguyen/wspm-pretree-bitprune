package vn.edu.wspm.backend.controllers.api.v1.category;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.services.category.CategoryService;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        CategoryController controller = new CategoryController(categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/categories returns category list")
    void shouldReturnCategoryList() throws Exception {
        CategoryResponse response = new CategoryResponse(
                "cat-1",
                "Electronics"
        );
        given(categoryService.findAll()).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value("cat-1"))
                .andExpect(jsonPath("$[0].categoryName").value("Electronics"));
    }
}
