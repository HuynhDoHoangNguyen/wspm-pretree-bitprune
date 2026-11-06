package vn.edu.wspm.backend.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.web.auth.AuthJson;
import vn.edu.wspm.backend.web.auth.AuthRequestReader;
import vn.edu.wspm.backend.services.auth.AuthInputValidator;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure Auth input boundary; does not replace email Bean Validation or HTTP/CSRF/DB integration. */
class AuthInputContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode read(String json) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        return AuthJson.read(request, mapper);
    }
    private JsonNode registration(String password) {
        return mapper.valueToTree(Map.of("username", " CUSTOMER.TEST ", "email", " PERSON@EXAMPLE.TEST ",
                "displayName", " Nguyễn An ", "password", password));
    }

    @Test void normalizesIdentityButPreservesPasswordAndRedactsStringRepresentations() throws Exception {
        String password = " Password123 ";
        var login = AuthRequestReader.login(read(mapper.writeValueAsString(Map.of("username", " CUSTOMER.TEST ", "password", password))));
        assertEquals("customer.test", login.username()); assertEquals(password, login.password());
        var registration = AuthRequestReader.registration(registration(password));
        assertEquals("customer.test", registration.username());
        assertEquals("person@example.test", registration.email());
        assertEquals("Nguyễn An", registration.displayName());
        assertEquals(password, registration.password());
        assertEquals(registration, AuthInputValidator.registration(registration));
        assertFalse(login.toString().contains(password));
        assertFalse(registration.toString().contains(password));
        assertFalse(registration.toString().contains("person@example.test"));
    }

    @ParameterizedTest @ValueSource(strings = {
            "}", "{\"username\":\"one\",\"username\":\"two\"}", "{} {}"
    })
    void rejectsMalformedDuplicateAndTrailingJsonAtReaderBoundary(String input) {
        var failure = assertThrows(ApplicationException.class, () -> read(input));
        assertEquals(400, failure.status().value()); assertEquals("MALFORMED_REQUEST", failure.code());
        assertTrue(failure.details().isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"[]", "null", "{}", "{\"username\":12,\"password\":\"Password123\"}",
            "{\"username\":\"customer.test\",\"password\":null}",
            "{\"username\":\"customer.test\",\"password\":false}",
            "{\"username\":\"customer.test\",\"password\":\"Password123\",\"secret-role\":\"ADMIN\"}"})
    void loginRequiresClosedObjectAndTextFieldsWithoutCoercion(String input) {
        var failure = assertThrows(ApplicationException.class, () -> AuthRequestReader.login(read(input)));
        assertEquals("VALIDATION_FAILED", failure.code());
        assertEquals("body", failure.details().getFirst().field());
        assertFalse(failure.getMessage().contains("secret-role"));
        assertFalse(failure.details().toString().contains("secret-role"));
    }

    @ParameterizedTest @ValueSource(strings = {"role", "roles", "status", "userId", "passwordHash", "profile"})
    void registrationRejectsClientAssignedPrivilegesAndExtraFields(String field) {
        var body = mapper.createObjectNode();
        body.setAll((tools.jackson.databind.node.ObjectNode) registration("Password123"));
        body.put(field, "synthetic-private-value");
        var failure = assertThrows(ApplicationException.class, () -> AuthRequestReader.registration(body));
        assertEquals("VALIDATION_FAILED", failure.code()); assertEquals("body", failure.details().getFirst().field());
        assertFalse(failure.details().toString().contains("synthetic-private-value"));
    }

    @Test void passwordByteAndCodePointBoundariesDifferBetweenLoginAndRegistration() {
        String exactly72Bytes = "€".repeat(24);
        assertEquals(exactly72Bytes, AuthRequestReader.registration(registration(exactly72Bytes)).password());
        assertEquals(exactly72Bytes, AuthRequestReader.login(mapper.valueToTree(Map.of("username", "customer.test", "password", exactly72Bytes))).password());
        for (String invalid : new String[]{"€".repeat(25), "Password\0test", "Password\uD800"}) {
            var registrationFailure = assertThrows(ApplicationException.class,
                    () -> AuthRequestReader.registration(registration(invalid)));
            assertEquals("password", registrationFailure.details().getFirst().field());
            var loginFailure = assertThrows(ApplicationException.class, () -> AuthRequestReader.login(
                    mapper.valueToTree(Map.of("username", "customer.test", "password", invalid))));
            assertEquals("password", loginFailure.details().getFirst().field());
        }
        assertEquals("x", AuthRequestReader.login(mapper.valueToTree(Map.of("username", "customer.test", "password", "x"))).password());
        assertEquals("password", assertThrows(ApplicationException.class,
                () -> AuthRequestReader.registration(registration("x"))).details().getFirst().field());
    }
}
