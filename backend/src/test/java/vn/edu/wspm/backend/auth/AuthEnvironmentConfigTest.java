package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.security.jwt.JwtTokenService;
import vn.edu.wspm.backend.web.auth.AuthCookies;
import vn.edu.wspm.backend.web.auth.AuthRequestFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Real ConfigData import and Auth wiring; no server, database or development secrets. */
class AuthEnvironmentConfigTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");
    @TempDir Path directory;

    @Test
    void dotenvUppercasePropertiesConfigureJwtAndLocalHttpCookies() throws Exception {
        runner(dotenv(false, false))
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            var service = context.getBean(JwtTokenService.class);
                            var claims =
                                    service.verify(
                                            service.issue("fixture-user", "fixture-session", NOW));
                            assertEquals(
                                    "https://dotenv.example.test", claims.getIssuer().toString());
                            assertEquals("fixture-user", claims.getSubject());
                            assertEquals(NOW.plusSeconds(900), claims.getExpiresAt());
                            var response = new MockHttpServletResponse();
                            context.getBean(AuthCookies.class)
                                    .setRefresh(response, "fixture-only", NOW.plusSeconds(900));
                            assertFalse(response.getHeader("Set-Cookie").contains("Secure"));
                            assertTrue(response.getHeader("Set-Cookie").contains("HttpOnly"));
                        });
    }

    @Test
    void dotenvSecureSettingOverridesLocalProfileDefault() throws Exception {
        runner(dotenv(true, false))
                .withPropertyValues("spring.profiles.active=local")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    "https://dotenv.example.test",
                                    context.getEnvironment().getProperty("auth.allowed-origins"));
                            var response = new MockHttpServletResponse();
                            context.getBean(AuthCookies.class)
                                    .setRefresh(response, "fixture-only", NOW.plusSeconds(900));
                            assertTrue(response.getHeader("Set-Cookie").contains("Secure"));
                        });
    }

    @Test
    void emptyDotenvSecretDoesNotFallBackToDevelopmentSecretOrGenerateOne() throws Exception {
        runner(dotenv(false, true)).run(context -> assertNotNull(context.getStartupFailure()));
    }

    private ApplicationContextRunner runner(Path dotenv) {
        return new ApplicationContextRunner()
                // The fixture, not an operator's shell variables, owns this test's configuration.
                .withInitializer(
                        context ->
                                context.getEnvironment()
                                        .getPropertySources()
                                        .remove(
                                                StandardEnvironment
                                                        .SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AuthOnly.class)
                .withPropertyValues(
                        "spring.config.location=classpath:/application.yaml",
                        "spring.config.import=" + dotenv.toUri() + "[.properties]");
    }

    private Path dotenv(boolean secure, boolean missingKey) throws Exception {
        Path file = directory.resolve(".env");
        Files.writeString(
                file,
                "AUTH_JWT_SECRET="
                        + (missingKey ? "" : AuthTestKeys.KEYS.secret())
                        + "\n"
                        + "AUTH_JWT_ISSUER=https://dotenv.example.test\n"
                        + "AUTH_JWT_KEY_ID=dotenv-fixture\n"
                        + "AUTH_COOKIE_SECURE="
                        + secure
                        + "\nAUTH_ALLOWED_ORIGINS="
                        + (secure ? "https://dotenv.example.test" : "http://localhost:3000")
                        + "\n");
        return file;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({JwtTokenService.class, AuthCookies.class, AuthRequestFilter.class})
    static class AuthOnly {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        ObjectMapper mapper() {
            return JsonMapper.builder().build();
        }
    }
}
