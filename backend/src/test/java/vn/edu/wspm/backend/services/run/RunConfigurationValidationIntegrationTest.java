package vn.edu.wspm.backend.services.run;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.config.*;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real Auth/HTTP/MySQL/artifacts/resolver/gate; preserves the accepted preview contract. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RunConfigurationValidationIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_configuration_gate_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path root;
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String DATASETS = "/api/v1/admin/dataset-snapshots", PREVIEW = "/api/v1/admin/mining-runs/preview";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        var setup = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String v2 = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int count = 0; while (definitions.find()) { setup.execute(definitions.group()); count++; } assertEquals(47, count);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never"); p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER); p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> root.toString()); AuthTestKeys.properties(p);
    }
    @BeforeAll static void catalog(@Autowired JdbcTemplate jdbc) throws Exception {
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('preview-category','Synthetic','preview-category')");
        for (var item : JSON.readTree(fixture("round-trip-expected.json")).get("catalogProducts")) {
            String id = item.asString(); jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'preview-category',999)", id, id, id, id);
        }
    }
    static byte[] fixture(String name) throws Exception { return new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray(); }
    HttpRequest.Builder request(String path) { return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30)); }
    JsonNode envelope(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String id = response.headers().firstValue("X-Request-Id").orElseThrow(); UUID.fromString(id);
        var body = JSON.readTree(response.body()); assertEquals(id, body.get("requestId").asString());
        if (status < 300) { assertEquals("SUCCESS", body.get("code").asString()); return body.get("data"); }
        assertFalse(body.toString().contains(root.toString())); return body;
    }
    JsonNode auth(String operation, Map<String, String> data, int status) throws Exception {
        var csrf = http.send(request("/api/v1/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String token = envelope(csrf, 200).get("token").asString();
        String cookie = csrf.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        return envelope(http.send(request("/api/v1/auth/" + operation).header("Origin", AuthTestKeys.ISSUER)
                .header("X-XSRF-TOKEN", token).header("Cookie", cookie).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(data))).build(), HttpResponse.BodyHandlers.ofByteArray()), status);
    }
    HttpResponse<byte[]> preview(String token, String body) throws Exception {
        var builder = request(PREVIEW).header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    String body(String id, String algorithm, String mode, String number) {
        return "{\"snapshotId\":\"" + id + "\",\"algorithm\":\"" + algorithm + "\",\"minsupMode\":\"" + mode + "\",\"minsupValue\":" + number + "}";
    }
    JsonNode post(String path, String token) throws Exception {
        return envelope(http.send(request(path).header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }
    String upload(String token) throws Exception {
        String boundary = "preview-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"round-trip.csv\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return envelope(http.send(request(DATASETS).header("Authorization", "Bearer " + token).header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, fixture("round-trip.csv"), suffix))).build(), HttpResponse.BodyHandlers.ofByteArray()), 201).get("snapshotId").asString();
    }
    Map<String, String> fileHashes() throws Exception {
        var result = new TreeMap<String, String>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) result.put(root.relativize(file).toString(),
                    HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
        }
        return result;
    }


    HttpResponse<byte[]> validation(String token, String body) throws Exception {
        var builder = request("/api/v1/admin/mining-runs/configuration-validation").header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    void rejected422(HttpResponse<byte[]> response) throws Exception {
        var error = envelope(response, 422);
        assertEquals("UNSUPPORTED_MINING_CONFIGURATION", error.get("code").asString());
        assertEquals("minsupValue", error.at("/details/0/field").asString());
        assertEquals("ZERO_EFFECTIVE_THRESHOLD", error.at("/details/0/code").asString());
        assertEquals("Effective minimum support must be positive while R6 remains open.", error.at("/details/0/message").asString());
    }
    @Test void realConfigurationGatePreservesPreviewAndErrorOrderWithoutBusinessWrites() throws Exception {
        assertEquals("AUTHENTICATION_REQUIRED", envelope(validation(null, "{"), 401).get("code").asString());
        String password = "Configuration12345";
        String user = auth("register", Map.of("username", "configuration.admin", "password", password,
                "email", "configuration@example.test", "displayName", "Configuration"), 201).get("userId").asString();
        String customer = auth("login", Map.of("username", "configuration.admin", "password", password), 200).get("accessToken").asString();
        assertEquals("ACCESS_DENIED", envelope(validation(customer, "{"), 403).get("code").asString());
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", user);
        String admin = auth("login", Map.of("username", "configuration.admin", "password", password), 200).get("accessToken").asString();
        assertEquals("MALFORMED_REQUEST", envelope(validation(admin, "{"), 400).get("code").asString());
        String missing = UUID.randomUUID().toString();
        envelope(validation(admin, body(missing, "WSPM_PRETREE", "PERCENT", "0")), 404);
        String id = upload(admin), base = DATASETS + "/" + id;
        String zero = body(id, "WSPM_PRETREE", "PERCENT", "0");
        envelope(validation(admin, zero), 409);
        post(base + "/validation", admin); envelope(validation(admin, zero), 409);
        var converted = post(base + "/conversion", admin);
        var snapshotBefore = jdbc.queryForMap("SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id=?", id);
        var mappingBefore = jdbc.queryForList("SELECT * FROM ITEM_MAPPING ORDER BY item_id");
        var filesBefore = fileHashes();
        for (String algorithm : List.of("WSPM_PRETREE", "WSPM_BITPRUNE")) {
            for (String mode : List.of("PERCENT", "ABSOLUTE")) {
                for (String value : List.of("14", "0.25", "100", "0", "-0.0", "4.9e-324", "1e-999")) {
                    String input = body(id, algorithm, mode, value);
                    var previewData = envelope(preview(admin, input), 200);
                    assertEquals("NOT_EVALUATED", previewData.get("executionEligibility").asString());
                    var resolved = new MinimumSupportResolver().resolve(new MinimumSupport(Double.parseDouble(value), MinimumSupportMode.valueOf(mode)), 3);
                    if (resolved.effectiveThreshold() == 0.0) {
                        rejected422(validation(admin, input));
                    } else {
                        var response = validation(admin, input);
                        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
                        var data = envelope(response, 200);
                        assertEquals(id, data.get("snapshotId").asString()); assertEquals(algorithm, data.get("algorithm").asString());
                        assertEquals("3", data.get("sequenceCount").asString()); assertEquals("SUPPORTED", data.get("configurationStatus").asString());
                        assertFalse(data.has("runId")); assertFalse(data.has("executionEligibility"));
                        assertEquals(converted.get("artifactVersion"), data.get("artifactVersion"));
                        assertEquals(converted.at("/sequence/sha256"), data.get("sequenceSha256"));
                        assertEquals(converted.at("/mapping/sha256"), data.get("mappingSha256"));
                        assertEquals(previewData.get("minimumSupport"), data.get("minimumSupport"));
                        assertEquals(mode, data.at("/minimumSupport/mode").asString());
                        assertEquals(Double.doubleToRawLongBits(resolved.configuredValue()), Double.doubleToRawLongBits(data.at("/minimumSupport/configuredValue").asDouble()));
                        assertEquals(Double.doubleToRawLongBits(resolved.effectiveThreshold()), Double.doubleToRawLongBits(data.at("/minimumSupport/effectiveThreshold").asDouble()));
                        assertEquals(data, envelope(validation(admin, input), 200));
                    }
                }
            }
        }
        envelope(validation(admin, body(id, "WSPM_BITPRUNE", "ABSOLUTE", "1.7976931348623157e308")), 200);
        envelope(validation(admin, body(id, "WSPM_BITPRUNE", "PERCENT", "101")), 400);
        envelope(validation(admin, body(id, "WSPM_BITPRUNE", "ABSOLUTE", "-1")), 400);
        assertEquals(snapshotBefore, jdbc.queryForMap("SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id=?", id));
        assertEquals(mappingBefore, jdbc.queryForList("SELECT * FROM ITEM_MAPPING ORDER BY item_id"));
        assertEquals(filesBefore, fileHashes()); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Integer.class));
        for (String column : List.of("raw_file_path", "sequence_file_path", "mapping_file_path")) {
            Path file = root.resolve((String) snapshotBefore.get(column)); byte[] original = Files.readAllBytes(file);
            try {
                Files.write(file, "tampered".getBytes(StandardCharsets.UTF_8));
                envelope(validation(admin, zero), 503);
            } finally { Files.write(file, original); }
        }
        try {
            jdbc.update("UPDATE DATASET_SNAPSHOT SET sequence_count=sequence_count+1 WHERE snapshot_id=?", id);
            envelope(validation(admin, zero), 409);
        } finally { jdbc.update("UPDATE DATASET_SNAPSHOT SET sequence_count=? WHERE snapshot_id=?", snapshotBefore.get("sequence_count"), id); }
        String report = (String) snapshotBefore.get("conversion_report_json");
        for (String state : List.of("RUNNING", "FAILED")) {
            try {
                jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=JSON_SET(conversion_report_json,'$.conversionStatus',?) WHERE snapshot_id=?", state, id);
                envelope(validation(admin, zero), 409);
            } finally { jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=? WHERE snapshot_id=?", report, id); }
        }
        assertEquals(snapshotBefore, jdbc.queryForMap("SELECT * FROM DATASET_SNAPSHOT WHERE snapshot_id=?", id));
        assertEquals(mappingBefore, jdbc.queryForList("SELECT * FROM ITEM_MAPPING ORDER BY item_id"));
        assertEquals(filesBefore, fileHashes()); assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Integer.class));
        rejected422(validation(admin, zero));
    }
}
