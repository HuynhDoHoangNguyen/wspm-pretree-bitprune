package vn.edu.wspm.backend.services.product;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("findAll returns mapped product response list")
    void shouldFindAllProducts() {
        ProductEntity entity = org.mockito.Mockito.mock(ProductEntity.class);
        ProductResponse response = new ProductResponse("prod-1", "Product A", "cat-1", "ACTIVE", BigDecimal.TEN);

        given(productRepository.findAllByOrderByProductNameAsc()).willReturn(List.of(entity));
        given(productMapper.toResponse(entity)).willReturn(response);

        List<ProductResponse> result = productService.findAll();

        assertEquals(1, result.size());
        assertEquals("prod-1", result.get(0).productId());
    }
}

