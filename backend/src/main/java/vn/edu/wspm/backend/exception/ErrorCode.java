package vn.edu.wspm.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body or transport is invalid."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "Request origin or CSRF token was rejected."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Request method is not supported."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Requested response media type is not supported."),
    IDENTITY_CONFLICT(HttpStatus.CONFLICT, "Account identity is already in use."),
    INVALID_RESOURCE_STATE(HttpStatus.CONFLICT, "Resource state does not permit this operation."),
    PAYLOAD_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "Export exceeds configured limits; select a smaller period."),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Request media type is not supported."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be completed."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service is temporarily unavailable.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
