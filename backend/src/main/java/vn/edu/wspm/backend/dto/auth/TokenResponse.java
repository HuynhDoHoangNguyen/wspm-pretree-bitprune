package vn.edu.wspm.backend.dto.auth;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        Instant expiresAt,
        IdentityResponse user) {
    @Override
    public String toString() {
        return "TokenResponse[redacted]";
    }
}
