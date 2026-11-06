package vn.edu.wspm.backend.web.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.common.api.ApiErrorResponse;
import vn.edu.wspm.backend.exception.ApplicationException;

import java.io.IOException;

/** The filter and MVC boundaries share DTOs and correlation, not advice dispatch. */
@Slf4j
public final class ApiErrorWriter {
    private ApiErrorWriter() {}

    public static void headers(HttpServletRequest request, HttpServletResponse response) {
        if (response.isCommitted()) return;
        response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, RequestIdFilter.requestId(request));
        response.setHeader("Cache-Control", "no-store");
    }

    public static ApiErrorResponse body(HttpServletRequest request, ApplicationException error) {
        return new ApiErrorResponse(
                error.status().value(),
                error.code(),
                error.getMessage(),
                request.getRequestURI(),
                RequestIdFilter.requestId(request),
                error.details());
    }

    public static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ApplicationException error,
            ObjectMapper mapper)
            throws IOException {
        if (response.isCommitted()) {
            log.warn(
                    "API error response already committed: requestId={}",
                    RequestIdFilter.requestId(request));
            return;
        }
        headers(request, response);
        response.setStatus(error.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), body(request, error));
    }
}
