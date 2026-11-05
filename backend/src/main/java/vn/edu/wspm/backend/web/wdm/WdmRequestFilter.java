package vn.edu.wspm.backend.web.wdm;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;
import vn.edu.wspm.backend.web.common.ApiPaths;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.io.IOException;

/** Applies before MVC validation/lookup, including unknown WDM resource paths. */
@RequiredArgsConstructor
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class WdmRequestFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(WdmRequestFilter.class);
    private final ObjectProvider<WdmAdminAuthorizer> authorizers;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ApiPaths.isWdm(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, RequestIdFilter.requestId(request));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            // getIfUnique can select @Primary among several beans. This seam's
            // contract requires exactly one provider, not preferred resolution.
            var candidates = authorizers.stream().limit(2).toList();
            if (candidates.size() != 1) {
                throw new ApplicationException(
                        ErrorCode.SERVICE_UNAVAILABLE,
                        "Admin authorization provider is unavailable.");
            }
            candidates.getFirst().requireAdmin(request);
        } catch (ApplicationException failure) {
            writeError(request, response, failure);
            return;
        } catch (RequestRejectedException malformed) {
            throw malformed; // ApiSecurityFilter owns sanitized transport rejection, not credential
                             // logging.
        } catch (RuntimeException failure) {
            var safe =
                    new IllegalStateException(
                            "WDM authorization failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error(
                    "WDM authorization failed: requestId={}",
                    RequestIdFilter.requestId(request),
                    safe);
            writeError(
                    request,
                    response,
                    new ApplicationException(
                            ErrorCode.INTERNAL_ERROR, "Request could not be completed."));
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeError(
            HttpServletRequest request, HttpServletResponse response, ApplicationException failure)
            throws IOException {
        if (response.isCommitted()) return;
        if (failure.status() == HttpStatus.UNAUTHORIZED)
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        ApiErrorWriter.write(request, response, failure, mapper);
    }
}
