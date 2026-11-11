package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class AuthCookies {
    private final boolean secure;
    private final Clock clock;

    public AuthCookies(@Value("${auth.cookie-secure:true}") boolean secure, Clock clock) {
        this.secure = secure;
        this.clock = clock;
    }

    public String refresh(HttpServletRequest request) {
        String found = null;
        if (request.getCookies() != null)
            for (var cookie : request.getCookies()) {
                if ("wspm_refresh".equals(cookie.getName())) {
                    if (found != null) return null; // Ambiguous cookies never select a session.
                    found = cookie.getValue();
                }
            }
        return found;
    }

    public void setRefresh(HttpServletResponse response, String raw, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(clock.instant(), expiresAt).getSeconds());
        response.addHeader(
                "Set-Cookie",
                ResponseCookie.from("wspm_refresh", raw)
                        .path("/api/v1/auth")
                        .httpOnly(true)
                        .secure(secure)
                        .sameSite("Lax")
                        .maxAge(seconds)
                        .build()
                        .toString());
    }

    public void clearRefresh(HttpServletResponse response) {
        setRefresh(response, "", clock.instant());
    }

    public void clearCsrf(HttpServletRequest request, HttpServletResponse response) {
        csrfRepository(secure).saveToken(null, request, response);
    }

    public static CookieCsrfTokenRepository csrfRepository(boolean secure) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).sameSite("Lax"));
        return repository;
    }
}
