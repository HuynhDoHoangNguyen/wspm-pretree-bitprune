package vn.edu.wspm.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import vn.edu.wspm.backend.common.api.ApiErrorResponse;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;
import vn.edu.wspm.backend.web.common.ApiPaths;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.util.Comparator;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handle(
            Exception exception, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        if (!ApiPaths.isApi(request)) throw exception;
        if (response.isCommitted()) {
            log.warn(
                    "API response transfer failed after commit: requestId={}",
                    RequestIdFilter.requestId(request));
            return null;
        }
        ApplicationException error = translate(exception, request, response);
        ApiErrorWriter.headers(request, response);
        return ResponseEntity.status(error.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorWriter.body(request, error));
    }

    /** Explicit type dispatch; payload construction and logging stay in named helpers. */
    private ApplicationException translate(
            Exception exception, HttpServletRequest request, HttpServletResponse response) {
        return switch (exception) {
            case ApplicationException known -> known;
            case MaxUploadSizeExceededException ignored -> new ApplicationException(
                    ErrorCode.PAYLOAD_TOO_LARGE, "Upload exceeds configured file or request limits.");
            case MultipartException ignored -> new ApplicationException(ErrorCode.MALFORMED_REQUEST);
            case MethodArgumentNotValidException validation -> invalidFields(validation);
            case ConstraintViolationException ignored -> invalidRequest();
            case HandlerMethodValidationException validation -> methodValidation(validation, request);
            case HttpMessageNotReadableException ignored -> new ApplicationException(ErrorCode.MALFORMED_REQUEST);
            case ServletRequestBindingException ignored -> new ApplicationException(ErrorCode.MALFORMED_REQUEST);
            case MethodArgumentTypeMismatchException ignored -> new ApplicationException(ErrorCode.MALFORMED_REQUEST);
            case HttpRequestMethodNotSupportedException method -> methodNotAllowed(method, response);
            case HttpMediaTypeNotAcceptableException ignored -> new ApplicationException(ErrorCode.NOT_ACCEPTABLE);
            case HttpMediaTypeNotSupportedException ignored -> new ApplicationException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            case NoResourceFoundException ignored -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
            case NoHandlerFoundException ignored -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
            case DataAccessResourceFailureException failure -> unexpected(failure, request, ErrorCode.SERVICE_UNAVAILABLE);
            case CannotCreateTransactionException failure -> unexpected(failure, request, ErrorCode.SERVICE_UNAVAILABLE);
            default -> unexpected(exception, request, ErrorCode.INTERNAL_ERROR);
        };
    }

    private static ApplicationException invalidFields(MethodArgumentNotValidException validation) {
        List<FieldErrorDetail> details = validation.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldErrorDetail(field.getField(), "INVALID_VALUE", "Supply a valid value."))
                .distinct()
                .sorted(Comparator.comparing(FieldErrorDetail::field)
                        .thenComparing(FieldErrorDetail::code)
                        .thenComparing(FieldErrorDetail::message))
                .toList();
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), details);
    }

    private static ApplicationException invalidRequest() {
        return ApplicationException.invalid("request", "Supply valid request values.");
    }

    private ApplicationException methodValidation(HandlerMethodValidationException validation, HttpServletRequest request) {
        // Spring distinguishes invalid input (400) from invalid controller output (500).
        return validation.isForReturnValue()
                ? unexpected(validation, request, ErrorCode.INTERNAL_ERROR)
                : invalidRequest();
    }

    private static ApplicationException methodNotAllowed(
            HttpRequestMethodNotSupportedException method, HttpServletResponse response) {
        if (method.getSupportedHttpMethods() != null) {
            response.setHeader("Allow", String.join(", ", method.getSupportedHttpMethods().stream().map(Object::toString).toList()));
        }
        return new ApplicationException(ErrorCode.METHOD_NOT_ALLOWED);
    }

    private ApplicationException unexpected(Exception exception, HttpServletRequest request, ErrorCode code) {
        // Raw exception messages/causes can contain SQL, PII or credentials.
        var safe = new IllegalStateException("API request failed: " + exception.getClass().getSimpleName());
        safe.setStackTrace(exception.getStackTrace());
        log.error("API request failed: requestId={}", RequestIdFilter.requestId(request), safe);
        return new ApplicationException(code);
    }
}
