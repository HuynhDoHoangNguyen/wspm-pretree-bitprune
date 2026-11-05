package vn.edu.wspm.backend.exception;

import org.springframework.http.HttpStatus;

import vn.edu.wspm.backend.common.api.FieldErrorDetail;

import java.util.List;
import java.util.Objects;

/** Messages/details must be deliberately safe, never raw input or exception text. */
public class ApplicationException extends RuntimeException {
    private final ErrorCode errorCode;
    private final List<FieldErrorDetail> details;

    public ApplicationException(ErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public ApplicationException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, safeMessage, List.of());
    }

    public ApplicationException(
            ErrorCode errorCode, String safeMessage, List<FieldErrorDetail> details) {
        super(safeMessage);
        this.errorCode = Objects.requireNonNull(errorCode);
        this.details = List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public HttpStatus status() {
        return errorCode.status();
    }

    public String code() {
        return errorCode.name();
    }

    public List<FieldErrorDetail> details() {
        return details;
    }

    public static ApplicationException invalid(String field, String safeMessage) {
        return invalid(field, "INVALID_VALUE", safeMessage);
    }

    public static ApplicationException invalid(String field, String code, String safeMessage) {
        return new ApplicationException(
                ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.message(),
                List.of(new FieldErrorDetail(field, code, safeMessage)));
    }
}
