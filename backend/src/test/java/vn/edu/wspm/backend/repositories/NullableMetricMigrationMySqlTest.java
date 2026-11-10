package vn.edu.wspm.backend.repositories;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.hibernate.cfg.Configuration;
import vn.edu.wspm.backend.db.review.NullablePatternMetricPreflight;
import vn.edu.wspm.backend.entities.*;
import vn.edu.wspm.backend.services.pattern.*;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
import javax.sql.DataSource;

/** Real versioned Flyway SQL, no bootstrap CREATE extraction, repair/baseline/clean or demo datasource. */
class NullableMetricMigrationMySqlTest {
    static Path review() {
        Path p=Path.of("backend/migration-review/TV2-INT-08F");
        return (Files.isDirectory(p)?p:Path.of("migration-review/TV2-INT-08F")).toAbsolutePath().normalize();
    }
    static Flyway flyway(DataSource ds,String target) {
        return Flyway.configure().dataSource(ds).resourceProvider(vn.edu.wspm.backend.testsupport.LegacyComboMigrations.provider(false))
                .target(target).callbacks(new NullablePatternMetricPreflight()).cleanDisabled(true).load();
    }
    static void fixture(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('S','CSV','fixture','VALID','{}',2)");
        jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES('R','S','WSPM_PRETREE','SUCCEEDED','fixture','ABSOLUTE',1,1,.5,1)");
        jdbc.update("INSERT INTO CATEGORY(category_id,name,slug) VALUES('C','Fixture','fixture')");
        jdbc.update("INSERT INTO PRODUCT(product_id,sku,name,slug,category_id,current_price) VALUES('P','CURRENT','Catalog','catalog','C',1)");
        jdbc.update("INSERT INTO RUN_ITEM_STATISTIC(run_id,item_id,product_id,occurrence_count,sequence_support,item_weight,product_name_snapshot,sku_snapshot) VALUES('R',1,'P',2,2,1,'Historic','HIST')");
        jdbc.update("INSERT INTO PATTERN(pattern_id,run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(10,'R','<(1)>',SHA2('<(1)>',256),2,1,1,1,1,.42,'UNCONFIRMED_REFERENCE')");
        jdbc.update("INSERT INTO PATTERN_ITEM VALUES(10,1,1,1)");
    }
    @Test void flywayUpgradePreservesDataReadersJsonAndOrmMappingAndRerunIsNoOp() throws Exception {
        try(var mysql=new OwnedMySql()) {
            var ds=mysql.dataSource();var jdbc=new JdbcTemplate(ds);
            assertEquals(2,flyway(ds,"2").migrate().migrationsExecuted);fixture(jdbc);
            var original=jdbc.queryForMap("SELECT * FROM PATTERN WHERE pattern_id=10");
            var f=flyway(ds,"3");assertEquals(1,f.migrate().migrationsExecuted);f.validate();
            assertEquals(original,jdbc.queryForMap("SELECT * FROM PATTERN WHERE pattern_id=10"));
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='PATTERN' AND index_name='idx_v2_pattern_run_rank'",Integer.class));
            jdbc.update("INSERT INTO PATTERN(pattern_id,run_id,canonical_pattern,canonical_pattern_hash,support_abs,support_ratio,weighted_support,pattern_item_count,itemset_count,pattern_weight,pattern_weight_policy) VALUES(11,'R','<(1)(1)>',SHA2('<(1)(1)>',256),2,1,1,2,2,NULL,NULL)");
            jdbc.update("INSERT INTO PATTERN_ITEM VALUES(11,1,1,1),(11,2,1,1)");
            jdbc.update("UPDATE MINING_RUN SET pattern_count=2 WHERE run_id='R'");
            var service=new PatternListService(new JdbcPatternListReader(ds));var page=service.list("R",0,20);
            assertNull(page.items().get(1).patternWeight());assertNull(page.items().get(1).patternWeightPolicy());assertFalse(page.weightFilter().enabled());
            assertEquals("MIXED_OR_MISSING_POLICY",page.weightFilter().reason());
            var detail=new PatternDetailService(new JdbcPatternDetailReader(ds)).detail("R","11");
            assertNull(detail.patternWeight());assertNull(detail.patternWeightPolicy());assertEquals(2,detail.itemsets().size());
            assertEquals("Historic",detail.itemsets().getFirst().items().getFirst().nameSnapshot());
            var mapper=new tools.jackson.databind.ObjectMapper();
            var json=mapper.readTree(mapper.writeValueAsString(detail));assertTrue(json.get("patternWeight").isNull());assertTrue(json.get("patternWeightPolicy").isNull());
            assertThrows(PatternReadException.class,()->service.list("R",0,20,new vn.edu.wspm.backend.dto.pattern.PatternFilterInput(List.of(),null,null,"0.1",null)));
            assertEquals(0,f.migrate().migrationsExecuted);f.validate();assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM PATTERN",Integer.class));
            assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('SNAPSHOT_CONVERSION_SEAL','MINING_RUN_INPUT_BINDING','MINING_RUN_OWNERSHIP','MINING_RUN_COMPLETION')",Integer.class));
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE PATTERN SET pattern_weight=.3 WHERE pattern_id=11"));
            validateOrmAndWriteNullPair(ds,jdbc);
        }
    }
    static void validateOrmAndWriteNullPair(DataSource ds,JdbcTemplate jdbc) {
        // Current RunEntity also maps WDM execution ownership. Apply that exact
        // additive runtime script for ORM compatibility, after historical Flyway
        // assertions above; its V3 identity is not mixed into the legacy chain.
        try (var connection=ds.getConnection()) {
            var resources=new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                    .getResources("classpath:db/migration/V3__*.sql");
            assertEquals(1,resources.length);
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,resources[0]);
        } catch (java.sql.SQLException | java.io.IOException e) {
            throw new IllegalStateException("ORM_COMPATIBILITY_SETUP_FAILED",e);
        }
        var cfg=new Configuration().addAnnotatedClass(PatternEntity.class).addAnnotatedClass(RunEntity.class);
        cfg.getProperties().put("hibernate.connection.datasource",ds);
        cfg.setProperty("hibernate.hbm2ddl.auto","validate");cfg.setProperty("hibernate.show_sql","false");
        try(var factory=cfg.buildSessionFactory();var session=factory.openSession()) {
            var transaction=session.beginTransaction();
            var pending=session.find(PatternEntity.class,11L);assertNull(pending.getWeight());assertNull(pending.getPatternWeightPolicy());
            var old=session.find(PatternEntity.class,10L);assertEquals(.42,old.getWeight());assertEquals("UNCONFIRMED_REFERENCE",old.getPatternWeightPolicy());
            old.setPatternMetric(null,null);session.flush();transaction.commit();
            assertNull(jdbc.queryForMap("SELECT pattern_weight,pattern_weight_policy FROM PATTERN WHERE pattern_id=10").get("pattern_weight_policy"));
        }
    }
    @Test void preflightRejectsInvalidLegacyPolicyBeforeV3DdlWithoutRepairingData() {
        try(var mysql=new OwnedMySql()) {
            var ds=mysql.dataSource();var jdbc=new JdbcTemplate(ds);flyway(ds,"2").migrate();fixture(jdbc);
            jdbc.update("UPDATE PATTERN SET pattern_weight_policy='   ' WHERE pattern_id=10");
            var error=assertThrows(FlywayException.class,()->flyway(ds,"3").migrate());
            String messages="";for(Throwable t=error;t!=null;t=t.getCause())messages+=Objects.toString(t.getMessage(),"");
            assertTrue(messages.contains("TV2_V3_PREFLIGHT_INVALID_METRIC_PAIR"));
            assertEquals("   ",jdbc.queryForObject("SELECT pattern_weight_policy FROM PATTERN WHERE pattern_id=10",String.class));
            assertEquals("NO",jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='PATTERN' AND column_name='pattern_weight'",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='SNAPSHOT_CONVERSION_SEAL'",Integer.class));
        }
    }
    @Test void emptySchemaChainRunsAllVersionsThroughFlywayAndConstraintsRemainActive() {
        try(var mysql=new OwnedMySql()) {
            var ds=mysql.dataSource();var jdbc=new JdbcTemplate(ds);var f=flyway(ds,"3");
            assertEquals(3,f.migrate().migrationsExecuted);f.validate();assertEquals("3",f.info().current().getVersion().toString());
            fixture(jdbc);
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO MINING_RUN_OWNERSHIP(run_id,fence,updated_at) VALUES('missing',0,CURRENT_TIMESTAMP(6))"));
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE PATTERN SET pattern_weight_policy=NULL WHERE pattern_id=10"));
            // Reuse E's DML fixture helpers against the schema created by REAL Flyway V3,
            // not its draft/bootstrap DDL. These are constraint fixtures, not publisher output.
            var fixtures=new PersistenceDraftMySqlTest();fixtures.jdbc=jdbc;fixtures.mysql=mysql;fixtures.run="R";
            String seal=fixtures.prepareInput();
            assertThrows(org.springframework.dao.DataAccessException.class,()->fixtures.uniqueSeal("S"));
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET raw_sha256=UNHEX(?) WHERE conversion_id=?","0".repeat(64),seal));
            byte[] oversized=new byte[262145];
            assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE SNAPSHOT_CONVERSION_SEAL SET manifest_bytes=?,seal_digest=UNHEX(?) WHERE conversion_id=?",oversized,PersistenceDraftMySqlTest.hash(oversized),seal));
            fixtures.createRun("SECOND","S");
            assertThrows(org.springframework.dao.DataAccessException.class,()->fixtures.input("SECOND","S",seal,"R".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            byte[] token=new byte[16];token[0]=1;fixtures.ownership(1,token);
            byte[] digest=jdbc.queryForObject("SELECT input_digest FROM MINING_RUN_INPUT_BINDING WHERE run_id='R'",byte[].class);
            assertThrows(org.springframework.dao.DataAccessException.class,()->fixtures.receipt(2,token,0,0,digest));
            fixtures.receipt(1,token,0,0,digest);
            assertThrows(org.springframework.dao.DataAccessException.class,()->fixtures.receipt(1,token,0,0,digest));
            assertEquals(0,f.migrate().migrationsExecuted);
        }
    }
}
