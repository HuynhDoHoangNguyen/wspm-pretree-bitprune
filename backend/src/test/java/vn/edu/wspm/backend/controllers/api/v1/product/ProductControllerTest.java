package vn.edu.wspm.backend.controllers.api.v1.product;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.services.product.ProductService;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/products returns product list")
    void shouldReturnProductList() throws Exception {
        ProductResponse response = new ProductResponse(
                "prod-1",
                "Laptop",
                "cat-1",
                "ACTIVE",
                BigDecimal.valueOf(1200.0)
        );
        given(productService.findAll()).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").value("prod-1"))
                .andExpect(jsonPath("$[0].productName").value("Laptop"))
                .andExpect(jsonPath("$[0].categoryId").value("cat-1"))
                .andExpect(jsonPath("$[0].productStatus").value("ACTIVE"))
                .andExpect(jsonPath("$[0].price").value(1200.0));
    }
}
