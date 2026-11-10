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
import vn.edu.wspm.backend.repositories.dataset.SnapshotConversionRepository;
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
            .withDatabaseName("wdm_gate_test").withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC")
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
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService acquisition;
    @Autowired SnapshotValidationService validation;
    @Autowired ConversionValidationGate gate;
    @Autowired SnapshotValidationRepository repository;
    @Autowired ValidationLimits limits;
    @Autowired ObjectMapper mapper;
    @Autowired ProductItemMappingService productMapping;
    @Autowired CustomerGroupingService customerGrouping;
    @Autowired TemporalOrderingService temporalOrdering;
    @Autowired SpmfDatasetSerializer spmfSerializer;
    @Autowired SnapshotConversionService conversion;
    @Autowired SnapshotConversionRepository conversionRepository;
    @Autowired ConversionArtifactCodec artifactCodec;
    @Autowired ConversionArtifactStorage artifactStorage;
    @Autowired RawSnapshotStorage rawStorage;
    @Autowired java.time.Clock clock;
    @Autowired ConversionReadService conversionReads;

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

    @Test void mappingConsumesOnlyKeptProductsAndLeavesSnapshotAndRawArtifactUnchanged() throws Exception {
        String id = ready("business-invalid.csv");
        String before = state(id);
        var dictionary = productMapping.resolve(gate.prepare(id));
        assertEquals(java.util.Set.of("product-laptop"), dictionary.productToItem().keySet());
        int item = dictionary.productToItem().get("product-laptop");
        assertTrue(item > 0);
        assertEquals(java.util.Map.of(item, "product-laptop"), dictionary.itemToProduct());
        assertEquals(dictionary.productToItem(), productMapping.resolve(gate.prepare(id)).productToItem());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ITEM_MAPPING WHERE product_id='product-laptop'", Integer.class));
        assertEquals(before, state(id));
        assertArrayEquals(fixture("business-invalid.csv"), Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
        try (var artifacts = Files.list(root.resolve(id))) {
            assertEquals(java.util.List.of("raw.csv"), artifacts.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test void actualCleanAndDirtyPipelineGroupsExactlyKeptRowsWithoutAnyPublication() throws Exception {
        for (String name : new String[]{"valid.csv", "business-invalid.csv"}) {
            String id = ready(name);
            var input = gate.prepare(id);
            var dictionary = productMapping.resolve(input);
            String before = state(id);
            var mappingBefore = jdbc.queryForList("SELECT product_id,item_id,created_at FROM ITEM_MAPPING ORDER BY item_id");
            var result = customerGrouping.group(input, dictionary);
            var retained = result.customers().values().stream().flatMap(c -> c.orders().values().stream())
                    .flatMap(o -> o.sourceRecords().stream()).toList();
            assertEquals(input.keptRecords().size(), retained.size());
            assertEquals(java.util.Set.copyOf(input.keptRecords()), java.util.Set.copyOf(retained));
            if (name.equals("valid.csv")) {
                assertEquals(java.util.Set.of("anon-A", "anon-B"), result.customers().keySet());
                var first = result.customers().get("anon-A").orders();
                assertEquals(java.util.Set.of("order-a", "order-b"), first.keySet());
                assertEquals(java.util.List.of(dictionary.productToItem().get("product-laptop")), first.get("order-a").itemIds());
                assertEquals(2, first.get("order-a").sourceRecords().size());
                assertEquals(java.util.List.of("2", "1"), first.get("order-a").sourceRecords().stream().map(r -> r.fields().get(8)).toList());
                assertTrue(first.get("order-a").sourceRecords().getFirst().fields().get(7).contains("Laptop Việt"));
                assertEquals(first.get("order-a").orderedAt(), first.get("order-b").orderedAt());
                assertEquals(java.util.List.of(dictionary.productToItem().get("product-mouse")), first.get("order-b").itemIds());
                var second = result.customers().get("anon-B").orders();
                assertEquals(java.util.Set.of("order-c"), second.keySet());
                assertEquals(java.util.Set.of(dictionary.productToItem().get("product-mouse"), dictionary.productToItem().get("product-bag")),
                        java.util.Set.copyOf(second.get("order-c").itemIds()));
                assertEquals(java.time.Instant.parse("2026-11-01T23:59:59.999999Z"), second.get("order-c").orderedAt());
            } else {
                assertEquals(java.util.Set.of("anon-A"), result.customers().keySet());
                var orders = result.customers().get("anon-A").orders();
                assertEquals(java.util.Set.of("order-duplicate"), orders.keySet());
                assertEquals(1, orders.get("order-duplicate").sourceRecords().size());
                assertEquals(java.util.List.of(dictionary.productToItem().get("product-laptop")), orders.get("order-duplicate").itemIds());
            }
            assertEquals(mappingBefore, jdbc.queryForList("SELECT product_id,item_id,created_at FROM ITEM_MAPPING ORDER BY item_id"));
            assertEquals(before, state(id));
            assertArrayEquals(fixture(name), Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
            try (var artifacts = Files.list(root.resolve(id))) {
                assertEquals(java.util.List.of("raw.csv"), artifacts.map(p -> p.getFileName().toString()).toList());
            }
        }
    }

    @Test void actualPipelineOrdersAndSerializesCleanDirtyAndShuffledMicrosecondInputWithoutPublication() throws Exception {
        byte[] shuffled = """
                order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code
                order-z,201,anon-B,2026-11-02T00:00:00.000000Z,COMPLETED,product-bag,old,Historical bag,1,100.00,0.00,100.00,VND
                order-2,202,anon-A,2026-11-01T00:00:00.000002Z,COMPLETED,product-mouse,old,Historical mouse,1,100.00,0.00,100.00,VND
                order-10,203,anon-A,2026-11-01T00:00:00.000002Z,COMPLETED,product-laptop,old,Historical laptop,1,100.00,0.00,100.00,VND
                first,204,anon-A,2026-11-01T00:00:00.000001Z,COMPLETED,product-laptop,old,Historical laptop,2,100.00,0.00,200.00,VND
                order-10,205,anon-A,2026-11-01T00:00:00.000002Z,COMPLETED,product-mouse,old,Historical mouse,1,100.00,0.00,100.00,VND
                order-10,206,anon-A,2026-11-01T00:00:00.000002Z,COMPLETED,product-laptop,another,Other historical laptop,1,100.00,0.00,100.00,VND
                """.getBytes(StandardCharsets.UTF_8);
        var sources = java.util.List.of(fixture("valid.csv"), fixture("business-invalid.csv"), shuffled);
        var expected = java.util.List.of(java.util.List.of("anon-A/order-a", "anon-A/order-b", "anon-B/order-c"),
                java.util.List.of("anon-A/order-duplicate"),
                java.util.List.of("anon-A/first", "anon-A/order-10", "anon-A/order-2", "anon-B/order-z"));
        for (int index = 0; index < sources.size(); index++) {
            byte[] raw = sources.get(index);
            String id = acquire(raw);
            assertEquals("VALID", validation.validate(id).validationStatus());
            var input = gate.prepare(id);
            var dictionary = productMapping.resolve(input);
            var grouped = customerGrouping.group(input, dictionary);
            String before = state(id);
            var mappingBefore = jdbc.queryForList("SELECT product_id,item_id,created_at FROM ITEM_MAPPING ORDER BY item_id");
            var result = temporalOrdering.order(grouped);
            assertEquals(expected.get(index), result.customers().stream().flatMap(c -> c.orders().stream()
                    .map(o -> c.customerCode() + "/" + o.orderId())).toList());
            byte[] spmf = spmfSerializer.serialize(result);
            int laptop = dictionary.productToItem().get("product-laptop");
            String expectedSpmf;
            if (index == 1) {
                expectedSpmf = laptop + " -1 -2\n";
            } else {
                int mouse = dictionary.productToItem().get("product-mouse");
                int bag = dictionary.productToItem().get("product-bag");
                if (index == 0) {
                    expectedSpmf = laptop + " -1 " + mouse + " -1 -2\n"
                            + Math.min(mouse, bag) + " " + Math.max(mouse, bag) + " -1 -2\n";
                } else {
                    expectedSpmf = laptop + " -1 " + Math.min(laptop, mouse) + " " + Math.max(laptop, mouse)
                            + " -1 " + mouse + " -1 -2\n" + bag + " -1 -2\n";
                }
            }
            assertArrayEquals(expectedSpmf.getBytes(StandardCharsets.UTF_8), spmf);
            SpmfDatasetSerializerTest.assertExactReadback(result, spmf);
            result.customers().forEach(c -> c.orders().forEach(o -> assertSame(grouped.customers()
                    .get(c.customerCode()).orders().get(o.orderId()), o)));
            var retained = result.customers().stream().flatMap(c -> c.orders().stream())
                    .flatMap(o -> o.sourceRecords().stream()).toList();
            assertEquals(input.keptRecords().size(), retained.size());
            assertEquals(java.util.Set.copyOf(input.keptRecords()), java.util.Set.copyOf(retained));
            if (index == 2) {
                var orders = result.customers().getFirst().orders();
                assertEquals(1000, orders.getFirst().orderedAt().getNano());
                assertEquals(2000, orders.get(1).orderedAt().getNano());
                assertEquals(orders.get(1).orderedAt(), orders.get(2).orderedAt());
                assertEquals(3, orders.get(1).sourceRecords().size());
                assertEquals(2, orders.get(1).itemIds().size());
                assertEquals("2", orders.getFirst().sourceRecords().getFirst().fields().get(8));
            }
            assertEquals(mappingBefore, jdbc.queryForList("SELECT product_id,item_id,created_at FROM ITEM_MAPPING ORDER BY item_id"));
            assertEquals(before, state(id));
            assertArrayEquals(raw, Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
            try (var artifacts = Files.list(root.resolve(id))) {
                assertEquals(java.util.List.of("raw.csv"), artifacts.map(p -> p.getFileName().toString()).toList());
            }
        }
    }

    @Test void conversionPublishesExactFilesStatisticsAndFrozenValidationOnV2() throws Exception {
        String id = ready("valid.csv");
        var report = validation.read(id);
        var result = conversion.convert(id);
        assertEquals(new ConversionArtifactResult.Statistics(2, 1, 1, 3, 3, 4, 2, 2, 2.0), result.statistics());
        assertEquals("SUCCEEDED", conversionState(id));
        var entity = conversionRepository.read(id);
        assertEquals("COMPUTED", entity.getConversionReportJson().get("statisticsStatus"));
        assertEquals(result, conversionRepository.completed(entity));
        assertEquals(report, validation.read(id));
        assertArrayEquals(fixture("valid.csv"), Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
        byte[] spmf = artifactStorage.read(id, result.attemptId(), "sequences.spmf", result.sequence());
        byte[] mapping = artifactStorage.read(id, result.attemptId(), "mapping.json", result.mapping());
        assertEquals(result.statistics(), artifactCodec.verify(spmf, mapping).statistics());
        assertEquals(ConversionArtifactCodec.sha256(spmf), entity.getSequenceSha256());
        assertEquals(ConversionArtifactCodec.sha256(mapping), entity.getMappingSha256());
        assertEquals(java.util.Set.of("sequences.spmf", "mapping.json"), files(attemptDirectory(id, result.attemptId())));
    }

    @Test void nonterminatingAverageLengthSurvivesMySqlDoublePublicationAndReadback() throws Exception {
        byte[] csv = """
                order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code
                a,901,c1,2026-11-01T00:00:00.000000Z,COMPLETED,product-laptop,old,Old,1,100.00,0.00,100.00,VND
                a,902,c1,2026-11-01T00:00:00.000000Z,COMPLETED,product-mouse,old,Old,1,100.00,0.00,100.00,VND
                b,903,c2,2026-11-01T00:00:00.000000Z,COMPLETED,product-laptop,old,Old,1,100.00,0.00,100.00,VND
                c,904,c3,2026-11-01T00:00:00.000000Z,COMPLETED,product-mouse,old,Old,1,100.00,0.00,100.00,VND
                """.getBytes(StandardCharsets.UTF_8);
        String id = acquire(csv); assertEquals("VALID", validation.validate(id).validationStatus());
        var result = conversion.convert(id);
        assertEquals(new ConversionArtifactResult.Statistics(3, 3, 0, 3, 2, 4, 1, 2, 4.0 / 3.0), result.statistics());
        assertEquals(result, conversion.convert(id));
    }

    @Test void dirtyConversionPublishesOnlyKeptDataWithLengthMeasuredAsItems() throws Exception {
        String id = ready("business-invalid.csv");
        var result = conversion.convert(id);
        assertEquals(new ConversionArtifactResult.Statistics(1, 1, 0, 1, 1, 1, 1, 1, 1.0), result.statistics());
        String mapping = Files.readString(root.resolve(result.mapping().reference()));
        assertTrue(mapping.contains("product-laptop")); assertFalse(mapping.contains("product-missing"));
        assertFalse(mapping.contains("product-bag"));
        assertEquals("VALID", conversionRepository.read(id).getValidationStatus());
    }

    @Test void repeatConversionVerifiesFrozenArtifactsWithoutAllocatingOrConsultingLiveMapping() throws Exception {
        String id = ready("valid.csv"); var first = conversion.convert(id);
        var neverMapping = org.mockito.Mockito.mock(ProductItemMappingService.class);
        var repeated = new SnapshotConversionService(gate, neverMapping, customerGrouping, temporalOrdering, spmfSerializer,
                artifactCodec, artifactStorage, conversionRepository, rawStorage, limits, conversionReads).convert(id);
        assertEquals(first, repeated);
        org.mockito.Mockito.verifyNoInteractions(neverMapping);
        assertEquals(1, attemptCount(id));
    }

    @Test void publishedTamperMissingFilesForgedStatisticsAndChangedRawAreNotRepaired() throws Exception {
        for (String tamper : java.util.List.of("sequence", "mapping", "missing", "statistics", "raw")) {
            String id = ready("valid.csv"); var result = conversion.convert(id);
            if (tamper.equals("statistics")) jdbc.update("UPDATE DATASET_SNAPSHOT SET total_item_occurrences=999 WHERE snapshot_id=?", id);
            else if (tamper.equals("missing")) Files.delete(root.resolve(result.sequence().reference()));
            else {
                Path path = tamper.equals("sequence") ? root.resolve(result.sequence().reference())
                        : tamper.equals("mapping") ? root.resolve(result.mapping().reference()) : root.resolve(id).resolve("raw.csv");
                byte[] bytes = Files.readAllBytes(path); bytes[0] ^= 1; Files.write(path, bytes);
            }
            var failure = assertThrows(ApplicationException.class, () -> conversion.convert(id));
            assertTrue(failure.status().value() == 409 || failure.status().value() == 503);
            assertEquals("SUCCEEDED", conversionState(id));
            assertEquals(1, attemptCount(id));
        }
    }

    @Test void sourceBindingChangedAfterGateCannotBeClaimed() throws Exception {
        String id = ready("valid.csv");
        var stale = new ConversionValidationGate(repository, rawStorage, limits, mapper) {
            @Override public VerifiedConversionInput prepare(String key) {
                var proof = gate.prepare(key);
                jdbc.update("UPDATE DATASET_SNAPSHOT SET source_type='DATABASE' WHERE snapshot_id=?", key);
                return proof;
            }
        };
        assertEquals(409, assertThrows(ApplicationException.class, () -> converter(stale, artifactStorage, conversionRepository).convert(id)).status().value());
        assertEquals("NOT_STARTED", conversionState(id));
        assertFalse(Files.exists(root.resolve(id).resolve("conversions")));
    }

    @Test void snapshotReferencedByRunCannotBeConvertedOrOverwritten() throws Exception {
        String id = ready("valid.csv");
        // Lifecycle fixture only, not evidence of an actual mining run.
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w) VALUES (?,?,'WSPM_PRETREE','QUEUED','fixture','ABSOLUTE',1,1,1)",
                java.util.UUID.randomUUID().toString(), id);
        assertEquals(409, assertThrows(ApplicationException.class, () -> conversion.convert(id)).status().value());
        assertEquals("NOT_STARTED", conversionState(id));
        assertFalse(Files.exists(root.resolve(id).resolve("conversions")));
    }

    @Test void simultaneousConversionsHaveOnlyOneClaimAndOnePublishedAttempt() throws Exception {
        String id = ready("valid.csv"); var barrier = new java.util.concurrent.CyclicBarrier(2);
        var competing = new ConversionValidationGate(repository, rawStorage, limits, mapper) {
            @Override public VerifiedConversionInput prepare(String key) {
                var proof = gate.prepare(key);
                try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (Exception failure) { throw new IllegalStateException("Test rendezvous failed", failure); }
                return proof;
            }
        };
        var service = converter(competing, artifactStorage, conversionRepository);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> work = () -> { try { return service.convert(id); } catch (ApplicationException failure) { return failure; } };
            var first = pool.submit(work); var second = pool.submit(work);
            var results = java.util.List.of(first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(ConversionArtifactResult.class::isInstance).count());
            var error = (ApplicationException) results.stream().filter(ApplicationException.class::isInstance).findFirst().orElseThrow();
            assertTrue(error.status().value() == 409 || error.status().value() == 503);
        }
        assertEquals("SUCCEEDED", conversionState(id)); assertEquals(1, attemptCount(id));
    }

    @Test void secondFileWriteFailureLeavesFailedUnpublishedStateAndPreservesRaw() throws Exception {
        String id = ready("valid.csv");
        var failing = new ConversionArtifactStorage(root.toString(), 67108864) {
            @Override protected void write(Receipt receipt, String name, byte[] bytes) throws IOException {
                if (name.equals("mapping.json")) throw new IOException("Injected mapping write failure");
                super.write(receipt, name, bytes);
            }
        };
        assertEquals(503, assertThrows(ApplicationException.class, () -> converter(gate, failing, conversionRepository).convert(id)).status().value());
        assertUnpublished(id, "FAILED"); assertEquals(0, attemptCount(id));
        assertArrayEquals(fixture("valid.csv"), Files.readAllBytes(root.resolve(id).resolve("raw.csv")));
        assertEquals(409, assertThrows(ApplicationException.class, () -> conversion.convert(id)).status().value());
    }

    @Test void rawChangedDuringConversionIsRejectedBeforeArtifactPublication() throws Exception {
        String id = ready("valid.csv");
        var tampering = new ConversionArtifactStorage(root.toString(), 67108864) {
            @Override public Receipt save(String key, String attempt, byte[] spmf, byte[] mapping) throws IOException {
                var receipt = super.save(key, attempt, spmf, mapping);
                Path path = root.resolve(key).resolve("raw.csv");
                byte[] bytes = Files.readAllBytes(path); bytes[0] ^= 1; Files.write(path, bytes);
                return receipt;
            }
        };
        assertEquals(503, assertThrows(ApplicationException.class, () -> converter(gate, tampering, conversionRepository).convert(id)).status().value());
        assertUnpublished(id, "FAILED"); assertEquals(0, attemptCount(id));
    }

    @Test void readbackFailureAfterWritingBothFilesCleansReceiptAfterConfirmedFailedState() throws Exception {
        String id = ready("valid.csv");
        assertEquals(503, assertThrows(ApplicationException.class, () -> converter(gate, unreadableStorage(), conversionRepository).convert(id)).status().value());
        assertUnpublished(id, "FAILED"); assertEquals(0, attemptCount(id));
    }

    @Test void actualPublicationRollbackKeepsFilesAndRunningClaimForReconciliation() throws Exception {
        String id = ready("valid.csv");
        jdbc.execute("CREATE TRIGGER wdm035_fail_publication BEFORE UPDATE ON DATASET_SNAPSHOT FOR EACH ROW BEGIN IF NEW.sequence_file_path IS NOT NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Injected publication rollback'; END IF; END");
        try { assertEquals(503, assertThrows(ApplicationException.class, () -> conversion.convert(id)).status().value()); }
        finally { jdbc.execute("DROP TRIGGER wdm035_fail_publication"); }
        assertUnpublished(id, "RUNNING"); assertEquals(1, attemptCount(id));
        assertEquals(409, assertThrows(ApplicationException.class, () -> conversion.convert(id)).status().value());
        String attempt = operationAttempt(id);
        assertEquals(java.util.Set.of("sequences.spmf", "mapping.json"), files(attemptDirectory(id, attempt)));
    }

    @Test void successfulCommitWithLostAcknowledgmentRetainsPublishedFilesAndRepeatVerifiesSuccess() throws Exception {
        String id = ready("valid.csv");
        var uncertain = new DelegatingConversionRepository() {
            @Override public void publish(Claim claim, ConversionArtifactResult result) {
                conversionRepository.publish(claim, result);
                throw new org.springframework.dao.DataAccessResourceFailureException("Injected lost commit acknowledgment");
            }
        };
        assertEquals(503, assertThrows(ApplicationException.class, () -> converter(gate, artifactStorage, uncertain).convert(id)).status().value());
        assertEquals("SUCCEEDED", conversionState(id)); assertEquals(1, attemptCount(id));
        assertEquals(conversionRepository.completed(conversionRepository.read(id)), conversion.convert(id));
        assertEquals(1, attemptCount(id));
    }

    @Test void uncertainFailureRecordingRetainsUnpublishedFilesInsteadOfDeletingWithoutProof() throws Exception {
        String id = ready("valid.csv");
        var unavailable = new DelegatingConversionRepository() {
            @Override public void fail(Claim claim) { throw new org.springframework.dao.DataAccessResourceFailureException("Injected failure recording unavailable"); }
        };
        assertEquals(503, assertThrows(ApplicationException.class, () -> converter(gate, unreadableStorage(), unavailable).convert(id)).status().value());
        assertUnpublished(id, "RUNNING"); assertEquals(1, attemptCount(id));
        assertEquals(java.util.Set.of("sequences.spmf", "mapping.json"), files(attemptDirectory(id, operationAttempt(id))));
    }

    private SnapshotConversionService converter(ConversionValidationGate customGate, ConversionArtifactStorage customStorage, SnapshotConversionRepository customRepository) {
        return new SnapshotConversionService(customGate, productMapping, customerGrouping, temporalOrdering, spmfSerializer,
                artifactCodec, customStorage, customRepository, rawStorage, limits, conversionReads);
    }
    private ConversionArtifactStorage unreadableStorage() {
        return new ConversionArtifactStorage(root.toString(), 67108864) {
            @Override public byte[] read(String id, String attempt, String name, ConversionArtifactResult.ArtifactFile expected) throws IOException {
                throw new IOException("Injected artifact readback failure");
            }
        };
    }
    private class DelegatingConversionRepository extends SnapshotConversionRepository {
        DelegatingConversionRepository() { super(artifactCodec, mapper, clock); }
        @Override public vn.edu.wspm.backend.entities.DatasetSnapshotEntity read(String id) { return conversionRepository.read(id); }
        @Override public Claim claim(VerifiedConversionInput input, String attempt) { return conversionRepository.claim(input, attempt); }
        @Override public void publish(Claim claim, ConversionArtifactResult result) { conversionRepository.publish(claim, result); }
        @Override public void fail(Claim claim) { conversionRepository.fail(claim); }
    }
    private String conversionState(String id) { return (String) conversionRepository.read(id).getConversionReportJson().get("conversionStatus"); }
    private String operationAttempt(String id) { return (String) ((java.util.Map<?, ?>) conversionRepository.read(id).getConversionReportJson().get("conversion")).get("attemptId"); }
    private Path attemptDirectory(String id, String attempt) { return root.resolve(id).resolve("conversions").resolve(attempt); }
    private long attemptCount(String id) throws IOException { try (var entries = Files.list(root.resolve(id).resolve("conversions"))) { return entries.count(); } }
    private java.util.Set<String> files(Path directory) throws IOException { try (var files = Files.list(directory)) { return files.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()); } }
    private void assertUnpublished(String id, String expectedState) {
        var s = conversionRepository.read(id);
        assertEquals(expectedState, s.getConversionReportJson().get("conversionStatus"));
        assertEquals("NOT_COMPUTED", s.getConversionReportJson().get("statisticsStatus"));
        assertEquals("VALID", s.getValidationStatus()); assertEquals(0L, s.getSequenceCount());
        assertNull(s.getSequenceFilePath()); assertNull(s.getSequenceSha256()); assertNull(s.getMappingFilePath()); assertNull(s.getMappingSha256());
        assertNull(s.getSingleItemsetSequenceCount()); assertNull(s.getMultiItemsetSequenceCount()); assertNull(s.getItemsetCount());
        assertNull(s.getDistinctItemCount()); assertNull(s.getTotalItemOccurrences()); assertNull(s.getAverageSequenceLength());
        assertNull(s.getMinSequenceLength()); assertNull(s.getMaxSequenceLength());
        var status = conversionReads.status(id);
        assertEquals(expectedState, status.conversionStatus());
        assertNull(status.statistics()); assertNull(status.sequence()); assertNull(status.mapping());
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
