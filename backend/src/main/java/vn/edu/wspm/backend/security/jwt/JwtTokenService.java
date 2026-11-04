package vn.edu.wspm.backend.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

/** Explicit Base64 HMAC secret from configuration; no generated/default signing secret. */
@Component
public class JwtTokenService {
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final String issuer;
    private final String kid;
    private final Clock clock;

    public JwtTokenService(
            @Value("${auth.jwt.secret:}") String secret,
            @Value("${auth.jwt.issuer:}") String issuer,
            @Value("${auth.jwt.key-id:}") String kid,
            Clock clock) {
        this.issuer = issuer;
        this.kid = kid;
        this.clock = clock;
        try {
            if (secret.isBlank() || issuer.isBlank() || kid.isBlank()) {
                throw new IllegalArgumentException();
            }
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            if (keyBytes.length < 32) throw new IllegalArgumentException();
            var key = new SecretKeySpec(keyBytes, "HmacSHA256");
            var jwk = new OctetSequenceKey.Builder(keyBytes).keyID(kid).build();
            encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            decoder =
                    NimbusJwtDecoder.withSecretKey(key)
                            .macAlgorithm(MacAlgorithm.HS256)
                            .validateType(false)
                            .build();
            decoder.setClaimSetConverter(
                    source -> {
                        for (String required : List.of("iss", "sub", "sid", "jti")) {
                            if (!(source.get(required) instanceof String))
                                throw new BadJwtException("Invalid required claim.");
                        }
                        return MappedJwtClaimSetConverter.withDefaults(Map.of()).convert(source);
                    });
            decoder.setJwtValidator(
                    jwt ->
                            valid(jwt)
                                    ? OAuth2TokenValidatorResult.success()
                                    : OAuth2TokenValidatorResult.failure(
                                            new OAuth2Error("invalid_token")));
        } catch (Exception invalid) {
            // Never include the configured secret or underlying parser diagnostics.
            throw new IllegalStateException(
                    "An explicit Base64 JWT secret of at least 32 bytes, issuer and key ID are"
                        + " required.");
        }
    }

    private boolean valid(Jwt jwt) {
        try {
            Instant now = clock.instant(),
                    iat = jwt.getIssuedAt(),
                    nbf = jwt.getNotBefore(),
                    exp = jwt.getExpiresAt();
            UUID.fromString(jwt.getId());
            return jwt.getHeaders().keySet().equals(Set.of("alg", "typ", "kid"))
                    && "HS256".equals(jwt.getHeaders().get("alg"))
                    && "at+jwt".equals(jwt.getHeaders().get("typ"))
                    && kid.equals(jwt.getHeaders().get("kid"))
                    && issuer.equals(jwt.getClaimAsString("iss"))
                    && List.of("wspm-api").equals(jwt.getAudience())
                    && identifier(jwt.getSubject())
                    && identifier(jwt.getClaimAsString("sid"))
                    && iat != null
                    && nbf != null
                    && exp != null
                    && nbf.equals(iat)
                    && !iat.isAfter(now)
                    && now.isBefore(exp)
                    && exp.equals(iat.plusSeconds(900));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean identifier(String value) {
        return value != null && !value.isBlank() && value.length() <= 40;
    }

    public String issue(String userId, String sessionId, Instant now) {
        var claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(List.of("wspm-api"))
                        .subject(userId)
                        .claim("sid", sessionId)
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .notBefore(now)
                        .expiresAt(now.plusSeconds(900))
                        .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).type("at+jwt").keyId(kid).build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public Jwt verify(String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException | IllegalArgumentException rejected) {
            throw unauthenticated();
        }
    }

    public static ApplicationException unauthenticated() {
        return new ApplicationException(
                ErrorCode.AUTHENTICATION_REQUIRED, "Authentication is required.");
    }
}
