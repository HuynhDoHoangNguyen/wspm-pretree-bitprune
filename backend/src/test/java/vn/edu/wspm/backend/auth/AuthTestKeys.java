package vn.edu.wspm.backend.auth;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.security.SecureRandom;
import java.util.Base64;

/** Random in-memory test secrets; never installed as application defaults. */
public final class AuthTestKeys {
    public static final String ISSUER = "https://auth.example.test";
    public static final String KID = "isolated-test-key";
    public static final Material KEYS = create(256);

    private AuthTestKeys() {}

    public static Material create(int bits) {
        if (bits <= 0 || bits % 8 != 0) throw new IllegalArgumentException("Whole bytes required");
        byte[] bytes = new byte[bits / 8];
        new SecureRandom().nextBytes(bytes);
        return new Material(Base64.getEncoder().encodeToString(bytes));
    }

    public static void properties(DynamicPropertyRegistry properties) {
        properties.add("auth.jwt.secret", KEYS::secret);
        properties.add("auth.jwt.issuer", () -> ISSUER);
        properties.add("auth.jwt.key-id", () -> KID);
    }

    public record Material(String secret) {
        public byte[] bytes() {
            return Base64.getDecoder().decode(secret);
        }

        @Override
        public String toString() {
            return "Material[redacted]";
        }
    }
}
