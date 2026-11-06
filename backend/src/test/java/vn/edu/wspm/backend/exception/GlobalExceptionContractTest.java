package vn.edu.wspm.backend.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.*;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.*;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.sql.SQLException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Direct handler/writer contracts. Real filter ordering and MVC dispatch have separate integration tests. */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionContractTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final JsonMapper mapper = JsonMapper.builder().build();

    static Stream<Arguments> exceptions() {
        String privateMessage = "synthetic-private-sql-or-credential";
        return Stream.of(
                Arguments.of(new MaxUploadSizeExceededException(1024), "PAYLOAD_TOO_LARGE", 413),
                Arguments.of(new MultipartException(privateMessage), "MALFORMED_REQUEST", 400),
                Arguments.of(new HttpMessageNotReadableException(privateMessage, new MockHttpInputMessage(new byte[0])), "MALFORMED_REQUEST", 400),
                Arguments.of(new MissingServletRequestParameterException(privateMessage, "String"), "MALFORMED_REQUEST", 400),
                Arguments.of(new MethodArgumentTypeMismatchException(privateMessage, Integer.class, privateMessage, null, null), "MALFORMED_REQUEST", 400),
                Arguments.of(new ConstraintViolationException(privateMessage, Set.of()), "VALIDATION_FAILED", 400),
                // Mock only framework metadata: isForReturnValue defaults false (input validation).
                Arguments.of(new HandlerMethodValidationException(mock(MethodValidationResult.class)), "VALIDATION_FAILED", 400),
                Arguments.of(new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST")), "METHOD_NOT_ALLOWED", 405),
                Arguments.of(new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), "NOT_ACCEPTABLE", 406),
                Arguments.of(new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), "UNSUPPORTED_MEDIA_TYPE", 415),
                Arguments.of(new NoResourceFoundException(HttpMethod.GET, privateMessage, privateMessage), "RESOURCE_NOT_FOUND", 404),
                Arguments.of(new NoHandlerFoundException("GET", privateMessage, HttpHeaders.EMPTY), "RESOURCE_NOT_FOUND", 404),
                Arguments.of(new HttpMessageNotWritableException(privateMessage), "INTERNAL_ERROR", 500),
                Arguments.of(new IOException(privateMessage), "INTERNAL_ERROR", 500),
                Arguments.of(new DataAccessResourceFailureException(privateMessage, new SQLException(privateMessage)), "SERVICE_UNAVAILABLE", 503),
                Arguments.of(new CannotCreateTransactionException(privateMessage), "SERVICE_UNAVAILABLE", 503),
                Arguments.of(new DataIntegrityViolationException(privateMessage), "INTERNAL_ERROR", 500),
                Arguments.of(new IllegalArgumentException(privateMessage), "INTERNAL_ERROR", 500),
                Arguments.of(new Exception(privateMessage), "INTERNAL_ERROR", 500));
    }

    @ParameterizedTest(name = "{index}: {1} -> {2}") @MethodSource("exceptions")
    void frameworkAndOperationalErrorsKeepTheirClassificationAndSafeEnvelope(Exception input, String code, int status) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/admin/dataset-snapshots");
        request.setQueryString("token=synthetic-private-query");
        var response = new MockHttpServletResponse();
        var actual = handler.handle(input, request, response);
        assertNotNull(actual); assertEquals(status, actual.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_JSON, actual.getHeaders().getContentType());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals(RequestIdFilter.requestId(request), response.getHeader("X-Request-Id"));
        var body = Objects.requireNonNull(actual.getBody());
        assertEquals(code, body.code()); assertEquals(status, body.status());
        assertEquals(request.getRequestURI(), body.path());
        assertEquals(RequestIdFilter.requestId(request), body.requestId());
        assertNotNull(body.details());
        var json = mapper.valueToTree(body);
        assertEquals(Set.of("status", "code", "message", "path", "requestId", "details"), new HashSet<>(json.propertyNames()));
        assertFalse(json.toString().contains("synthetic-private"));
        if (status == 405) assertEquals(Set.of("GET", "POST"), Set.of(response.getHeader("Allow").split(", ")));
    }

    @ParameterizedTest @CsvSource({
            "VALIDATION_FAILED,400", "MALFORMED_REQUEST,400", "AUTHENTICATION_REQUIRED,401", "ACCESS_DENIED,403",
            "CSRF_REJECTED,403", "RESOURCE_NOT_FOUND,404", "METHOD_NOT_ALLOWED,405", "NOT_ACCEPTABLE,406",
            "IDENTITY_CONFLICT,409", "INVALID_RESOURCE_STATE,409", "PAYLOAD_TOO_LARGE,413", "UNSUPPORTED_MEDIA_TYPE,415",
            "INTERNAL_ERROR,500", "SERVICE_UNAVAILABLE,503"
    })
    void applicationStatusPairsAndDetailsAreIdenticalAcrossMvcAndFilterWriter(String code, int status) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var detail = new FieldErrorDetail("request", "INVALID_VALUE", "Supply a valid value.");
        var original = new ArrayList<>(List.of(detail));
        var error = new ApplicationException(ErrorCode.valueOf(code), "Safe business message.", original);
        original.clear();
        var mvcResponse = new MockHttpServletResponse();
        var actual = handler.handle(error, request, mvcResponse);
        var filterResponse = new MockHttpServletResponse();
        ApiErrorWriter.write(request, filterResponse, error, mapper);
        assertEquals(status, actual.getStatusCode().value()); assertEquals(status, filterResponse.getStatus());
        assertEquals(mapper.writeValueAsString(actual.getBody()), filterResponse.getContentAsString());
        assertEquals("Safe business message.", actual.getBody().message());
        assertEquals(code, actual.getBody().code()); assertEquals(List.of(detail), actual.getBody().details());
        assertThrows(UnsupportedOperationException.class, () -> actual.getBody().details().clear());
        assertEquals(mvcResponse.getHeader("X-Request-Id"), filterResponse.getHeader("X-Request-Id"));
        assertEquals("no-store", filterResponse.getHeader("Cache-Control"));
    }

    @Test void unexpectedFailureLogsSafeStackWithoutOriginalMessageOrCause(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var failure = new DataAccessResourceFailureException("RF01-secret-driver-message", new SQLException("RF01-secret-cause"));
        handler.handle(failure, request, new MockHttpServletResponse());
        assertTrue(output.getAll().contains("API request failed"));
        assertTrue(output.getAll().contains(RequestIdFilter.requestId(request)));
        assertFalse(output.getAll().contains("RF01-secret-driver-message"));
        assertFalse(output.getAll().contains("RF01-secret-cause"));
    }

    @Test void unsupportedMethodWithoutDeclaredAlternativesDoesNotInventAnAllowHeader() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        var response = new MockHttpServletResponse();
        var actual = handler.handle(new HttpRequestMethodNotSupportedException("PATCH"), request, response);
        assertEquals(405, actual.getStatusCode().value());
        assertEquals("METHOD_NOT_ALLOWED", actual.getBody().code());
        assertNull(response.getHeader("Allow"));
    }

    @Test void bindingDetailsAreDeduplicatedSortedAndNeverExposeRejectedValuesOrValidatorMessages() throws Exception {
        var binding = new BeanPropertyBindingResult(new Object(), "input");
        binding.addError(new FieldError("input", "z", "RF05-secret-value", false, null, null, "RF05-secret-validator"));
        binding.addError(new FieldError("input", "a", "RF05-secret-value", false, null, null, "RF05-secret-validator"));
        binding.addError(new FieldError("input", "a", "another-private-value", false, null, null, "another-private-message"));
        var parameter = new MethodParameter(GlobalExceptionContractTest.class.getDeclaredMethod("input", Object.class), 0);
        var request = new MockHttpServletRequest("POST", "/api/test-validation");
        var actual = handler.handle(new MethodArgumentNotValidException(parameter, binding), request, new MockHttpServletResponse());
        assertEquals(400, actual.getStatusCode().value());
        assertEquals(List.of(new FieldErrorDetail("a", "INVALID_VALUE", "Supply a valid value."),
                new FieldErrorDetail("z", "INVALID_VALUE", "Supply a valid value.")), actual.getBody().details());
        String json = mapper.writeValueAsString(actual.getBody());
        assertFalse(json.contains("RF05-secret")); assertFalse(json.contains("private"));
    }

    private static void input(Object ignored) {}
}
