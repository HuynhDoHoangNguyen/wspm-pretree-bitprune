package vn.edu.wspm.backend.web.wdm;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;

/** Adapts verified shared account/session identity to the WDM ADMIN requirement. */
@Component
@RequiredArgsConstructor
public class SharedAuthWdmAdminAuthorizer implements WdmAdminAuthorizer {
    private final SharedRequestAuthenticator authenticator;

    @Override
    public void requireAdmin(HttpServletRequest request) {
        var identity = authenticator.authenticate(request);
        if (!identity.roles().contains("ADMIN")) {
            throw new ApplicationException(
                    ErrorCode.ACCESS_DENIED, "Admin permission is required.");
        }
    }
}
