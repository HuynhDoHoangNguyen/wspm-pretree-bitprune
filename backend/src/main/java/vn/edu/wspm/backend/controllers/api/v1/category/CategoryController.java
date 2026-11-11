package vn.edu.wspm.backend.controllers.api.v1.category;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.services.category.CategoryService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> findAll(HttpServletRequest request) {
        return ApiResponse.success(categoryService.findAll(), RequestIdFilter.requestId(request));
    }
}
