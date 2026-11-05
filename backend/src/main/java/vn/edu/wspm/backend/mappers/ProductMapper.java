package vn.edu.wspm.backend.mappers;

import org.mapstruct.Mapper;

import vn.edu.wspm.backend.dto.product.ProductResponse;
import vn.edu.wspm.backend.entities.ProductEntity;

@Mapper(config = BackendMapperConfig.class)
public interface ProductMapper {
    ProductResponse toResponse(ProductEntity entity);
}
