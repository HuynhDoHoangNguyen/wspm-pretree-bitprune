package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.web.auth.AuthRequestFilter;

/** Filter/config unit tests, no Spring context or database. */
class AuthBoundaryTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void missingOriginConfigurationHasNoPermissiveFallback() throws Exception {
        var filter = new AuthRequestFilter(mapper, "", true);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.addHeader("Origin", "https://auth.example.test");
        request.addHeader("X-XSRF-TOKEN", "arbitrary");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Request must not reach MVC"));
        assertEquals(403, response.getStatus());
        assertEquals(
                "CSRF_REJECTED",
                mapper.readTree(response.getContentAsString()).get("code").asString());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    void insecureCookieRequiresExplicitLocalConfiguration() throws Exception {
        for (boolean secure : new boolean[] {true, false}) {
            var filter =
                    new AuthRequestFilter(
                            mapper,
                            secure ? "https://auth.example.test" : "http://localhost:3000",
                            secure);
            var request = new MockHttpServletRequest("GET", "/api/v1/auth/csrf");
            var response = new MockHttpServletResponse();
            filter.doFilter(
                    request,
                    response,
                    (req, res) -> assertNotNull(req.getAttribute(AuthRequestFilter.RAW_CSRF)));
            String cookie = response.getHeader("Set-Cookie");
            assertNotNull(cookie);
            assertEquals(secure, cookie.contains("Secure"));
            // MockHttpServletResponse omits extended attributes when serializing
            // addCookie(); the real HTTP integration test verifies the wire header.
            assertEquals("Lax", response.getCookie("XSRF-TOKEN").getAttribute("SameSite"));
            assertFalse(cookie.contains("HttpOnly"));
            assertNull(request.getSession(false));
        }
    }

    @Test
    void rejectsWildcardPathsAndInsecureDeploymentOrigins() {
        for (String origin :
                new String[] {
                    "*",
                    "https://example.test/path",
                    "https://example.test/",
                    "https://example.test?query=x",
                    "https://user@example.test",
                    "http://example.test"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new AuthRequestFilter(mapper, origin, true));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthRequestFilter(mapper, "http://example.test", false));
    }

    @Test
    void csrfQueryParameterCannotReplaceRequiredHeaderAndDuplicateOriginIsRejected()
            throws Exception {
        var filter = new AuthRequestFilter(mapper, "https://auth.example.test", true);
        for (boolean duplicateOrigin : new boolean[] {false, true}) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
            request.addHeader("Origin", "https://auth.example.test");
            if (duplicateOrigin) {
                request.addHeader("Origin", "https://auth.example.test");
                request.addHeader("X-XSRF-TOKEN", "arbitrary");
            }
            request.addParameter("_csrf", "arbitrary");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("Request must not reach MVC"));
            assertEquals(403, response.getStatus());
        }
    }
}
