package vn.edu.wspm.backend.dto.auth;

import jakarta.validation.constraints.Email;

/** Closed Auth input data; web reading and application validation are separate. */
public record RegistrationRequest(
        String username, @Email String email, String password, String displayName) {
    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
