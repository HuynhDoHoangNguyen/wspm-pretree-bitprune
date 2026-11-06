package vn.edu.wspm.backend.common.api;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "Request completed successfully.", data, requestId);
    }

    @Override
    public String toString() {
        return "ApiResponse[code=" + code + ", requestId=" + requestId + ", data=redacted]";
    }
}
