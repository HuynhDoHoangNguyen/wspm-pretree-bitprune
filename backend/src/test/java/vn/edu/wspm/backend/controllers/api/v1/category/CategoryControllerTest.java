package vn.edu.wspm.backend.controllers.api.v1.category;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import java.util.List;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    private MockMvc mockMvc;

    @Mock private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        CategoryController controller = new CategoryController(categoryService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .addFilters(new vn.edu.wspm.backend.web.common.RequestIdFilter())
                        .build();
    }

    @Test
    @DisplayName("GET /api/v1/categories returns category list")
    void shouldReturnCategoryList() throws Exception {
        CategoryResponse response = new CategoryResponse("cat-1", "Electronics");
        given(categoryService.findAll()).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(
                        result ->
                                org.junit.jupiter.api.Assertions.assertEquals(
                                        result.getResponse().getHeader("X-Request-Id"),
                                        tools.jackson.databind.json.JsonMapper.builder()
                                                .build()
                                                .readTree(result.getResponse().getContentAsString())
                                                .get("requestId")
                                                .asString()))
                .andExpect(jsonPath("$.data[0].categoryId").value("cat-1"))
                .andExpect(jsonPath("$.data[0].categoryName").value("Electronics"));
    }
}
