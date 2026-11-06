package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.auth.ApiSecurityFilter;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.*;

/** Actual API/WDM filters and shared RBAC adapter; only JWT/session identity seam is mocked. */
class ComboReadSecurityTest {
    private final SharedRequestAuthenticator auth = mock(SharedRequestAuthenticator.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private ApiSecurityFilter api;
    private WdmRequestFilter admin;

    @BeforeEach void setup() {
        api = new ApiSecurityFilter(mapper, auth);
        ObjectProvider<WdmAdminAuthorizer> provider = mock(ObjectProvider.class);
        when(provider.stream()).thenAnswer(call -> Stream.of(new SharedAuthWdmAdminAuthorizer(auth)));
        admin = new WdmRequestFilter(provider, mapper);
    }
    private MockHttpServletResponse request(String method, String path, String identity) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        if (identity != null) request.addHeader("Authorization", "Bearer unit-" + identity);
        var response = new MockHttpServletResponse();
        FilterChain destination = (req, res) -> { res.setContentType("application/json"); res.getWriter().write("{}"); };
        new RequestIdFilter().doFilter(request, response,
                (r, s) -> api.doFilter(r, s, (ar, as) -> admin.doFilter(ar, as, destination)));
        return response;
    }
    private void identity(String role) {
        doReturn(new IdentityResponse("u", "unit", "unit@example.test",
                "Unit", "ACTIVE", List.of(role), false)).when(auth).authenticate(any());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/v1/recommendations/combos", "/api/v1/recommendations/combos/c"})
    void exactGetPathsArePublicForGuestCustomerAndAdmin(String path) throws Exception {
        for (String identity : new String[] {null, "customer", "admin"}) {
            var response = request("GET", path, identity);
            assertEquals(200, response.getStatus());
            assertNotNull(response.getHeader("X-Request-Id"));
        }
        verifyNoInteractions(auth);
    }

    @ParameterizedTest @ValueSource(strings = {"HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE"})
    void nonGetMethodsNeverReachController(String method) throws Exception {
        for (String path : List.of("/api/v1/recommendations/combos", "/api/v1/recommendations/combos/c")) {
            var response = request(method, path, null);
            // StrictHttpFirewall rejects TRACE at the transport boundary; all others are method errors.
            assertEquals(method.equals("TRACE") ? 400 : 405, response.getStatus());
            var body = mapper.readTree(response.getContentAsString());
            assertEquals(method.equals("TRACE") ? "MALFORMED_REQUEST" : "METHOD_NOT_ALLOWED", body.get("code").asString());
            assertEquals(response.getHeader("X-Request-Id"), body.get("requestId").asString());
            if (!method.equals("TRACE")) assertEquals("GET", response.getHeader("Allow"));
        }
        verifyNoInteractions(auth);
    }

    @Test void publicRuleDoesNotCoverSubtreesOrUnknownApis() throws Exception {
        when(auth.authenticate(any())).thenThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        for (String path : List.of("/api/v1/recommendations/combos/c/publish", "/api/v1/recommendations/other", "/api/v1/private"))
            assertEquals(401, request("GET", path, null).getStatus());
    }

    @Test void adminRbacStillUsesSharedAuthenticator() throws Exception {
        String path = "/api/v1/admin/mining-runs";
        when(auth.authenticate(any())).thenThrow(new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
        assertEquals(401, request("GET", path, null).getStatus());
        identity("CUSTOMER");
        var customer = request("GET", path, "customer");
        assertEquals(403, customer.getStatus());
        assertEquals("ACCESS_DENIED", mapper.readTree(customer.getContentAsString()).get("code").asString());
        identity("ADMIN");
        assertEquals(200, request("GET", path, "admin").getStatus());
        verify(auth, times(3)).authenticate(any());
    }

    @Test void existingCatalogAndHealthHeadPolicyIsUnchanged() throws Exception {
        for (String path : List.of("/api/health", "/api/v1/products", "/api/v1/categories")) {
            assertEquals(200, request("GET", path, null).getStatus());
            assertEquals(200, request("HEAD", path, null).getStatus());
        }
        verifyNoInteractions(auth);
    }
}
