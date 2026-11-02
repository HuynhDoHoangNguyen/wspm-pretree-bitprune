package vn.edu.wspm.backend.services.product;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.mappers.ProductMapper;
import vn.edu.wspm.backend.repositories.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository
                .findAllByOrderByProductNameAsc()
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }
}

