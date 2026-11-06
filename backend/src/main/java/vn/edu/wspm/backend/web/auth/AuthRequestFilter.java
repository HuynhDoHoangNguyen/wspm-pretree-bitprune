package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;
import vn.edu.wspm.backend.web.common.ApiPaths;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Auth-only CSRF boundary, not a login/token verifier or WDM authorizer. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthRequestFilter extends OncePerRequestFilter {
    public static final String RAW_CSRF = "auth.rawCsrf";
    private final Set<String> origins;
    private final CsrfFilter csrf;
    private final ObjectMapper mapper;

    public AuthRequestFilter(
            ObjectMapper mapper,
            @Value("${auth.allowed-origins:}") String origins,
            @Value("${auth.cookie-secure:true}") boolean secure) {
        this.mapper = mapper;
        this.origins =
                Arrays.stream(origins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
        for (String origin : this.origins) {
            URI uri = URI.create(origin);
            boolean localHttp =
                    !secure
                            && "http".equals(uri.getScheme())
                            && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if ((!"https".equals(uri.getScheme()) && !localHttp)
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException(
                        "Auth origins must be explicit origins; HTTP is local-only.");
            }
        }
        CookieCsrfTokenRepository cookies = AuthCookies.csrfRepository(secure);
        csrf = new CsrfFilter(cookies);
        csrf.setRequestHandler(new SpaCsrfHandler());
        csrf.setAccessDeniedHandler((request, response, denied) -> reject(request, response));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ApiPaths.isAuth(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiErrorWriter.headers(request, response);
        if (CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) {
            var supplied = request.getHeaders("Origin");
            var csrfHeaders = request.getHeaders("X-XSRF-TOKEN");
            if (!supplied.hasMoreElements()
                    || !origins.contains(supplied.nextElement())
                    || supplied.hasMoreElements()
                    || !csrfHeaders.hasMoreElements()
                    || !StringUtils.hasText(csrfHeaders.nextElement())
                    || csrfHeaders.hasMoreElements()) {
                reject(request, response);
                return;
            }
        }
        csrf.doFilter(request, response, chain);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiErrorWriter.write(
                request,
                response,
                new ApplicationException(
                        ErrorCode.CSRF_REJECTED, "Request origin or CSRF token was rejected."),
                mapper);
    }

    private static final class SpaCsrfHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestAttributeHandler plain =
                new CsrfTokenRequestAttributeHandler();
        private final XorCsrfTokenRequestAttributeHandler xor =
                new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(
                HttpServletRequest request,
                HttpServletResponse response,
                Supplier<CsrfToken> token) {
            xor.handle(request, response, token);
            request.setAttribute(
                    RAW_CSRF, token.get()); // Materialize cookie for the SPA bootstrap.
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken token) {
            return (StringUtils.hasText(request.getHeader(token.getHeaderName())) ? plain : xor)
                    .resolveCsrfTokenValue(request, token);
        }
    }
}
