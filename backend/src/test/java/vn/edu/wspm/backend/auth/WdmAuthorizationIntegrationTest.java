package vn.edu.wspm.backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.system.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import vn.edu.wspm.backend.repositories.auth.AuthSessionRepository;
import vn.edu.wspm.backend.repositories.export.OrderExportRepository;
import vn.edu.wspm.backend.services.dataset.CsvUploadService;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import vn.edu.wspm.backend.web.wdm.WdmAdminAuthorizer;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** Actual shared Auth + JWT + WDM filter/controller/service/JDBC on disposable MySQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=1024B",
                "spring.servlet.multipart.max-request-size=2048B"})
@Testcontainers
@Import(WdmAuthorizationIntegrationTest.TimeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class WdmAuthorizationIntegrationTest {
    static final String ADMIN = "wdm.admin", CUSTOMER = "wdm.customer", PASSWORD = "Wdm12345";
    static final String ORIGIN = AuthTestKeys.ISSUER;
    static final Instant NOW = Instant.parse("2026-11-04T07:08:09Z");
    static final String EXPORT =
            "/api/v1/admin/order-exports?fromTime=2026-11-01T00:00:00Z&toTime=2026-12-01T00:00:00Z";
    static final String HEADER =
            "order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\r\n";
    static final JsonMapper JSON = JsonMapper.builder().build();
    @TempDir static Path snapshotRoot;

    @Container
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withDatabaseName("auth_wdm_test")
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
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> ORIGIN);
        p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> snapshotRoot.toString());
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
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        AtomicInteger probeCalls() {
            return new AtomicInteger();
        }

        @Bean
        RouterFunction<ServerResponse> privateProbe(AtomicInteger probeCalls) {
            return RouterFunctions.route()
                    .GET(
                            "/api/v1/future-private-probe",
                            request -> {
                                probeCalls.incrementAndGet();
                                return ServerResponse.ok().body("PRIVATE-FIXTURE-PROBE");
                            })
                    .GET(
                            "/api/v1/auth/register",
                            request -> {
                                probeCalls.incrementAndGet();
                                return ServerResponse.ok().body("UNDECLARED-AUTH-METHOD-PROBE");
                            })
                    .build();
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired ApplicationContext context;
    @Autowired AtomicInteger probeCalls;
    @MockitoSpyBean OrderExportRepository exports;
    @MockitoSpyBean AuthSessionRepository accounts;
    @MockitoSpyBean CsvUploadService uploads;
    @MockitoSpyBean DatasetSnapshotRepository snapshots;
    @MockitoSpyBean RawSnapshotStorage artifacts;
    final HttpClient http = HttpClient.newHttpClient();
    String adminId, customerId, adminToken, customerToken, adminRefresh;

    @BeforeEach
    void fixtures() throws Exception {
        clock.time.set(NOW);
        probeCalls.set(0);
        for (String table :
                List.of(
                        "ORDER_ITEM",
                        "DATASET_SNAPSHOT",
                        "ITEM_MAPPING",
                        "ORDERS",
                        "USER_ROLE",
                        "CUSTOMER",
                        "USER_SESSION",
                        "USER_ACCOUNT",
                        "ROLE",
                        "PRODUCT",
                        "CATEGORY")) jdbc.update("DELETE FROM " + table);
        jdbc.update(
                "INSERT INTO ROLE(role_id,code,name)"
                    + " VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        adminId = register(ADMIN);
        customerId = register(CUSTOMER);
        // Execute the review-only statement ONLY in this disposable fixture DB.
        String grant =
                Files.readString(
                        Path.of(
                                System.getProperty("basedir"),
                                "scripts",
                                "auth",
                                "grant-admin-role.sql"));
        assertEquals(1, jdbc.update(grant, null, adminId));
        var admin = login(ADMIN);
        adminToken = token(admin);
        adminRefresh = refreshCookie(admin);
        customerToken = token(login(CUSTOMER));
        jdbc.update(
                "UPDATE CUSTOMER SET anonymous_code='shopper-code' WHERE user_id=?", customerId);
        jdbc.update(
                "INSERT INTO CATEGORY(category_id,name,slug) VALUES('category','Test"
                        + " category','test-category')");
        jdbc.update(
                "INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price)"
                    + " VALUES('product','CURRENT-SKU','Current name','product','category',999)");
        String customer =
                jdbc.queryForObject(
                        "SELECT customer_id FROM CUSTOMER WHERE user_id=?",
                        String.class,
                        customerId);
        jdbc.update(
                "INSERT INTO"
                    + " ORDERS(order_id,order_number,customer_id,ordered_at,completed_at,status,source,currency_code)"
                    + " VALUES('historical-order','history-001',?,'2026-11-02"
                    + " 03:04:05.123456','2026-11-02 04:00:00','COMPLETED','IMPORT','VND')",
                customer);
        jdbc.update(
                "INSERT INTO"
                    + " ORDER_ITEM(order_item_id,order_id,product_id,quantity,list_unit_price,allocated_discount_amount,line_total,sku_snapshot,product_name_snapshot)"
                    + " VALUES(1,'historical-order','product',3,100,0,300,'HISTORY-SKU','Historical"
                    + " item')");
        clearInvocations(exports);
        clearInvocations(uploads);
        clearInvocations(AopTestUtils.<DatasetSnapshotRepository>getUltimateTargetObject(snapshots), artifacts);
    }

    @Test
    void uploadUsesActualSharedAuthBeforeTransportChecksAndOnlyAdminCreatesSnapshot() throws Exception {
        String path = "/api/v1/admin/dataset-snapshots";
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class);
        String body = "--wdm\r\nContent-Disposition: form-data; name=\"file\"; filename=\"orders.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\nnot-yet-validated\r\n--wdm--\r\n";
        for (String credential : new String[]{null, customerToken, adminToken}) {
            var request = base(path).header("Content-Type", "multipart/form-data; boundary=wdm");
            if (credential != null) request.header("Authorization", "Bearer " + credential);
            var response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (adminToken.equals(credential)) {
                assertEquals(201, response.statusCode(), response.body());
                metadata(response);
                assertEquals("PENDING", JSON.readTree(response.body()).get("data").get("validationStatus").asString());
            } else {
                error(response, credential == null ? 401 : 403,
                        credential == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED");
            }
            if (!adminToken.equals(credential)) verifyNoInteractions(uploads);
        }
        verify(uploads).upload(any(), eq(false));
        clearInvocations(uploads);
        for (String credential : new String[]{null, customerToken, adminToken}) {
            for (boolean oversized : new boolean[]{true, false}) {
                var request = base(path).header("Content-Type", oversized
                        ? "multipart/form-data; boundary=wdm" : "multipart/form-data");
                if (credential != null) request.header("Authorization", "Bearer " + credential);
                var response = http.send(request.POST(HttpRequest.BodyPublishers.ofString(
                        oversized ? body.replace("not-yet-validated", "x".repeat(4096)) : "bad")).build(),
                        HttpResponse.BodyHandlers.ofString());
                error(response, credential == null ? 401 : credential.equals(customerToken) ? 403
                                : oversized ? 413 : 400,
                        credential == null ? "AUTHENTICATION_REQUIRED"
                                : credential.equals(customerToken) ? "ACCESS_DENIED"
                                : oversized ? "PAYLOAD_TOO_LARGE" : "MALFORMED_REQUEST");
            }
        }
        verifyNoInteractions(uploads);
        assertEquals(before + 1, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
        verifyNoInteractions(exports);
    }

    @Test
    void snapshotFixturesPersistAndReloadExactRawBytesAndHonestInitialMetadata() throws Exception {
        clock.time.set(NOW.plusNanos(123456789));
        for (String name : List.of("valid.csv", "empty.csv", "business-invalid.csv")) {
            byte[] bytes = new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray();
            assertAcquisition(upload(bytes, name), bytes, name);
        }
        byte[] original = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'a', '\r', '\n', 'b', '\n', (byte) 0xff};
        assertAcquisition(upload(original, "original.CSV"), original, "original.CSV");
        assertEquals(4L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
    }

    @Test
    void uploadOfActualDatabaseExportStillHasCsvProvenanceAndNoTrustedPeriod() throws Exception {
        var exported = get(EXPORT, adminToken);
        assertEquals(200, exported.statusCode());
        byte[] bytes = exported.body().getBytes(StandardCharsets.UTF_8);
        assertAcquisition(upload(bytes, "exported.csv"), bytes, "exported.csv");
    }

    @Test
    void concurrentUploadsCreateIndependentPersistedArtifacts() throws Exception {
        var ids = new HashSet<String>();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var responses = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < 8; i++) {
                byte value = (byte) i;
                responses.add(executor.submit(() -> upload(new byte[]{value}, "same.csv")));
            }
            for (int i = 0; i < responses.size(); i++) {
                var response = responses.get(i).get();
                String id = assertAcquisition(response, new byte[]{(byte) i}, "same.csv");
                assertTrue(ids.add(id));
            }
        }
        assertEquals(8L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
    }

    @Test
    void databaseRollbackAfterActualInsertCleansOnlyItsNewArtifact() throws Exception {
        byte[] bytes = new byte[]{7};
        String prior = assertAcquisition(upload(bytes, "prior.csv"), bytes, "prior.csv");
        var retained = snapshots.findById(prior).orElseThrow();
        long directories;
        try (var entries = Files.list(snapshotRoot)) { directories = entries.count(); }
        doAnswer(call -> {
            call.callRealMethod(); // Actual INSERT/flush in the real transaction before injected failure.
            throw new IllegalStateException("private-db-marker");
        }).when(AopTestUtils.<DatasetSnapshotRepository>getUltimateTargetObject(snapshots)).insert(any());
        var response = upload(new byte[]{8}, "failed.csv");
        error(response, 503, "SERVICE_UNAVAILABLE");
        assertFalse(response.body().contains("private-db-marker"));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
        try (var entries = Files.list(snapshotRoot)) { assertEquals(directories, entries.count()); }
        assertArrayEquals(bytes, Files.readAllBytes(snapshotRoot.resolve(retained.getRawFilePath())));
    }

    private String assertAcquisition(HttpResponse<String> response, byte[] bytes, String name) throws Exception {
        assertEquals(201, response.statusCode(), response.body());
        metadata(response);
        var data = JSON.readTree(response.body()).get("data");
        String id = data.get("snapshotId").asString();
        java.util.UUID.fromString(id);
        var persisted = snapshots.findById(id).orElseThrow();
        var reloaded = snapshots.findById(id).orElseThrow();
        assertNotSame(persisted, reloaded); // Separate repository transactions / persistence contexts.
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertEquals(sha256, persisted.getRawSha256());
        assertEquals(sha256, reloaded.getRawSha256());
        assertEquals(sha256, data.get("rawSha256").asString());
        assertEquals(Long.toString(bytes.length), data.get("rawSizeBytes").asString());
        assertEquals(id + "/raw.csv", reloaded.getRawFilePath());
        Path path = snapshotRoot.resolve(reloaded.getRawFilePath()).normalize();
        assertTrue(path.startsWith(snapshotRoot));
        assertArrayEquals(bytes, Files.readAllBytes(path));
        assertEquals("CSV", reloaded.getSourceType());
        assertEquals("CSV", data.get("sourceType").asString());
        assertEquals(name, reloaded.getSourceName());
        assertEquals(name, data.get("sourceName").asString());
        assertEquals("PENDING", reloaded.getValidationStatus());
        assertEquals("PENDING", data.get("validationStatus").asString());
        assertEquals(0L, reloaded.getSequenceCount());
        assertTrue(data.get("sequenceCount").isNull());
        assertNull(reloaded.getFromTime());
        assertNull(reloaded.getToTime());
        assertTrue(data.get("fromTime").isNull());
        assertTrue(data.get("toTime").isNull());
        assertNull(reloaded.getSequenceFilePath());
        assertNull(reloaded.getSequenceSha256());
        assertNull(reloaded.getMappingFilePath());
        assertNull(reloaded.getMappingSha256());
        assertNull(reloaded.getSingleItemsetSequenceCount());
        assertNull(reloaded.getMultiItemsetSequenceCount());
        assertNull(reloaded.getItemsetCount());
        assertNull(reloaded.getDistinctItemCount());
        assertNull(reloaded.getTotalItemOccurrences());
        assertNull(reloaded.getMinSequenceLength());
        assertNull(reloaded.getMaxSequenceLength());
        assertNull(reloaded.getAverageSequenceLength());
        assertEquals(clock.instant().truncatedTo(ChronoUnit.MICROS), reloaded.getCreatedAt());
        assertEquals(reloaded.getCreatedAt(), Instant.parse(data.get("createdAt").asString()));
        var report = reloaded.getConversionReportJson();
        assertEquals("wdm-order-csv-v1", report.get("contractVersion"));
        assertEquals("NOT_STARTED", report.get("conversionStatus"));
        assertEquals("NOT_COMPUTED", report.get("statisticsStatus"));
        assertEquals("NOT_STARTED", data.get("conversionStatus").asString());
        assertEquals("NOT_COMPUTED", data.get("statisticsStatus").asString());
        var provenance = (Map<?, ?>) report.get("provenance");
        assertEquals("CSV_UPLOAD", provenance.get("acquisition"));
        assertNull(provenance.get("sourceTimeZone"));
        assertEquals("ordered_at", provenance.get("timeField"));
        assertEquals("COMPLETED_CURRENT_AT_ACQUISITION", provenance.get("statusPolicy"));
        assertEquals(Long.toString(bytes.length), provenance.get("rawSizeBytes"));
        assertNull(data.get("rawFilePath"));
        assertFalse(response.body().contains(snapshotRoot.toString()));
        return id;
    }

    private HttpResponse<String> upload(byte[] bytes, String filename) throws Exception {
        String boundary = "wdm-" + java.util.UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\nContent-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return http.send(base("/api/v1/admin/dataset-snapshots")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(prefix, bytes, suffix))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void detailAndRawDownloadReadPersistedSnapshotWithoutMutatingAcquisition() throws Exception {
        byte[] original = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, 'a', '\r', '\n', 'b', '\n', (byte) 0xff};
        String id = assertAcquisition(upload(original, "original.csv"), original, "original.csv");
        String before = jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
        var response = snapshotGet("/api/v1/admin/dataset-snapshots/" + id, adminToken);
        assertEquals(200, response.statusCode(), response.body()); metadata(response);
        var data = JSON.readTree(response.body()).get("data");
        assertEquals(id, data.get("snapshotId").asString());
        assertEquals("AVAILABLE", data.get("rawAvailability").asString());
        assertEquals("PENDING", data.get("validationStatus").asString());
        assertEquals("NOT_COMPUTED", data.get("statisticsStatus").asString());
        assertTrue(data.get("sequenceCount").isNull());
        assertTrue(data.get("provenance").get("sourceTimeZone").isNull());
        assertEquals("CSV_UPLOAD", data.get("provenance").get("acquisition").asString());
        assertFalse(response.body().contains("rawFilePath"));
        assertFalse(response.body().contains("conversionReportJson"));
        assertFalse(response.body().contains(snapshotRoot.toString()));
        var raw = http.send(base("/api/v1/admin/dataset-snapshots/" + id + "/artifacts/raw")
                .header("Authorization", "Bearer " + adminToken).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, raw.statusCode()); assertArrayEquals(original, raw.body());
        assertEquals(Long.toString(original.length), raw.headers().firstValue("Content-Length").orElseThrow());
        assertEquals("no-store", raw.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(raw.headers().firstValue("Content-Disposition").orElseThrow().contains("snapshot-" + id + "-raw.csv"));
        assertEquals(before, jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id));
        assertAcquisition(upload(original, "second.csv"), original, "second.csv");
        var old = snapshots.findById(id).orElseThrow();
        assertArrayEquals(original, Files.readAllBytes(snapshotRoot.resolve(old.getRawFilePath())));
        assertEquals(data.get("rawSha256").asString(), old.getRawSha256());
    }

    @Test
    void readAuthorizationAndInvalidIdsPrecedeRepositoryOrArtifactAccess() throws Exception {
        for (String credential : new String[]{null, customerToken}) {
            for (String suffix : new String[]{"bad-id", "bad-id/artifacts/raw"}) {
                error(snapshotGet("/api/v1/admin/dataset-snapshots/" + suffix, credential),
                        credential == null ? 401 : 403, credential == null ? "AUTHENTICATION_REQUIRED" : "ACCESS_DENIED");
            }
        }
        for (String suffix : new String[]{"bad-id", "bad-id/artifacts/raw"}) {
            error(snapshotGet("/api/v1/admin/dataset-snapshots/" + suffix, adminToken), 400, "VALIDATION_FAILED");
        }
        verifyNoInteractions(AopTestUtils.<DatasetSnapshotRepository>getUltimateTargetObject(snapshots), artifacts);
        error(snapshotGet("/api/v1/admin/dataset-snapshots/11111111-2222-4333-8444-555555555555", adminToken), 404, "RESOURCE_NOT_FOUND");
        verifyNoInteractions(artifacts);
    }

    @Test
    void missingTamperedAndUnsafeArtifactsDenyDownloadsButPreserveSnapshotMetadata() throws Exception {
        byte[] original = {1, 2, 3};
        String id = assertAcquisition(upload(original, "original.csv"), original, "original.csv");
        var entity = snapshots.findById(id).orElseThrow();
        Path file = snapshotRoot.resolve(entity.getRawFilePath());
        String path = "/api/v1/admin/dataset-snapshots/" + id;
        Files.write(file, new byte[]{3, 2, 1}); // Mutate only the artifact owned by this disposable fixture.
        error(snapshotGet(path + "/artifacts/raw", adminToken), 503, "SERVICE_UNAVAILABLE");
        assertEquals(entity.getRawSha256(), snapshots.findById(id).orElseThrow().getRawSha256());
        Files.delete(file);
        var detail = snapshotGet(path, adminToken);
        assertEquals(200, detail.statusCode());
        assertEquals("MISSING", JSON.readTree(detail.body()).get("data").get("rawAvailability").asString());
        error(snapshotGet(path + "/artifacts/raw", adminToken), 404, "RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE DATASET_SNAPSHOT SET raw_file_path='../private-marker.csv' WHERE snapshot_id=?", id);
        detail = snapshotGet(path, adminToken);
        assertEquals("UNAVAILABLE", JSON.readTree(detail.body()).get("data").get("rawAvailability").asString());
        assertFalse(detail.body().contains("private-marker"));
        error(snapshotGet(path + "/artifacts/raw", adminToken), 503, "SERVICE_UNAVAILABLE");
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
    }

    @Test
    void actualAdminConversionGetAndDownloadsPreserveBytesAndIdempotentPublication() throws Exception {
        byte[] csv = get(EXPORT, adminToken).body().getBytes(StandardCharsets.UTF_8);
        var acquired = upload(csv, "conversion.csv");
        assertEquals(201, acquired.statusCode(), acquired.body());
        String id = JSON.readTree(acquired.body()).get("data").get("snapshotId").asString();
        String prefix = "/api/v1/admin/dataset-snapshots/" + id;
        var pending = snapshotGet(prefix + "/conversion", adminToken); metadata(pending);
        assertEquals("NOT_STARTED", JSON.readTree(pending.body()).get("data").get("conversionStatus").asString());
        error(snapshotGet(prefix + "/artifacts/sequence", adminToken), 409, "INVALID_RESOURCE_STATE");
        assertEquals(200, snapshotPost(prefix + "/validation", adminToken).statusCode());
        var converted = snapshotPost(prefix + "/conversion", adminToken);
        assertEquals(200, converted.statusCode(), converted.body()); metadata(converted);
        var result = JSON.readTree(converted.body()).get("data");
        assertEquals("SUCCEEDED", result.get("conversionStatus").asString());
        assertEquals("COMPUTED", result.get("statisticsStatus").asString());
        assertEquals("1", result.get("statistics").get("sequenceCount").asString());
        assertEquals("1", result.get("statistics").get("totalItemOccurrences").asString());
        assertEquals(prefix + "/artifacts/sequence", result.get("sequence").get("downloadUrl").asString());
        for (String privateField : List.of("attemptId", "validationSha256", "reference", "conversion_report_json", "/conversions/")) {
            assertFalse(converted.body().contains(privateField));
        }
        String before = jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
        var status = snapshotGet(prefix + "/conversion", adminToken);
        assertEquals(200, status.statusCode()); metadata(status);
        assertEquals(result, JSON.readTree(status.body()).get("data"));
        byte[] sequence = null;
        for (String kind : List.of("sequence", "mapping")) {
            var response = http.send(base(prefix + "/artifacts/" + kind).header("Authorization", "Bearer " + adminToken).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals(response.body().length, Integer.parseInt(response.headers().firstValue("Content-Length").orElseThrow()));
            assertTrue(response.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));
            java.util.UUID.fromString(response.headers().firstValue("X-Request-Id").orElseThrow());
            assertEquals(result.get(kind).get("sha256").asString(), vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.sha256(response.body()));
            if (kind.equals("sequence")) sequence = response.body();
            else {
                var mapping = JSON.readTree(response.body());
                assertEquals("CONVERSION_ARTIFACT_V1", mapping.get("formatVersion").asString());
                assertEquals("product", mapping.get("items").get(0).get("productId").asString());
            }
        }
        assertEquals("1 -1 -2\n", new String(sequence, StandardCharsets.UTF_8));
        var db = new vn.edu.wspm.io.SpmfSequenceParser().parse(new java.io.StringReader(new String(sequence, StandardCharsets.UTF_8)));
        assertEquals(1, db.size()); assertEquals(1, db.records().getFirst().sid());
        assertEquals(List.of(new vn.edu.wspm.model.Item(1)), db.records().getFirst().sequence().itemsets().getFirst().items());
        var repeated = snapshotPost(prefix + "/conversion", adminToken);
        assertEquals(200, repeated.statusCode()); assertEquals(result, JSON.readTree(repeated.body()).get("data"));
        assertEquals(before, jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id));
    }

    @Test
    void conversionRoutesUseRealSharedAuthorizationAndTamperFailsWithoutMutation() throws Exception {
        for (String suffix : List.of("/conversion", "/artifacts/sequence", "/artifacts/mapping")) {
            String bad = "/api/v1/admin/dataset-snapshots/bad" + suffix + "?force=true";
            error(snapshotGet(bad, null), 401, "AUTHENTICATION_REQUIRED");
            error(snapshotGet(bad, customerToken), 403, "ACCESS_DENIED");
        }
        error(snapshotPost("/api/v1/admin/dataset-snapshots/bad/conversion", null), 401, "AUTHENTICATION_REQUIRED");
        error(snapshotPost("/api/v1/admin/dataset-snapshots/bad/conversion", customerToken), 403, "ACCESS_DENIED");
        error(snapshotGet("/api/v1/admin/dataset-snapshots/bad/conversion", adminToken), 400, "VALIDATION_FAILED");
        error(snapshotGet("/api/v1/admin/dataset-snapshots/00000000-0000-4000-8000-000000000000/conversion", adminToken), 404, "RESOURCE_NOT_FOUND");
        String id = JSON.readTree(upload(get(EXPORT, adminToken).body().getBytes(StandardCharsets.UTF_8), "tamper-conversion.csv").body()).get("data").get("snapshotId").asString();
        String prefix = "/api/v1/admin/dataset-snapshots/" + id;
        assertEquals(200, snapshotPost(prefix + "/validation", adminToken).statusCode());
        assertEquals(200, snapshotPost(prefix + "/conversion", adminToken).statusCode());
        String before = jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
        String reference = jdbc.queryForObject("SELECT mapping_file_path FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
        Path path = snapshotRoot.resolve(reference);
        byte[] bytes = Files.readAllBytes(path); bytes[0] ^= 1; Files.write(path, bytes);
        for (String suffix : List.of("/conversion", "/artifacts/sequence", "/artifacts/mapping")) {
            error(snapshotGet(prefix + suffix, adminToken), 503, "SERVICE_UNAVAILABLE");
        }
        error(snapshotPost(prefix + "/conversion", adminToken), 503, "SERVICE_UNAVAILABLE");
        assertEquals(before, jdbc.queryForObject("SELECT CAST(conversion_report_json AS CHAR) FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id));
        assertEquals(204, post("logout", "", adminRefresh).statusCode());
        error(snapshotGet(prefix + "/artifacts/sequence", adminToken), 401, "AUTHENTICATION_REQUIRED");
    }

    private HttpResponse<String> snapshotPost(String path, String credential) throws Exception {
        var request = base(path).header("Accept", "application/json");
        if (credential != null) request.header("Authorization", "Bearer " + credential);
        return http.send(request.POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> snapshotGet(String path, String credential) throws Exception {
        var request = base(path).header("Accept", "*/*");
        if (credential != null) request.header("Authorization", "Bearer " + credential);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void eligibleAdminDownloadsExactHistoricalCsvThroughUniqueRealProvider() throws Exception {
        assertEquals(1, context.getBeansOfType(WdmAdminAuthorizer.class).size());
        var response = get(EXPORT, adminToken);
        assertEquals(200, response.statusCode(), response.body());
        metadata(response);
        assertEquals(
                "\uFEFF" + HEADER
                        + "historical-order,1,shopper-code,2026-11-02T03:04:05.123456Z,COMPLETED,product,HISTORY-SKU,Historical"
                        + " item,3,100.00,0.00,300.00,VND\r\n",
                response.body());
        assertTrue(
                response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/csv"));
        assertTrue(
                response.headers()
                        .firstValue("Content-Disposition")
                        .orElseThrow()
                        .contains("attachment"));
        assertFalse(response.body().contains("session"));
        assertFalse(response.body().contains(adminId));
        verify(exports).findCompletedInPeriod(any(), any(), eq(100000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "malformed", "signature", "expired", "unsigned", "refresh"})
    void invalidCredentialIs401BeforeQueryOrValidation(String variant) throws Exception {
        String credential = adminToken;
        if (variant.equals("missing")) credential = null;
        if (variant.equals("malformed")) credential = "unverified-marker";
        if (variant.equals("refresh"))
            credential = adminRefresh.substring("wspm_refresh=".length());
        var claims =
                JWTClaimsSet.parse(
                        new String(
                                Base64.getUrlDecoder().decode(adminToken.split("\\.")[1]),
                                StandardCharsets.UTF_8));
        if (variant.equals("unsigned")) credential = new PlainJWT(claims).serialize();
        if (variant.equals("signature")) credential = sign(claims, AuthTestKeys.create(256));
        if (variant.equals("expired")) clock.time.set(NOW.plusSeconds(900));
        error(
                get("/api/v1/admin/order-exports?unknown=bad", credential),
                401,
                "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(exports);
    }

    @Test
    void customerAndSpoofedRoleHeadersCannotReachAnyBusinessResource() throws Exception {
        for (String path :
                List.of(
                        EXPORT,
                        "/api/v1/admin/dataset-snapshots/nonexistent",
                        "/api/v1/admin/patterns/nonexistent/artifacts/file")) {
            error(get(path, customerToken), 403, "ACCESS_DENIED");
            error(get(path, null), 401, "AUTHENTICATION_REQUIRED");
        }
        var request =
                base(EXPORT)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("X-Role", "ADMIN")
                        .header("X-User-Id", adminId)
                        .GET()
                        .build();
        error(http.send(request, HttpResponse.BodyHandlers.ofString()), 403, "ACCESS_DENIED");
        verifyNoInteractions(exports);
    }

    @Test
    void evenSignedAdminRoleClaimDoesNotGrantCustomerDatabasePrivileges() throws Exception {
        var claims =
                JWTClaimsSet.parse(
                        new String(
                                Base64.getUrlDecoder().decode(customerToken.split("\\.")[1]),
                                StandardCharsets.UTF_8));
        var forged = new JWTClaimsSet.Builder(claims).claim("roles", List.of("ADMIN")).build();
        error(get(EXPORT, sign(forged, AuthTestKeys.KEYS)), 403, "ACCESS_DENIED");
        verifyNoInteractions(exports);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "PENDING",
                "LOCKED",
                "DISABLED",
                "REVOKED",
                "SESSION_EXPIRED",
                "ROLE_REMOVED",
                "LOWERCASE_ROLE"
            })
    void currentAccountSessionOrRoleChangeTakesEffectWithSameUnexpiredJwt(String state)
            throws Exception {
        if (List.of("PENDING", "LOCKED", "DISABLED").contains(state))
            jdbc.update("UPDATE USER_ACCOUNT SET status=? WHERE user_id=?", state, adminId);
        if (state.equals("REVOKED"))
            jdbc.update(
                    "UPDATE USER_SESSION SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=?", adminId);
        if (state.equals("SESSION_EXPIRED"))
            jdbc.update(
                    "UPDATE USER_SESSION SET expires_at=? WHERE user_id=?",
                    LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                    adminId);
        if (state.equals("ROLE_REMOVED"))
            jdbc.update("DELETE FROM USER_ROLE WHERE user_id=? AND role_id='admin-role'", adminId);
        if (state.equals("LOWERCASE_ROLE"))
            jdbc.update("UPDATE ROLE SET code='admin' WHERE role_id='admin-role'");
        boolean role = state.equals("ROLE_REMOVED") || state.equals("LOWERCASE_ROLE");
        error(
                get(EXPORT, adminToken),
                role ? 403 : 401,
                role ? "ACCESS_DENIED" : "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(exports);
    }

    @Test
    void logoutRevokesAdminJwtBeforeExportEvenWhenJwtStillHasTime() throws Exception {
        assertEquals(204, post("logout", "", adminRefresh).statusCode());
        error(get(EXPORT, adminToken), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(exports);
    }

    @Test
    void refreshKeepsAdminAccessAndOldAccessRemainsSubjectToSameSession() throws Exception {
        var refreshed = post("refresh", "", adminRefresh);
        assertEquals(200, refreshed.statusCode(), refreshed.body());
        assertEquals(200, get(EXPORT, token(refreshed)).statusCode());
        clearInvocations(exports);
        assertEquals(204, post("logout", "", refreshCookie(refreshed)).statusCode());
        error(get(EXPORT, adminToken), 401, "AUTHENTICATION_REQUIRED");
        error(get(EXPORT, token(refreshed)), 401, "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(exports);
    }

    @Test
    void encodedCanonicalPathsStillAuthenticateAndAmbiguousPathsAreRejected() throws Exception {
        for (String path :
                List.of("/api/v1/%61dmin/order-exports", "/%61pi/v1/admin/order-exports")) {
            error(get(path, null), 401, "AUTHENTICATION_REQUIRED");
            error(get(path, customerToken), 403, "ACCESS_DENIED");
        }
        for (String path :
                List.of(
                        "/api/v1/admin;ignored/order-exports",
                        "/api/v1/admin/order-exports;ignored",
                        "/api/v1/%2561dmin/order-exports",
                        "/api/v1/admin//order-exports")) {
            error(get(path, adminToken), 400, "MALFORMED_REQUEST");
        }
        verifyNoInteractions(exports);
        assertEquals(200, get(EXPORT.replace("/admin/", "/%61dmin/"), adminToken).statusCode());
    }

    @Test
    void encodedSeparatorsRejectedBeforeApplicationNeverReturnCsvOrReadOrders() throws Exception {
        for (String path :
                List.of("/api/v1/admin%2forder-exports", "/api/v1/admin%5corder-exports")) {
            var response = get(path, adminToken);
            assertEquals(400, response.statusCode());
            assertFalse(response.body().contains("historical-order"));
        }
        verifyNoInteractions(
                exports); // Tomcat may reject before application envelope/filter processing.
    }

    @Test
    void authorizationPrecedesValidationUnsupportedMethodAndUnknownResource() throws Exception {
        error(get("/api/v1/admin/order-exports?unknown=bad", null), 401, "AUTHENTICATION_REQUIRED");
        error(get("/api/v1/admin/order-exports?unknown=bad", customerToken), 403, "ACCESS_DENIED");
        error(get("/api/v1/admin/order-exports?unknown=bad", adminToken), 400, "VALIDATION_FAILED");
        for (String token : new String[] {null, customerToken, adminToken}) {
            var request = base("/api/v1/admin/order-exports");
            if (token != null) request.header("Authorization", "Bearer " + token);
            error(
                    http.send(
                            request.POST(HttpRequest.BodyPublishers.ofString("malformed-body"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()),
                    token == null ? 401 : token.equals(customerToken) ? 403 : 405,
                    token == null
                            ? "AUTHENTICATION_REQUIRED"
                            : token.equals(customerToken) ? "ACCESS_DENIED" : "METHOD_NOT_ALLOWED");
        }
        error(
                get("/api/v1/admin/unknown-resource/nonexistent", adminToken),
                404,
                "RESOURCE_NOT_FOUND");
        verifyNoInteractions(exports);
    }

    @Test
    void queryCookieAndDuplicateBearerCannotReplaceTheSharedCredentialCarrier() throws Exception {
        error(get(EXPORT + "&access_token=" + adminToken, null), 401, "AUTHENTICATION_REQUIRED");
        var cookie = base(EXPORT).header("Cookie", adminRefresh).GET().build();
        error(
                http.send(cookie, HttpResponse.BodyHandlers.ofString()),
                401,
                "AUTHENTICATION_REQUIRED");
        var duplicate =
                base(EXPORT)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Authorization", "Bearer " + adminToken)
                        .GET()
                        .build();
        error(
                http.send(duplicate, HttpResponse.BodyHandlers.ofString()),
                401,
                "AUTHENTICATION_REQUIRED");
        verifyNoInteractions(exports);
    }

    @Test
    void authStorageFailureIs503WithoutQueryingOrders() throws Exception {
        doThrow(new DataAccessResourceFailureException("private-storage-marker"))
                .when(accounts)
                .account(anyString(), anyBoolean());
        error(get(EXPORT, adminToken), 503, "SERVICE_UNAVAILABLE");
        verifyNoInteractions(exports);
    }

    @Test
    void crossOriginPreflightDoesNotGrantDataOrBypassAuthorization() throws Exception {
        var request =
                base(EXPORT)
                        .header("Origin", "https://attacker.example.test")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization")
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        error(response, 401, "AUTHENTICATION_REQUIRED");
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        verifyNoInteractions(exports);
    }

    @Test
    void adminGrantScriptCannotGrantInactiveMissingOrDuplicateAccounts() throws Exception {
        String grant =
                Files.readString(
                        Path.of(
                                System.getProperty("basedir"),
                                "scripts",
                                "auth",
                                "grant-admin-role.sql"));
        assertEquals(0, jdbc.update(grant, null, "missing-account"));
        jdbc.update("UPDATE USER_ACCOUNT SET status='LOCKED' WHERE user_id=?", customerId);
        assertEquals(0, jdbc.update(grant, null, customerId));
        assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update(grant, null, adminId));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM USER_ROLE WHERE role_id='admin-role'",
                        Integer.class));
    }

    @Test
    void successfulAndDeniedDownloadsDoNotLogTokens(CapturedOutput output) throws Exception {
        assertEquals(200, get(EXPORT, adminToken).statusCode());
        error(get(EXPORT, customerToken), 403, "ACCESS_DENIED");
        for (String secret :
                List.of(
                        adminToken,
                        customerToken,
                        adminRefresh.substring("wspm_refresh=".length()),
                        PASSWORD)) {
            assertFalse(output.getAll().contains(secret));
        }
    }

    @Test
    void unclassifiedRealEndpointIsDeniedByDefaultAndPublicReadsStayPublic() throws Exception {
        error(get("/api/v1/future-private-probe", null), 401, "AUTHENTICATION_REQUIRED");
        error(get("/api/v1/future-private-probe", customerToken), 403, "ACCESS_DENIED");
        error(get("/api/v1/future-private-probe", adminToken), 403, "ACCESS_DENIED");
        assertEquals(0, probeCalls.get());
        verifyNoInteractions(exports);
        var unsupported = get("/api/v1/auth/register", null);
        error(unsupported, 405, "METHOD_NOT_ALLOWED");
        assertEquals("POST", unsupported.headers().firstValue("Allow").orElseThrow());
        assertEquals(0, probeCalls.get());
        for (String path : List.of("/api/health", "/api/v1/products", "/api/v1/categories")) {
            var request = base(path).header("Accept", "application/json").GET().build();
            assertEquals(
                    200, http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    private String register(String username) throws Exception {
        var response =
                post(
                        "register",
                        JSON.writeValueAsString(
                                Map.of(
                                        "username",
                                        username,
                                        "password",
                                        PASSWORD,
                                        "email",
                                        username + "@example.test",
                                        "displayName",
                                        username)),
                        null);
        assertEquals(201, response.statusCode(), response.body());
        return JSON.readTree(response.body()).get("data").get("userId").asString();
    }

    private HttpResponse<String> login(String username) throws Exception {
        var response =
                post(
                        "login",
                        JSON.writeValueAsString(Map.of("username", username, "password", PASSWORD)),
                        null);
        assertEquals(200, response.statusCode(), response.body());
        return response;
    }

    private HttpRequest.Builder base(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        var request = base(path).header("Accept", "text/csv");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String operation, String body, String refresh)
            throws Exception {
        var csrf =
                http.send(
                        base("/api/v1/auth/csrf").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, csrf.statusCode(), csrf.body());
        String cookie =
                csrf.headers().allValues("Set-Cookie").stream()
                        .filter(c -> c.startsWith("XSRF-TOKEN="))
                        .findFirst()
                        .orElseThrow()
                        .split(";", 2)[0];
        var request =
                base("/api/v1/auth/" + operation)
                        .header("Origin", ORIGIN)
                        .header(
                                "X-XSRF-TOKEN",
                                JSON.readTree(csrf.body()).get("data").get("token").asString())
                        .header("Cookie", cookie + (refresh == null ? "" : "; " + refresh));
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

    private static String sign(JWTClaimsSet claims, AuthTestKeys.Material keys) throws Exception {
        var token =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.HS256)
                                .type(new JOSEObjectType("at+jwt"))
                                .keyID(AuthTestKeys.KID)
                                .build(),
                        claims);
        token.sign(new MACSigner(keys.bytes()));
        return token.serialize();
    }

    private void error(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertEquals(code, body.get("code").asString());
        metadata(response);
        assertTrue(
                response.headers()
                        .firstValue("Content-Type")
                        .orElseThrow()
                        .contains("application/json"));
        if (status == 401)
            assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Disposition").isEmpty());
        for (String privateValue :
                List.of(
                        "historical-order",
                        "HISTORY-SKU",
                        "Historical item",
                        "private-storage-marker",
                        PASSWORD)) assertFalse(response.body().contains(privateValue));
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
        java.util.UUID.fromString(id);
        if (response.statusCode() >= 400) {
            JsonNode body = JSON.readTree(response.body());
            assertEquals(id, body.get("requestId").asString());
            assertEquals(response.statusCode(), body.get("status").asInt());
        }
    }
}
