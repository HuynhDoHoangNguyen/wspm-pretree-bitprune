package vn.edu.wspm.backend.controllers.api.v1.product;

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

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.services.product.ProductService;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    private MockMvc mockMvc;

    @Mock private ProductService productService;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new vn.edu.wspm.backend.exception.GlobalExceptionHandler())
                        .addFilters(new vn.edu.wspm.backend.web.common.RequestIdFilter())
                        .addFilters(new vn.edu.wspm.backend.web.auth.ApiSecurityFilter(
                                tools.jackson.databind.json.JsonMapper.builder().build(),
                                org.mockito.Mockito.mock(vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator.class)))
                        .build();
    }

    @Test
    @DisplayName("GET /api/v1/products returns product list")
    void shouldReturnProductList() throws Exception {
        ProductResponse response =
                new ProductResponse(
                        "prod-1", "Laptop", "cat-1", "ACTIVE", BigDecimal.valueOf(1200.0));
        given(productService.findAll()).willReturn(List.of(response));

        mockMvc.perform(get("/api/v1/products"))
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
                .andExpect(jsonPath("$.data[0].productId").value("prod-1"))
                .andExpect(jsonPath("$.data[0].productName").value("Laptop"))
                .andExpect(jsonPath("$.data[0].categoryId").value("cat-1"))
                .andExpect(jsonPath("$.data[0].productStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].price").value(1200.0));
    }

    @Test void publicDetailUsesSameEnvelopeAndReturns404ForMissingProduct() throws Exception {
        var created = java.time.LocalDateTime.parse("2026-11-03T10:20:30.123456");
        given(productService.findById("p1")).willReturn(
                new vn.edu.wspm.backend.dto.product.ProductDetailResponse("p1", "Laptop", "cat",
                        "DISCONTINUED", new BigDecimal("123.45"), null, created, List.of()));
        mockMvc.perform(get("/api/v1/products/p1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.productStatus").value("DISCONTINUED"))
                .andExpect(jsonPath("$.data.price").value(123.45))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-03T10:20:30.123456"))
                .andExpect(jsonPath("$.data.images").isEmpty());
        given(productService.findById("missing")).willThrow(new vn.edu.wspm.backend.exception.ApplicationException(
                vn.edu.wspm.backend.exception.ErrorCode.RESOURCE_NOT_FOUND));
        mockMvc.perform(get("/api/v1/products/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test void latestIsExplicitAndInvalidSortIsRejected() throws Exception {
        given(productService.findLatest()).willReturn(List.of());
        mockMvc.perform(get("/api/v1/products?sort=latest")).andExpect(status().isOk());
        org.mockito.Mockito.verify(productService).findLatest();
        org.mockito.Mockito.verify(productService, org.mockito.Mockito.never()).findAll();
        mockMvc.perform(get("/api/v1/products?sort=unknown")).andExpect(status().isBadRequest());
    }
}
