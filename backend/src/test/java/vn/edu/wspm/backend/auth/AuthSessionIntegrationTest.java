package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.repositories.auth.AuthSessionRepository;
import vn.edu.wspm.backend.security.jwt.JwtTokenService;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Full HTTP/JPA lifecycle on a disposable V2 MySQL schema, with a controlled clock. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(AuthSessionIntegrationTest.TimeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthSessionIntegrationTest {
    static final String USER = "session.customer",
            PASSWORD = "Auth1234",
            ORIGIN = AuthTestKeys.ISSUER;
    static final Instant NOW = Instant.parse("2026-11-04T07:08:09Z");
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("auth_session_test")
                    .withUrlParam("connectionTimeZone", "UTC")
                    .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) throws Exception {
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
        vn.edu.wspm.backend.testsupport.ExecutionSchema.apply(setup);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> ORIGIN);
        p.add("auth.cookie-secure", () -> true);
        AuthTestKeys.properties(p);
    }

    static class MutableClock extends Clock {
        final AtomicReference<Instant> time = new AtomicReference<>(NOW);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return time.get();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfig {
        @Bean
        @Primary
        MutableClock controlledClock() {
            return new MutableClock();
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @MockitoSpyBean AuthSessionRepository repository;
    @MockitoSpyBean JwtTokenService jwt;
    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void customer() throws Exception {
        clock.time.set(NOW);
        for (String table :
                List.of("USER_ROLE", "CUSTOMER", "USER_SESSION", "USER_ACCOUNT", "ROLE"))
            jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO ROLE(role_id,code,name)"
                    + " VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        var response =
                post(
                        "register",
                        JSON.writeValueAsString(
                                Map.of(
                                        "username",
                                        USER,
                                        "password",
                                        PASSWORD,
                                        "email",
                                        "session@example.test",
                                        "displayName",
                                        "Session customer")),
                        null);
        assertEquals(201, response.statusCode(), response.body());
    }

    @Test
    void loginRefreshLogoutAndIdentityUseRealSessionHashAndFixedExpiry() throws Exception {
        var login = login(USER, PASSWORD);
        assertEquals(200, login.statusCode(), login.body());
        metadata(login);
        JsonNode first = JSON.readTree(login.body()).get("data");
        String access = first.get("accessToken").asString(), cookie = refreshCookie(login);
        assertEquals(900, first.get("expiresIn").asInt());
        assertEquals("Bearer", first.get("tokenType").asString());
        assertEquals(NOW.plusSeconds(900).toString(), first.get("expiresAt").asString());
        var claims = jwt.verify(access);
        String sid = claims.getClaimAsString("sid");
        var session = jdbc.queryForMap("SELECT * FROM USER_SESSION");
        String hash = (String) session.get("refresh_token_hash");
        assertEquals(sid, session.get("session_id"));
        assertTrue(hash.matches("[a-f0-9]{64}"));
        assertEquals(
                hash,
                vn.edu.wspm.backend.services.auth.AuthSessionService.refreshHash(raw(cookie)));
        assertFalse(login.body().contains(raw(cookie)));
        assertNull(session.get("revoked_at"));
        assertNull(session.get("device_info"));
        assertNull(session.get("ip_hash"));
        assertEquals(
                NOW,
                jdbc.queryForObject("SELECT last_login_at FROM USER_ACCOUNT", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC));
        Instant expiry =
                jdbc.queryForObject("SELECT expires_at FROM USER_SESSION", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC);
        assertEquals(NOW.plusSeconds(604800), expiry);
        assertCookie(login, "Max-Age=604800");
        assertTrue(
                login.headers().allValues("Set-Cookie").stream()
                        .anyMatch(
                                c ->
                                        c.startsWith("XSRF-TOKEN=;")
                                                && java.net.HttpCookie.parse(c)
                                                                .getFirst()
                                                                .getMaxAge()
                                                        == 0),
                login.headers().allValues("Set-Cookie").stream()
                        .map(c -> c.substring(c.indexOf(';')))
                        .toList()
                        .toString());
        assertEquals(200, me(access).statusCode());
        clock.time.set(NOW.plusSeconds(120));
        var refreshed = post("refresh", "", cookie);
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        String next = refreshCookie(refreshed);
        assertNotEquals(cookie, next);
        assertCookie(refreshed, "Max-Age=604680");
        assertEquals(sid, jwt.verify(token(refreshed)).getClaimAsString("sid"));
        assertEquals(
                expiry,
                jdbc.queryForObject("SELECT expires_at FROM USER_SESSION", LocalDateTime.class)
                        .toInstant(ZoneOffset.UTC));
        assertNotEquals(
                hash,
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class));
        error(post("refresh", "", cookie), 401);
        assertEquals(200, me(access).statusCode());
        assertEquals(204, post("logout", "", next).statusCode());
        assertNotNull(
                jdbc.queryForObject("SELECT revoked_at FROM USER_SESSION", LocalDateTime.class));
        error(me(access), 401);
        error(me(token(refreshed)), 401);
        error(post("refresh", "", next), 401);
        assertEquals(204, post("logout", "", next).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user", "PENDING", "LOCKED", "DISABLED"})
    void loginRejectsCredentialsAndIneligibleAccountsWithSame401(String reason) throws Exception {
        if (List.of("PENDING", "LOCKED", "DISABLED").contains(reason))
            jdbc.update("UPDATE USER_ACCOUNT SET status=?", reason);
        var response =
                login(
                        reason.equals("unknown-user") ? "unknown.user" : USER,
                        reason.equals("wrong-password") ? "Wrong123" : PASSWORD);
        error(response, 401);
        assertEquals(
                "AUTHENTICATION_REQUIRED", JSON.readTree(response.body()).get("code").asString());
        assertEquals(0, countSessions());
        assertTrue(
                response.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
    }

    @Test
    void expiryHasZeroSkewAndSessionCannotRefreshAtSevenDayBoundary() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        clock.time.set(NOW.plusSeconds(899));
        assertEquals(200, me(token).statusCode());
        clock.time.set(NOW.plusSeconds(900));
        error(me(token), 401);
        var refreshed = post("refresh", "", cookie);
        assertEquals(200, refreshed.statusCode());
        clock.time.set(NOW.plusSeconds(604800));
        var expired = post("refresh", "", refreshCookie(refreshed));
        error(expired, 401);
        assertCookie(expired, "Max-Age=0");
    }

    @Test
    void accountSessionAndCurrentRolesAreReadFromDatabase() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        jdbc.update(
                "INSERT INTO USER_ROLE(user_id,role_id) SELECT user_id,'admin-role' FROM"
                    + " USER_ACCOUNT");
        assertTrue(me(token).body().contains("ADMIN"));
        jdbc.update("DELETE FROM USER_ROLE WHERE role_id='admin-role'");
        assertFalse(me(token).body().contains("ADMIN"));
        jdbc.update("UPDATE USER_ACCOUNT SET status='LOCKED'");
        error(me(token), 401);
        error(post("refresh", "", cookie), 401);
        assertEquals(204, post("logout", "", cookie).statusCode());
    }

    @Test
    void revokedSessionWrongOwnerAndRefreshAsBearerCannotAuthenticate() throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        error(me(raw(cookie)), 401);
        error(me(jwt.issue("wrong-user", jwt.verify(token).getClaimAsString("sid"), NOW)), 401);
        jdbc.update("UPDATE USER_SESSION SET revoked_at=UTC_TIMESTAMP(6)");
        error(me(token), 401);
        error(post("refresh", "", cookie), 401);
    }

    @Test
    void missingMalformedDuplicateAndQueryCredentialsAreRejectedBeforeIdentity() throws Exception {
        error(get("/api/v1/auth/me", null), 401);
        error(get("/api/v1/auth/me?access_token=untrusted", null), 401);
        error(me("invalid"), 401);
        var login = login(USER, PASSWORD);
        String access = token(login);
        var duplicate =
                base("me")
                        .header("Authorization", "Bearer " + access)
                        .header("Authorization", "Bearer " + access)
                        .GET()
                        .build();
        error(http.send(duplicate, HttpResponse.BodyHandlers.ofString()), 401);
        error(get("/api/v1/%61uth/me", null), 401);
        error(get("/api/v1/auth;matrix/me", null), 400);
    }

    @Test
    void missingMalformedDuplicateRefreshCookiesAndStaleLogoutAreSafe() throws Exception {
        error(post("refresh", "", null), 401);
        error(post("refresh", "", "wspm_refresh=invalid"), 401);
        var first = login(USER, PASSWORD);
        String original = refreshCookie(first);
        var rotated = post("refresh", "", original);
        String current = refreshCookie(rotated);
        assertEquals(204, post("logout", "", original).statusCode());
        assertEquals(200, me(token(rotated)).statusCode());
        error(post("refresh", "", current + "; " + current), 401);
        assertEquals(204, post("logout", "", null).statusCode());
        assertEquals(1, countSessions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"refresh", "logout"})
    void emptyBodyAndCsrfOriginRemainRequired(String operation) throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        var csrf = csrf();
        error(post(operation, "{}", cookie), 400);
        var missing =
                base(operation)
                        .header("Origin", ORIGIN)
                        .header("Cookie", cookie)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        error(http.send(missing, HttpResponse.BodyHandlers.ofString()), 403);
        var wrong =
                base(operation)
                        .header("Origin", "https://attacker.example.test")
                        .header("Cookie", csrf.cookie() + "; " + cookie)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();
        error(http.send(wrong, HttpResponse.BodyHandlers.ofString()), 403);
        assertEquals(200, me(token(login)).statusCode());
    }

    @Test
    void concurrentRefreshHasExactlyOneWinner() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        barrier();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> post("refresh", "", cookie));
            var b = workers.submit(() -> post("refresh", "", cookie));
            var replies = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(
                    List.of(200, 401),
                    replies.stream().map(HttpResponse::statusCode).sorted().toList());
            var winner =
                    replies.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow();
            assertEquals(200, me(token(winner)).statusCode());
        }
        assertEquals(1, countSessions());
    }

    @Test
    void concurrentRefreshAndLogoutHonorCurrentHashWithoutDeadlock() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        barrier();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var r = workers.submit(() -> post("refresh", "", cookie));
            var l = workers.submit(() -> post("logout", "", cookie));
            var refreshed = r.get(30, TimeUnit.SECONDS);
            assertEquals(204, l.get(30, TimeUnit.SECONDS).statusCode());
            assertTrue(List.of(200, 401).contains(refreshed.statusCode()), refreshed.body());
            if (refreshed.statusCode() == 200)
                assertEquals(200, me(token(refreshed)).statusCode()); // Logout used a stale hash.
            else error(me(token(login)), 401); // Logout won and revoked the original session.
        }
    }

    @Test
    void signingFailureRollsBackLoginAndRefreshWithoutIssuingCookies() throws Exception {
        doThrow(new IllegalStateException("private signing detail"))
                .when(jwt)
                .issue(anyString(), anyString(), any());
        var failed = login(USER, PASSWORD);
        error(failed, 500);
        assertEquals(0, countSessions());
        assertTrue(
                failed.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
        reset(jwt);
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login);
        String before =
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class);
        doThrow(new IllegalStateException("private signing detail"))
                .when(jwt)
                .issue(anyString(), anyString(), any());
        error(post("refresh", "", cookie), 500);
        assertEquals(
                before,
                jdbc.queryForObject("SELECT refresh_token_hash FROM USER_SESSION", String.class));
    }

    @Test
    void storageFailuresFailClosedAndLogoutDoesNotClaimSuccess() throws Exception {
        var login = login(USER, PASSWORD);
        String cookie = refreshCookie(login), token = token(login);
        doThrow(new DataAccessResourceFailureException("private DB detail"))
                .when(repository)
                .account(anyString(), anyBoolean());
        error(me(token), 503);
        error(post("refresh", "", cookie), 503);
        var logout = post("logout", "", cookie);
        error(logout, 503);
        assertTrue(
                logout.headers().allValues("Set-Cookie").stream()
                        .noneMatch(c -> c.startsWith("wspm_refresh=")));
    }

    @Test
    void credentialsAndTokensAreAbsentFromDebugLogs(CapturedOutput output) throws Exception {
        var login = login(USER, PASSWORD);
        String token = token(login), cookie = refreshCookie(login);
        assertEquals(200, me(token).statusCode());
        var refresh = post("refresh", "", cookie);
        assertEquals(200, refresh.statusCode());
        assertEquals(204, post("logout", "", refreshCookie(refresh)).statusCode());
        for (String sensitive :
                List.of(
                        PASSWORD,
                        token,
                        raw(cookie),
                        token(refresh),
                        raw(refreshCookie(refresh)),
                        "session@example.test",
                        jdbc.queryForObject(
                                "SELECT refresh_token_hash FROM USER_SESSION", String.class))) {
            assertFalse(output.getAll().contains(sensitive));
        }
    }

    private void barrier() {
        CountDownLatch owners = new CountDownLatch(2);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            owners.countDown();
                            assertTrue(owners.await(10, TimeUnit.SECONDS));
                            return result;
                        })
                .when(repository)
                .ownerOfRefresh(anyString());
    }

    private int countSessions() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM USER_SESSION", Integer.class);
    }

    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/auth/" + path));
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        return post(
                "login",
                JSON.writeValueAsString(Map.of("username", username, "password", password)),
                null);
    }

    private HttpResponse<String> me(String token) throws Exception {
        return get("/api/v1/auth/me", token);
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Csrf csrf() throws Exception {
        var response = get("/api/v1/auth/csrf", null);
        assertEquals(200, response.statusCode(), response.body());
        return new Csrf(
                response.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("XSRF-TOKEN="))
                        .findFirst()
                        .orElseThrow()
                        .split(";", 2)[0],
                JSON.readTree(response.body()).get("data").get("token").asString());
    }

    private HttpResponse<String> post(String operation, String body, String refresh)
            throws Exception {
        var csrf = csrf();
        var request =
                base(operation)
                        .header("Origin", ORIGIN)
                        .header("X-XSRF-TOKEN", csrf.token())
                        .header("Cookie", csrf.cookie() + (refresh == null ? "" : "; " + refresh));
        if (!body.isEmpty()) request.header("Content-Type", "application/json");
        return http.send(
                request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String token(HttpResponse<String> response) throws Exception {
        return JSON.readTree(response.body()).get("data").get("accessToken").asString();
    }

    private String refreshCookie(HttpResponse<String> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("wspm_refresh="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
    }

    private String raw(String cookie) {
        return cookie.substring("wspm_refresh=".length());
    }

    private void assertCookie(HttpResponse<String> response, String attribute) {
        String cookie =
                response.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("wspm_refresh="))
                        .findFirst()
                        .orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
        assertTrue(cookie.contains(attribute), cookie);
        assertFalse(cookie.contains("Domain="));
    }

    private void error(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        metadata(response);
        if (status == 401
                && JSON.readTree(response.body()).get("path").asString().endsWith("/me")) {
            assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
        }
    }

    private void metadata(HttpResponse<String> response) throws Exception {
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

        if (response.statusCode() >= 400) {
            JsonNode error = JSON.readTree(response.body());
            assertEquals(id, error.get("requestId").asString());
            assertEquals(response.statusCode(), error.get("status").asInt());
        }
    }

    record Csrf(String cookie, String token) {}
}
