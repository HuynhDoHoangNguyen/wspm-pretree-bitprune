package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.security.web.firewall.FirewalledRequest;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;

import java.io.IOException;
import java.util.List;

/** API boundary: strict transport checks and explicit routes, default denial. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiSecurityFilter extends OncePerRequestFilter {
    private static final PathPattern API = PathPatternParser.defaultInstance.parse("/api/**");
    private static final PathPattern ADMIN =
            PathPatternParser.defaultInstance.parse("/api/v1/admin/**");
    private static final List<AuthRoute> AUTH =
            List.of("csrf", "register", "login", "refresh", "logout", "me").stream()
                    .map(
                            name ->
                                    new AuthRoute(
                                            name,
                                            PathPatternParser.defaultInstance.parse(
                                                    "/api/v1/auth/" + name)))
                    .toList();
    private static final List<PathPattern> PUBLIC_READS =
            List.of("/api/health", "/api/v1/products", "/api/v1/products/{productId}", "/api/v1/categories").stream()
                    .map(PathPatternParser.defaultInstance::parse)
                    .toList();
    private static final List<PathPattern> COMBO_READS =
            List.of("/api/v1/recommendations/combos", "/api/v1/recommendations/combos/{comboId}")
                    .stream().map(PathPatternParser.defaultInstance::parse).toList();
    private final StrictHttpFirewall firewall = new StrictHttpFirewall();
    private final ObjectMapper mapper;
    private final SharedRequestAuthenticator authenticator;

    public ApiSecurityFilter(ObjectMapper mapper, SharedRequestAuthenticator authenticator) {
        this.mapper = mapper;
        this.authenticator = authenticator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Include encoded API segments using the same segment decoding as MVC.
        try {
            return !API.matches(
                    RequestPath.parse(request.getRequestURI(), request.getContextPath())
                            .pathWithinApplication());
        } catch (IllegalArgumentException malformed) {
            return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FirewalledRequest checked = null;
        try {
            checked = firewall.getFirewalledRequest(request);
            if (!authMethodAllowed(checked, response)) return;
            if (!explicitRoute(checked)) {
                try {
                    authenticator.authenticate(checked);
                } catch (ApplicationException denied) {
                    reject(request, response, denied.errorCode(), denied.getMessage());
                    return;
                }
                reject(
                        request,
                        response,
                        ErrorCode.ACCESS_DENIED,
                        "Endpoint access is not enabled by policy.");
                return;
            }
            chain.doFilter(checked, firewall.getFirewalledResponse(response));
        } catch (RequestRejectedException rejected) {
            if (response.isCommitted())
                return; // Never append JSON to an artifact already in progress.
            reject(
                    request,
                    response,
                    ErrorCode.MALFORMED_REQUEST,
                    "Request path or transport was rejected.");
        } finally {
            if (checked != null) checked.reset();
        }
    }

    private static boolean explicitRoute(HttpServletRequest request) {
        PathContainer path =
                RequestPath.parse(request.getRequestURI(), request.getContextPath())
                        .pathWithinApplication();
        if (ADMIN.matches(path) || AUTH.stream().anyMatch(route -> route.path().matches(path)))
            return true;
        if ("GET".equals(request.getMethod()) && COMBO_READS.stream().anyMatch(route -> route.matches(path)))
            return true;
        return ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))
                && PUBLIC_READS.stream().anyMatch(route -> route.matches(path));
    }

    private boolean authMethodAllowed(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        PathContainer path =
                RequestPath.parse(request.getRequestURI(), request.getContextPath())
                        .pathWithinApplication();
        // Combo reads are GET-only; preserve the existing Catalog HEAD policy.
        if (COMBO_READS.stream().anyMatch(route -> route.matches(path))
                && !"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            reject(request, response, ErrorCode.METHOD_NOT_ALLOWED, "Request method is not supported.");
            return false;
        }
        for (AuthRoute route : AUTH) {
            if (!route.path().matches(path) || route.allowed(request.getMethod())) continue;
            // Keep identity authentication ahead of method/resource disclosure.
            if (route.name().equals("me")) {
                try {
                    authenticator.authenticate(request);
                } catch (ApplicationException denied) {
                    reject(request, response, denied.errorCode(), denied.getMessage());
                    return false;
                }
            }
            response.setHeader("Allow", route.readOnly() ? "GET, HEAD" : "POST");
            reject(
                    request,
                    response,
                    ErrorCode.METHOD_NOT_ALLOWED,
                    "Request method is not supported.");
            return false;
        }
        return true;
    }

    private record AuthRoute(String name, PathPattern path) {
        boolean readOnly() {
            return name.equals("csrf") || name.equals("me");
        }

        boolean allowed(String method) {
            return readOnly()
                    ? method.equals("GET") || method.equals("HEAD")
                    : method.equals("POST");
        }
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode code,
            String message)
            throws IOException {
        var error = new ApplicationException(code, message);
        if (response.isCommitted()) return;
        if (code == ErrorCode.AUTHENTICATION_REQUIRED)
            response.setHeader("WWW-Authenticate", "Bearer");
        ApiErrorWriter.write(request, response, error, mapper);
    }
}
