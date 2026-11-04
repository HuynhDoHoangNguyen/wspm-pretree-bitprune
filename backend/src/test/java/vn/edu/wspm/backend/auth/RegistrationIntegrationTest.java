package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.repositories.auth.RegistrationRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Real HTTP + complete application/JPA wiring + disposable MySQL. No dev DB access. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
@Import(RegistrationIntegrationTest.TimeConfiguration.class)
class RegistrationIntegrationTest {
    // Disposable fixtures only. These credentials cannot be used in the development app.
    static final String USERNAME = "customer.test";
    static final String PASSWORD = "Test1234"; // Exactly 8 characters; never a production default.
    static final String ORIGIN = "https://auth.example.test";
    static final Instant NOW = Instant.parse("2026-11-04T07:08:09.123456Z");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("auth_registration_test")
                    .withCommand(
                            "--log-bin-trust-function-creators=1") // Disposable test server only;
                                                                   // enables failure trigger.
                    .withUrlParam("connectionTimeZone", "UTC")
                    .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        AuthTestKeys.properties(properties);
        // Build only the actual V2 CREATE/ALTER statements, before Hibernate validate.
        // Never execute the migration's rename/copy/drop or connect to .env datasource.
        var setup =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String v2 =
                new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                        .getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int count = 0;
        while (definitions.find()) {
            setup.execute(definitions.group());
            count++;
        }
        assertEquals(47, count);
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("spring.sql.init.mode", () -> "never");
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("auth.allowed-origins", () -> ORIGIN);
        properties.add("auth.cookie-secure", () -> true);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean
        @Primary
        Clock testAuthClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @MockitoSpyBean RegistrationRepository repository;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void fixtures() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_customer_registration");
        for (String table :
                List.of("USER_ROLE", "CUSTOMER", "USER_SESSION", "USER_ACCOUNT", "ROLE")) {
            jdbc.update("DELETE FROM " + table); // Fixed allowlist, test container only.
        }
        jdbc.update(
                "INSERT INTO ROLE(role_id,code,name) VALUES"
                    + " ('customer-role','CUSTOMER','Customer'), ('admin-role','ADMIN','Admin')");
    }

    @Test
    void registrationPersistsCompleteV2CustomerAndHashWithoutSession() throws Exception {
        var response = register(valid(), csrf());
        assertEquals(201, response.statusCode(), response.body());
        JsonNode user = JSON.readTree(response.body()).get("data");
        String id = user.get("userId").asString();
        UUID.fromString(id);
        assertEquals(USERNAME, user.get("username").asString());
        assertEquals("ACTIVE", user.get("status").asString());
        assertFalse(user.get("emailVerified").asBoolean());
        assertEquals(List.of("CUSTOMER"), JSON.convertValue(user.get("roles"), List.class));
        var account = jdbc.queryForMap("SELECT * FROM USER_ACCOUNT WHERE user_id=?", id);
        String hash = (String) account.get("password_hash");
        assertTrue(hash.startsWith("{bcrypt}$2a$12$"), hash);
        assertTrue(passwords.matches(PASSWORD, hash));
        assertFalse(passwords.matches(PASSWORD + "x", hash));
        assertNull(account.get("email_verified_at"));
        assertNull(account.get("phone_verified_at"));
        assertNull(account.get("phone"));
        assertNull(account.get("last_login_at"));
        assertEquals(0L, ((Number) account.get("version")).longValue());
        assertEquals(NOW, ((LocalDateTime) account.get("created_at")).toInstant(ZoneOffset.UTC));
        assertEquals(NOW, ((LocalDateTime) account.get("updated_at")).toInstant(ZoneOffset.UTC));
        var customer = jdbc.queryForMap("SELECT * FROM CUSTOMER WHERE user_id=?", id);
        UUID.fromString((String) customer.get("customer_id"));
        assertTrue(((String) customer.get("anonymous_code")).startsWith("anon-"));
        assertEquals(NOW, ((LocalDateTime) customer.get("created_at")).toInstant(ZoneOffset.UTC));
        var assignment = jdbc.queryForMap("SELECT * FROM USER_ROLE WHERE user_id=?", id);
        assertEquals("customer-role", assignment.get("role_id"));
        assertNull(assignment.get("assigned_by"));
        assertEquals(
                NOW, ((LocalDateTime) assignment.get("assigned_at")).toInstant(ZoneOffset.UTC));
        assertEquals(0, rows("USER_SESSION"));
        assertFalse(response.body().contains(hash));
        assertFalse(response.body().contains(PASSWORD));
        assertTrue(
                response.headers().allValues("Set-Cookie").stream()
                        .noneMatch(s -> s.contains("wspm_refresh") || s.contains("JSESSIONID")));
        metadata(response);
    }

    @Test
    void canonicalizesIdentityButDoesNotTrimPasswordAndUsesDistinctSalts() throws Exception {
        var request = valid();
        request.put("username", "  CUSTOMER.TEST  ");
        request.put("email", "  CUSTOMER.TEST@EXAMPLE.TEST  ");
        request.put("displayName", "  Test customer  ");
        request.put("password", " 123456 ");
        assertEquals(201, register(request, csrf()).statusCode());
        var account =
                jdbc.queryForMap(
                        "SELECT username,email,display_name,password_hash FROM USER_ACCOUNT");
        assertEquals(USERNAME, account.get("username"));
        assertEquals("customer.test@example.test", account.get("email"));
        assertEquals("Test customer", account.get("display_name"));
        String first = (String) account.get("password_hash");
        assertTrue(passwords.matches(" 123456 ", first));
        assertFalse(passwords.matches("123456", first));
        request.put("username", "second.test");
        request.put("email", "second@example.test");
        assertEquals(201, register(request, csrf()).statusCode());
        assertNotEquals(
                first,
                jdbc.queryForObject(
                        "SELECT password_hash FROM USER_ACCOUNT WHERE username='second.test'",
                        String.class));
    }

    @Test
    void rejectsDuplicateUsernameAndEmailIncludingDatabaseCaseRules() throws Exception {
        var token = csrf();
        assertEquals(201, register(valid(), token).statusCode());
        for (var duplicate :
                List.of(
                        Map.of("username", "CUSTOMER.TEST", "email", "other@example.test"),
                        Map.of(
                                "username",
                                "different.test",
                                "email",
                                "CUSTOMER.TEST@EXAMPLE.TEST"))) {
            var request = valid();
            request.putAll(duplicate);
            var response = register(request, token);
            error(response, 409, "IDENTITY_CONFLICT");
        }
        assertEquals(1, rows("USER_ACCOUNT"));
        assertEquals(1, rows("CUSTOMER"));
        assertEquals(1, rows("USER_ROLE"));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsBeforeDatabaseWrites(String field, Object value) throws Exception {
        var request = valid();
        request.put(field, value);
        error(register(request, csrf()), 400, "VALIDATION_FAILED");
        assertEquals(0, rows("USER_ACCOUNT"));
        assertEquals(0, rows("CUSTOMER"));
        assertEquals(0, rows("USER_ROLE"));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("password", "1234567"),
                Arguments.of("password", "a".repeat(73)),
                Arguments.of("password", "\u00e9".repeat(37)),
                Arguments.of("password", "abc\0defgh"),
                Arguments.of("password", null),
                Arguments.of("password", 12345678),
                Arguments.of("username", "ab"),
                Arguments.of("username", "a".repeat(81)),
                Arguments.of("username", "\u00e9customer"),
                Arguments.of("username", "_customer"),
                Arguments.of("email", "not-an-email"),
                Arguments.of("email", "\u00e9@example.test"),
                Arguments.of("email", "a".repeat(245) + "@example.test"),
                Arguments.of("displayName", "  "),
                Arguments.of("displayName", "x".repeat(161)),
                Arguments.of("roles", List.of("ADMIN")),
                Arguments.of("role", "ADMIN"),
                Arguments.of("status", "ACTIVE"),
                Arguments.of("userId", "admin"),
                Arguments.of("passwordHash", "plaintext"));
    }

    @Test
    void supportsExactUtf8ByteBoundaryAndEightUnicodeCodePoints() throws Exception {
        var request = valid();
        request.put("password", "\u00e9".repeat(36));
        assertEquals(201, register(request, csrf()).statusCode());
        request.put("username", "unicode.test");
        request.put("email", "unicode@example.test");
        request.put("password", "\ud83d\ude00".repeat(8));
        request.put("displayName", "\ud83d\ude00".repeat(160));
        assertEquals(201, register(request, csrf()).statusCode());
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM USER_ACCOUNT WHERE username='unicode.test'",
                        String.class);
        assertTrue(passwords.matches("\ud83d\ude00".repeat(8), hash));
        assertEquals(
                "\ud83d\ude00".repeat(160),
                jdbc.queryForObject(
                        "SELECT display_name FROM USER_ACCOUNT WHERE username='unicode.test'",
                        String.class));
    }

    @Test
    void missingOrIncorrectlyCasedCustomerRoleFailsClosedWithoutProvisioning() throws Exception {
        jdbc.update("DELETE FROM ROLE WHERE role_id='customer-role'");
        error(register(valid(), csrf()), 503, "SERVICE_UNAVAILABLE");
        assertEquals(0, rows("USER_ACCOUNT"));
        jdbc.update(
                "INSERT INTO ROLE(role_id,code,name) VALUES('lower-role','customer','Wrong case')");
        error(register(valid(), csrf()), 503, "SERVICE_UNAVAILABLE");
        assertEquals(0, rows("USER_ACCOUNT"));
        assertEquals(0, rows("CUSTOMER"));
    }

    @Test
    void rollsBackAccountIfCustomerInsertFailsAndDoesNotMisclassifyOtherIntegrityErrors()
            throws Exception {
        jdbc.execute(
                "CREATE TRIGGER reject_customer_registration BEFORE INSERT ON CUSTOMER FOR EACH ROW"
                    + " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure"
                    + " uk_v2_user_email'");
        var response = register(valid(), csrf());
        error(response, 500, "INTERNAL_ERROR");
        assertFalse(response.body().contains("fixture failure"));
        assertEquals(0, rows("USER_ACCOUNT"));
        assertEquals(0, rows("CUSTOMER"));
        assertEquals(0, rows("USER_ROLE"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentIdentityRaceCommitsExactlyOneCompleteRegistration(boolean sameEmailOnly)
            throws Exception {
        CountDownLatch checked = new CountDownLatch(2);
        doAnswer(
                        call -> {
                            boolean exists = (boolean) call.callRealMethod();
                            checked.countDown();
                            assertTrue(checked.await(10, TimeUnit.SECONDS));
                            return exists;
                        })
                .when(repository)
                .identityExists(any(), any());
        var token = csrf();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> register(valid(), token));
            var peer = valid();
            if (sameEmailOnly) peer.put("username", "race.peer");
            var second = workers.submit(() -> register(peer, token));
            var responses =
                    List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(
                    List.of(201, 409),
                    responses.stream().map(HttpResponse::statusCode).sorted().toList());
            error(
                    responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow(),
                    409,
                    "IDENTITY_CONFLICT");
        }
        assertEquals(1, rows("USER_ACCOUNT"));
        assertEquals(1, rows("CUSTOMER"));
        assertEquals(1, rows("USER_ROLE"));
    }

    @Test
    void storageFailureIs503WithoutWritesOrLeakingDriverMessage() throws Exception {
        // Fault injection at the repository boundary; not a physical DB-outage test.
        doThrow(new DataAccessResourceFailureException("private storage failure marker"))
                .when(repository)
                .customerRole();
        var response = register(valid(), csrf());
        error(response, 503, "SERVICE_UNAVAILABLE");
        assertFalse(response.body().contains("private storage failure marker"));
        assertEquals(0, rows("USER_ACCOUNT"));
        assertEquals(0, rows("CUSTOMER"));
    }

    @Test
    void csrfAndOriginAreRequiredBeforeRegistrationAndCookieIsHostOnlySecureLax() throws Exception {
        var bootstrap = get("/api/v1/auth/csrf");
        assertEquals(200, bootstrap.statusCode());
        metadata(bootstrap);
        String cookie =
                bootstrap.headers().allValues("Set-Cookie").stream()
                        .filter(s -> s.startsWith("XSRF-TOKEN="))
                        .findFirst()
                        .orElseThrow();
        assertTrue(cookie.contains("Path=/"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertFalse(cookie.contains("Domain="));
        assertFalse(cookie.contains("HttpOnly"));
        var token =
                new Csrf(
                        cookie.split(";", 2)[0],
                        JSON.readTree(bootstrap.body()).get("data").get("token").asString());
        error(send(valid(), null, ORIGIN, true), 403, "CSRF_REJECTED");
        error(send(valid(), token, null, true), 403, "CSRF_REJECTED");
        error(send(valid(), token, "null", true), 403, "CSRF_REJECTED");
        error(send(valid(), token, "https://attacker.example.test", true), 403, "CSRF_REJECTED");
        error(send(valid(), token, ORIGIN, false), 403, "CSRF_REJECTED");
        error(
                send(valid(), new Csrf(token.cookie(), "incorrect"), ORIGIN, true),
                403,
                "CSRF_REJECTED");
        assertEquals(0, rows("USER_ACCOUNT"));
        assertEquals(201, register(valid(), token).statusCode());
    }

    @Test
    void malformedMissingAndNonObjectBodiesHaveSafeEnvelopes() throws Exception {
        var token = csrf();
        for (String body :
                List.of(
                        "{",
                        "",
                        "null",
                        "[]",
                        "{}",
                        "{} {}",
                        "{\"username\":\"one\",\"username\":\"two\"}")) {
            var response =
                    http.send(
                            base("/api/v1/auth/register")
                                    .header("Origin", ORIGIN)
                                    .header("Cookie", token.cookie())
                                    .header("X-XSRF-TOKEN", token.token())
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(400, response.statusCode(), response.body());
            metadata(response);
        }
        assertEquals(0, rows("USER_ACCOUNT"));
    }

    @Test
    void debugLogsDoNotContainPasswordHashIdentityOrCsrfToken(CapturedOutput output)
            throws Exception {
        var token = csrf();
        assertEquals(201, register(valid(), token).statusCode());
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM USER_ACCOUNT WHERE username=?",
                        String.class,
                        USERNAME);
        error(register(valid(), token), 409, "IDENTITY_CONFLICT");
        String logs = output.getAll();
        assertFalse(logs.contains(PASSWORD));
        assertFalse(logs.contains(hash));
        assertFalse(logs.contains(token.token()));
        assertFalse(logs.contains("customer.test@example.test"));
        assertFalse(logs.contains("Using generated security password"));
    }

    @Test
    void authAdvicePreservesWdmSecurityAndUnknownAuthErrorMetadata() throws Exception {
        error(get("/api/v1/auth/not-implemented"), 401, "AUTHENTICATION_REQUIRED");
        error(get("/api/v1/auth/register"), 405, "METHOD_NOT_ALLOWED");
        for (String path :
                List.of(
                        "/api/v1/admin/order-exports?unknown=bad",
                        "/api/v1/%61dmin/order-exports")) {
            error(get(path), 401, "AUTHENTICATION_REQUIRED");
        }
        error(get("/api/v1/admin;ignored/order-exports"), 400, "MALFORMED_REQUEST");
        assertEquals(200, get("/api/health").statusCode());
    }

    Map<String, Object> valid() {
        return new HashMap<>(
                Map.of(
                        "username",
                        USERNAME,
                        "email",
                        "customer.test@example.test",
                        "password",
                        PASSWORD,
                        "displayName",
                        "Test customer"));
    }

    int rows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    HttpResponse<String> get(String path) throws Exception {
        return http.send(base(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    Csrf csrf() throws Exception {
        var response = get("/api/v1/auth/csrf");
        assertEquals(200, response.statusCode(), response.body());
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(s -> s.startsWith("XSRF-TOKEN="))
                        .findFirst()
                        .orElseThrow()
                        .split(";", 2)[0];
        return new Csrf(cookie, JSON.readTree(response.body()).get("data").get("token").asString());
    }

    HttpResponse<String> register(Map<String, Object> body, Csrf token) throws Exception {
        return send(body, token, ORIGIN, true);
    }

    HttpResponse<String> send(Map<String, Object> body, Csrf token, String origin, boolean header)
            throws Exception {
        var request = base("/api/v1/auth/register").header("Content-Type", "application/json");
        if (origin != null) request.header("Origin", origin);
        if (token != null) {
            request.header("Cookie", token.cookie());
            if (header) request.header("X-XSRF-TOKEN", token.token());
        }
        return http.send(
                request.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    void metadata(HttpResponse<String> response) throws Exception {
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow();
        if ((response.statusCode() == 200 || response.statusCode() == 201)
                && response.headers()
                        .firstValue("Content-Type")
                        .orElse("")
                        .contains("application/json")) {
            JsonNode envelope = JSON.readTree(response.body());
            assertEquals(4, envelope.size());
            assertEquals("SUCCESS", envelope.get("code").asString());
            assertEquals("Request completed successfully.", envelope.get("message").asString());
            assertEquals(id, envelope.get("requestId").asString());
            assertNotNull(envelope.get("data"));
        }

        UUID.fromString(id);
        if (response.statusCode() >= 400) {
            JsonNode body = JSON.readTree(response.body());
            assertEquals(id, body.get("requestId").asString());
            assertEquals(response.statusCode(), body.get("status").asInt());
            assertTrue(body.get("details").isArray());
        }
    }

    void error(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).get("code").asString());
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                        .contains("application/json"));
        assertFalse(response.body().contains(PASSWORD));
        metadata(response);
    }

    record Csrf(String cookie, String token) {}
}
