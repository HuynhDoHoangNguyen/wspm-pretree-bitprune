package vn.edu.wspm.backend.services.product;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.dto.product.ProductDetailResponse;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.ProductImageRepository;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductRepository;

import java.util.List;

@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return responses(productRepository.findAllByOrderByProductNameAsc());
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findLatest() {
        return responses(productRepository.findAllByOrderByCreatedAtDescProductIdAsc());
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse findById(String productId) {
        var product = productRepository.findById(productId).orElseThrow(() -> {
            log.debug("Product detail not found");
            return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        });
        var images = productImageRepository
                .findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(List.of(product.getProductId()));
        var dto = withImage(product, images.isEmpty() ? null : images.getFirst().getImageUrl());
        log.debug("Product detail read completed");
        return new ProductDetailResponse(dto.productId(),
                dto.productName(), dto.categoryId(), dto.productStatus(), dto.price(), dto.imageUrl(),
                dto.createdAt(), images.stream().map(image ->
                    new ProductDetailResponse.Image(image.getImageId(),
                            image.getImageUrl(), image.getAltText(), image.getDisplayOrder())).toList(),
                new ProductDetailResponse.Information(product.getSku(),
                        product.getCategory() == null ? null : product.getCategory().getCategoryName(),
                        product.getShortDescription(), product.getDescription(), product.getCurrencyCode()));
    }

    private List<ProductResponse> responses(List<ProductEntity> products) {
        if (products.isEmpty()) return List.of();
        var images = new java.util.HashMap<String, String>();
        for (var image : productImageRepository.findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(
                products.stream().map(ProductEntity::getProductId).toList())) {
            images.putIfAbsent(image.getProduct().getProductId(), image.getImageUrl());
        }
        log.debug("Product catalog read completed: count={}", products.size());
        return products.stream().map(product -> withImage(product, images.get(product.getProductId()))).toList();
    }

    private ProductResponse withImage(ProductEntity product, String imageUrl) {
        var dto = productMapper.toResponse(product);
        return new ProductResponse(dto.productId(), dto.productName(), dto.categoryId(),
                dto.productStatus(), dto.price(), imageUrl, dto.createdAt());
    }
}
