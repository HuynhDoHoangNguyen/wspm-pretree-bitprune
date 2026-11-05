package vn.edu.wspm.backend.controllers.api.v1.product;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.services.product.ProductService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<List<ProductResponse>> findAll(HttpServletRequest request) {
        return ApiResponse.success(productService.findAll(), RequestIdFilter.requestId(request));
    }
}
