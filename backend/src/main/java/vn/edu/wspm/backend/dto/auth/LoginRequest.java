package vn.edu.wspm.backend.dto.auth;

public record LoginRequest(String username, String password) {
    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
