package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import tools.jackson.databind.ObjectMapper;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.web.common.ApiErrorWriter;

import java.io.IOException;

/** Identity endpoint guard; WDM consumes the same shared authenticator. */
@RequiredArgsConstructor
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class AuthAccessFilter extends OncePerRequestFilter {
    public static final String IDENTITY = "auth.identity";
    private static final PathPattern ME =
            PathPatternParser.defaultInstance.parse("/api/v1/auth/me");
    private final SharedRequestAuthenticator authenticator;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ME.matches(
                RequestPath.parse(request.getRequestURI(), request.getContextPath())
                        .pathWithinApplication());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiErrorWriter.headers(request, response);
        try {
            request.setAttribute(IDENTITY, authenticator.authenticate(request));
        } catch (ApplicationException rejected) {
            if (rejected.status().value() == 401) response.setHeader("WWW-Authenticate", "Bearer");
            ApiErrorWriter.write(request, response, rejected, mapper);
            return;
        }
        chain.doFilter(request, response);
    }
}
