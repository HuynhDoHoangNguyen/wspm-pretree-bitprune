package vn.edu.wspm.backend.repositories;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.BackendApplication;
import vn.edu.wspm.backend.db.review.NullablePatternMetricPreflight;
import vn.edu.wspm.backend.services.combo.review.*;
import vn.edu.wspm.backend.services.combo.persistence.DraftBatchWrite.*;
import vn.edu.wspm.backend.services.combo.generation.ComboGenerationService;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static vn.edu.wspm.backend.services.combo.review.ComboReview.*;

/** Explicit test classpath launcher only. Never component-scanned or packaged in production. */
public final class Tv2BrowserApplication {
    static final Set<String> allowedBatches=new HashSet<>();
    static final Map<String,WriterEvidenceReviewAuthority.Inspection> reviewRoots=new HashMap<>();
    public static void main(String[] args)throws Exception {
        if(!"OWNED_DISPOSABLE_INT14".equals(System.getenv("TV2_INT14_MODE")))throw new IllegalStateException("TEST_ONLY_LAUNCHER");
        var config=JsonMapper.builder().build().readTree(System.getenv("SPRING_APPLICATION_JSON"));
        String url=config.get("spring.datasource.url").asString(),db=System.getenv("TV2_INT14_DATABASE");
        if(db==null||!db.matches("tv2_int14_[a-f0-9]{10}")||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/"+db+"\\?.*"))throw new IllegalStateException("UNOWNED_TEST_DATASOURCE");
        var ds=new DriverManagerDataSource(url,config.get("spring.datasource.username").asString(),config.get("spring.datasource.password").asString());
        var jdbc=new JdbcTemplate(ds);
        if(!db.equals(jdbc.queryForObject("SELECT DATABASE()",String.class))||jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)!=0)
            throw new IllegalStateException("TEST_DATABASE_NOT_EMPTY");
        Path base=Path.of("backend/migration-review").toAbsolutePath();
        Flyway.configure().dataSource(ds).locations("classpath:db/migration","filesystem:"+base.resolve("TV2-INT-08F"),"filesystem:"+base.resolve("INT-09_10"))
                .callbacks(new NullablePatternMetricPreflight()).cleanDisabled(true).load().migrate();
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug,status) VALUES('LCAT','TEST_FIXTURE Laptop','laptop','ACTIVE'),('ACAT','TEST_FIXTURE Accessory','accessory','ACTIVE')");
        jdbc.update("INSERT INTO ROLE(role_id,code,name) VALUES('customer-role','CUSTOMER','Customer'),('admin-role','ADMIN','Admin')");
        DraftBatchWriterMySqlTest f=null;
        var inspector=new WriterEvidenceReviewAuthority(ds,500,4*1024*1024,b->ComboReviewAuthority.Check.unavailable());
        var readTx=new TransactionTemplate(new DataSourceTransactionManager(ds));readTx.setReadOnly(true);
        readTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var batches=new ArrayList<String>();var combos=new ArrayList<String>();
        for(int n=0;n<8;n++) {
            var generation=new ComboGenerationMySqlTest();generation.f.ds=ds;generation.f.jdbc=jdbc;generation.fixture();f=generation.f;
            String batch="TEST_FIXTURE_BATCH_"+n;var fields=new HashMap<vn.edu.wspm.backend.services.combo.grouping.CandidateGrouping.StructureKey,Fields>();
            for(var e:f.request.fields().entrySet()) {
                var x=e.getValue();String id="T14-"+n+"-"+x.comboId();
                fields.put(e.getKey(),new Fields(id,"TEST_FIXTURE "+n+" "+x.name(),x.explanation(),x.currency(),x.referenceOriginalPrice(),x.referenceSalePrice(),x.priceEvidenceRef(),x.discountType(),x.items()));
                combos.add(id);
            }
            var grant=generation.grant;
            generation.grant=new ComboGenerationService.Grant(grant.fullPayloadDigest(),grant.purpose(),grant.purposeReference(),grant.evaluationPolicy(),grant.catalog(),
                    grant.catalogReference(),grant.evaluatedAt(),grant.ranking(),grant.anchorProductId(),batch,grant.createdBy(),fields,grant.generatorVersion(),grant.commercialPolicyReference(),grant.resolvedBusinessEvidence());
            var saved=generation.service().generate(generation.intent());
            if(!saved.status().equals("INSERTED"))throw new IllegalStateException("FIXTURE_WRITER_REJECTED");
            final String sample=fields.values().iterator().next().comboId();
            var root=readTx.execute(tx->inspector.inspect(new JdbcComboAdminReader(ds).detailCurrent(sample)));
            if(!root.valid())throw new IllegalStateException("FIXTURE_REVIEW_EVIDENCE_INVALID:"+root.issue());
            reviewRoots.put(batch,root);
            batches.add(batch);if(n!=0)allowedBatches.add(batch);
        }
        // Labels only; no change to writer's sealed run/input/receipt facts.
        String patternRun="TEST_FIXTURE_PATTERNS",empty="TEST_FIXTURE_EMPTY";
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('TEST_FIXTURE_PATTERN_S','CSV','TEST_FIXTURE_NOT_MINING_OUTPUT','VALID','{}',9007199254740995)");
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count,created_at) VALUES(?,'TEST_FIXTURE_PATTERN_S','WSPM_PRETREE','SUCCEEDED','TEST_FIXTURE_NOT_MINING_OUTPUT','ABSOLUTE',.1,.1,.5,23,'2026-11-06 01:00:00')",patternRun);
        for(int i=1;i<=2;i++)jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES(?,?,?,9007199254740993,9007199254740993,.5,?,?)",patternRun,i,f.products.get(i-1),i==1?"TEST_FIXTURE Laptop snapshot":"TEST_FIXTURE Mouse snapshot",i==1?"SKU-T14-L":"SKU-T14-M");
        var patterns=new ArrayList<Map<String,String>>();
        for(int i=0;i<23;i++) {
            String id=i==0?"18446744073709551615":i==1?"9007199254740993":Integer.toString(100+i);
            String canonical=i==0?"<(1 2)>":i==22?"<(1)>":"<"+"(1)".repeat(i)+"(2)>";
            String support=i==0?"9007199254740993":i==1?"8":"3";
            int sets=i==0||i==22?1:i+1,items=i==0?2:i==22?1:i+1;
            jdbc.update("INSERT INTO PATTERN(pattern_id,run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(?,?,?,SHA2(?,256),?,.5,.5,?,?,.5,'TEST_FIXTURE_UNAPPROVED_WEIGHT')",new BigDecimal(id),patternRun,canonical,canonical,new BigDecimal(support),items,sets);
            if(i==0)jdbc.update("INSERT INTO PATTERN_ITEM VALUES(?,1,1,1),(?,1,2,2)",new BigDecimal(id),new BigDecimal(id));
            else if(i==22)jdbc.update("INSERT INTO PATTERN_ITEM VALUES(?,1,1,1)",new BigDecimal(id));
            else for(int s=1;s<=sets;s++)jdbc.update("INSERT INTO PATTERN_ITEM VALUES(?,?,1,?)",new BigDecimal(id),s,s==sets?2:1);
            patterns.add(Map.of("id",id,"canonical",canonical,"support",support));
        }
        for(int i=0;i<23;i++) {
            String id=i==22?empty:"TEST_FIXTURE_OTHER_"+i;
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count,created_at) VALUES(?,'TEST_FIXTURE_PATTERN_S','WSPM_PRETREE','SUCCEEDED','TEST_FIXTURE_NOT_MINING_OUTPUT','ABSOLUTE',.1,.1,.5,0,'2020-01-01 00:00:00')",id);
        }
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count,created_at) VALUES('TEST_FIXTURE_NOT_READY','TEST_FIXTURE_PATTERN_S','WSPM_PRETREE','RUNNING','TEST_FIXTURE_NOT_MINING_OUTPUT','ABSOLUTE',.1,.1,.5,0,'2021-01-01 00:00:00')");
        var metadata=Map.of("label","TEST_FIXTURE_NOT_MINING_OUTPUT","comboCreation","COMBO_GENERATION_SERVICE_AND_EXECUTABLE_WRITER","patternRun",patternRun,"emptyRun",empty,"notReadyRun","TEST_FIXTURE_NOT_READY","writerRun",f.run,"batches",batches,"combos",combos,"patterns",patterns);
        Files.writeString(Path.of(System.getenv("TV2_INT14_METADATA")),JsonMapper.builder().build().writeValueAsString(metadata),StandardCharsets.UTF_8);
        // Assert current compiled application classes, rather than silently serving an old packaged JAR.
        if(!ComboManagementService.class.getProtectionDomain().getCodeSource().getLocation().toString().endsWith("/target/classes/"))throw new IllegalStateException("STALE_APPLICATION_CLASSPATH");
        new SpringApplicationBuilder(BackendApplication.class,ControlledPolicy.class).run(args);
    }
    @TestConfiguration(proxyBeanMethods=false)
    public static class ControlledPolicy {
        @Bean ComboReviewAuthority controlledReview(javax.sql.DataSource ds) {
            var inspector=new WriterEvidenceReviewAuthority(ds,500,4*1024*1024,b->ComboReviewAuthority.Check.unavailable());
            return new WriterEvidenceReviewAuthority(ds,500,4*1024*1024,b->{
                if(!allowedBatches.contains(b.row().batchId()))return ComboReviewAuthority.Check.unavailable();
                var current=inspector.inspect(b);var expected=reviewRoots.get(b.row().batchId());
                boolean verified=current.valid() && expected!=null && expected.writerRoot().equals(current.writerRoot()) && expected.protectedFacts().equals(current.protectedFacts());
                return new ComboReviewAuthority.Check(new Policy("NEXT04_CONTROLLED_REVIEW_POLICY_V1"),new Evidence(verified,verified),"TEST_FIXTURE_POLICY_VERSION_1");
            });
        }
    }
}
