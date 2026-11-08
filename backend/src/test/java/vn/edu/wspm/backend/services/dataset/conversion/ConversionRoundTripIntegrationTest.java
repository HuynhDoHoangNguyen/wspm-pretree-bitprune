package vn.edu.wspm.backend.services.dataset.conversion;

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
import vn.edu.wspm.io.SpmfSequenceParser;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Real Auth/HTTP/JDBC/files/core parser, with a hand-authored product-layout oracle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=4096B", "spring.servlet.multipart.max-request-size=8192B"})
@Testcontainers
class ConversionRoundTripIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_round_trip_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path root;
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BASE = "/api/v1/admin/dataset-snapshots";
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        var setup = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String v2 = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int count = 0;
        while (definitions.find()) { setup.execute(definitions.group()); count++; }
        assertEquals(47, count);
        vn.edu.wspm.backend.testsupport.ExecutionSchema.apply(setup);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
        p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> root.toString());
        AuthTestKeys.properties(p);
    }
    static byte[] fixture(String name) throws Exception {
        return new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray();
    }
    @BeforeAll static void catalog(@Autowired JdbcTemplate jdbc) throws Exception {
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('rt-category','Synthetic','rt-category')");
        var expected = JSON.readTree(fixture("round-trip-expected.json"));
        for (var product : expected.get("catalogProducts")) {
            String id = product.asString();
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'rt-category',999)", id, id, id, id);
        }
        // Deliberately non-contiguous and non-product-sorted: never assume IDs begin at 1.
        jdbc.update("INSERT INTO ITEM_MAPPING(product_id,item_id) VALUES('rt-unused',17),('rt-p2',42)");
    }
    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
    }
    JsonNode envelope(HttpResponse<byte[]> response, int status) throws Exception {
        assertEquals(status, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        String requestId = response.headers().firstValue("X-Request-Id").orElseThrow(); UUID.fromString(requestId);
        var body = JSON.readTree(response.body());
        assertEquals("SUCCESS", body.get("code").asString());
        assertEquals(requestId, body.get("requestId").asString());
        return body.get("data");
    }
    JsonNode auth(String operation, Map<String, String> body, int status) throws Exception {
        var csrf = http.send(request("/api/v1/auth/csrf").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        String token = envelope(csrf, 200).get("token").asString();
        String cookie = csrf.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow().split(";", 2)[0];
        return envelope(http.send(request("/api/v1/auth/" + operation).header("Origin", AuthTestKeys.ISSUER)
                .header("X-XSRF-TOKEN", token).header("Cookie", cookie).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(), HttpResponse.BodyHandlers.ofByteArray()), status);
    }
    JsonNode get(String path, String token) throws Exception {
        return envelope(http.send(request(path).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }
    JsonNode post(String path, String token) throws Exception {
        return envelope(http.send(request(path).header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray()), 200);
    }
    String upload(byte[] bytes, String name, String token) throws Exception {
        String boundary = "rt-" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name
                + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return envelope(http.send(request(BASE).header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, bytes, suffix))).build(),
                HttpResponse.BodyHandlers.ofByteArray()), 201).get("snapshotId").asString();
    }
    byte[] download(String path, String token, JsonNode artifact, String filename, String mime) throws Exception {
        var response = http.send(request(path).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
        UUID.fromString(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertEquals(mime, response.headers().firstValue("Content-Type").orElseThrow().split(";")[0]);
        assertEquals("attachment; filename=\"" + filename + "\"", response.headers().firstValue("Content-Disposition").orElseThrow());
        assertEquals(Integer.toString(response.body().length), response.headers().firstValue("Content-Length").orElseThrow());
        assertEquals(artifact.get("sizeBytes").asString(), Integer.toString(response.body().length));
        assertEquals(artifact.get("sha256").asString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body())));
        assertEquals(path, artifact.get("downloadUrl").asString());
        return response.body();
    }
    static void assertStructure(byte[] bytes, JsonNode expected, Map<String, Integer> mapping) throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        var sequences = expected.get("sequences");
        assertEquals(sequences.size(), db.size());
        for (int sid = 0; sid < sequences.size(); sid++) {
            var record = db.records().get(sid);
            assertEquals(sid + 1, record.sid());
            var itemsets = sequences.get(sid).get("itemsets");
            assertEquals(itemsets.size(), record.sequence().size());
            for (int position = 0; position < itemsets.size(); position++) {
                var items = new ArrayList<Integer>();
                for (var product : itemsets.get(position)) items.add(Objects.requireNonNull(mapping.get(product.asString())));
                Collections.sort(items); // Itemset membership is unordered; wire tokens have numeric order.
                assertEquals(items, record.sequence().itemsets().get(position).items().stream().map(item -> item.id()).toList());
            }
        }
    }
    @Test void downloadedRichAndShuffledSnapshotsMatchIndependentOracleThroughCoreParser() throws Exception {
        var expected = JSON.readTree(fixture("round-trip-expected.json"));
        String password = "RoundTrip12345";
        String user = auth("register", Map.of("username", "roundtrip.admin", "password", password,
                "email", "roundtrip@example.test", "displayName", "Round trip"), 201).get("userId").asString();
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES(?,'admin-role')", user);
        String token = auth("login", Map.of("username", "roundtrip.admin", "password", password), 200).get("accessToken").asString();
        byte[] firstSequence = null, firstMapping = null;
        for (String name : List.of("round-trip.csv", "round-trip-shuffled.csv")) {
            byte[] raw = fixture(name);
            String id = upload(raw, name, token), path = BASE + "/" + id;
            var before = get(path, token);
            var report = post(path + "/validation", token);
            assertEquals("VALID", report.get("validationStatus").asString());
            assertEquals("READY", report.get("cleaningOutcome").asString());
            assertTrue(report.get("countsComplete").asBoolean());
            expected.get("counts").properties().forEach(entry -> assertEquals(entry.getValue(), report.get("counts").get(entry.getKey()), entry.getKey()));
            var converted = post(path + "/conversion", token);
            assertEquals("SUCCEEDED", converted.get("conversionStatus").asString());
            assertEquals("COMPUTED", converted.get("statisticsStatus").asString());
            assertEquals(expected.get("statistics"), converted.get("statistics"));
            byte[] mappingBytes = download(path + "/artifacts/mapping", token, converted.get("mapping"), "snapshot-" + id + "-mapping.json", "application/json");
            byte[] sequence = download(path + "/artifacts/sequence", token, converted.get("sequence"), "snapshot-" + id + ".spmf", "text/plain");
            var dictionary = JSON.readTree(mappingBytes);
            assertEquals("CONVERSION_ARTIFACT_V1", dictionary.get("formatVersion").asString());
            assertEquals("GLOBAL_PRODUCT_ITEM_INT_V1", dictionary.get("policyVersion").asString());
            var mapping = new HashMap<String, Integer>(); var itemIds = new HashSet<Integer>();
            int previous = 0;
            for (var entry : dictionary.get("items")) {
                String product = entry.get("productId").asString(); int item = entry.get("itemId").asInt();
                assertTrue(item > previous); previous = item;
                assertNull(mapping.put(product, item)); assertTrue(itemIds.add(item));
                assertEquals(item, jdbc.queryForObject("SELECT item_id FROM ITEM_MAPPING WHERE product_id=?", Integer.class, product));
            }
            assertEquals(Set.of("rt-p1", "rt-p2", "rt-p3"), mapping.keySet()); assertEquals(42, mapping.get("rt-p2"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ITEM_MAPPING WHERE product_id='rt-p4'", Integer.class));
            assertStructure(sequence, expected, mapping);
            String persisted = jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
            var internal = JSON.readTree(persisted); String attempt = internal.get("conversion").get("attemptId").asString(); UUID.fromString(attempt);
            var directory = root.resolve(id).resolve("conversions").resolve(attempt);
            assertArrayEquals(sequence, Files.readAllBytes(directory.resolve("sequences.spmf")));
            assertArrayEquals(mappingBytes, Files.readAllBytes(directory.resolve("mapping.json")));
            assertArrayEquals(raw, Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
            assertEquals(report, get(path + "/validation?page=0&size=20", token));
            var after = get(path, token);
            for (String field : List.of("sourceType", "sourceName", "rawSha256", "rawSizeBytes", "createdAt", "fromTime", "toTime", "provenance")) assertEquals(before.get(field), after.get(field), field);
            assertEquals(converted, get(path + "/conversion", token)); assertEquals(converted, post(path + "/conversion", token));
            assertEquals(persisted, jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id));
            try (var attempts = Files.list(directory.getParent())) { assertEquals(List.of(attempt), attempts.map(p -> p.getFileName().toString()).toList()); }
            if (firstSequence == null) { firstSequence = sequence; firstMapping = mappingBytes; }
            else { assertArrayEquals(firstSequence, sequence); assertArrayEquals(firstMapping, mappingBytes); }
        }
    }
}
