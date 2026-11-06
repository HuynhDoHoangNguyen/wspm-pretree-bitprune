package vn.edu.wspm.backend.common.api;

import java.util.List;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldErrorDetail> details) {
    public ApiErrorResponse {
        details = List.copyOf(details);
    }
}
