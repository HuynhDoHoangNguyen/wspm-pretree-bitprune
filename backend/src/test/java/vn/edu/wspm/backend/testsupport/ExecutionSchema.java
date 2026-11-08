package vn.edu.wspm.backend.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Additive V3/V4 on test-owned V2 fixtures only. Actual Flyway upgrades have separate integration tests. */
public final class ExecutionSchema {
    private ExecutionSchema() {}
    public static void apply(JdbcTemplate jdbc) throws IOException {
        applyOwnershipOnly(jdbc);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/migration/V4__mining_run_metadata_provenance.sql"));
            return null;
        });
    }
    /** Historical V2/V3 contracts with a fixed 45-table inventory, not current execution creation. */
    public static void applyOwnershipOnly(JdbcTemplate jdbc) throws IOException {
        jdbc.execute(new ClassPathResource("db/migration/V3__mining_run_execution_ownership.sql").getContentAsString(StandardCharsets.UTF_8));
    }
}
