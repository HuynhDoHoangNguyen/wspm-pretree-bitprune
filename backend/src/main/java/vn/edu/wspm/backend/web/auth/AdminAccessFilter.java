package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;
import vn.edu.wspm.backend.web.common.ApiPaths;

/** Namespace guard, after transport and existing Auth CSRF filters. Does not enable any business endpoint. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public final class AdminAccessFilter extends OncePerRequestFilter {
    private final SharedRequestAuthenticator authenticator;
    private final ObjectMapper mapper;
    public AdminAccessFilter(SharedRequestAuthenticator authenticator, ObjectMapper mapper) {
        this.authenticator = authenticator;
        this.mapper = mapper;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !ApiPaths.isWdm(request); }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiErrorWriter.headers(request, response);
        final AdminPrincipal principal;
        try {
            var identity = authenticator.authenticate(request);
            if (!"ACTIVE".equals(identity.status()) || identity.userId() == null || identity.userId().isBlank()
                    || identity.userId().length() > 40) throw JwtTokenService.unauthenticated();
            if (!identity.roles().contains("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
            principal = new AdminPrincipal(identity.userId(), identity.roles());
        } catch (ApplicationException denied) {
            if (denied.status().value() == 401) response.setHeader("WWW-Authenticate", "Bearer");
            ApiErrorWriter.write(request, response, denied, mapper);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return principal.userId(); }
            @Override public boolean isUserInRole(String role) { return role != null && principal.roles().contains(role); }
        }, response);
    }
}
