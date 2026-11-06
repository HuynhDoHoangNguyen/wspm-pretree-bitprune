package vn.edu.wspm.backend.web.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;

/** No request-body/header identity fallback. Recheck mutable eligibility in the future review transaction. */
@Component
public final class AdminPrincipalResolver {
    public AdminPrincipal require(HttpServletRequest request) {
        if (!(request.getUserPrincipal() instanceof AdminPrincipal principal)) throw JwtTokenService.unauthenticated();
        if (!principal.roles().contains("ADMIN")) throw new ApplicationException(ErrorCode.ACCESS_DENIED);
        if (principal.userId() == null || principal.userId().isBlank() || principal.userId().length() > 40)
            throw JwtTokenService.unauthenticated();
        return principal;
    }
    public String reviewerId(HttpServletRequest request) { return require(request).userId(); }
}
