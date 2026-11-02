package vn.edu.wspm.backend.services.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.mappers.CategoryMapper;
import vn.edu.wspm.backend.repositories.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(
            CategoryRepository categoryRepository,
            CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository
                .findAllByOrderByCategoryNameAsc()
                .stream()
                .map(categoryMapper::toResponse)
                .toList();
    }
}

