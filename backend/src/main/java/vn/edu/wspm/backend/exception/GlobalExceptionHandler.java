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

    private ApplicationException translate(
            Exception exception, HttpServletRequest request, HttpServletResponse response) {
        if (exception instanceof ApplicationException known) return known;
        if (exception instanceof MethodArgumentNotValidException validation) {
            List<FieldErrorDetail> details =
                    validation.getBindingResult().getFieldErrors().stream()
                            .map(
                                    field ->
                                            new FieldErrorDetail(
                                                    field.getField(),
                                                    "INVALID_VALUE",
                                                    "Supply a valid value."))
                            .distinct()
                            .sorted(
                                    Comparator.comparing(FieldErrorDetail::field)
                                            .thenComparing(FieldErrorDetail::code)
                                            .thenComparing(FieldErrorDetail::message))
                            .toList();
            return new ApplicationException(
                    ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), details);
        }
        if (exception instanceof ConstraintViolationException
                || exception instanceof HandlerMethodValidationException) {
            return ApplicationException.invalid("request", "Supply valid request values.");
        }
        if (exception instanceof HttpMessageNotReadableException
                || exception instanceof ServletRequestBindingException
                || exception instanceof MethodArgumentTypeMismatchException) {
            return new ApplicationException(ErrorCode.MALFORMED_REQUEST);
        }
        if (exception instanceof HttpRequestMethodNotSupportedException method) {
            if (method.getSupportedHttpMethods() != null) {
                response.setHeader(
                        "Allow",
                        String.join(
                                ", ",
                                method.getSupportedHttpMethods().stream()
                                        .map(Object::toString)
                                        .toList()));
            }
            return new ApplicationException(ErrorCode.METHOD_NOT_ALLOWED);
        }
        if (exception instanceof HttpMediaTypeNotAcceptableException)
            return new ApplicationException(ErrorCode.NOT_ACCEPTABLE);
        if (exception instanceof HttpMediaTypeNotSupportedException)
            return new ApplicationException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        if (exception instanceof NoResourceFoundException)
            return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);

        // Raw exception messages/causes can contain SQL, PII or credentials.
        var safe =
                new IllegalStateException(
                        "API request failed: " + exception.getClass().getSimpleName());
        safe.setStackTrace(exception.getStackTrace());
        log.error("API request failed: requestId={}", RequestIdFilter.requestId(request), safe);
        return new ApplicationException(
                exception instanceof DataAccessResourceFailureException
                                || exception instanceof CannotCreateTransactionException
                        ? ErrorCode.SERVICE_UNAVAILABLE
                        : ErrorCode.INTERNAL_ERROR);
    }
}
