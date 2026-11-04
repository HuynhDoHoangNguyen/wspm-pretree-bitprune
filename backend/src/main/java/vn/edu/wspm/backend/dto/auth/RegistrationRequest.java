package vn.edu.wspm.backend.dto.auth;

import jakarta.validation.constraints.Email;

import tools.jackson.databind.JsonNode;

import vn.edu.wspm.backend.exception.ApplicationException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** A closed input DTO: clients cannot assign status, IDs, profile fields or roles. */
public record RegistrationRequest(
        String username, @Email String email, String password, String displayName) {
    private static final Set<String> FIELDS =
            Set.of("username", "email", "password", "displayName");
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,79}");

    public static RegistrationRequest from(JsonNode body) {
        if (body == null || !body.isObject())
            throw ApplicationException.invalid("body", "Supply a JSON object.");
        for (String field : body.propertyNames()) {
            if (!FIELDS.contains(field))
                throw ApplicationException.invalid("body", "Unknown fields are not accepted.");
        }
        return new RegistrationRequest(
                        text(body, "username"),
                        text(body, "email"),
                        text(body, "password"),
                        text(body, "displayName"))
                .validated();
    }

    private static String text(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null || !value.isString())
            throw ApplicationException.invalid(field, "Supply a text value.");
        return value.asString();
    }

    public RegistrationRequest validated() {
        String canonicalUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        String canonicalEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String name = displayName == null ? "" : displayName.trim();
        if (!USERNAME.matcher(canonicalUsername).matches()) {
            throw ApplicationException.invalid(
                    "username", "Use 3 to 80 ASCII letters, digits, dots, underscores or hyphens.");
        }
        if (canonicalEmail.length() > 254
                || !canonicalEmail.chars().allMatch(c -> c > 32 && c < 127)
                || canonicalEmail.isEmpty()) {
            throw ApplicationException.invalid(
                    "email", "Supply a valid ASCII email address of at most 254 characters.");
        }
        if (name.isBlank()
                || name.codePointCount(0, name.length()) > 160
                || name.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)) {
            throw ApplicationException.invalid(
                    "displayName", "Supply a display name of 1 to 160 characters.");
        }
        if (password == null
                || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.indexOf('\0') >= 0
                || password.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)) {
            throw ApplicationException.invalid(
                    "password",
                    "Use at least 8 characters and at most 72 UTF-8 bytes, without NUL.");
        }
        return new RegistrationRequest(canonicalUsername, canonicalEmail, password, name);
    }

    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
