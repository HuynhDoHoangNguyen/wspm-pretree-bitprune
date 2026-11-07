package vn.edu.wspm.e2e;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;

/** Outside vn.edu.wspm.backend scan and absent from the packaged production JAR. */
@Configuration(proxyBeanMethods = false)
public class EngineE2ETestConfiguration {
    @Bean static EngineExecutionGate engineExecutionGate(@Value("${engine.e2e.directory}") String directory) {
        return new EngineExecutionGate(Path.of(directory));
    }
    @Bean(destroyMethod = "close") EngineRunEvidenceObserver engineRunEvidenceObserver(
            @Value("${engine.e2e.directory}") String directory, ManagedRunExecutionAdmission manager, JdbcTemplate jdbc) {
        return new EngineRunEvidenceObserver(Path.of(directory), manager, jdbc);
    }
}
