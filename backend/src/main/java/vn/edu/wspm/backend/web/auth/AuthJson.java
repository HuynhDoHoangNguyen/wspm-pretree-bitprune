package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.io.IOException;

/** Private parsing avoids MVC body-converter DEBUG logging of credentials. */
public final class AuthJson {
    private AuthJson() {}

    public static JsonNode read(HttpServletRequest request, ObjectMapper mapper)
            throws IOException {
        try {
            return mapper.reader()
                    .with(
                            DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(request.getInputStream());
        } catch (JacksonException invalid) {
            throw new ApplicationException(ErrorCode.MALFORMED_REQUEST, "Request body is invalid.");
        }
    }

    public static void empty(HttpServletRequest request) throws IOException {
        if (request.getInputStream().read() != -1)
            throw ApplicationException.invalid("body", "This operation requires an empty body.");
    }
}
