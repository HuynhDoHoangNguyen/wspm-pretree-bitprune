package vn.edu.wspm.backend.web.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.web.auth.AuthJson;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Request-shape behavior only; authorization order is exercised by MVC and real HTTP suites. */
class RequestChecksTest {
    private static final String AUTH_MESSAGE = "This operation requires an empty body.";
    private static final String POST_MESSAGE = "Validation POST accepts no body or query parameters.";
    private static final String QUERY_MESSAGE = "Unknown or repeated query parameter.";

    private void error(ApplicationException error, String field, String code, String message) {
        assertEquals(400, error.status().value()); assertEquals("VALIDATION_FAILED", error.code());
        assertEquals(List.of(new FieldErrorDetail(field, code, message)), error.details());
    }

    @ParameterizedTest(name = "{index}: nonempty Auth body") @ValueSource(strings = {" ", "{}", "\0"})
    void authRejectsAnyBodyContentIncludingWhitespaceAndNul(String content) {
        var request = new MockHttpServletRequest(); request.setContent(content.getBytes(StandardCharsets.UTF_8));
        error(assertThrows(ApplicationException.class, () -> AuthJson.empty(request)), "body", "INVALID_VALUE", AUTH_MESSAGE);
    }

    @Test void authEmptyBodyStillAllowsQueryAndDoesNotInspectParameters() throws Exception {
        var request = spy(new MockHttpServletRequest()); request.addParameter("ignored-query", "value");
        AuthJson.empty(request);
        verify(request, never()).getParameterMap();
    }

    @Test void parameterRejectionPrecedesEvenAThrowingBodyStream() throws Exception {
        var request = spy(new MockHttpServletRequest()); request.addParameter("private-key", "private-value");
        doThrow(new IOException("private-stream-failure")).when(request).getInputStream();
        error(assertThrows(ApplicationException.class, () -> RequestChecks.requireNoParametersOrBody(request, POST_MESSAGE)),
                "request", "INVALID_VALUE", POST_MESSAGE);
        verify(request, never()).getInputStream();
    }

    @Test void parameterFreeNonemptyBodyKeepsEndpointSpecificRequestError() {
        var request = new MockHttpServletRequest(); request.setContent(new byte[]{' '});
        error(assertThrows(ApplicationException.class, () -> RequestChecks.requireNoParametersOrBody(request, POST_MESSAGE)),
                "request", "INVALID_VALUE", POST_MESSAGE);
    }

    @Test void emptyParametersAndBodyAreAccepted() throws Exception {
        RequestChecks.requireNoParametersOrBody(new MockHttpServletRequest(), POST_MESSAGE);
    }

    @Test void bodyCheckReadsOnlyOneByteAndDoesNotTrustOrInspectDeclaredLength() throws Exception {
        var stream = spy(new DelegatingServletInputStream(new ByteArrayInputStream(new byte[]{0, 1, 2})));
        var request = mock(HttpServletRequest.class); when(request.getInputStream()).thenReturn(stream);
        error(assertThrows(ApplicationException.class, () -> RequestChecks.requireEmptyBody(request, "body", AUTH_MESSAGE)),
                "body", "INVALID_VALUE", AUTH_MESSAGE);
        verify(stream).read(); verifyNoMoreInteractions(stream);
        verify(request).getInputStream(); verifyNoMoreInteractions(request);
    }

    @Test void streamIoFailurePropagatesWithoutBecomingClientValidationError() throws Exception {
        var request = spy(new MockHttpServletRequest()); var failure = new IOException("private-io-failure");
        doThrow(failure).when(request).getInputStream();
        assertSame(failure, assertThrows(IOException.class, () -> RequestChecks.requireNoParametersOrBody(request, POST_MESSAGE)));
    }

    @ParameterizedTest @ValueSource(strings = {"UNKNOWN_FIELD", "INVALID_VALUE"})
    void queryNameCheckPreservesCallerErrorCodeAndNeverEchoesUnknownKey(String code) {
        error(assertThrows(ApplicationException.class, () -> RequestChecks.requireAllowedQueryNames(
                Set.of("private-key"), Set.of("page", "size"), code, QUERY_MESSAGE)), "query", code, QUERY_MESSAGE);
    }

    @Test void singleQueryPolicyRejectsUnknownRepeatedAndZeroValueParameters() {
        var unknown = new MockHttpServletRequest(); unknown.addParameter("private-key", "private-value");
        var duplicate = new MockHttpServletRequest(); duplicate.addParameter("page", "0", "1");
        var zeroValues = new MockHttpServletRequest(); zeroValues.addParameter("size", new String[0]);
        for (var request : List.of(unknown, duplicate, zeroValues)) {
            error(assertThrows(ApplicationException.class, () -> RequestChecks.requireSingleQueryParameters(
                    request, Set.of("page", "size"), QUERY_MESSAGE)), "query", "INVALID_VALUE", QUERY_MESSAGE);
        }
    }

    @Test void knownSingleParametersAreNotParsedOrRequiredByShapeChecks() {
        var request = new MockHttpServletRequest(); request.addParameter("page", "-1"); request.addParameter("size", "not-a-number");
        RequestChecks.requireSingleQueryParameters(request, Set.of("page", "size"), QUERY_MESSAGE);
        RequestChecks.requireSingleQueryParameters(new MockHttpServletRequest(), Set.of("page", "size"), QUERY_MESSAGE);
    }

    @Test void allowedQueryNamesAcceptEmptyOrKnownNamesWithoutImposingValuePolicy() {
        RequestChecks.requireAllowedQueryNames(Set.of(), Set.of("fromTime", "toTime"), "UNKNOWN_FIELD", "Unsupported filter.");
        RequestChecks.requireAllowedQueryNames(Set.of("fromTime", "toTime"), Set.of("fromTime", "toTime"), "UNKNOWN_FIELD", "Unsupported filter.");
    }
}
