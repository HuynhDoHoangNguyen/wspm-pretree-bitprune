package vn.edu.wspm.backend.services.dataset.validation;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.ProductRepository;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.csv.ProductReferenceLookup;
import vn.edu.wspm.backend.services.dataset.csv.RepositoryProductReferenceLookup;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Real storage/catalog/JPA publication on disposable MySQL; no application config or dev datasource. */
@SpringJUnitConfig(SnapshotValidationIntegrationTest.Config.class)
@Testcontainers
class SnapshotValidationIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("wdm_validation_test");
    @TempDir static Path storageRoot;
    private static final AtomicBoolean FAIL_WRITE_COMMIT = new AtomicBoolean();

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            var actual = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
            return new AbstractDataSource() {
                @Override public Connection getConnection() throws SQLException { return connection(actual.getConnection()); }
                @Override public Connection getConnection(String user, String password) throws SQLException {
                    return connection(actual.getConnection(user, password));
                }
            };
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("vn.edu.wspm.backend.entities");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean ProductRepository products(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(ProductRepository.class);
        }
        @Bean SnapshotValidationRepository repository() { return new SnapshotValidationRepository(); }
        @Bean ProductReferenceLookup catalog(ProductRepository products) { return new RepositoryProductReferenceLookup(products); }
        @Bean RawSnapshotStorage storage() { return new RawSnapshotStorage(storageRoot.toString(), new MultipartConfigElement("", 16777216, 16778240, 0)); }
        @Bean ValidationLimits limits() { return ValidationReportEngineTest.LIMITS; }
        @Bean Clock clock() { return ValidationReportEngineTest.CLOCK; }
        @Bean JsonMapper mapper() { return JsonMapper.builder().build(); }
        @Bean ValidationReportEngine engine(ProductReferenceLookup catalog, ValidationLimits limits, Clock clock) {
            return new ValidationReportEngine(catalog, limits, clock);
        }
        @Bean SnapshotValidationService service(SnapshotValidationRepository repository, RawSnapshotStorage storage,
                ValidationReportEngine engine, ValidationLimits limits, JsonMapper mapper) {
            return new SnapshotValidationService(repository, storage, engine, limits, mapper);
        }
    }

    /** Synthetic pre-commit failure only after DATASET_SNAPSHOT write; actual rollback is performed by Spring. */
    private static Connection connection(Connection actual) {
        var writes = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                    && sql.toLowerCase(java.util.Locale.ROOT).startsWith("update dataset_snapshot ")) writes.set(true);
            if (method.getName().equals("commit") && writes.get() && FAIL_WRITE_COMMIT.compareAndSet(true, false)) {
                actual.rollback();
                throw new SQLException("Synthetic pre-commit failure");
            }
            try { return method.invoke(actual, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired SnapshotValidationRepository repository;
    @Autowired RawSnapshotStorage storage;
    @Autowired ProductReferenceLookup catalog;
    @Autowired SnapshotValidationService service;
    @Autowired JsonMapper mapper;
    @Autowired ValidationLimits limits;

    @BeforeAll
    static void schema(@Autowired JdbcTemplate jdbc) throws Exception {
        String v2 = new ClassPathResource("db/migration/V2__complete_laptop_combo_schema.sql").getContentAsString(StandardCharsets.UTF_8);
        var definitions = Pattern.compile("(?ms)^CREATE TABLE `(?:CATEGORY|BRAND|PRODUCT|DATASET_SNAPSHOT)` .*?;").matcher(v2);
        int count = 0;
        while (definitions.find()) { jdbc.execute(definitions.group()); count++; }
        assertEquals(4, count);
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES ('cat','Test','test')");
        for (String id : new String[]{"product-laptop", "product-mouse", "product-bag"}) jdbc.update("""
                INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status)
                VALUES (?,?,?,?,'cat',999.99,'DISCONTINUED')
                """, id, id, id, id);
    }

    private String acquire(byte[] bytes) throws Exception {
        var raw = storage.save(new ByteArrayInputStream(bytes));
        var root = new LinkedHashMap<String, Object>(Map.of("contractVersion", "wdm-order-csv-v1",
                "conversionStatus", "NOT_STARTED", "statisticsStatus", "NOT_COMPUTED",
                "provenance", Map.of("rawSizeBytes", Long.toString(raw.sizeBytes()), "acquisition", "CSV_UPLOAD"),
                "futureExtension", Map.of("preserved", true)));
        jdbc.update("""
                INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,raw_file_path,raw_sha256,
                validation_status,conversion_report_json,sequence_count,created_at)
                VALUES (?,'CSV','synthetic.csv',?,?,'PENDING',?,0,CURRENT_TIMESTAMP(6))
                """, raw.snapshotId(), raw.reference(), raw.sha256(), mapper.writeValueAsString(root));
        return raw.snapshotId();
    }

    @Test
    void reportAndStatusPersistAndReloadTogetherPreservingAcquisitionAndUnknownJsonMembers() throws Exception {
        String id = acquire(ValidationReportEngineTest.fixture("business-invalid.csv"));
        var report = service.validate(id);
        assertEquals("VALID", report.validationStatus());
        assertEquals("3", report.counts().rejectedRecords());
        assertEquals("1", report.counts().deduplicatedRecords());
        assertEquals(report, service.read(id));
        assertEquals(report, service.validate(id));
        var entity = repository.find(id).orElseThrow();
        assertEquals("VALID", entity.getValidationStatus());
        assertEquals(Map.of("preserved", true), entity.getConversionReportJson().get("futureExtension"));
        assertEquals("NOT_STARTED", entity.getConversionReportJson().get("conversionStatus"));
        assertEquals("NOT_COMPUTED", entity.getConversionReportJson().get("statisticsStatus"));
        assertEquals(0, entity.getSequenceCount());
        assertNull(entity.getSequenceFilePath());
        assertEquals(5, report.manifest().size());
    }

    @Test
    void structuralAndEmptyOutcomesArePersistedInvalidAndNotOperationalFailures() throws Exception {
        String invalid = acquire(new byte[]{(byte) 0xff});
        assertEquals("INVALID", service.validate(invalid).validationStatus());
        assertFalse(service.read(invalid).countsComplete());
        assertEquals("INVALID", repository.find(invalid).orElseThrow().getValidationStatus());
        String empty = acquire(ValidationReportEngineTest.fixture("empty.csv"));
        assertEquals("EMPTY_INPUT", service.validate(empty).cleaningOutcome());
        assertEquals("INVALID", service.read(empty).validationStatus());
    }

    @Test
    void tamperedRawCannotPublishReportAndCanRetryAfterRestoringTestOwnedBytes() throws Exception {
        byte[] original = ValidationReportEngineTest.fixture("valid.csv");
        String id = acquire(original);
        Path file = storageRoot.resolve(id).resolve("raw.csv");
        byte[] corrupt = original.clone();
        corrupt[corrupt.length - 3] ^= 1;
        Files.write(file, corrupt);
        assertEquals(503, assertThrows(ApplicationException.class, () -> service.validate(id)).status().value());
        var pending = repository.find(id).orElseThrow();
        assertEquals("PENDING", pending.getValidationStatus());
        assertFalse(pending.getConversionReportJson().containsKey("validation"));
        Files.write(file, original);
        assertEquals("VALID", service.validate(id).validationStatus());
    }

    @Test
    void actualPreCommitFailureLeavesNeitherReportNorStatusAndAllowsRetry() throws Exception {
        String id = acquire(ValidationReportEngineTest.fixture("valid.csv"));
        FAIL_WRITE_COMMIT.set(true);
        try {
            assertEquals(503, assertThrows(ApplicationException.class, () -> service.validate(id)).status().value());
            assertFalse(FAIL_WRITE_COMMIT.get(), "Fault must reach a real publication commit");
            var entity = repository.find(id).orElseThrow();
            assertEquals("PENDING", entity.getValidationStatus());
            assertFalse(entity.getConversionReportJson().containsKey("validation"));
            assertEquals("VALID", service.validate(id).validationStatus());
        } finally { FAIL_WRITE_COMMIT.set(false); }
    }

    @Test
    void competingServiceInstancesReturnFirstCommittedReportWithoutOverwriting() throws Exception {
        String id = acquire(ValidationReportEngineTest.fixture("valid.csv"));
        var computed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var slow = new ValidationReportEngine(catalog, limits,
                Clock.fixed(Instant.parse("2000-01-01T00:00:00Z"), ZoneOffset.UTC)) {
            @Override public ValidationReport generate(String key, String sha, String size, byte[] raw, Runnable budget) throws IOException {
                var result = super.generate(key, sha, size, raw, budget);
                computed.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test deadline"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("Interrupted"); }
                return result;
            }
        };
        var otherInstance = new SnapshotValidationService(repository, storage, slow, limits, mapper);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> otherInstance.validate(id));
            try {
                assertTrue(computed.await(10, TimeUnit.SECONDS));
                var winner = service.validate(id);
                release.countDown();
                assertEquals(winner, pending.get(10, TimeUnit.SECONDS));
                assertEquals("2026-11-05T00:00:00Z", winner.completedAt());
                assertEquals(winner, service.read(id));
            } finally { release.countDown(); }
        }
    }

    @Test
    void concurrentSnapshotMutationPreventsStaleReportPublication() throws Exception {
        String id = acquire(ValidationReportEngineTest.fixture("valid.csv"));
        var mutating = new ValidationReportEngine(catalog, limits, ValidationReportEngineTest.CLOCK) {
            @Override public ValidationReport generate(String key, String sha, String size, byte[] raw, Runnable budget) throws IOException {
                var report = super.generate(key, sha, size, raw, budget);
                jdbc.update("UPDATE DATASET_SNAPSHOT SET conversion_report_json=JSON_SET(conversion_report_json,'$.conversionStatus','CHANGED') WHERE snapshot_id=?", key);
                return report;
            }
        };
        var operation = new SnapshotValidationService(repository, storage, mutating, limits, mapper);
        assertEquals(409, assertThrows(ApplicationException.class, () -> operation.validate(id)).status().value());
        var entity = repository.find(id).orElseThrow();
        assertEquals("PENDING", entity.getValidationStatus());
        assertFalse(entity.getConversionReportJson().containsKey("validation"));
        assertEquals("CHANGED", entity.getConversionReportJson().get("conversionStatus"));
    }
}
