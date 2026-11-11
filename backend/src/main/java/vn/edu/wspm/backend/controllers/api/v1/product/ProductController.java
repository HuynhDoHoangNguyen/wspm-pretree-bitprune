package vn.edu.wspm.backend.controllers.api.v1.product;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.dto.product.ProductDetailResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
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

    @GetMapping(params = "sort")
    public ApiResponse<List<ProductResponse>> sorted(
            @RequestParam("sort") String sort, HttpServletRequest request) {
        if (!"latest".equals(sort)) throw ApplicationException.invalid(
                "sort", "Supported sort is latest.");
        return ApiResponse.success(productService.findLatest(), RequestIdFilter.requestId(request));
    }

    @GetMapping("/{productId}")
    public ApiResponse<ProductDetailResponse> findById(
            @PathVariable("productId") String productId, HttpServletRequest request) {
        return ApiResponse.success(productService.findById(productId), RequestIdFilter.requestId(request));
    }
}
