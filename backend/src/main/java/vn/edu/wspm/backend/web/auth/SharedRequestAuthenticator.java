package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;
import vn.edu.wspm.backend.services.auth.AuthSessionService;

/** One credential carrier/verifier for identity and server-side consumers. */
@RequiredArgsConstructor
@Component
public class SharedRequestAuthenticator {
    private final AuthSessionService sessions;

    public IdentityResponse authenticate(HttpServletRequest request) {
        var headers = request.getHeaders("Authorization");
        if (!headers.hasMoreElements()) throw JwtTokenService.unauthenticated();
        String value = headers.nextElement();
        if (headers.hasMoreElements()
                || value.length() > 8192
                || !value.regionMatches(true, 0, "Bearer ", 0, 7)
                || !value.substring(7)
                        .matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw JwtTokenService.unauthenticated();
        }
        return sessions.authenticate(value.substring(7));
    }
}
