package vn.edu.wspm.backend.services.auth;

import vn.edu.wspm.backend.dto.auth.LoginRequest;
import vn.edu.wspm.backend.dto.auth.RegistrationRequest;
import vn.edu.wspm.backend.exception.ApplicationException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure Auth input policy. No credential logging, database access or session/role decisions. */
public final class AuthInputValidator {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,79}");

    private AuthInputValidator() {}

    /** The web reader has already required text fields; existing login credentials may be short. */
    public static LoginRequest login(LoginRequest input) {
        String username = input.username().trim().toLowerCase(Locale.ROOT);
        String password = input.password();
        if (!USERNAME.matcher(username).matches())
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

    /** Also called by the service for direct callers; email Bean Validation remains there. */
    public static RegistrationRequest registration(RegistrationRequest input) {
        String canonicalUsername = input.username() == null ? "" : input.username().trim().toLowerCase(Locale.ROOT);
        String canonicalEmail = input.email() == null ? "" : input.email().trim().toLowerCase(Locale.ROOT);
        String name = input.displayName() == null ? "" : input.displayName().trim();
        String password = input.password();
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
}
