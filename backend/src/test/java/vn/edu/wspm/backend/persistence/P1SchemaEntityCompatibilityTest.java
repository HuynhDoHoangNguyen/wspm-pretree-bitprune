package vn.edu.wspm.backend.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.entities.PatternEntity;
import vn.edu.wspm.backend.entities.RunEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Step B only: synthetic schema/entity fixtures, never mined results or an owner datasource.
 * No Spring Boot context, .env loading, engine, admission or publication service.
 * Explicit approval is checked before constructing or starting any external resource.
 */
class P1SchemaEntityCompatibilityTest {
    private static final Instant CREATED = Instant.parse("2026-11-03T23:59:59.123456Z");
    private static final String SNAPSHOT = "p1-synthetic-snapshot";
    private static MySQLContainer mysql;
    private static JdbcTemplate jdbc;
    private static LocalContainerEntityManagerFactoryBean factory;
    private static EntityManagerFactory emf;

    @BeforeAll
    static void isolatedSchema() {
        if (!Boolean.getBoolean("p1.schema.db.approved")) {
            throw new IllegalStateException("Separate P1 Step B approval is required; no database was started");
        }
        mysql = new MySQLContainer("mysql:8.4").withDatabaseName("p1_schema_entity_test")
                .withUsername("p1test").withPassword("p1-test-only")
                .withUrlParam("connectionTimeZone", "UTC")
                .withUrlParam("forceConnectionTimeZoneToSession", "true");
        mysql.start();
        var dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        var migrations = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).target("4").load();
        migrations.migrate();
        migrations.validate();
        assertEquals(Arrays.asList("1", "2", "3", "4"), Arrays.stream(migrations.info().applied())
                .map(m -> m.getVersion().getVersion()).toList());
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPersistenceUnitName("p1SchemaEntityIsolated");
        factory.setManagedTypes(PersistenceManagedTypes.of(RunEntity.class.getName(), PatternEntity.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC",
                "hibernate.show_sql", "false", "hibernate.cache.use_second_level_cache", "false"));
        factory.afterPropertiesSet();
        emf = factory.getObject();
        assertNotNull(emf);
        snapshot(SNAPSHOT);
    }

    @AfterAll
    static void closeOwnedResources() {
        try {
            if (factory != null) factory.destroy();
        } finally {
            if (mysql != null) mysql.close();
        }
    }

    @Test
    void validatesQueuedMappingUtcMicrosecondsAndNullableOwnership() {
        rolledBack(em -> {
            var run = queued(UUID.randomUUID().toString());
            Instant started = Instant.parse("2026-11-04T00:00:00.234567Z");
            Instant finished = Instant.parse("2026-11-04T00:00:01.345678Z");
            ReflectionTestUtils.setField(run, "startedAt", started);
            ReflectionTestUtils.setField(run, "finishedAt", finished);
            em.persist(run);
            em.flush();
            em.clear();
            var stored = em.find(RunEntity.class, run.getRunId());
            assertEquals(CREATED, stored.getCreatedAt());
            assertEquals(LocalDateTime.ofInstant(CREATED, ZoneOffset.UTC), stored.getRunTime());
            assertEquals(started, stored.getStartedAt());
            assertEquals(finished, stored.getFinishedAt());
            assertEquals(SNAPSHOT, stored.getSnapshotId());
            assertEquals("QUEUED", stored.getStatus());
            assertEquals("PERCENT", stored.getMinsupMode());
            assertEquals(14.0, stored.getMinsupConfigured());
            assertEquals(0.14, stored.getMinsupRelativeFraction());
            assertEquals(stored.getMinsupRatio(), stored.getMinsupRelativeFraction());
            assertEquals(stored.getMinsupAbs(), stored.getEffectiveThreshold());
            assertNull(stored.getExecutionOwnerId());
            assertNull(stored.getExecutionAttemptId());
            assertNull(stored.getExecutionLeaseUntil());
            assertNull(stored.getPatternCount());
            assertNull(stored.getTotalExecutionMs());
        });
    }

    @Test
    void preservesLegacyConstructorAndCompatibilityViewsWithoutInventingMetadata() {
        String id = UUID.randomUUID().toString();
        snapshot(id); // The preserved legacy constructor uses runId as snapshotId.
        rolledBack(em -> {
            var wall = LocalDateTime.ofInstant(CREATED, ZoneOffset.UTC);
            var run = new RunEntity(id, "WSPM_PRETREE", "legacy fixture", .14, .98, 7L, .5, wall, "ignored legacy path");
            em.persist(run);
            em.flush();
            em.clear();
            var stored = em.find(RunEntity.class, id);
            assertEquals(wall, stored.getRunTime());
            assertEquals(CREATED, stored.getCreatedAt());
            assertEquals("LEGACY_COMPATIBILITY", stored.getMinsupMode());
            assertNull(stored.getSequenceCount());
            assertNull(stored.getInputSnapshot());
            assertNull(stored.getTopK());
            assertNull(stored.getCategoryFilter());
            assertNull(stored.getStartedAt());
            assertNull(stored.getFinishedAt());
            assertNull(stored.getMaxW());
            assertNull(stored.getMinW());
        });
    }

    @Test
    void roundTripsOwnershipWithoutIntroducingLifecycleBehavior() {
        rolledBack(em -> {
            var run = queued(UUID.randomUUID().toString());
            Instant lease = CREATED.plusSeconds(60);
            ReflectionTestUtils.setField(run, "executionOwnerId", "synthetic-owner");
            ReflectionTestUtils.setField(run, "executionAttemptId", "synthetic-attempt");
            ReflectionTestUtils.setField(run, "executionLeaseUntil", lease);
            em.persist(run);
            em.flush();
            em.clear();
            var stored = em.find(RunEntity.class, run.getRunId());
            assertEquals("synthetic-owner", stored.getExecutionOwnerId());
            assertEquals("synthetic-attempt", stored.getExecutionAttemptId());
            assertEquals(lease, stored.getExecutionLeaseUntil());
            assertEquals("QUEUED", stored.getStatus());
            assertNull(stored.getStartedAt());
        });
    }

    @Test
    void roundTripsCompletePatternAndPreservesRunAndTemporalRelationships() throws Exception {
        String canonical = "<(1)(2)>";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        rolledBack(em -> {
            var run = queued(UUID.randomUUID().toString());
            em.persist(run);
            // Explicit synthetic mapping values: this test makes no mining/policy correctness claim.
            var pattern = new PatternEntity(run, canonical, hash, 1L, 1.0 / 3, .5, 2, 2, .4, "P1_MAPPING_FIXTURE");
            em.persist(pattern);
            em.flush();
            Long id = pattern.getPatternId();
            em.createNativeQuery("INSERT INTO PATTERN_ITEM(pattern_id,itemset_order,item_order,item_id) VALUES(:id,1,1,1),(:id,2,1,2)")
                    .setParameter("id", id).executeUpdate();
            em.clear();
            var stored = em.find(PatternEntity.class, id);
            assertEquals(run.getRunId(), stored.getRunId());
            assertEquals(run.getRunId(), stored.getRun().getRunId());
            assertEquals(canonical, stored.getCanonicalPattern());
            assertEquals(hash, stored.getCanonicalPatternHash());
            assertEquals(1L, stored.getSupport());
            assertEquals(1.0 / 3, stored.getSupportRatio());
            assertEquals(.5, stored.getWeightedSupport());
            assertEquals(2, stored.getPatternLength());
            assertEquals(2, stored.getItemsetCount());
            assertEquals(.4, stored.getWeight());
            assertEquals("P1_MAPPING_FIXTURE", stored.getPatternWeightPolicy());
            var rows = em.createNativeQuery("SELECT itemset_order,item_order,item_id FROM PATTERN_ITEM WHERE pattern_id=:id ORDER BY itemset_order,item_order")
                    .setParameter("id", id).getResultList();
            assertEquals(2, rows.size());
            assertEquals(1, ((Number) ((Object[]) rows.getFirst())[0]).intValue());
            assertEquals(2, ((Number) ((Object[]) rows.getLast())[0]).intValue());
        });
    }

    @Test
    void enforcesOwnershipAllNullOrAllPresent() {
        String id = storedRun();
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE MINING_RUN SET execution_attempt_id='partial' WHERE run_id=?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE MINING_RUN SET execution_owner_id='owner',execution_attempt_id='attempt' WHERE run_id=?", id));
        assertEquals(1, jdbc.update("UPDATE MINING_RUN SET execution_owner_id='owner',execution_attempt_id='attempt',execution_lease_until='2026-11-04 00:00:00.123456' WHERE run_id=?", id));
    }

    @Test
    void enforcesProvenanceJsonVersionAndRestrictedRunRelationship() {
        String id = storedRun();
        String insert = "INSERT INTO RUN_PROVENANCE(run_id,provenance_version,provenance_json,captured_at) VALUES(?,?,?,UTC_TIMESTAMP(6))";
        for (String invalid : Arrays.asList("[]", "{}", "{\"version\":null}", "{\"version\":3}", "{\"version\":\"other\"}")) {
            assertThrows(DataAccessException.class, () -> jdbc.update(insert, id, "fixture-v1", invalid));
        }
        assertThrows(DataAccessException.class, () -> jdbc.update(insert, "missing-run", "fixture-v1", "{\"version\":\"fixture-v1\"}"));
        assertEquals(1, jdbc.update(insert, id, "fixture-v1", "{\"version\":\"fixture-v1\"}"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM MINING_RUN WHERE run_id=?", id));
    }

    @Test
    void enforcesPublicationStateCompletenessAndRestrictedRunRelationship() {
        String id = storedRun();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RUN_PROVENANCE WHERE run_id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM RUN_RESULT_PUBLICATION WHERE run_id=?", Integer.class, id));
        assertThrows(DataAccessException.class, () -> jdbc.update("INSERT INTO RUN_RESULT_PUBLICATION(run_id) VALUES('missing-run')"));
        jdbc.update("INSERT INTO RUN_RESULT_PUBLICATION(run_id) VALUES(?)", id);
        assertEquals("NOT_PUBLISHED", jdbc.queryForObject("SELECT publication_status FROM RUN_RESULT_PUBLICATION WHERE run_id=?", String.class, id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED' WHERE run_id=?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET publication_status='OTHER' WHERE run_id=?", id));
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET pattern_count=0 WHERE run_id=?", id));
        String commit = "UPDATE RUN_RESULT_PUBLICATION SET publication_status='COMMITTED',manifest_version='P1_FIXTURE',result_sha256=?,pattern_count=0,pattern_item_count=0,run_item_count=0,pattern_weight_policy='P1_FIXTURE',publication_elapsed_ms=0,committed_at=UTC_TIMESTAMP(6) WHERE run_id=?";
        assertThrows(DataAccessException.class, () -> jdbc.update(commit, "invalid-hash", id));
        assertEquals(1, jdbc.update(commit, "a".repeat(64), id));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM MINING_RUN WHERE run_id=?", id));
    }

    private static RunEntity queued(String id) {
        return RunEntity.queued(id, SNAPSHOT, "WSPM_BITPRUNE", "P1 fixture", "PERCENT", 14, .14, .42, .8, .2, .5, CREATED);
    }

    private static void snapshot(String id) {
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES(?,'CSV','P1 fixture','LEGACY_UNVERIFIED','{}',3)", id);
    }

    private static String storedRun() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,created_at) VALUES(?,?,'WSPM_PRETREE','LEGACY_UNVERIFIED','P1 fixture','ABSOLUTE',.1,.1,.5,'2026-11-03 23:59:59.123456')", id, SNAPSHOT);
        return id;
    }

    private static void rolledBack(Consumer<EntityManager> assertions) {
        var em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            assertions.accept(em);
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }
}
