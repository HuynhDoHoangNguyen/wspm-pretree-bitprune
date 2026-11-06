package vn.edu.wspm.backend.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_ID(HttpStatus.BAD_REQUEST, "Invalid Combo or batch ID."),
    INVALID_COMBO_STATUS(HttpStatus.BAD_REQUEST, "Invalid Combo status."),
    INVALID_COMBO_KIND(HttpStatus.BAD_REQUEST, "Invalid Combo kind."),
    BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Batch was not found."),
    COMBO_NOT_FOUND(HttpStatus.NOT_FOUND, "Combo was not found."),
    INVALID_REVIEW_COMMAND(HttpStatus.BAD_REQUEST, "Invalid review command."),
    REVIEW_NOTE_REQUIRED(HttpStatus.BAD_REQUEST, "A rejection note is required."),
    INVALID_REVIEW_NOTE(HttpStatus.BAD_REQUEST, "Invalid review note."),
    REVIEW_BLOCKED(HttpStatus.CONFLICT, "Review prerequisites are unavailable."),
    STALE_REVIEW_DATA(HttpStatus.CONFLICT, "Review data has changed."),
    REVIEW_DECISION_CONFLICT(HttpStatus.CONFLICT, "A different decision is already stored."),
    INVALID_RUN_ID(HttpStatus.BAD_REQUEST, "Invalid run ID."),
    INVALID_PATTERN_ID(HttpStatus.BAD_REQUEST, "Invalid pattern ID."),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "Invalid pagination."),
    INVALID_FILTER(HttpStatus.BAD_REQUEST, "Invalid pattern filter."),
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "Invalid query parameters."),
    ITEM_NOT_IN_RUN_DICTIONARY(HttpStatus.BAD_REQUEST, "Selected items do not belong to the run dictionary."),
    RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "Run was not found."),
    PATTERN_NOT_FOUND(HttpStatus.NOT_FOUND, "Pattern was not found."),
    RESULTS_NOT_AVAILABLE(HttpStatus.CONFLICT, "Run results are not available."),
    RESULT_INCONSISTENT(HttpStatus.CONFLICT, "Run results are inconsistent."),
    WEIGHT_FILTER_UNAVAILABLE(HttpStatus.CONFLICT, "Weight filtering is unavailable."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request body or transport is invalid."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access is denied."),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "Request origin or CSRF token was rejected."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Request method is not supported."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Requested response media type is not supported."),
    IDENTITY_CONFLICT(HttpStatus.CONFLICT, "Account identity is already in use."),
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
