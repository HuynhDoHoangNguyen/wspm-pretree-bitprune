package vn.edu.wspm.backend.services.dataset.conversion;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Complete application wiring, actual acquisition/validation and disposable MySQL. No development DB. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ConversionValidationGateIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_gate_test").withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path root;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) throws Exception {
        AuthTestKeys.properties(p);
        var setup = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        String v2 = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^(?:CREATE TABLE|ALTER TABLE) `.*?;").matcher(v2);
        int count = 0;
        while (definitions.find()) { setup.execute(definitions.group()); count++; }
        assertEquals(47, count);
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.flyway.enabled", () -> false);
        p.add("spring.sql.init.mode", () -> "never");
        p.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
        p.add("auth.cookie-secure", () -> true);
        p.add("wdm.snapshot-storage.root", () -> root.toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService acquisition;
    @Autowired SnapshotValidationService validation;
    @Autowired ConversionValidationGate gate;
    @Autowired SnapshotValidationRepository repository;
    @Autowired ValidationLimits limits;
    @Autowired ObjectMapper mapper;

    @BeforeAll static void catalog(@Autowired JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES ('cat','Test','test')");
        for (String id : new String[]{"product-laptop", "product-mouse", "product-bag"}) jdbc.update("""
                INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status)
                VALUES (?,?,?,?,'cat',999.99,'DISCONTINUED')
                """, id, id, id, id);
    }

    private byte[] fixture(String name) throws IOException {
        return new ClassPathResource("wdm/csv-v1/" + name).getContentAsByteArray();
    }
    private String acquire(byte[] bytes) {
        return acquisition.acquire(new MockMultipartFile("file", "test.csv", "text/csv", bytes)).snapshotId();
    }
    private String ready(String fixture) throws IOException {
        String id = acquire(fixture(fixture));
        assertEquals("VALID", validation.validate(id).validationStatus());
        return id;
    }
    private void rejected(String id, int status) {
        assertEquals(status, assertThrows(ApplicationException.class, () -> gate.prepare(id)).status().value());
    }
    private String state(String id) {
        return jdbc.queryForObject("SELECT CAST(JSON_OBJECT('report',conversion_report_json,'status',validation_status,"
                + "'sequence',sequence_file_path,'mapping',mapping_file_path,'count',sequence_count) AS CHAR)"
                + " FROM DATASET_SNAPSHOT WHERE snapshot_id=?", String.class, id);
    }

    @Test void actualPipelineProducesReusableReadOnlyProofForCleanAndDirtyInput() throws Exception {
        for (String name : new String[]{"valid.csv", "business-invalid.csv"}) {
            String id = ready(name), before = state(id);
            var proof = gate.prepare(id);
            assertEquals(name.equals("valid.csv") ? 5 : 1, proof.keptRecords().size());
            assertArrayEquals(fixture(name), proof.rawBytes());
            assertEquals(validation.read(id), proof.report());
            assertEquals(proof.report(), gate.prepare(id).report());
            assertEquals(before, state(id));
            try (var artifacts = Files.list(root.resolve(id))) {
                assertEquals(java.util.List.of("raw.csv"), artifacts.map(p -> p.getFileName().toString()).toList());
            }
        }
    }

    @Test void pendingInvalidAndColumnOnlyValidCannotPass() throws Exception {
        String pending = acquire(fixture("valid.csv")); rejected(pending, 409);
        // Corruption fixture only: a status column is never sufficient evidence.
        jdbc.update("UPDATE DATASET_SNAPSHOT SET validation_status='VALID' WHERE snapshot_id=?", pending);
        rejected(pending, 409);
        for (byte[] bytes : new byte[][]{fixture("empty.csv"), new byte[]{(byte) 0xff}}) {
            String id = acquire(bytes); validation.validate(id); rejected(id, 409);
        }
    }

    @Test void staleBindingAndExistingConversionStateFailClosed() throws Exception {
        String id = ready("valid.csv");
        jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=JSON_SET(conversion_report_json,"
                + "'$.validation.rawSha256',?) WHERE snapshot_id=?", "a".repeat(64), id);
        rejected(id, 409);
        String other = ready("valid.csv");
        jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=JSON_SET(conversion_report_json,"
                + "'$.conversionStatus','RUNNING') WHERE snapshot_id=?", other);
        rejected(other, 409);
    }

    @Test void checksumFailureCannotChangePublishedValidation() throws Exception {
        String id = ready("valid.csv"), before = state(id);
        byte[] bytes = fixture("valid.csv"); bytes[bytes.length - 3] ^= 1;
        Files.write(root.resolve(id).resolve("raw.csv"), bytes);
        rejected(id, 503); assertEquals(before, state(id));
    }

    @Test void freshJpaReadRejectsStateChangedDuringFileVerification() throws Exception {
        String id = ready("valid.csv");
        var mutating = new RawSnapshotStorage(root.toString(), new MultipartConfigElement("", 16777216, 16778240, 0)) {
            @Override public byte[] verifiedBytes(String key, String reference, String sha, String size) throws IOException {
                byte[] bytes = super.verifiedBytes(key, reference, sha, size);
                jdbc.update("UPDATE DATASET_SNAPSHOT SET validation_status='INVALID' WHERE snapshot_id=?", key);
                return bytes;
            }
        };
        var operation = new ConversionValidationGate(repository, mutating, limits, mapper);
        assertEquals(409, assertThrows(ApplicationException.class, () -> operation.prepare(id)).status().value());
        assertEquals("INVALID", repository.find(id).orElseThrow().getValidationStatus());
    }
}
