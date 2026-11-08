package vn.edu.wspm.backend.services.combo.generation;
import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
class NativeComboMigrationTest {
    @Test void upgradeV4WithOldBatchKeepsRowsAndRerunIsNoOp(){
        try(var mysql=new OwnedMySql()){
            var ds=mysql.dataSource();var jdbc=new JdbcTemplate(ds);
            var config=Flyway.configure().dataSource(ds).locations("classpath:db/migration");
            assertEquals(4,config.target("4").load().migrate().migrationsExecuted);
            jdbc.update("INSERT INTO DATASET_SNAPSHOT(snapshot_id,source_type,source_name,validation_status,conversion_report_json,sequence_count) VALUES('old-snapshot','CSV','old.csv','VALID','{}',1)");
            jdbc.update("INSERT INTO MINING_RUN(run_id,snapshot_id,algorithm,status,dataset_source,minsup_mode,minsup_configured,effective_threshold,mean_w,pattern_count) VALUES('old-run','old-snapshot','WSPM_PRETREE','SUCCEEDED','CSV','ABSOLUTE',.1,.1,.5,0)");
            jdbc.update("INSERT INTO COMBO_BATCH(batch_id,run_id,ranking_policy,pattern_weight_policy,top_k) VALUES('old-batch','old-run','UNCONFIRMED','UNCONFIRMED',1)");
            var before=jdbc.queryForMap("SELECT * FROM COMBO_BATCH WHERE batch_id='old-batch'");
            var flyway=Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
            assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(before,jdbc.queryForMap("SELECT * FROM COMBO_BATCH WHERE batch_id='old-batch'"));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM COMBO_BATCH_WRITE_EVIDENCE",Integer.class));
        }
    }
}
