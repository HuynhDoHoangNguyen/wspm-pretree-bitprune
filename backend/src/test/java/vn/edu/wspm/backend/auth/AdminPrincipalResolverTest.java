package vn.edu.wspm.backend.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.auth.AdminPrincipal;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import static org.junit.jupiter.api.Assertions.*;

class AdminPrincipalResolverTest {
    private final AdminPrincipalResolver resolver = new AdminPrincipalResolver();
    @Test void unverifiedGenericPrincipalOrClaimedHeadersCannotResolveReviewer() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "claimed-user");
        request.addHeader("X-Role", "ADMIN");
        request.setUserPrincipal(() -> "claimed-user");
        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, assertThrows(ApplicationException.class, () -> resolver.reviewerId(request)).errorCode());
    }
    @Test void typedPrincipalResolvesServerIdOnlyWithExactAdminRole() {
        var request = new MockHttpServletRequest();
        request.setUserPrincipal(new AdminPrincipal("server-id", List.of("CUSTOMER", "ADMIN")));
        request.addParameter("reviewedBy", "claimed-user");
        assertEquals("server-id", resolver.reviewerId(request));
        request.setUserPrincipal(new AdminPrincipal("server-id", List.of("ROLE_ADMIN")));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(ApplicationException.class, () -> resolver.reviewerId(request)).errorCode());
    }
}
