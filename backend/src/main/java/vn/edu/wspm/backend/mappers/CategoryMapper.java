package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(CategoryEntity category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryName()
        );
    }
}

