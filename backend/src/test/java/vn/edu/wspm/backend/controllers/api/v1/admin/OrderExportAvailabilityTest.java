package vn.edu.wspm.backend.controllers.api.v1.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Real provider wiring on disposable MySQL: invalid credentials deny before MVC. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never",
            "spring.jpa.hibernate.ddl-auto=validate"
        })
@Testcontainers
class OrderExportAvailabilityTest {
    @TempDir static Path snapshotRoot;
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_availability_test")
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");

    @DynamicPropertySource
    static void isolatedApplication(DynamicPropertyRegistry properties) throws Exception {
        var setup = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String schema = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(schema);
        int count = 0;
        while (definitions.find()) { setup.execute(definitions.group()); count++; }
        assertEquals(47, count);
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("wdm.snapshot-storage.root", () -> snapshotRoot.toString());
        properties.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
        properties.add("auth.cookie-secure", () -> true);
        AuthTestKeys.properties(properties);
    }

    @LocalServerPort int port;

    @Test
    void defaultApplicationDeniesNamespaceBeforeValidationIncludingEncodedAndMatrixPaths()
            throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path :
                    new String[] {
                        "/api/v1/admin/order-exports?unknown=bad",
                        "/api/v1/%61dmin/order-exports",
                        "/api/v1/admin;ignored/order-exports",
                        "/api/v1/admin/dataset-snapshots/nonexistent"
                    }) {
                var response =
                        client.send(
                                HttpRequest.newBuilder(
                                                URI.create("http://localhost:" + port + path))
                                        .header("Authorization", "Bearer unverified-test-value")
                                        .GET()
                                        .build(),
                                HttpResponse.BodyHandlers.ofString());
                boolean matrix = path.contains(";");
                assertEquals(matrix ? 400 : 401, response.statusCode(), path);
                var body = JsonMapper.builder().build().readTree(response.body());
                assertEquals(
                        matrix ? "MALFORMED_REQUEST" : "AUTHENTICATION_REQUIRED",
                        body.get("code").asString());
                if (!matrix)
                    assertEquals(
                            "Bearer",
                            response.headers().firstValue("WWW-Authenticate").orElseThrow());
                assertEquals(
                        response.headers().firstValue("X-Request-Id").orElseThrow(),
                        body.get("requestId").asString());
                assertEquals(
                        "no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                assertFalse(response.body().contains("unverified-test-value"));
            }
            var health =
                    client.send(
                            HttpRequest.newBuilder(
                                            URI.create("http://localhost:" + port + "/api/health"))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
        }
    }
}
