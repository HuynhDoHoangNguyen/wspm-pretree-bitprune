package vn.edu.wspm.backend.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Additive V3 on test-owned V2 fixtures only. Actual Flyway upgrade has a separate integration test. */
public final class ExecutionSchema {
    private ExecutionSchema() {}
    public static void apply(JdbcTemplate jdbc) throws IOException {
        jdbc.execute(new ClassPathResource("db/migration/V3__mining_run_execution_ownership.sql").getContentAsString(StandardCharsets.UTF_8));
    }
}
