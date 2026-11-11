package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

class JwtTokenServiceTest {
    static final Instant NOW = Instant.parse("2026-11-04T07:08:09Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    final JwtTokenService service =
            new JwtTokenService(
                    AuthTestKeys.KEYS.secret(), AuthTestKeys.ISSUER, AuthTestKeys.KID, CLOCK);

    @Test
    void roundTripHasOnlyRequiredIdentityClaimsAnd900SecondLifetime() {
        var jwt = service.verify(service.issue("user", "session", NOW));
        assertEquals("user", jwt.getSubject());
        assertEquals("session", jwt.getClaimAsString("sid"));
        assertEquals(NOW.plusSeconds(900), jwt.getExpiresAt());
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW, jwt.getNotBefore());
        assertEquals(
                java.util.Set.of("iss", "aud", "sub", "sid", "jti", "iat", "nbf", "exp"),
                jwt.getClaims().keySet());
    }

    static Stream<String> invalidClaims() {
        return Stream.of(
                "iss",
                "aud",
                "sub",
                "sid",
                "jti",
                "iat",
                "nbf",
                "exp",
                "expired",
                "future",
                "wrongIssuer",
                "wrongAudience",
                "wrongSidType");
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void rejectsMissingWrongOrExpiredClaims(String change) throws Exception {
        var claims = new HashMap<>(claims().toJSONObject());
        if (List.of("iss", "aud", "sub", "sid", "jti", "iat", "nbf", "exp").contains(change))
            claims.remove(change);
        if (change.equals("expired")) {
            claims.put("iat", NOW.minusSeconds(900).getEpochSecond());
            claims.put("nbf", NOW.minusSeconds(900).getEpochSecond());
            claims.put("exp", NOW.getEpochSecond());
        }
        if (change.equals("future")) {
            claims.put("iat", NOW.plusSeconds(1).getEpochSecond());
            claims.put("nbf", NOW.plusSeconds(1).getEpochSecond());
            claims.put("exp", NOW.plusSeconds(901).getEpochSecond());
        }
        if (change.equals("wrongIssuer")) claims.put("iss", "other");
        if (change.equals("wrongAudience")) claims.put("aud", List.of("other"));
        if (change.equals("wrongSidType")) claims.put("sid", List.of("session"));
        rejected(sign(JWTClaimsSet.parse(claims), AuthTestKeys.KID, "at+jwt", AuthTestKeys.KEYS));
    }

    @Test
    void rejectsWrongSignatureKidTypeAlgorithmAndTampering() throws Exception {
        rejected(sign(claims(), AuthTestKeys.KID, "at+jwt", AuthTestKeys.create(256)));
        rejected(sign(claims(), "unknown-key", "at+jwt", AuthTestKeys.KEYS));
        rejected(sign(claims(), AuthTestKeys.KID, "JWT", AuthTestKeys.KEYS));
        String token = service.issue("user", "session", NOW);
        var parts = token.split("\\.");
        rejected(
                parts[0]
                        + "."
                        + parts[1]
                        + "."
                        + (parts[2].charAt(0) == 'a' ? "b" : "a")
                        + parts[2].substring(1));
        rejected(new com.nimbusds.jwt.PlainJWT(claims()).serialize());
        var wrongAlgorithm =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS512)
                                .keyID(AuthTestKeys.KID)
                                .type(new JOSEObjectType("at+jwt"))
                                .build(),
                        claims());
        wrongAlgorithm.sign(new MACSigner(AuthTestKeys.create(512).bytes()));
        rejected(wrongAlgorithm.serialize());
    }

    @Test
    void invalidSecretConfigurationFailsStartupInsteadOfGeneratingFallback() {
        new ApplicationContextRunner()
                .withUserConfiguration(OnlyJwt.class)
                .withPropertyValues("auth.jwt.secret=", "auth.jwt.issuer=", "auth.jwt.key-id=")
                .run(context -> assertNotNull(context.getStartupFailure()));
        for (String invalid : List.of("", "not valid base64!", AuthTestKeys.create(128).secret())) {
            var error =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    new JwtTokenService(
                                            invalid, AuthTestKeys.ISSUER, AuthTestKeys.KID, CLOCK));
            assertEquals(
                    "An explicit Base64 JWT secret of at least 32 bytes, issuer and key ID are"
                        + " required.",
                    error.getMessage());
        }
    }

    @Test
    void rejectsLegacyRsaTokensAndDoesNotLeakSecretDiagnostics() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var legacy =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .keyID(AuthTestKeys.KID)
                                .type(new JOSEObjectType("at+jwt"))
                                .build(),
                        claims());
        legacy.sign(
                new com.nimbusds.jose.crypto.RSASSASigner(
                        generator.generateKeyPair().getPrivate()));
        rejected(legacy.serialize());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JwtTokenService.class)
    static class OnlyJwt {
        @Bean
        Clock clock() {
            return CLOCK;
        }
    }

    static JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder()
                .issuer(AuthTestKeys.ISSUER)
                .audience("wspm-api")
                .subject("user")
                .claim("sid", "session")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(NOW))
                .notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900)))
                .build();
    }

    static String sign(JWTClaimsSet claims, String kid, String typ, AuthTestKeys.Material keys)
            throws Exception {
        var token =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256)
                                .keyID(kid)
                                .type(new JOSEObjectType(typ))
                                .build(),
                        claims);
        token.sign(new MACSigner(keys.bytes()));
        return token.serialize();
    }

    void rejected(String token) {
        assertEquals(
                401,
                assertThrows(ApplicationException.class, () -> service.verify(token))
                        .status()
                        .value());
    }
}
