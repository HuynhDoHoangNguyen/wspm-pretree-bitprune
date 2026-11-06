package vn.edu.wspm.backend.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.entities.UserAccountEntity;

import java.util.List;

/** Roles are resolved by the service; mapping never queries or assigns permissions. */
@Mapper(config = BackendMapperConfig.class)
public interface IdentityMapper {
    @Mapping(target = "userId", source = "account.userId")
    @Mapping(target = "username", source = "account.username")
    @Mapping(target = "email", source = "account.email")
    @Mapping(target = "displayName", source = "account.displayName")
    @Mapping(target = "status", source = "account.status")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "emailVerified", expression = "java(account.getEmailVerifiedAt() != null)")
    IdentityResponse toResponse(UserAccountEntity account, List<String> roles);
}
