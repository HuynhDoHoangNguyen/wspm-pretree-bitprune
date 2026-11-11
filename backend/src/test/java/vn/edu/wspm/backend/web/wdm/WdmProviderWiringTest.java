package vn.edu.wspm.backend.web.wdm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.dto.auth.IdentityResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.web.auth.SharedRequestAuthenticator;

import java.util.List;

/** Consumer/provider unit tests; real ObjectProvider uniqueness, not Auth/DB E2E. */
class WdmProviderWiringTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void missingOrAmbiguousProviderIsUnavailableWithoutEnteringMvc(int count) throws Exception {
        var beans = new DefaultListableBeanFactory();
        for (int index = 0; index < count; index++)
            beans.registerSingleton(
                    "provider" + index,
                    (WdmAdminAuthorizer)
                            request -> fail("Ambiguous provider must not be selected"));
        unavailable(beans);
    }

    @Test
    void primaryDoesNotHideMultipleProviderBeans() throws Exception {
        var beans = new DefaultListableBeanFactory();
        var primary = new RootBeanDefinition(WdmAdminAuthorizer.class);
        primary.setInstanceSupplier(
                () -> (WdmAdminAuthorizer) request -> fail("Primary must not hide ambiguity"));
        primary.setPrimary(true);
        beans.registerBeanDefinition("primary", primary);
        beans.registerSingleton(
                "other", (WdmAdminAuthorizer) request -> fail("Multiple providers must deny"));
        unavailable(beans);
    }

    private void unavailable(DefaultListableBeanFactory beans) throws Exception {
        var mapper = JsonMapper.builder().build();
        var filter = new WdmRequestFilter(beans.getBeanProvider(WdmAdminAuthorizer.class), mapper);
        var request = new MockHttpServletRequest("GET", "/api/v1/admin/order-exports");
        var response = new MockHttpServletResponse();
        filter.doFilter(
                request, response, (req, res) -> fail("Unavailable Auth must not enter MVC/query"));
        assertEquals(503, response.getStatus());
        var body = mapper.readTree(response.getContentAsString());
        assertEquals("SERVICE_UNAVAILABLE", body.get("code").asString());
        assertEquals(response.getHeader("X-Request-Id"), body.get("requestId").asString());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "admin", "ROLE_ADMIN"})
    void providerRequiresExactAdminCode(String code) {
        var shared = mock(SharedRequestAuthenticator.class);
        var request = new MockHttpServletRequest();
        when(shared.authenticate(request))
                .thenReturn(
                        new IdentityResponse(
                                "user",
                                "name",
                                "name@example.test",
                                "Name",
                                "ACTIVE",
                                List.of(code),
                                false));
        var error =
                assertThrows(
                        ApplicationException.class,
                        () -> new SharedAuthWdmAdminAuthorizer(shared).requireAdmin(request));
        assertEquals(HttpStatus.FORBIDDEN, error.status());
        assertEquals("ACCESS_DENIED", error.code());
    }

    @Test
    void providerPreservesSharedAuthenticationAndStorageDenials() {
        var shared = mock(SharedRequestAuthenticator.class);
        var request = new MockHttpServletRequest();
        for (var status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.SERVICE_UNAVAILABLE)) {
            doThrow(
                            new ApplicationException(
                                    status == HttpStatus.UNAUTHORIZED
                                            ? ErrorCode.AUTHENTICATION_REQUIRED
                                            : ErrorCode.SERVICE_UNAVAILABLE,
                                    "Safe rejection."))
                    .when(shared)
                    .authenticate(request);
            var error =
                    assertThrows(
                            ApplicationException.class,
                            () -> new SharedAuthWdmAdminAuthorizer(shared).requireAdmin(request));
            assertEquals(status, error.status());
        }
    }
}
