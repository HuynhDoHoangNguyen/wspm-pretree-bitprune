package vn.edu.wspm.backend.services.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.entities.ProductImageEntity;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductRepository;
import vn.edu.wspm.backend.repositories.ProductImageRepository;

class ProductServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductService service = new ProductService(
            products, Mappers.getMapper(ProductMapper.class), images);

    private ProductImageEntity image(ProductEntity product, String url) {
        var image = new ProductImageEntity();
        image.setProduct(product);
        image.setImageUrl(url);
        return image;
    }

    @Test void returnsTwentyProductsAndFirstOrderedImageInOneBatch() {
        var list = IntStream.range(0, 20).mapToObj(i -> new ProductEntity(
                "p" + i, "Product " + i, null, "ACTIVE", BigDecimal.TEN)).toList();
        var ids = list.stream().map(ProductEntity::getProductId).toList();
        when(products.findAllByOrderByProductNameAsc()).thenReturn(list);
        when(images.findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(ids))
                .thenReturn(List.of(image(list.getFirst(), "/uploads/products/first.jpg"),
                        image(list.getFirst(), "/uploads/products/second.jpg"),
                        image(list.get(1), "/uploads/products/other.jpg")));
        var result = service.findAll();
        assertEquals(20, result.size());
        assertEquals("/uploads/products/first.jpg", result.getFirst().imageUrl());
        assertEquals("/uploads/products/other.jpg", result.get(1).imageUrl());
        assertNull(result.get(2).imageUrl());
        assertEquals("p0", result.getFirst().productId());
        assertEquals(BigDecimal.TEN, result.getFirst().price());
        assertEquals("ACTIVE", result.getFirst().productStatus());
        verify(images, times(1)).findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(ids);
    }

    @Test void emptyCatalogSkipsImageQuery() {
        when(products.findAllByOrderByProductNameAsc()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
        verifyNoInteractions(images);
    }
}
