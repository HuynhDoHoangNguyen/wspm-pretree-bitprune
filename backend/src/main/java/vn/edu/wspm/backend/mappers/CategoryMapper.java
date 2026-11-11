package vn.edu.wspm.backend.mappers;

import org.mapstruct.Mapper;

import vn.edu.wspm.backend.dto.category.CategoryResponse;
import vn.edu.wspm.backend.entities.CategoryEntity;

@Mapper(config = BackendMapperConfig.class)
public interface CategoryMapper {
    CategoryResponse toResponse(CategoryEntity entity);
}
