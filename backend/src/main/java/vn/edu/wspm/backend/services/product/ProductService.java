package vn.edu.wspm.backend.services.product;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductRepository;
import vn.edu.wspm.backend.repositories.ProductImageRepository;

import java.util.List;
import java.util.HashMap;

@RequiredArgsConstructor
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ProductImageRepository productImageRepository;

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        var products = productRepository.findAllByOrderByProductNameAsc();
        if (products.isEmpty()) return List.of();
        var images = new HashMap<String, String>();
        for (var image : productImageRepository
                .findByProduct_ProductIdInOrderByDisplayOrderAscImageIdAsc(
                        products.stream().map(p -> p.getProductId()).toList())) {
            images.putIfAbsent(image.getProduct().getProductId(), image.getImageUrl());
        }
        return products.stream().map(product -> {
            var dto = productMapper.toResponse(product);
            return new ProductResponse(dto.productId(), dto.productName(), dto.categoryId(),
                    dto.productStatus(), dto.price(), images.get(dto.productId()));
        }).toList();
    }
}
