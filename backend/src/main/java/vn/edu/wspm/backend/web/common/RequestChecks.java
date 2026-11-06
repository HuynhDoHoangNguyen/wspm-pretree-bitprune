package vn.edu.wspm.backend.web.common;

import jakarta.servlet.http.HttpServletRequest;
import vn.edu.wspm.backend.exception.ApplicationException;

import java.io.IOException;
import java.util.Set;

/** Small request-shape checks, invoked after endpoint authorization. No domain validation or logging. */
public final class RequestChecks {
    private RequestChecks() {}

    /** Exact empty body: whitespace is content too; content-length is not evidence of emptiness. */
    public static void requireEmptyBody(HttpServletRequest request, String field, String safeMessage) throws IOException {
        if (request.getInputStream().read() != -1) throw ApplicationException.invalid(field, safeMessage);
    }

    /** Preserve parameter-first rejection, without reading the body when parameters are present. */
    public static void requireNoParametersOrBody(HttpServletRequest request, String safeMessage) throws IOException {
        if (!request.getParameterMap().isEmpty()) throw ApplicationException.invalid("request", safeMessage);
        requireEmptyBody(request, "request", safeMessage);
    }

    /** Endpoints supply fixed safe errors; never echo an untrusted query key. */
    public static void requireAllowedQueryNames(Set<String> names, Set<String> allowed, String detailCode, String safeMessage) {
        for (String name : names) {
            if (!allowed.contains(name)) throw ApplicationException.invalid("query", detailCode, safeMessage);
        }
    }

    /** Optional known parameters may appear at most once; value parsing stays with the endpoint. */
    public static void requireSingleQueryParameters(HttpServletRequest request, Set<String> allowed, String safeMessage) {
        var parameters = request.getParameterMap();
        requireAllowedQueryNames(parameters.keySet(), allowed, "INVALID_VALUE", safeMessage);
        if (parameters.values().stream().anyMatch(values -> values.length != 1)) {
            throw ApplicationException.invalid("query", safeMessage);
        }
    }
}
