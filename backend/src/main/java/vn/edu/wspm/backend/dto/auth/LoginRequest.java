package vn.edu.wspm.backend.dto.auth;

import tools.jackson.databind.JsonNode;

import vn.edu.wspm.backend.exception.ApplicationException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public record LoginRequest(String username, String password) {
    public static LoginRequest from(JsonNode node) {
        if (node == null || !node.isObject())
            throw ApplicationException.invalid("body", "Supply a JSON object.");
        for (String field : node.propertyNames())
            if (!Set.of("username", "password").contains(field)) {
                throw ApplicationException.invalid("body", "Unknown fields are not accepted.");
            }
        if (node.get("username") == null
                || !node.get("username").isString()
                || node.get("password") == null
                || !node.get("password").isString()) {
            throw ApplicationException.invalid("body", "Supply username and password as text.");
        }
        String username = node.get("username").asString().trim().toLowerCase(Locale.ROOT);
        String password = node.get("password").asString();
        if (!username.matches("[a-z0-9][a-z0-9._-]{2,79}"))
            throw ApplicationException.invalid("username", "Supply a valid username.");
        if (password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.indexOf('\0') >= 0
                || password.codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)) {
            throw ApplicationException.invalid(
                    "password", "Supply a password of at most 72 UTF-8 bytes, without NUL.");
        }
        return new LoginRequest(username, password);
    }

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}
