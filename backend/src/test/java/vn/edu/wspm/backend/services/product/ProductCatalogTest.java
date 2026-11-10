package vn.edu.wspm.backend.services.product;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.entities.ProductImageEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductImageRepository;
import vn.edu.wspm.backend.repositories.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductCatalogTest {
    final ProductRepository products = mock(ProductRepository.class);
    final ProductImageRepository images = mock(ProductImageRepository.class);
    final ProductService service = new ProductService(products, Mappers.getMapper(ProductMapper.class), images);

    @Test void batchImagesPreserveProductOrderAndMissingImagesAndRealFields() {
        var first = product("p2");
        var second = product("p1");
        when(products.findAllByOrderByProductNameAsc()).thenReturn(List.of(first, second));
        when(images.findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(List.of("p2", "p1")))
                .thenReturn(List.of(image(first, "i1", 0), image(first, "i2", 1)));
        var result = service.findAll();
        assertEquals(List.of("p2", "p1"), result.stream().map(p -> p.productId()).toList());
        assertEquals("/uploads/products/i1.jpg", result.getFirst().imageUrl());
        assertNull(result.getLast().imageUrl());
        assertEquals(first.getCreatedAt(), result.getFirst().createdAt());
        assertEquals(new BigDecimal("9007199254740993.25"), result.getFirst().price());
        assertEquals("DISCONTINUED", result.getFirst().productStatus());
        verify(images, times(1)).findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(any());
    }

    @Test void detailKeepsGalleryMetadataAndMissingProductIs404() {
        var product = product("p1");
        when(products.findById("p1")).thenReturn(Optional.of(product));
        when(images.findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(List.of("p1")))
                .thenReturn(List.of(image(product, "i1", 0), image(product, "i2", 1)));
        var detail = service.findById("p1");
        assertEquals(2, detail.images().size());
        assertEquals(detail.images().getFirst().imageUrl(), detail.imageUrl());
        assertEquals("alt i2", detail.images().getLast().altText());
        assertEquals(1, detail.images().getLast().displayOrder());
        assertThrows(UnsupportedOperationException.class, () -> detail.images().clear());
        assertEquals(404, assertThrows(ApplicationException.class, () -> service.findById("missing")).status().value());
    }

    @Test void noImageDetailIsNullAndEmptyListDoesNotQueryImages() {
        when(products.findAllByOrderByProductNameAsc()).thenReturn(List.of());
        assertTrue(service.findAll().isEmpty());
        verifyNoInteractions(images);
        when(products.findById("p1")).thenReturn(Optional.of(product("p1")));
        var detail = service.findById("p1");
        assertNull(detail.imageUrl());
        assertTrue(detail.images().isEmpty());
    }

    @Test void latestPreservesTimestampAndDelegatesOrderingToRepository() {
        var first = product("p1");
        var second = product("p2");
        when(products.findAllByOrderByCreatedAtDescProductIdAsc()).thenReturn(List.of(first, second));
        var result = service.findLatest();
        assertEquals(List.of("p1", "p2"), result.stream().map(p -> p.productId()).toList());
        assertEquals(first.getCreatedAt(), result.getFirst().createdAt());
        assertEquals(first.getCreatedAt(), result.getLast().createdAt());
        verify(products, never()).findAllByOrderByProductNameAsc();
    }

    private static ProductEntity product(String id) {
        var result = new ProductEntity(id, "Laptop", null, "DISCONTINUED", new BigDecimal("9007199254740993.25"));
        result.setCreatedAt(LocalDateTime.parse("2026-11-03T12:34:56.123456"));
        return result;
    }

    private static ProductImageEntity image(ProductEntity product, String id, int order) {
        var result = new ProductImageEntity();
        result.setProduct(product); result.setImageId(id); result.setDisplayOrder(order);
        result.setImageUrl("/uploads/products/" + id + ".jpg"); result.setAltText("alt " + id);
        return result;
    }
}
