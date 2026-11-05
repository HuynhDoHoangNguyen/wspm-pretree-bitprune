package vn.edu.wspm.backend.web.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.exception.GlobalExceptionHandler;

import java.util.List;

class ApiContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void correlationIgnoresInboundIdAndMatchesMvcAndFilterBodies() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.addHeader("X-Request-Id", "untrusted-client-id");
        var response = new MockHttpServletResponse();
        new RequestIdFilter().doFilter(request, response, (req, res) -> {});
        String id = response.getHeader("X-Request-Id");
        assertNotEquals("untrusted-client-id", id);
        assertEquals(id, RequestIdFilter.requestId(request));

        var success = ApiResponse.success(List.of(), id);
        var tree = mapper.readTree(mapper.writeValueAsString(success));
        assertEquals(4, tree.size());
        assertEquals("SUCCESS", tree.get("code").asString());
        assertEquals(id, tree.get("requestId").asString());
        assertTrue(tree.get("data").isArray());

        var failure = ApplicationException.invalid("body", "Supply a valid value.");
        var mvc = new GlobalExceptionHandler().handle(failure, request, response);
        var filterResponse = new MockHttpServletResponse();
        ApiErrorWriter.write(request, filterResponse, failure, mapper);
        assertEquals(mapper.writeValueAsString(mvc.getBody()), filterResponse.getContentAsString());
        assertEquals(id, filterResponse.getHeader("X-Request-Id"));
        assertEquals("no-store", filterResponse.getHeader("Cache-Control"));
        assertEquals(400, filterResponse.getStatus());
    }

    @Test
    void unexpectedPublicApiFailureIsSanitizedAndNotMappedToClientError() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/products");
        request.setQueryString("password=secret-query");
        var result =
                new GlobalExceptionHandler()
                        .handle(
                                new IllegalArgumentException("secret-sql"),
                                request,
                                new MockHttpServletResponse());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        String json = mapper.writeValueAsString(result.getBody());
        assertFalse(json.contains("secret"));
        assertEquals("/api/v1/products", result.getBody().path());
        assertTrue(result.getBody().details().isEmpty());
    }

    @Test
    void commonWriterNeverAppendsToCommittedResponseAndEnvelopeToStringIsRedacted()
            throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/order-exports");
        var response = new MockHttpServletResponse();
        response.getWriter().write("csv-bytes");
        response.flushBuffer();
        ApiErrorWriter.write(
                request, response, new ApplicationException(ErrorCode.INTERNAL_ERROR), mapper);
        assertEquals("csv-bytes", response.getContentAsString());
        assertNull(response.getHeader("X-Request-Id"));
        assertFalse(ApiResponse.success("secret-token", "id").toString().contains("secret-token"));
    }
}
