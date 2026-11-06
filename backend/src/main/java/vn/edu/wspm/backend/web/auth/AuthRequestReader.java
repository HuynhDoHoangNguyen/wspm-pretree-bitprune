package vn.edu.wspm.backend.web.auth;

import tools.jackson.databind.JsonNode;
import vn.edu.wspm.backend.dto.auth.LoginRequest;
import vn.edu.wspm.backend.dto.auth.RegistrationRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.auth.AuthInputValidator;

import java.util.Set;

/** Closed input projection after AuthJson's strict parsing; never bind credentials through MVC. */
public final class AuthRequestReader {
    private static final Set<String> LOGIN_FIELDS = Set.of("username", "password");
    private static final Set<String> REGISTRATION_FIELDS = Set.of("username", "email", "password", "displayName");

    private AuthRequestReader() {}

    public static LoginRequest login(JsonNode body) {
        closedObject(body, LOGIN_FIELDS);
        if (body.get("username") == null || !body.get("username").isString()
                || body.get("password") == null || !body.get("password").isString()) {
            throw ApplicationException.invalid("body", "Supply username and password as text.");
        }
        return AuthInputValidator.login(new LoginRequest(body.get("username").asString(), body.get("password").asString()));
    }

    public static RegistrationRequest registration(JsonNode body) {
        closedObject(body, REGISTRATION_FIELDS);
        return AuthInputValidator.registration(new RegistrationRequest(text(body, "username"), text(body, "email"),
                text(body, "password"), text(body, "displayName")));
    }

    private static void closedObject(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject())
            throw ApplicationException.invalid("body", "Supply a JSON object.");
        for (String field : body.propertyNames()) {
            if (!allowed.contains(field))
                throw ApplicationException.invalid("body", "Unknown fields are not accepted.");
        }
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isString())
            throw ApplicationException.invalid(field, "Supply a text value.");
        return value.asString();
    }
}
