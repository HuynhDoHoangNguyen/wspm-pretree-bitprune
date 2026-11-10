package vn.edu.wspm.backend.services.miningrun;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.mappers.MiningRunResponseMapper;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.wspm.backend.repositories.RunRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-only Gate 1 deliverable. Compilation and execution require separate approval.
 * No static initializer opens a connection. Surefire must explicitly select this *IT.
 *
 * Future execution requires -Dtv3.history.persistence.approved=true and ALL of:
 * TV3_HISTORY_HOST (only the literal 127.0.0.1), TV3_HISTORY_PORT,
 * TV3_HISTORY_SCHEMA (tv3history followed by 6-32 lowercase letters/digits),
 * TV3_HISTORY_SERVER_UUID, TV3_HISTORY_USERNAME (tv3history-prefixed test user),
 * TV3_HISTORY_PASSWORD, TV3_HISTORY_EXPECTED_ACCOUNT (exact CURRENT_USER()),
 * TV3_HISTORY_INIT_MODE=initialize-empty, and
 * TV3_HISTORY_DISPOSABLE_ACK=<schema>@<server UUID>.
 * Supply secrets through the future test process environment, never JVM arguments.
 *
 * This initial harness intentionally accepts only a loopback, fresh disposable MySQL 8.4
 * target. A remote or preinitialized target needs a separately reviewed design.
 * No application properties, Boot auto-configuration, database creation, clean, repair,
 * truncate, schema/container teardown, or TV1 writer is used.
 */
