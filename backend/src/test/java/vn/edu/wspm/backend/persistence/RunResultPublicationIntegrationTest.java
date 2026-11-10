package vn.edu.wspm.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import vn.edu.wspm.backend.auth.AuthTestKeys;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.PatternItemRepository;
import vn.edu.wspm.backend.repositories.run.*;
import vn.edu.wspm.backend.services.dataset.DatasetSnapshotService;
import vn.edu.wspm.backend.services.dataset.validation.SnapshotValidationService;
import vn.edu.wspm.backend.services.dataset.conversion.SnapshotConversionService;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.services.run.engine.MiningAlgorithm;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.Outcome;
import vn.edu.wspm.backend.services.run.persistence.*;

/** Actual CSV/storage/conversion/managed core/MySQL. SQL writes below are labeled negative controls. */
@SpringBootTest(properties = {"wdm.execution.enabled=true", "wdm.execution.parallelism=1", "wdm.execution.queue-capacity=64",
        "spring.config.import=", "wdm.result-publication.enabled=false", "spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=validate",
        "tv2.combo.laptop-category-ids=native-laptops", "tv2.combo.accessory-category-ids=temporal-cat"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunResultPublicationIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("wdm_item_statistics")
            .withCommand("--log-bin-trust-function-creators=1")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    @TempDir static Path storage;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatasetSnapshotService snapshots;
    @Autowired SnapshotValidationService validation;
    @Autowired SnapshotConversionService conversion;
    @Autowired RunCreationService creation;
    @Autowired ManagedRunExecutionAdmission provider;
    @Autowired PatternPersistenceService parentService;
    @Autowired PatternPersistenceRepository parents;
    @Autowired TemporalPatternPersistenceService temporalService;
    @Autowired RunItemStatisticsPersistenceService service;
    @Autowired RunItemStatisticsPersistenceRepository statistics;
    @Autowired TemporalPatternPersistenceRepository temporal;
    @Autowired PatternItemRepository itemRepository;
    @Autowired RunMetadataRepository metadata;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RunResultPublicationService publication;
    @Autowired vn.edu.wspm.backend.services.run.query.RunResultQueryService queries;
    @Autowired vn.edu.wspm.backend.services.combo.PublishedMiningResultProvider combos;
    @Autowired vn.edu.wspm.backend.services.combo.generation.PublishedComboInputService comboInput;
    @Autowired vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration nativeCombos;
    @Autowired vn.edu.wspm.backend.services.combo.review.ComboManagementService reviews;
    @Autowired vn.edu.wspm.backend.services.combo.review.ComboPublicationService comboPublication;
    @Autowired vn.edu.wspm.backend.services.recommendation.ComboReadService customerCombos;
    @Autowired javax.sql.DataSource comboDataSource;
    @Autowired tools.jackson.databind.ObjectMapper comboJson;
    @Autowired org.springframework.core.env.Environment comboEnvironment;
    @Autowired vn.edu.wspm.backend.services.combo.generation.NativeComboPolicy nativePolicy;
    static String nativeSnapshot;
    @Autowired RunResultPublicationRepository manifest;
    static String snapshot;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("wdm.execution.enabled", () -> true);
        var flyway = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load(); flyway.migrate(); flyway.validate();
        assertEquals("5", flyway.info().current().getVersion().getVersion());
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("wdm.snapshot-storage.root", () -> storage.toString());
        AuthTestKeys.properties(p); p.add("auth.allowed-origins", () -> AuthTestKeys.ISSUER);
    }

    @BeforeEach void pipeline() throws Exception {
        if (snapshot != null) return;
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('temporal-cat','Synthetic','temporal-cat')");
        for (int i = 1; i <= 3; i++) {
            String id = "rt-p" + i;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'temporal-cat',999)", id, id, id, id);
        }
        snapshot = snapshots.acquire(new MockMultipartFile("file", "temporal.csv", "text/csv",
                new ClassPathResource("wdm/csv-v1/round-trip.csv").getContentAsByteArray())).snapshotId();
        assertEquals("VALID", validation.validate(snapshot).validationStatus()); conversion.convert(snapshot);
    }

    Outcome run(MiningAlgorithm algorithm, String mode, double threshold) throws Exception {
        return run(snapshot, algorithm, mode, threshold);
    }
    Outcome run(String snapshotId, MiningAlgorithm algorithm, String mode, double threshold) throws Exception {
        String id = creation.create(new RunCreateRequest(snapshotId, algorithm.name(), mode, threshold)).runId();
        var result = provider.completion(id).orElseThrow().toCompletableFuture().get(30, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!result.lifecycleConfirmed() && System.nanoTime() < deadline) { Thread.sleep(20); result = provider.outcome(id).orElseThrow(); }
        assertTrue(result.lifecycleConfirmed()); assertEquals(ManagedRunExecutionAdmission.State.COMPLETED, result.state());
        assertEquals(0, parentCount(id)); assertEquals(0, items(id).size()); return result;
    }
    Outcome run() throws Exception { return run(MiningAlgorithm.WSPM_PRETREE, "ABSOLUTE", .1); }
    int parentCount(String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN WHERE run_id=?", Integer.class, id); }
    List<Map<String, Object>> items(String id) {
        return jdbc.queryForList("SELECT i.* FROM PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id"
                + " WHERE p.run_id=? ORDER BY i.pattern_id,i.itemset_order,i.item_order", id);
    }
    List<Map<String, Object>> parentRows(String id) {
        return jdbc.queryForList("SELECT * FROM PATTERN WHERE run_id=? ORDER BY pattern_id", id);
    }
    Map<String, Long> expected(Outcome outcome) {
        var map = new TreeMap<String, Long>(); outcome.result().supports().forEach((p, s) -> map.put(p.serialize(), s.longValue())); return map;
    }

    @Test void bothEnginesPublishExactMapsAndReadAfterAcknowledgement() throws Exception {
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome=run(algorithm,"ABSOLUTE",.1);
            assertThrows(ApplicationException.class,() -> combos.requireEligible(outcome.runId()));
            assertThrows(ApplicationException.class,() -> comboInput.prepare(outcome.runId()));
            String hash=publication.publish(outcome);
            assertEquals(hash,publication.publish(outcome)); // lost post-commit signal reconciliation
            var bundle=combos.requireEligible(outcome.runId());
            var actual=new TreeMap<String,Long>();
            bundle.patterns().forEach(p -> actual.put(p.pattern().canonicalPattern(),Long.parseLong(p.pattern().supportAbs())));
            assertEquals(expected(outcome),actual);
            assertEquals(3,bundle.runItems().size());
            assertTrue(bundle.result().publishedReadable()); assertTrue(bundle.result().eligibleForCombo());
            assertEquals(hash,bundle.result().resultSha256());
            var prepared = comboInput.prepare(outcome.runId());
            var consumerMap = new TreeMap<String,Long>();
            prepared.patterns().forEach(p -> consumerMap.put(p.canonicalPattern(), Long.parseLong(p.supportAbs())));
            assertEquals(expected(outcome), consumerMap);
            assertEquals(bundle.runItems().size(), prepared.dictionary().size());
            assertEquals(bundle.patterns().getFirst().itemsets().size(), prepared.patterns().getFirst().itemsets().size());
            var summary = comboInput.summary(outcome.runId());
            assertEquals("VERIFIED_PUBLISHED_INPUT", summary.inputStatus());
            assertEquals("BLOCKED", summary.generationStatus());
            assertEquals(hash, summary.publicationDigest());
            assertTrue(provider.acknowledge(outcome.runId(),outcome.token()));
            assertTrue(provider.outcome(outcome.runId()).isEmpty());
            jdbc.update("UPDATE PRODUCT SET name='Current catalog renamed' WHERE product_id='rt-p1'");
            assertEquals(bundle,combos.requireEligible(outcome.runId()));
            assertEquals(prepared.patterns(),comboInput.prepare(outcome.runId()).patterns());
            assertEquals(prepared.dictionary(),comboInput.prepare(outcome.runId()).dictionary());
            var doc=new tools.jackson.databind.ObjectMapper().readTree(queries.export(outcome.runId(),"published"));
            assertEquals("WDM_RESULT_EXPORT_V2",doc.get("formatVersion").asString());
            assertEquals(actual.size(),doc.get("patterns").size()); assertEquals(3,doc.get("runItems").size());
            assertThrows(ApplicationException.class,() -> queries.page(outcome.runId(),"staging",null,null));
        }
    }
    @Test void nativeGenerationFreezesPreviewRetriesAndReviewsRealPublishedPatterns() throws Exception {
        jdbc.update("INSERT IGNORE INTO CATEGORY(category_id,name,slug,status) VALUES('native-laptops','Laptop','native-laptops','ACTIVE')");
        jdbc.update("UPDATE PRODUCT SET category_id='native-laptops',status='ACTIVE' WHERE product_id='rt-p1'");
        jdbc.update("UPDATE PRODUCT SET category_id='temporal-cat',status='ACTIVE' WHERE product_id IN ('rt-p2','rt-p3')");
        jdbc.update("INSERT INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES('native-admin','native-admin','fixture-password-not-login','Admin','ACTIVE')");
        jdbc.update("INSERT IGNORE INTO ROLE(role_id,code,name) VALUES('native-admin-role','ADMIN','Admin')");
        String role=jdbc.queryForObject("SELECT role_id FROM ROLE WHERE code='ADMIN'",String.class);
        jdbc.update("INSERT INTO USER_ROLE(user_id,role_id) VALUES('native-admin',?)",role);
        var actor=new vn.edu.wspm.backend.web.auth.AdminPrincipal("native-admin",List.of("ADMIN"));
        var outcome=run(MiningAlgorithm.WSPM_PRETREE,"ABSOLUTE",.1);publication.publish(outcome);
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
        var choice=nativeCombos.preview(outcome.runId(),null,1,"native-request",actor);
        assertEquals("SELECT_ANCHOR",choice.status());assertFalse(choice.anchors().isEmpty());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class));
        var preview=nativeCombos.preview(outcome.runId(),choice.anchors().getFirst().productId(),1,"native-request",actor);
        assertEquals("READY",preview.status(),preview.issues().toString());
        var inserted=nativeCombos.submit(preview.contextToken(),actor,false);
        assertEquals("INSERTED",inserted.status(),inserted.issues().toString());
        assertEquals(preview.groups(),inserted.storedGroups());
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,true).status());
        String combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=? ORDER BY combo_id LIMIT 1",String.class,inserted.batchId());
        var detail=reviews.detail(combo);
        assertEquals(List.of("APPROVE","REJECT"),detail.assessment().allowedActions(),detail.assessment().blockingIssues().toString());
        var approved=reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor);
        assertEquals("APPLIED",approved.outcome(),approved.issues().toString());
        assertEquals("native-admin",approved.storedAudit().reviewedBy());
        assertEquals("ALREADY_DECIDED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor).outcome());
        assertEquals("CONFLICT",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("REJECT","opposite",detail.reviewToken()),actor).outcome());
        assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM COMBO_BATCH WHERE batch_id=?",String.class,inserted.batchId()));
        assertEquals("CONFLICT",nativeCombos.submit(preview.contextToken(),actor,false).status());
        assertThrows(RuntimeException.class,()->nativeCombos.submit(preview.contextToken()+"x",actor,false));
    }
    @Test void largeNativeBatchSavesAllGroupsWithCompactEvidenceAndCanRetryAndReview() throws Exception {
        var actor=nativeActor();
        jdbc.update("INSERT IGNORE INTO CATEGORY(category_id,name,slug,status) VALUES('native-laptops','Laptop','native-laptops','ACTIVE')");
        var csv=new StringBuilder("order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\n");
        for(int i=0;i<11;i++) {
            String p="large-native-"+i;
            String name="Product "+i+" "+"x".repeat(180);
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES(?,?,?,?,?,100,'ACTIVE')",p,p,name,p,i==0?"native-laptops":"temporal-cat");
            csv.append("large-order,").append(i+1).append(",LARGE,2026-11-01T00:00:01.000000Z,COMPLETED,")
                    .append(p).append(',').append(p).append(',').append(name).append(",1,100.00,0.00,100.00,VND\n");
        }
        String snapshot=snapshots.acquire(new MockMultipartFile("file","large-native.csv","text/csv",csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).snapshotId();
        validation.validate(snapshot);conversion.convert(snapshot);
        var outcome=run(snapshot,MiningAlgorithm.WSPM_PRETREE,"ABSOLUTE",.001);publication.publish(outcome);
        var preview=nativeCombos.preview(outcome.runId(),"large-native-0",5,"large-native-request",actor);
        assertEquals("READY",preview.status(),preview.issues().toString());assertEquals(175,preview.groups());
        var inserted=nativeCombos.submit(preview.contextToken(),actor,false);
        assertEquals("INSERTED",inserted.status(),inserted.issues().toString());assertEquals(175,inserted.storedGroups());
        byte[] content=jdbc.queryForObject("SELECT content_bytes FROM COMBO_BATCH_WRITE_EVIDENCE WHERE batch_id=?",byte[].class,inserted.batchId());
        assertTrue(new String(content,java.nio.charset.StandardCharsets.UTF_8).contains("NATIVE_COMBO_CONTENT_DIGEST_V2"));
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,true).status());
        String combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=? LIMIT 1",String.class,inserted.batchId());
        var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
        assertEquals("APPLIED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor).outcome());
    }
    @Test void selectedProductsQuantitiesAllMatchAcrossItemsetsAndReviewWithoutLaptop() throws Exception {
        var actor=nativeActor();var outcome=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        var options=nativeCombos.options(outcome.runId(),actor);
        var ids=options.products().stream().collect(java.util.stream.Collectors.toMap(p->p.productId(),p->p.itemId()));
        var selection=new vn.edu.wspm.backend.services.combo.generation.ProductSelection(List.of(
                new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(ids.get("native-keyboard"),2),
                new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(ids.get("native-bag"),3)),2,4);
        var preview=nativeCombos.preview(outcome.runId(),null,1,"selected-qty",actor,selection);
        assertEquals("READY",preview.status(),preview.issues().toString());assertTrue(preview.groups()>1);assertEquals(1,preview.proposals().size());
        for(var p:preview.proposals()){
            assertEquals(2,p.items().stream().filter(i->i.productId().equals("native-keyboard")).findFirst().orElseThrow().quantity());
            assertEquals(3,p.items().stream().filter(i->i.productId().equals("native-bag")).findFirst().orElseThrow().quantity());
            assertTrue(p.items().stream().filter(i->!i.required()).allMatch(i->i.quantity()==1));
        }
        var inserted=nativeCombos.submit(preview.contextToken(),actor,false);assertEquals("INSERTED",inserted.status(),inserted.issues().toString());
        assertEquals(preview.groups(),inserted.storedGroups());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO c WHERE c.batch_id=? AND c.reference_original_price<>(SELECT SUM(i.quantity*i.unit_price_snapshot) FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id)",Integer.class,inserted.batchId()));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM COMBO c WHERE c.batch_id=? AND NOT EXISTS(SELECT 1 FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id='native-laptop')",Integer.class,inserted.batchId())>0);
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,true).status());
        var view=nativeCombos.preview(outcome.runId(),"native-bag",20,"selected-qty",actor,selection);
        assertEquals(preview.groups(),view.groups());assertEquals(preview.contextToken(),view.contextToken());
        assertEquals("NO_OP",nativeCombos.submit(view.contextToken(),actor,false).status());
        var changed=new vn.edu.wspm.backend.services.combo.generation.ProductSelection(List.of(new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(ids.get("native-keyboard"),4)),2,4);
        assertThrows(ApplicationException.class,()->nativeCombos.preview(outcome.runId(),null,1,"selected-qty",actor,changed));
        var foreign=new vn.edu.wspm.backend.services.combo.generation.ProductSelection(List.of(new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(Integer.MAX_VALUE,1)),2,4);
        assertThrows(ApplicationException.class,()->nativeCombos.preview(outcome.runId(),null,5,"foreign-item",actor,foreign));
        String combo=jdbc.queryForObject("SELECT c.combo_id FROM COMBO c WHERE c.batch_id=? AND NOT EXISTS(SELECT 1 FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id='native-laptop') LIMIT 1",String.class,inserted.batchId());
        var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
        assertEquals("APPLIED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor).outcome());
        assertEquals("ALREADY_DECIDED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor).outcome());
        assertEquals("CONFLICT",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("REJECT","opposite",detail.reviewToken()),actor).outcome());
    }
    @Test void selectedPolicyPersistsFiveProductsAndUnsignedQuantityWithExactTotals() throws Exception {
        var actor=nativeActor();jdbc.update("INSERT INTO CATEGORY(category_id,name,slug,status) VALUES('selection-cat','Other','selection-cat','ACTIVE')");
        var csv=new StringBuilder("order_id,order_item_id,customer_code,ordered_at,status,product_id,sku_snapshot,product_name_snapshot,quantity,list_unit_price,allocated_discount_amount,line_total,currency_code\n");
        for(int i=0;i<5;i++){
            String p="selection-"+i;jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES(?,?,?,?,'selection-cat',100,'ACTIVE')",p,p,p,p);
            int order=i<2?1:i==2?2:3;
            csv.append("order-").append(order).append(',').append(i+1).append(",SELECT,2026-11-01T00:00:0").append(order).append(".000000Z,COMPLETED,").append(p).append(',').append(p).append(',').append(p).append(",1,100.00,0.00,100.00,VND\n");
        }
        String snapshot=snapshots.acquire(new MockMultipartFile("file","selected.csv","text/csv",csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))).snapshotId();
        validation.validate(snapshot);conversion.convert(snapshot);var outcome=run(snapshot,MiningAlgorithm.WSPM_PRETREE,"ABSOLUTE",.001);publication.publish(outcome);
        var options=nativeCombos.options(outcome.runId(),actor);var ids=options.products().stream().collect(java.util.stream.Collectors.toMap(p->p.productId(),p->p.itemId()));
        var selection=new vn.edu.wspm.backend.services.combo.generation.ProductSelection(List.of(
                new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(ids.get("selection-0"),4294967295L),
                new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(ids.get("selection-4"),2)),5,5);
        var preview=nativeCombos.preview(outcome.runId(),null,5,"five-products",actor,selection);
        assertEquals("READY",preview.status(),preview.issues().toString());assertEquals(1,preview.groups());
        assertEquals("429496730000.00",preview.proposals().getFirst().total());
        var inserted=nativeCombos.submit(preview.contextToken(),actor,false);assertEquals("INSERTED",inserted.status(),inserted.issues().toString());
        assertEquals(5,jdbc.queryForObject("SELECT max_product_count FROM COMBO_BATCH WHERE batch_id=?",Integer.class,inserted.batchId()));
        assertEquals(4294967295L,jdbc.queryForObject("SELECT MAX(i.quantity) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",Long.class,inserted.batchId()));
        String combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=?",String.class,inserted.batchId());
        var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
        jdbc.update("UPDATE PRODUCT SET current_price=101,version=version+1 WHERE product_id='selection-0'");
        assertTrue(reviews.detail(combo).assessment().allowedActions().isEmpty());
    }
    @Test void selectedPolicyRetainsAtomicFailuresUnknownReconcileAndConcurrentIdentity() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        long item=nativeCombos.options(run.runId(),actor).products().stream().filter(p->p.productId().equals("native-keyboard")).findFirst().orElseThrow().itemId();
        var selection=new vn.edu.wspm.backend.services.combo.generation.ProductSelection(List.of(new vn.edu.wspm.backend.services.combo.generation.ProductSelection.RequiredItem(item,2)),2,4);
        for(String table:List.of("COMBO_BATCH","COMBO","COMBO_ITEM","COMBO_SOURCE","COMBO_BATCH_WRITE_EVIDENCE")){
            int before=jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
            var preview=nativeCombos.preview(run.runId(),null,5,"selection-fault-"+table,actor,selection);
            assertEquals("ROLLED_BACK",nativeWithFault("INSERT INTO "+table+"(").submit(preview.contextToken(),actor,false).status());
            assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class));
        }
        var committed=nativeCombos.preview(run.runId(),null,5,"selection-unknown",actor,selection);
        assertEquals("UNKNOWN",nativeWithFault("AFTER_COMMIT").submit(committed.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(committed.contextToken(),actor,true).status());
        var absent=nativeCombos.preview(run.runId(),null,5,"selection-absent",actor,selection);
        assertEquals("UNKNOWN",nativeWithFault("AFTER_ROLLBACK").submit(absent.contextToken(),actor,false).status());
        assertEquals("UNKNOWN",nativeCombos.submit(absent.contextToken(),actor,true).status());
        var concurrent=nativeCombos.preview(run.runId(),null,5,"selection-concurrent",actor,selection);
        var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
        try{
            var a=pool.submit(()->{latch.await();return nativeCombos.submit(concurrent.contextToken(),actor,false);});
            var b=pool.submit(()->{latch.await();return nativeCombos.submit(concurrent.contextToken(),actor,false);});latch.countDown();
            assertEquals(Set.of("INSERTED","NO_OP"),Set.of(a.get(60,TimeUnit.SECONDS).status(),b.get(60,TimeUnit.SECONDS).status()));
        }finally{pool.shutdownNow();}
    }
    @Test void requiredCategoriesAllowDifferentConcreteProductsAndBindQuantityThroughReview() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        var options=nativeCombos.categoryOptions(run.runId(),actor);
        assertEquals(3,options.categories().stream().filter(c->c.categoryId().equals("temporal-cat")).findFirst().orElseThrow().productCount());
        var selection=new vn.edu.wspm.backend.services.combo.generation.CategorySelection(List.of(new vn.edu.wspm.backend.services.combo.generation.CategorySelection.RequiredCategory("temporal-cat",2)),2,4);
        var preview=nativeCombos.previewCategories(run.runId(),null,5,"categories",actor,selection);
        assertEquals("READY",preview.status(),preview.issues().toString());assertFalse(preview.anchors().isEmpty());
        var inserted=nativeCombos.submit(preview.contextToken(),actor,false);assertEquals("INSERTED",inserted.status(),inserted.issues().toString());
        var products=new java.util.HashSet<>(jdbc.queryForList("SELECT DISTINCT i.product_id FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=?",String.class,inserted.batchId()));
        assertEquals(products,preview.anchors().stream().map(a->a.productId()).collect(java.util.stream.Collectors.toSet()));
        for(String product:products){
            var byProduct=nativeCombos.previewCategories(run.runId(),product,20,"categories",actor,selection);
            assertEquals("READY",byProduct.status(),byProduct.issues().toString());
            assertEquals(preview.groups(),byProduct.groups());assertEquals(preview.contextToken(),byProduct.contextToken());
            assertTrue(byProduct.eligible()>0);
            assertTrue(byProduct.proposals().stream().allMatch(p->p.items().stream().anyMatch(i->i.productId().equals(product))));
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id JOIN PRODUCT p ON p.product_id=i.product_id WHERE c.batch_id=? AND i.quantity<>(CASE WHEN p.category_id='temporal-cat' THEN 2 ELSE 1 END)",Integer.class,inserted.batchId()));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM COMBO c WHERE c.batch_id=? AND NOT EXISTS(SELECT 1 FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id='native-keyboard')",Integer.class,inserted.batchId())>0);
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,true).status());
        var filtered=nativeCombos.previewCategories(run.runId(),"native-keyboard",1,"categories",actor,selection);
        assertEquals(preview.groups(),filtered.groups());assertEquals(preview.contextToken(),filtered.contextToken());
        var changed=new vn.edu.wspm.backend.services.combo.generation.CategorySelection(List.of(new vn.edu.wspm.backend.services.combo.generation.CategorySelection.RequiredCategory("temporal-cat",3)),2,4);
        assertThrows(ApplicationException.class,()->nativeCombos.previewCategories(run.runId(),null,5,"categories",actor,changed));
        var absent=new vn.edu.wspm.backend.services.combo.generation.CategorySelection(List.of(new vn.edu.wspm.backend.services.combo.generation.CategorySelection.RequiredCategory("not-in-run",1)),2,4);
        assertThrows(ApplicationException.class,()->nativeCombos.previewCategories(run.runId(),null,5,"unknown-category",actor,absent));
        String combo=jdbc.queryForObject("SELECT c.combo_id FROM COMBO c WHERE c.batch_id=? AND NOT EXISTS(SELECT 1 FROM COMBO_ITEM i WHERE i.combo_id=c.combo_id AND i.product_id='native-laptop') LIMIT 1",String.class,inserted.batchId());
        var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
        assertEquals("APPLIED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,detail.reviewToken()),actor).outcome());
    }
    @Test void customerTopKPersistsIndependentlyOfPreviewAndBindsRetryIdentity() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        var selection=new vn.edu.wspm.backend.services.combo.generation.CategorySelection(List.of(new vn.edu.wspm.backend.services.combo.generation.CategorySelection.RequiredCategory("temporal-cat",1)),2,4);
        jdbc.update("INSERT INTO CUSTOMER(customer_id,user_id,anonymous_code) VALUES('topk-customer','native-owner','topk-customer')");
        jdbc.update("INSERT INTO ORDERS(order_id,order_number,customer_id,ordered_at,status,source) VALUES('topk-order','topk-order','topk-customer','2026-01-01','COMPLETED','TEST')");
        jdbc.update("INSERT INTO ORDER_ITEM(order_id,product_id,quantity,list_unit_price,line_total,sku_snapshot,product_name_snapshot) VALUES('topk-order','native-laptop',1,100,100,'TEST','TEST')");
        for(int k:List.of(2,5)){
            String identity="customer-k-"+k;
            var preview=nativeCombos.previewCategories(run.runId(),null,1,identity,actor,selection,k);
            assertEquals("READY",preview.status(),preview.issues().toString());assertEquals(1,preview.proposals().size());assertTrue(preview.groups()>k);
            assertEquals(k,nativeCombos.decode(preview.contextToken()).resolvedRecommendationK());
            var stored=nativeCombos.submit(preview.contextToken(),actor,false);
            assertEquals("INSERTED",stored.status(),stored.issues().toString());assertEquals(preview.groups(),stored.storedGroups());
            assertEquals(k,jdbc.queryForObject("SELECT top_k FROM COMBO_BATCH WHERE batch_id=?",Integer.class,stored.batchId()));
            var changedPreview=nativeCombos.previewCategories(run.runId(),null,20,identity,actor,selection,k);
            assertEquals(preview.contextToken(),changedPreview.contextToken());assertEquals(preview.groups(),changedPreview.groups());
            assertEquals("NO_OP",nativeCombos.submit(changedPreview.contextToken(),actor,true).status());
            assertThrows(ApplicationException.class,()->nativeCombos.previewCategories(run.runId(),null,1,identity,actor,selection,k==2?5:2));
            var combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=? LIMIT 1",String.class,stored.batchId());
            var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
            assertEquals(Integer.toString(k),reviews.batches(0,100).items().stream().filter(b->b.batchId().equals(stored.batchId())).findFirst().orElseThrow().topK());
            for(String id:jdbc.queryForList("SELECT combo_id FROM COMBO WHERE batch_id=?",String.class,stored.batchId())){
                var d=reviews.detail(id);
                assertEquals("APPLIED",reviews.review(id,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,d.reviewToken()),actor).outcome());
            }
            comboPublication.publish(stored.batchId(),actor);
            assertEquals(k,customerCombos.topK("native-keyboard",null).items().size());
            assertEquals(k,customerCombos.personalized(actor.userId()).items().size());
            assertEquals(1,customerCombos.topK("native-keyboard",1).items().size());
            assertTrue(customerCombos.latest().size()<=2);

        }
    }
    @Test void signedContextIssuedBeforeCustomerKStillSubmitsAndReviewsWithOriginalFive() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        var selection=new vn.edu.wspm.backend.services.combo.generation.CategorySelection(List.of(new vn.edu.wspm.backend.services.combo.generation.CategorySelection.RequiredCategory("temporal-cat",1)),2,4);
        var preview=nativeCombos.previewCategories(run.runId(),null,1,"old-customer-k",actor,selection);
        String currentJson=new String(Base64.getUrlDecoder().decode(preview.contextToken().split("\\.")[0]),java.nio.charset.StandardCharsets.UTF_8);
        // Preserve numeric lexemes/BigDecimal scale from the original signed payload.
        String oldJson=currentJson.replace(",\"recommendationK\":5","");assertNotEquals(currentJson,oldJson);
        byte[] bytes=oldJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(comboEnvironment.getProperty("auth.jwt.secret").getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        mac.update("TV2_NATIVE_COMBO_CONTEXT_V1\0".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String oldToken=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(bytes));
        assertEquals(5,nativeCombos.decode(oldToken).resolvedRecommendationK());
        var stored=nativeCombos.submit(oldToken,actor,false);assertEquals("INSERTED",stored.status(),stored.issues().toString());
        assertEquals(5,jdbc.queryForObject("SELECT top_k FROM COMBO_BATCH WHERE batch_id=?",Integer.class,stored.batchId()));
        assertEquals("NO_OP",nativeCombos.submit(oldToken,actor,true).status());
        String combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=? LIMIT 1",String.class,stored.batchId());
        var detail=reviews.detail(combo);assertTrue(detail.assessment().allowedActions().contains("APPROVE"),detail.assessment().blockingIssues().toString());
    }
    private vn.edu.wspm.backend.web.auth.AdminPrincipal nativeActor(){
        jdbc.update("INSERT IGNORE INTO USER_ACCOUNT(user_id,username,password_hash,display_name,status) VALUES('native-owner','native-owner','fixture-not-login','Owner','ACTIVE')");
        jdbc.update("INSERT IGNORE INTO ROLE(role_id,code,name) VALUES('native-admin-role','ADMIN','Admin')");
        String role=jdbc.queryForObject("SELECT role_id FROM ROLE WHERE code='ADMIN'",String.class);
        jdbc.update("INSERT IGNORE INTO USER_ROLE(user_id,role_id) VALUES('native-owner',?)",role);
        return new vn.edu.wspm.backend.web.auth.AdminPrincipal("native-owner",List.of("ADMIN"));
    }
    private Outcome nativeRun(MiningAlgorithm algorithm) throws Exception {
        if(nativeSnapshot==null){
            jdbc.update("INSERT IGNORE INTO CATEGORY(category_id,name,slug,status) VALUES('native-laptops','Laptop','native-laptops','ACTIVE')");
            for(String p:List.of("native-laptop","native-keyboard","native-mouse","native-bag"))jdbc.update(
                    "INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price,status) VALUES(?,?,?,?,?,100,'ACTIVE')",
                    p,p,"Current "+p,p,p.equals("native-laptop")?"native-laptops":"temporal-cat");
            nativeSnapshot=snapshots.acquire(new MockMultipartFile("file","native-combo.csv","text/csv",new ClassPathResource("wdm/csv-v1/native-combo.csv").getContentAsByteArray())).snapshotId();
            validation.validate(nativeSnapshot);conversion.convert(nativeSnapshot);
        }
        var result=run(nativeSnapshot,algorithm,"ABSOLUTE",.1);publication.publish(result);return result;
    }
    @Test void bothEnginesNativeThreeKindsSaveBeyondTopKAndFreezeCatalog() throws Exception {
        var actor=nativeActor();
        for(var algorithm:MiningAlgorithm.values()){
            var run=nativeRun(algorithm);String key="native-three-"+algorithm;
            var preview=nativeCombos.preview(run.runId(),"native-laptop",1,key,actor);
            assertEquals("READY",preview.status());assertEquals(1,preview.topPatternIds().size());assertTrue(preview.groups()>1);
            var stored=nativeCombos.submit(preview.contextToken(),actor,false);
            assertEquals("INSERTED",stored.status(),stored.issues().toString());assertEquals(preview.groups(),stored.storedGroups());
            assertEquals(Set.of("SAME_ORDER_BUNDLE","SEQUENTIAL_CROSS_SELL","MIXED_SEQUENCE"),new HashSet<>(jdbc.queryForList("SELECT DISTINCT combo_kind FROM COMBO WHERE batch_id=?",String.class,stored.batchId())));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_ITEM i JOIN COMBO c ON c.combo_id=i.combo_id WHERE c.batch_id=? AND (i.quantity<>1 OR i.is_discount_eligible<>0)",Integer.class,stored.batchId()));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO WHERE batch_id=? AND (discount_type<>'NONE' OR reference_original_price<>reference_sale_price OR currency_code<>'VND')",Integer.class,stored.batchId()));
            assertThrows(ApplicationException.class,()->nativeCombos.preview(run.runId(),"native-laptop",2,key,actor));
            assertEquals(preview.contextToken(),nativeCombos.preview(run.runId(),"native-laptop",1,key,actor).contextToken());
            String combo=jdbc.queryForObject("SELECT combo_id FROM COMBO WHERE batch_id=? ORDER BY combo_id LIMIT 1",String.class,stored.batchId());
            var before=reviews.detail(combo);var old=jdbc.queryForMap("SELECT current_price,version,updated_at FROM PRODUCT WHERE product_id='native-laptop'");
            try{
                jdbc.update("UPDATE PRODUCT SET current_price=current_price+1,version=version+1 WHERE product_id='native-laptop'");
                assertTrue(reviews.detail(combo).assessment().allowedActions().isEmpty());
                var denied=reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("APPROVE",null,before.reviewToken()),actor);
                assertNotEquals("APPLIED",denied.outcome());
                assertEquals("NO_OP",nativeCombos.submit(preview.contextToken(),actor,false).status()); // frozen retry; no reprice
            }finally{jdbc.update("UPDATE PRODUCT SET current_price=?,version=?,updated_at=? WHERE product_id='native-laptop'",old.get("current_price"),old.get("version"),old.get("updated_at"));}
            var review=reviews.detail(combo);
            assertEquals("BLOCKED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("REJECT",null,review.reviewToken()),actor).outcome());
            assertEquals("APPLIED",reviews.review(combo,new vn.edu.wspm.backend.services.combo.review.ComboReview.Command("REJECT","Owner rejected",review.reviewToken()),actor).outcome());
            assertTrue(provider.acknowledge(run.runId(),run.token()));
        }
    }
    @Test void concurrentNativeGenerationIsOneInsertAndOneNoOp() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        var preview=nativeCombos.preview(run.runId(),"native-laptop",5,"native-concurrent",actor);
        var latch=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->{latch.await();return nativeCombos.submit(preview.contextToken(),actor,false);});
            var b=pool.submit(()->{latch.await();return nativeCombos.submit(preview.contextToken(),actor,false);});latch.countDown();
            var first=a.get(30,TimeUnit.SECONDS);var second=b.get(30,TimeUnit.SECONDS);
            assertEquals(Set.of("INSERTED","NO_OP"),Set.of(first.status(),second.status()));assertEquals(first.batchId(),second.batchId());
        }finally{pool.shutdownNow();}
    }
    @Test void nativeCommandsRejectForeignActorsInvalidKAnchorAndStaleCatalog() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        assertThrows(ApplicationException.class,()->nativeCombos.preview(run.runId(),"native-laptop",0,"invalid-k",actor));
        assertThrows(ApplicationException.class,()->nativeCombos.preview(run.runId(),"native-laptop",21,"invalid-k",actor));
        assertEquals("BLOCKED",nativeCombos.preview(run.runId(),"outside",5,"invalid-anchor",actor).status());
        var preview=nativeCombos.preview(run.runId(),"native-laptop",5,"native-stale",actor);
        assertThrows(ApplicationException.class,()->nativeCombos.submit(preview.contextToken(),new vn.edu.wspm.backend.web.auth.AdminPrincipal("foreign",List.of("ADMIN")),false));
        var old=jdbc.queryForMap("SELECT current_price,version,updated_at FROM PRODUCT WHERE product_id='native-laptop'");
        try{
            jdbc.update("UPDATE PRODUCT SET current_price=current_price+10,version=version+1 WHERE product_id='native-laptop'");
            assertEquals("CONFLICT",nativeCombos.submit(preview.contextToken(),actor,false).status());
        }finally{jdbc.update("UPDATE PRODUCT SET current_price=?,version=?,updated_at=? WHERE product_id='native-laptop'",old.get("current_price"),old.get("version"),old.get("updated_at"));}
    }
    @Test void nativeWriteFaultsRollbackAndUnknownReconcilesWithoutBlindRetry() throws Exception {
        var actor=nativeActor();var run=nativeRun(MiningAlgorithm.WSPM_PRETREE);
        for(String fault:List.of("INSERT INTO COMBO_BATCH(","INSERT INTO COMBO(","INSERT INTO COMBO_ITEM(","INSERT INTO COMBO_SOURCE(","INSERT INTO COMBO_BATCH_WRITE_EVIDENCE(")){
            var preview=nativeCombos.preview(run.runId(),"native-laptop",5,"fault-"+Math.abs(fault.hashCode()),actor);
            int before=jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
            var broken=nativeWithFault(fault);var outcome=broken.submit(preview.contextToken(),actor,false);
            assertEquals("ROLLED_BACK",outcome.status(),fault+":"+outcome);
            assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO c LEFT JOIN COMBO_BATCH b ON b.batch_id=c.batch_id WHERE b.batch_id IS NULL",Integer.class));
        }
        var committed=nativeCombos.preview(run.runId(),"native-laptop",5,"commit-unknown",actor);
        assertEquals("UNKNOWN",nativeWithFault("AFTER_COMMIT").submit(committed.contextToken(),actor,false).status());
        assertEquals("NO_OP",nativeCombos.submit(committed.contextToken(),actor,true).status());
        var absent=nativeCombos.preview(run.runId(),"native-laptop",5,"rollback-unknown",actor);
        assertEquals("UNKNOWN",nativeWithFault("AFTER_ROLLBACK").submit(absent.contextToken(),actor,false).status());
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
        assertEquals("UNKNOWN",nativeCombos.submit(absent.contextToken(),actor,true).status());
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class));
    }
    private vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration nativeWithFault(String fault){
        var fired=new java.util.concurrent.atomic.AtomicBoolean();
        javax.sql.DataSource faulty=new org.springframework.jdbc.datasource.AbstractDataSource(){
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var real=comboDataSource.getConnection();var writing=new java.util.concurrent.atomic.AtomicBoolean();
                return (java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.Connection.class},(proxy,method,args)->{
                    try{
                        if(method.getName().equals("commit")&&writing.get()&&fault.startsWith("AFTER_")&&fired.compareAndSet(false,true)){
                            if(fault.equals("AFTER_COMMIT"))real.commit();else real.rollback();throw new java.sql.SQLException("Controlled post-server commit/rollback reply failure");
                        }
                        Object value=method.invoke(real,args);
                        if(method.getName().equals("prepareStatement")&&args[0] instanceof String sql&&value instanceof java.sql.PreparedStatement ps){
                            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.PreparedStatement.class},(p,m,a)->{
                                try{
                                    Object result=m.invoke(ps,a);
                                    if(m.getName().equals("executeUpdate")&&sql.startsWith("INSERT INTO COMBO"))writing.set(true);
                                    if(m.getName().equals("executeUpdate")&&sql.startsWith(fault)&&fired.compareAndSet(false,true))throw new java.sql.SQLException("Controlled failure after real INSERT");
                                    return result;
                                }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                            });
                        }return value;
                    }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
            }
            @Override public java.sql.Connection getConnection(String u,String p) throws java.sql.SQLException{return getConnection();}
        };
        return new vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration(comboInput,nativePolicy,comboJson,faulty,
                comboEnvironment.getProperty("auth.jwt.secret"),queries);
    }
    @Test void multiDigitItemIdsPublishAndExportInNumericOrderForBothEngines() throws Exception {
        // Ten independent one-item sequences: exactly ten singleton patterns, each support=1.
        // The existing snapshot already allocated IDs 1..3, so this crosses the decimal 9/10 boundary.
        for (int n = 1; n <= 10; n++) {
            String product = "numeric-p" + n;
            jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES(?,?,?,?,'temporal-cat',999)", product, product, product, product);
        }
        String numericSnapshot = snapshots.acquire(new MockMultipartFile("file", "numeric-item-order.csv", "text/csv",
                new ClassPathResource("wdm/csv-v1/numeric-item-order.csv").getContentAsByteArray())).snapshotId();
        assertEquals("VALID", validation.validate(numericSnapshot).validationStatus()); conversion.convert(numericSnapshot);
        for (var algorithm : MiningAlgorithm.values()) {
            var outcome = run(numericSnapshot, algorithm, "ABSOLUTE", .1);
            assertEquals(10, outcome.result().patternCount());
            outcome.result().supports().forEach((p, s) -> { assertEquals(1, p.size()); assertEquals(1, p.length()); assertEquals(1, s); });
            var expectedIds = service.prepare(outcome).rows().stream().map(RunItemStatisticsProjector.Row::itemId).sorted().toList();
            assertEquals(10, expectedIds.size()); assertTrue(expectedIds.stream().anyMatch(id -> id >= 10));
            String digest = publication.publish(outcome);
            assertEquals(digest, publication.publish(outcome));
            assertTrue(provider.acknowledge(outcome.runId(), outcome.token()));
            var bundle = combos.requireEligible(outcome.runId());
            assertEquals(expectedIds, bundle.runItems().stream().map(i -> i.itemId()).toList());
            var actual = new TreeMap<String, Long>();
            bundle.patterns().forEach(p -> actual.put(p.pattern().canonicalPattern(), Long.parseLong(p.pattern().supportAbs())));
            assertEquals(expected(outcome), actual);
            var doc = new tools.jackson.databind.ObjectMapper().readTree(queries.export(outcome.runId(), "published"));
            var exportedIds = new ArrayList<Integer>(); doc.get("runItems").forEach(i -> exportedIds.add(i.get("itemId").asInt()));
            assertEquals(expectedIds, exportedIds); assertEquals(10, doc.get("patterns").size());
        }
    }
    @Test void zeroPatternsAreCompleteButNotComboEligible() throws Exception {
        var outcome=run(MiningAlgorithm.WSPM_PRETREE,"ABSOLUTE",100000);
        assertEquals(0,outcome.result().patternCount()); publication.publish(outcome);
        var data=queries.publishedBundle(outcome.runId());
        assertTrue(data.result().publishedReadable()); assertFalse(data.result().eligibleForCombo());
        assertTrue(data.patterns().isEmpty()); assertEquals(3,data.runItems().size());
        assertThrows(ApplicationException.class,() -> combos.requireEligible(outcome.runId()));
          int batchCount=jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class);
          var empty=nativeCombos.preview(outcome.runId(),null,5,"native-zero",nativeActor());
          assertEquals("EMPTY",empty.status());assertEquals(List.of("NO_PATTERNS"),empty.issues());assertNull(empty.contextToken());
          assertEquals(batchCount,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH",Integer.class));
        jdbc.update("UPDATE RUN_ITEM_STATISTIC SET sku_snapshot='Changed unused item' WHERE run_id=?",outcome.runId());
        assertThrows(ApplicationException.class,() -> queries.metadata(outcome.runId()));
    }
    @Test void publishedExportEnforcesWholeFileBudgetBeforeReturningBytes() throws Exception {
        var o=run();publication.publish(o);
        var jdbcReader=new vn.edu.wspm.backend.repositories.run.RunResultQueryRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var limited=new vn.edu.wspm.backend.services.run.query.RunResultQueryService(jdbcReader,new tools.jackson.databind.ObjectMapper(),4096);
        limited.publications(manifest);
        var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var failure=assertThrows(ApplicationException.class,() -> tx.execute(s -> limited.export(o.runId(),"published")));
        assertEquals("PAYLOAD_TOO_LARGE",failure.code());
        assertTrue(queries.export(o.runId(),"published").length>4096);
    }
    @Test void concurrentDeliveriesCommitOnePublication() throws Exception {
        var outcome=run();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(() -> publication.publish(outcome)); var b=pool.submit(() -> publication.publish(outcome));
            assertEquals(a.get(40,TimeUnit.SECONDS),b.get(40,TimeUnit.SECONDS));
        }
        assertEquals(expected(outcome).size(),parentCount(outcome.runId()));
    }
    @Test void publicationMarkerFailureRollsBackAllRows() throws Exception {
        var outcome=run();
        // Force final publication SQL failure after all three writers have run.
        jdbc.execute("CREATE TRIGGER reject_publication BEFORE UPDATE ON RUN_RESULT_PUBLICATION FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled failure'");
        try { assertThrows(RuntimeException.class,() -> publication.publish(outcome)); }
        finally { jdbc.execute("DROP TRIGGER reject_publication"); }
        assertEquals(0,parentCount(outcome.runId())); assertTrue(items(outcome.runId()).isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM RUN_ITEM_STATISTIC WHERE run_id=?",Integer.class,outcome.runId()));
        assertEquals("NOT_PUBLISHED",metadata.read(outcome.runId()).orElseThrow().publication().status());
        assertSame(outcome,provider.outcome(outcome.runId()).orElseThrow());
        publication.publish(outcome); assertTrue(queries.metadata(outcome.runId()).publishedReadable());
    }
    @ParameterizedTest @ValueSource(strings={"support","temporal","name","delete","version","provenance","extra-stat"})
    void corruptPublishedDataRejectsReadsAndConsumer(String kind) throws Exception {
        var o=run();publication.publish(o);String id=o.runId();
        switch(kind) {
            case "support" -> jdbc.update("UPDATE PATTERN SET support_abs=support_abs+1 WHERE run_id=?",id);
            case "temporal" -> jdbc.update("UPDATE PATTERN_ITEM i JOIN PATTERN p ON p.pattern_id=i.pattern_id SET i.item_id=99999 WHERE p.run_id=?",id);
            case "name" -> jdbc.update("UPDATE RUN_ITEM_STATISTIC SET product_name_snapshot='Changed' WHERE run_id=?",id);
            case "delete" -> jdbc.update("DELETE FROM PATTERN_ITEM WHERE pattern_id IN (SELECT pattern_id FROM PATTERN WHERE run_id=?)",id);
            case "version" -> jdbc.update("UPDATE RUN_RESULT_PUBLICATION SET manifest_version='unknown' WHERE run_id=?",id);
            case "provenance" -> jdbc.update("UPDATE RUN_PROVENANCE SET provenance_json=JSON_SET(provenance_json,'$.binding.rawSha256',?) WHERE run_id=?","e".repeat(64),id);
            case "extra-stat" -> jdbc.update("DELETE FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id DESC LIMIT 1",id);
        }
        assertThrows(ApplicationException.class,() -> queries.publishedBundle(id));
        assertThrows(ApplicationException.class,() -> queries.export(id,"published"));
        assertThrows(ApplicationException.class,() -> combos.requireEligible(id));
        assertThrows(ApplicationException.class,() -> comboInput.prepare(id));
        assertThrows(RuntimeException.class,() -> publication.publish(o));
        assertSame(o,provider.outcome(id).orElseThrow());
    }
    @Test void automaticConsumerPublishesThenReleasesCapacity() throws Exception {
        var o=run();int before=provider.retainedCount();
        try(var consumer=new vn.edu.wspm.backend.services.run.execution.RunResultPublicationConsumer(provider,publication)) {
            consumer.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            while(provider.outcome(o.runId()).isPresent() && System.nanoTime()<deadline) Thread.sleep(50);
            assertTrue(provider.outcome(o.runId()).isEmpty());
            assertTrue(provider.retainedCount()<before);
            assertTrue(queries.metadata(o.runId()).publishedReadable());
        }
    }
}
