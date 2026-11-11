package vn.edu.wspm.backend.dto.auth;

import java.util.List;

public record IdentityResponse(
        String userId,
        String username,
        String email,
        String displayName,
        String status,
        List<String> roles,
        boolean emailVerified) {
    public IdentityResponse {
        roles = List.copyOf(roles);
    }

    @Override
    public String toString() {
        return "IdentityResponse[redacted]";
    }
}
