package vn.edu.wspm.backend.db.review;

import java.sql.SQLException;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.*;

/** Explicit review/deployment callback, NOT a Spring bean and NOT a data repair. */
public final class NullablePatternMetricPreflight implements Callback {
    @Override public boolean supports(Event event,Context context) {
        return event==Event.BEFORE_EACH_MIGRATE && context.getMigrationInfo()!=null
                && context.getMigrationInfo().getVersion()!=null
                && "3".equals(context.getMigrationInfo().getVersion().toString());
    }
    @Override public boolean canHandleInTransaction(Event event,Context context) { return true; }
    @Override public String getCallbackName() { return "tv2-nullable-pattern-metric-preflight"; }
    @Override public void handle(Event event,Context context) {
        try {
            var c=context.getConnection();
            try(var q=c.createStatement();var rs=q.executeQuery("""
                SELECT COUNT(*) FROM PATTERN
                WHERE (pattern_weight IS NULL AND pattern_weight_policy IS NOT NULL)
                   OR (pattern_weight IS NOT NULL AND pattern_weight_policy IS NULL)
                   OR (pattern_weight_policy IS NOT NULL AND
                       (REGEXP_LIKE(pattern_weight_policy,'^[[:space:]]*$')
                        OR INSTR(pattern_weight_policy,CHAR(0))>0))
                """)) {
                rs.next();long count=rs.getLong(1);
                if(count!=0)throw new FlywayException("TV2_V3_PREFLIGHT_INVALID_METRIC_PAIR: rows="+count);
            }
            try(var q=c.createStatement();var rs=q.executeQuery("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema=DATABASE() AND table_name IN
                  ('SNAPSHOT_CONVERSION_SEAL','MINING_RUN_INPUT_BINDING','MINING_RUN_OWNERSHIP','MINING_RUN_COMPLETION')
                """)) {
                rs.next();if(rs.getInt(1)!=0)throw new FlywayException("TV2_V3_PREFLIGHT_SCHEMA_DRIFT");
            }
            try(var q=c.createStatement();var rs=q.executeQuery("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema=DATABASE() AND
                  ((table_name='MINING_RUN' AND column_name IN ('run_id','snapshot_id'))
                    OR (table_name='DATASET_SNAPSHOT' AND column_name='snapshot_id'))
                  AND column_type='varchar(40)' AND character_set_name='utf8mb4'
                  AND collation_name='utf8mb4_0900_ai_ci'
                """)) {
                rs.next();if(rs.getInt(1)!=3)throw new FlywayException("TV2_V3_PREFLIGHT_UNSUPPORTED_FK_PROFILE");
            }
        } catch(SQLException failure) {
            // No SQL/payload/credentials in the outward failure, no automatic repair.
            throw new FlywayException("TV2_V3_PREFLIGHT_READ_FAILED");
        }
    }
}