@SpringJUnitConfig(MiningRunHistoryPersistenceIT.PersistenceConfig.class)
@Transactional
@Rollback
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MiningRunHistoryPersistenceIT {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 3, 23, 59, 59, 123456000);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 11, 4, 0, 0, 0, 234567000);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 11, 4, 0, 0, 1, 345678000);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 11, 4, 0, 0, 2, 456789000);
    private static final LocalDateTime T4 = LocalDateTime.of(2026, 11, 4, 0, 0, 3, 567890000);
    private static final String RAW_ERROR = "Synthetic SQLException: SELECT internal_field FROM internal_table";
    private static final String PUBLIC_ERROR =
            "RUN có thông tin lỗi được ghi nhận; chi tiết chẩn đoán không được công bố qua API.";

    @Autowired
    private MiningRunQueryService service;
    @Autowired
    private RunRepository runs;
    @Autowired
    private DatasetSnapshotRepository snapshots;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void insertTransactionalFixtures() {
        assertTrue(TestTransaction.isActive(), "Fixtures require a Spring-managed test transaction");
        assertTrue(TestTransaction.isFlaggedForRollback(), "Fixture transaction must roll back");
        assertEquals(0, jdbc.queryForObject("SELECT @@session.autocommit", Integer.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
        insertSnapshot("tv3-snap-a", 500L, 42L);
        insertSnapshot("tv3-snap-b", 250L, 21L);
        insertRun("tv3-run-01", "tv3-snap-a", "WSPM_PRETREE", "SUCCEEDED", T0, null);
        insertRun("tv3-run-02", "tv3-snap-a", "WSPM_BITPRUNE", "SUCCEEDED", T1, null);
        insertRun("tv3-run-03", "tv3-snap-b", "WSPM_PRETREE", "FAILED", T1, RAW_ERROR);
        insertRun("tv3-run-04", "tv3-snap-b", "WSPM_BITPRUNE", "RUNNING", T2, null);
        insertRun("tv3-run-05", "tv3-snap-a", "WSPM_PRETREE", "QUEUED", T3, null);
        insertRun("tv3-run-06", "tv3-snap-b", "WSPM_BITPRUNE", "LEGACY_UNVERIFIED", T4, null);
        setMeasurements("tv3-run-01", 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, T0, T0);
        setMeasurements("tv3-run-02", 17L, 91L, 11L, 12L, 13L, 25L, 14L, 50L, 123456L,
                T1, T1.plusNanos(50_000_000));
    }

    @AfterTransaction
    void verifyFixtureRollback() {
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM MINING_RUN", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM DATASET_SNAPSHOT", Long.class));
        // No compensating deletion: leaked data is a test failure, not permission to clean.
    }

    @Test
    void readsMappedRunDetailsWithIndependentAndSharedSnapshotIds() {
        RunEntity run = runs.findById("tv3-run-02").orElseThrow();
        assertEquals("tv3-snap-a", run.getSnapshotId());
        assertNotEquals(run.getRunId(), run.getSnapshotId());
        assertEquals(T1, run.getRunTime());
        DatasetSnapshotEntity snapshot = snapshots.findById(run.getSnapshotId()).orElseThrow();
        assertEquals(500L, snapshot.getSequenceCount());
        assertEquals(42L, snapshot.getDistinctItemCount());
        MiningRunDetailResponse expected = new MiningRunDetailResponse(
                "tv3-run-02", "WSPM_BITPRUNE", "SUCCEEDED", "TV3_SYNTHETIC", "tv3-snap-a",
                "RELATIVE", 0.05, 25.0, 17L, 91L, 50L, 123456L,
                Instant.parse("2026-11-04T00:00:00.234567Z"),
                Instant.parse("2026-11-04T00:00:00.234567Z"),
                Instant.parse("2026-11-04T00:00:00.284567Z"),
                0.05, 0.8, 0.1, 0.3, 11L, 12L, 13L, 25L, 14L, null, 500L, 42L, "VALID");
        assertEquals(expected, service.findById("tv3-run-02"));
        assertEquals(service.findById("tv3-run-01").snapshotId(), expected.snapshotId());
        assertEquals(250L, service.findById("tv3-run-03").sequenceCount());
        assertEquals(21L, service.findById("tv3-run-03").distinctItemCount());
        assertThrows(MiningRunQueryService.MiningRunNotFoundException.class,
                () -> service.findById("tv3-run-missing"));
    }

    @Test
    void filtersPersistedRunsByAlgorithmStatusAndTheirIntersection() {
        assertEquals(List.of("tv3-run-05", "tv3-run-03", "tv3-run-01"),
                ids(service.findAll(0, 20, "WSPM_PRETREE", null)));
        assertEquals(List.of("tv3-run-06", "tv3-run-04", "tv3-run-02"),
                ids(service.findAll(0, 20, "WSPM_BITPRUNE", null)));
        assertEquals(List.of("tv3-run-02", "tv3-run-01"),
                ids(service.findAll(0, 20, null, "SUCCEEDED")));
        assertEquals(List.of("tv3-run-01"),
                ids(service.findAll(0, 20, " WSPM_PRETREE ", " SUCCEEDED ")));
        assertEquals(List.of("tv3-run-06"),
                ids(service.findAll(0, 20, "WSPM_BITPRUNE", "LEGACY_UNVERIFIED")));
    }

    @Test
    void sortsByCreatedTimeAndRunIdAndReturnsStableZeroBasedPages() {
        assertEquals(List.of("tv3-run-06", "tv3-run-05", "tv3-run-04", "tv3-run-03",
                "tv3-run-02", "tv3-run-01"), ids(service.findAll(0, 20, null, null)));
        MiningRunPageResponse first = service.findAll(0, 2, null, null);
        MiningRunPageResponse second = service.findAll(1, 2, null, null);
        assertEquals(List.of("tv3-run-06", "tv3-run-05"), ids(first));
        assertEquals(List.of("tv3-run-04", "tv3-run-03"), ids(second));
        assertEquals(List.of("tv3-run-02", "tv3-run-01"), ids(service.findAll(2, 2, null, null)));
        assertEquals(0, first.page());
        assertEquals(1, second.page());
        for (MiningRunPageResponse page : List.of(first, second)) {
            assertEquals(2, page.size());
            assertEquals(6L, page.totalElements());
            assertEquals(3, page.totalPages());
        }
        MiningRunPageResponse beyond = service.findAll(7, 2, null, null);
        assertTrue(beyond.items().isEmpty());
        assertEquals(7, beyond.page());
        assertEquals(6L, beyond.totalElements());
        assertEquals(3, beyond.totalPages());
    }

    @Test
    void preservesNullAndZeroMeasurementsWithoutDerivingMetrics() {
        MiningRunDetailResponse missing = service.findById("tv3-run-05");
        assertAll("tv3-run-05: unavailable measurements must remain null",
                () -> assertNull(missing.patternCount(), "patternCount"),
                () -> assertNull(missing.candidateCount(), "candidateCount"),
                () -> assertNull(missing.datasetIoMs(), "datasetIoMs"),
                () -> assertNull(missing.preprocessingMs(), "preprocessingMs"),
                () -> assertNull(missing.dfsMiningMs(), "dfsMiningMs"),
                () -> assertNull(missing.completeAlgorithmMs(), "completeAlgorithmMs"),
                () -> assertNull(missing.outputIoMs(), "outputIoMs"),
                () -> assertNull(missing.totalExecutionMs(), "totalExecutionMs"),
                () -> assertNull(missing.peakSampledJvmHeapBytes(), "peakSampledJvmHeapBytes"),
                () -> assertNull(missing.maxW(), "maxW"),
                () -> assertNull(missing.minW(), "minW"),
                () -> assertNull(missing.startedAt(), "startedAt"),
                () -> assertNull(missing.finishedAt(), "finishedAt"));
        MiningRunDetailResponse zero = service.findById("tv3-run-01");
        assertAll("tv3-run-01: explicit zero measurements must remain zero",
                () -> assertEquals(0L, zero.patternCount(), "patternCount"),
                () -> assertEquals(0L, zero.candidateCount(), "candidateCount"),
                () -> assertEquals(0L, zero.datasetIoMs(), "datasetIoMs"),
                () -> assertEquals(0L, zero.preprocessingMs(), "preprocessingMs"),
                () -> assertEquals(0L, zero.dfsMiningMs(), "dfsMiningMs"),
                () -> assertEquals(0L, zero.completeAlgorithmMs(), "completeAlgorithmMs"),
                () -> assertEquals(0L, zero.outputIoMs(), "outputIoMs"),
                () -> assertEquals(0L, zero.totalExecutionMs(), "totalExecutionMs"),
                () -> assertEquals(0L, zero.peakSampledJvmHeapBytes(), "peakSampledJvmHeapBytes"));
        assertEquals(Instant.parse("2026-11-03T23:59:59.123456Z"), zero.startedAt());
        assertEquals(zero.startedAt(), zero.finishedAt());
    }

    @Test
    void readsUtcMicrosecondsAcrossMidnightWithoutTimezoneShift() {
        assertEquals("+00:00", jdbc.queryForObject("SELECT @@session.time_zone", String.class));
        assertEquals(T0, jdbc.queryForObject("SELECT created_at FROM MINING_RUN WHERE run_id = ?",
                (row, number) -> row.getObject(1, LocalDateTime.class), "tv3-run-01"));
        assertEquals(Instant.parse("2026-11-04T00:00:00.234567Z"), runs.findById("tv3-run-02").orElseThrow().getCreatedAt());
        assertEquals(Instant.parse("2026-11-03T23:59:59.123456Z"), service.findById("tv3-run-01").createdAt());
        assertEquals(Instant.parse("2026-11-04T00:00:00.234567Z"), service.findById("tv3-run-02").createdAt());
        assertEquals(Instant.parse("2026-11-04T00:00:00.284567Z"), service.findById("tv3-run-02").finishedAt());
    }

    @Test
    void hidesPersistedDiagnosticsWithoutChangingStoredContentOrRunState() {
        assertEquals(RAW_ERROR, storedError("tv3-run-03"));
        assertEquals(RAW_ERROR, runs.findById("tv3-run-03").orElseThrow().getErrorMessage());
        assertEquals(PUBLIC_ERROR, service.findById("tv3-run-03").errorMessage());
        assertEquals("FAILED", service.findById("tv3-run-03").status());
        assertEquals(RAW_ERROR, storedError("tv3-run-03"));
        assertEquals(RAW_ERROR, runs.findById("tv3-run-03").orElseThrow().getErrorMessage());
        assertNull(service.findById("tv3-run-02").errorMessage());
        jdbc.update("UPDATE MINING_RUN SET error_message = ? WHERE run_id = ?", RAW_ERROR, "tv3-run-04");
        jdbc.update("UPDATE MINING_RUN SET error_message = ? WHERE run_id = ?", null, "tv3-run-03");
        SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory).clear();
        assertEquals("RUNNING", service.findById("tv3-run-04").status());
        assertEquals(PUBLIC_ERROR, service.findById("tv3-run-04").errorMessage());
        assertEquals(RAW_ERROR, storedError("tv3-run-04"));
        assertEquals("FAILED", service.findById("tv3-run-03").status());
        assertNull(service.findById("tv3-run-03").errorMessage());
        assertNull(storedError("tv3-run-03"));
    }

    private List<String> ids(MiningRunPageResponse page) {
        return page.items().stream().map(item -> item.runId()).toList();
    }

    private String storedError(String runId) {
        return jdbc.queryForObject("SELECT error_message FROM MINING_RUN WHERE run_id = ?", String.class, runId);
    }

    private void insertSnapshot(String id, long sequences, long items) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO DATASET_SNAPSHOT
                    (snapshot_id, source_type, source_name, validation_status, conversion_report_json,
                     sequence_count, distinct_item_count, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setString(1, id);
            statement.setString(2, "BENCHMARK");
            statement.setString(3, "TV3 synthetic " + id);
            statement.setString(4, "VALID");
            statement.setString(5, "{\"synthetic\":true}");
            statement.setLong(6, sequences);
            statement.setLong(7, items);
            statement.setObject(8, T0);
            return statement;
        });
    }

    private void insertRun(String id, String snapshot, String algorithm, String status,
            LocalDateTime created, String error) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO MINING_RUN
                    (run_id, snapshot_id, algorithm, status, dataset_source, minsup_mode, minsup_configured,
                     minsup_relative_fraction, effective_threshold, mean_w, created_at, error_message)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setString(1, id);
            statement.setString(2, snapshot);
            statement.setString(3, algorithm);
            statement.setString(4, status);
            statement.setString(5, "TV3_SYNTHETIC");
            statement.setString(6, "RELATIVE");
            statement.setDouble(7, 0.05);
            statement.setDouble(8, 0.05);
            statement.setDouble(9, 25.0);
            statement.setDouble(10, 0.3);
            statement.setObject(11, created);
            statement.setString(12, error);
            return statement;
        });
    }

    private void setMeasurements(String id, long patterns, long candidates, long io, long preprocessing,
            long dfs, long complete, long output, long total, long heap, LocalDateTime start, LocalDateTime end) {
        jdbc.update("""
                UPDATE MINING_RUN SET pattern_count=?, candidate_count=?, dataset_io_ms=?, preprocessing_ms=?,
                dfs_mining_ms=?, complete_algorithm_ms=?, output_io_ms=?, total_execution_ms=?,
                peak_sampled_jvm_heap_bytes=?, max_w=?, min_w=?, started_at=?, finished_at=? WHERE run_id=?
                """, patterns, candidates, io, preprocessing, dfs, complete, output, total, heap, 0.8, 0.1, start, end, id);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class PersistenceConfig {
        @Bean
        TestInputs inputs() {
            return TestInputs.readExplicitInputs();
        }

        @Bean(destroyMethod = "close")
        VerifiedTarget verifiedTarget(TestInputs inputs) throws SQLException {
            return VerifiedTarget.open(inputs);
        }

        @Bean
        DataSource dataSource(VerifiedTarget target) {
            return target.dataSource;
        }

        @Bean
        Flyway verifiedSchema(VerifiedTarget target) throws SQLException {
            // VerifiedTarget has already checked identity, privileges, lock, and empty schema.
            target.assertLockHeld();
            Flyway flyway = Flyway.configure().dataSource(target.dataSource)
                    .locations("classpath:db/migration").schemas(target.inputs.schema)
                    .defaultSchema(target.inputs.schema).createSchemas(false).baselineOnMigrate(false)
                    .cleanDisabled(true).outOfOrder(false).validateOnMigrate(true).failOnMissingLocations(true)
                    .target(MigrationVersion.fromVersion("4")).load();
            flyway.migrate();
            flyway.validate();
            var applied = flyway.info().applied();
            require(Arrays.stream(applied).map(info -> info.getVersion() == null ? "" : info.getVersion().getVersion())
                    .toList().equals(List.of("1", "2", "3", "4")), "Expected exactly Flyway V1 through V4");
            for (var migration : applied) {
                require(migration.getChecksum() != null && "SUCCESS".equals(migration.getState().name()),
                        "Migration checksum/state verification failed");
            }
            target.verifyV4Tables();
            return flyway;
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, Flyway verifiedSchema) {
            require(verifiedSchema != null, "Schema verification must precede JPA initialization");
            LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPersistenceUnitName("tv3HistoryIsolatedTest");
            factory.setManagedTypes(PersistenceManagedTypes.of(RunEntity.class.getName(), DatasetSnapshotEntity.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            // DATETIME(6) stores UTC wall time: avoid java.sql.Timestamp conversion via the host timezone.
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.type.java_time_use_direct_jdbc", "true",
                    "hibernate.show_sql", "false", "hibernate.cache.use_second_level_cache", "false"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory, DataSource dataSource) {
            JpaTransactionManager manager = new JpaTransactionManager(factory);
            manager.setDataSource(dataSource);
            return manager;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        JpaRepositoryFactoryBean<RunRepository, RunEntity, String> runRepository(EntityManagerFactory factory) {
            JpaRepositoryFactoryBean<RunRepository, RunEntity, String> bean = new JpaRepositoryFactoryBean<>(RunRepository.class);
            bean.setEntityManager(SharedEntityManagerCreator.createSharedEntityManager(factory));
            bean.setTransactionManager("transactionManager");
            return bean;
        }

        @Bean
        DatasetSnapshotRepository snapshotRepository(EntityManagerFactory factory) {
            DatasetSnapshotRepository repository = new DatasetSnapshotRepository();
            ReflectionTestUtils.setField(repository, "entityManager",
                    SharedEntityManagerCreator.createSharedEntityManager(factory));
            return repository;
        }

        @Bean
        MiningRunResponseMapper mapper() {
            return new MiningRunResponseMapper();
        }

        @Bean
        MiningRunQueryService service(RunRepository runs, DatasetSnapshotRepository snapshots, MiningRunResponseMapper mapper) {
            return new MiningRunQueryService(runs, snapshots, mapper);
        }
    }

    /** Deliberately no record-generated toString(): this object contains a password. */
    static final class TestInputs {
        private final String schema;
        private final String serverUuid;
        private final String username;
        private final String password;
        private final String account;
        private final int port;

        private TestInputs(String schema, String serverUuid, String username, String password, String account, int port) {
            this.schema = schema;
            this.serverUuid = serverUuid;
            this.username = username;
            this.password = password;
            this.account = account;
            this.port = port;
        }

        static TestInputs readExplicitInputs() {
            require("true".equals(System.getProperty("tv3.history.persistence.approved")),
                    "Separate Gate 2 execution approval is required before datasource creation");
            require("127.0.0.1".equals(required("TV3_HISTORY_HOST")), "Only an explicit loopback target is supported");
            String schema = required("TV3_HISTORY_SCHEMA");
            require(schema.matches("tv3history[a-z0-9]{6,32}"), "Invalid disposable test schema identifier");
            String uuid = required("TV3_HISTORY_SERVER_UUID");
            require(uuid.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"), "Expected server UUID is required");
            uuid = UUID.fromString(uuid).toString();
            String username = required("TV3_HISTORY_USERNAME");
            require(username.matches("tv3history[a-zA-Z0-9_]{1,22}"), "A dedicated test-only username is required");
            String password = required("TV3_HISTORY_PASSWORD");
            String account = required("TV3_HISTORY_EXPECTED_ACCOUNT");
            require(account.startsWith(username + "@") && account.length() > username.length() + 1
                    && !account.contains("\n") && !account.contains("\r"), "Expected database account is required");
            String portText = required("TV3_HISTORY_PORT");
            require(portText.matches("[0-9]{1,5}"), "Invalid test database port");
            int port = Integer.parseInt(portText);
            require(port >= 1 && port <= 65535, "Invalid test database port");
            require("initialize-empty".equals(required("TV3_HISTORY_INIT_MODE")), "Only fresh schema initialization is supported");
            require((schema + "@" + uuid).equals(required("TV3_HISTORY_DISPOSABLE_ACK")),
                    "Explicit disposable target acknowledgement is required");
            return new TestInputs(schema, uuid, username, password, account, port);
        }

        private static String required(String key) {
            String value = System.getenv(key);
            require(value != null && !value.isBlank(), "Missing test-only input: " + key);
            return value;
        }

        private DriverManagerDataSource newDataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            // URL is constructed from validated identifiers; credentials are separate properties.
            source.setUrl("jdbc:mysql://127.0.0.1:" + port + "/" + schema
                    + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&preserveInstants=true"
                    + "&sendFractionalSeconds=true&autoReconnect=false&allowMultiQueries=false"
                    + "&connectTimeout=5000&socketTimeout=30000&sslMode=DISABLED");
            source.setUsername(username);
            source.setPassword(password);
            return source;
        }
    }

    /**
     * Holds a cooperative MySQL advisory lock until context close; never owns database teardown.
     * This rejects concurrent harness instances, not arbitrary DBA writes. Gate 2 must designate
     * the target/account for exclusive disposable test use outside this process as well.
     */
    static final class VerifiedTarget implements AutoCloseable {
        private final TestInputs inputs;
        private final DriverManagerDataSource raw;
        private final Connection lockConnection;
        private final String lockName;
        private final long lockOwner;
        private final DataSource dataSource;

        private VerifiedTarget(TestInputs inputs, DriverManagerDataSource raw, Connection connection, long owner) {
            this.inputs = inputs;
            this.raw = raw;
            this.lockConnection = connection;
            this.lockName = "tv3history:" + inputs.schema;
            this.lockOwner = owner;
            this.dataSource = new AbstractDataSource() {
                @Override
                public Connection getConnection() throws SQLException {
                    return verifiedConnection();
                }

                @Override
                public Connection getConnection(String username, String password) throws SQLException {
                    throw new SQLFeatureNotSupportedException("Alternate datasource credentials are forbidden");
                }
            };
        }

        static VerifiedTarget open(TestInputs inputs) throws SQLException {
            DriverManagerDataSource raw = inputs.newDataSource();
            Connection connection = raw.getConnection();
            try {
                verifyIdentity(connection, inputs);
                verifyTableNameCasing(connection);
                verifyRestrictedGrants(connection, inputs);
                String lock = "tv3history:" + inputs.schema;
                require(Long.valueOf(1).equals(scalar(connection, "SELECT GET_LOCK(?, 0)", lock)),
                        "Another test owns the target; concurrent execution is forbidden");
                long owner = scalar(connection, "SELECT CONNECTION_ID()");
                VerifiedTarget target = new VerifiedTarget(inputs, raw, connection, owner);
                target.assertLockHeld();
                require(target.objectCount("TABLES") == 0 && target.objectCount("ROUTINES") == 0
                        && target.objectCount("TRIGGERS") == 0 && target.objectCount("EVENTS") == 0,
                        "Fresh initialization requires a completely empty disposable schema");
                return target;
            } catch (SQLException | RuntimeException failure) {
                connection.close(); // Closing the session releases its advisory lock, without deleting data.
                throw failure;
            }
        }

        private Connection verifiedConnection() throws SQLException {
            assertLockHeld();
            Connection connection = raw.getConnection();
            try {
                verifyIdentity(connection, inputs);
                return connection;
            } catch (SQLException | RuntimeException failure) {
                connection.close();
                throw failure;
            }
        }

        void assertLockHeld() throws SQLException {
            require(!lockConnection.isClosed() && Long.valueOf(lockOwner).equals(
                    scalar(lockConnection, "SELECT IS_USED_LOCK(?)", lockName)), "Test target lock was lost");
        }

        private static void verifyIdentity(Connection connection, TestInputs inputs) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT @@server_uuid, DATABASE(), CURRENT_USER(), VERSION(),
                           @@session.time_zone, @@session.foreign_key_checks, CURRENT_ROLE()
                    """); ResultSet row = statement.executeQuery()) {
                require(row.next(), "Database identity check returned no row");
                require(inputs.serverUuid.equalsIgnoreCase(row.getString(1)), "Server identity mismatch");
                require(inputs.schema.equals(row.getString(2)) && inputs.schema.equals(connection.getCatalog()),
                        "Database catalog mismatch");
                require(inputs.account.equals(row.getString(3)), "Database account mismatch");
                require(row.getString(4).startsWith("8.4."), "MySQL 8.4 is required");
                require("+00:00".equals(row.getString(5)), "Connection session must use UTC");
                require(row.getInt(6) == 1, "Foreign-key checks must remain enabled");
                require("NONE".equals(row.getString(7)), "Role-based privileges are not supported by this strict harness");
            }
        }

        private static void verifyTableNameCasing(Connection connection) {
            String safeMessage = "This harness requires a readable lower_case_table_names=0 before migration";
            try (PreparedStatement statement = connection.prepareStatement("SELECT @@lower_case_table_names");
                    ResultSet row = statement.executeQuery()) {
                require(row.next(), safeMessage);
                String value = row.getString(1);
                require("0".equals(value), safeMessage);
            } catch (SQLException failure) {
                // Do not include driver messages, URLs, or raw exception details in this guard failure.
                throw new IllegalStateException(safeMessage);
            }
        }

        private static void verifyRestrictedGrants(Connection connection, TestInputs inputs) throws SQLException {
            // MySQL 8.4 SHOW GRANTS renders account/schema identifiers with backticks.
            // Recognize only these complete canonical statements; do not parse arbitrary GRANT SQL.
            String host = inputs.account.substring(inputs.username.length() + 1);
            String grantee = quoteGrantIdentifier(inputs.username) + "@" + quoteGrantIdentifier(host);
            String usage = "GRANT USAGE ON *.* TO " + grantee;
            String allOnSchema = "GRANT ALL PRIVILEGES ON " + quoteGrantIdentifier(inputs.schema) + ".* TO " + grantee;
            boolean usageGrant = false;
            boolean schemaGrant = false;
            try (PreparedStatement statement = connection.prepareStatement("SHOW GRANTS");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String grant = rows.getString(1);
                    if (usage.equals(grant)) {
                        require(!usageGrant, "Duplicate connection-level grant is not supported");
                        usageGrant = true;
                    } else if (allOnSchema.equals(grant)) {
                        require(!schemaGrant, "Duplicate schema grant is not supported");
                        schemaGrant = true;
                    } else {
                        // Reject roles (including mandatory roles shown by SHOW GRANTS), global/other-schema
                        // permissions, partial privilege lists, GRANT OPTION, REVOKE, and unknown suffixes.
                        throw new IllegalStateException("Only USAGE and exact test-schema ALL PRIVILEGES are permitted");
                    }
                }
            }
            require(schemaGrant, "Exact test-schema ALL PRIVILEGES without GRANT OPTION are required");
            // This conservative policy is not a universal metadata-visibility or disposability proof.
            // Gate 2 must independently verify target provenance and the actual MySQL environment.
        }

        private static String quoteGrantIdentifier(String identifier) {
            return "`" + identifier.replace("`", "``") + "`";
        }

        private long objectCount(String kind) throws SQLException {
            String schemaColumn = switch (kind) {
                case "TABLES" -> "TABLE_SCHEMA";
                case "ROUTINES" -> "ROUTINE_SCHEMA";
                case "TRIGGERS" -> "TRIGGER_SCHEMA";
                case "EVENTS" -> "EVENT_SCHEMA";
                default -> throw new IllegalArgumentException("Unsupported metadata object");
            };
            Long count = scalar(lockConnection,
                    "SELECT COUNT(*) FROM information_schema." + kind + " WHERE " + schemaColumn + " = ?", inputs.schema);
            require(count != null, "Schema object count is unavailable");
            return count;
        }

        void verifyV4Tables() throws SQLException {
            assertLockHeld();
            Set<String> expected = Set.of("USER_ACCOUNT", "ROLE", "USER_ROLE", "USER_SESSION", "USER_ADDRESS", "CUSTOMER",
                    "CATEGORY", "BRAND", "PRODUCT", "PRODUCT_IMAGE", "WAREHOUSE", "SUPPLIER", "PURCHASE_ORDER",
                    "PURCHASE_ORDER_ITEM", "GOODS_RECEIPT", "GOODS_RECEIPT_ITEM", "INVENTORY_BALANCE", "STOCK_MOVEMENT",
                    "INVENTORY_SERIAL", "ORDERS", "ORDER_ITEM", "ORDER_APPLIED_COMBO", "ORDER_ADDRESS", "ORDER_STATUS_HISTORY",
                    "CART", "CART_APPLIED_COMBO", "CART_ITEM", "CHECKOUT_SESSION", "CHECKOUT_ADDRESS", "STOCK_RESERVATION",
                    "PAYMENT", "PAYMENT_ATTEMPT", "PAYMENT_TRANSACTION", "PAYMENT_WEBHOOK_EVENT", "ITEM_MAPPING",
                    "DATASET_SNAPSHOT", "MINING_RUN", "RUN_ITEM_STATISTIC", "PATTERN", "PATTERN_ITEM", "COMBO_BATCH",
                    "COMBO", "COMBO_ITEM", "COMBO_SOURCE", "CATEGORY_EXCLUSION", "RUN_PROVENANCE", "RUN_RESULT_PUBLICATION");
            Set<String> actual = new java.util.HashSet<>();
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?")) {
                statement.setString(1, inputs.schema);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        actual.add(rows.getString(1));
                    }
                }
            }
            require(actual.remove("flyway_schema_history"), "Flyway history table is missing");
            require(actual.equals(expected), "Applied tables do not exactly match V4");
        }

        private static Long scalar(Connection connection, String sql, Object... values) throws SQLException {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < values.length; i++) {
                    statement.setObject(i + 1, values[i]);
                }
                try (ResultSet row = statement.executeQuery()) {
                    require(row.next(), "Verification query returned no row");
                    long value = row.getLong(1);
                    return row.wasNull() ? null : value;
                }
            }
        }

        @Override
        public void close() throws SQLException {
            lockConnection.close(); // Session close releases the lock; no schema/container cleanup.
        }
    }

    private static void require(boolean condition, String safeMessage) {
        if (!condition) {
            throw new IllegalStateException(safeMessage);
        }
    }
}
