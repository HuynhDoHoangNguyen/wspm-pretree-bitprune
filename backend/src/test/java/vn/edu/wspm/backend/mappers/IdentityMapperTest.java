package vn.edu.wspm.backend.mappers;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import vn.edu.wspm.backend.entities.UserAccountEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

class IdentityMapperTest {
    private final IdentityMapper mapper = Mappers.getMapper(IdentityMapper.class);

    @Test
    void identityUsesExplicitRolesPreservesNullableFieldsAndExcludesCredentials() {
        var account = new UserAccountEntity();
        account.setUserId("9007199254740993");
        account.setStatus("ACTIVE");
        account.setDisplayName("Name");
        account.setPasswordHash("secret-hash");
        var roles = new ArrayList<>(List.of("CUSTOMER"));
        var response = mapper.toResponse(account, roles);
        roles.add("ADMIN");
        assertEquals("9007199254740993", response.userId());
        assertNull(response.email());
        assertNull(response.username());
        assertEquals(List.of("CUSTOMER"), response.roles());
        assertFalse(response.emailVerified());
        assertFalse(response.toString().contains("secret-hash"));
        account.setEmailVerifiedAt(Instant.EPOCH);
        assertTrue(mapper.toResponse(account, List.of("ADMIN")).emailVerified());
    }
}
