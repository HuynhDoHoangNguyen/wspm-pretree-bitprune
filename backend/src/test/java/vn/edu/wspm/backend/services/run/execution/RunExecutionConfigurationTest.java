package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.edu.wspm.backend.config.RunExecutionConfiguration;
import vn.edu.wspm.backend.services.run.RunExecutionAdmission;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;
import vn.edu.wspm.backend.repositories.run.RunLifecycleRepository;

/** Real conditional Spring wiring without Boot datasource/Auth. */
class RunExecutionConfigurationTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(RunExecutionConfiguration.class)
            .withBean(jakarta.persistence.EntityManagerFactory.class, () -> mock(jakarta.persistence.EntityManagerFactory.class))
            .withBean(RunLifecycleRepository.class, () -> mock(RunLifecycleRepository.class))
            .withBean(MiningEngine.class, () -> mock(MiningEngine.class))
            .withBean(RunExecutionVerifier.class, () -> mock(RunExecutionVerifier.class));

    @Test void missingOrFalseEnabledFlagHasNoProviderAndNoExecutorLimitsBean() {
        runner.run(context -> { assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty());
            assertTrue(context.getBeansOfType(ExecutionLimits.class).isEmpty()); });
        runner.withPropertyValues("wdm.execution.enabled=false").run(context ->
                assertTrue(context.getBeansOfType(RunExecutionAdmission.class).isEmpty()));
    }
    @Test void enabledRegistersOneProviderWithConservativeDefaultsAndNoStartedJob() {
        runner.withPropertyValues("wdm.execution.enabled=true").run(context -> {
            assertEquals(1, context.getBeansOfType(RunExecutionAdmission.class).size());
            assertEquals(new ExecutionLimits(1, 1, 30000), context.getBean(ExecutionLimits.class));
            assertEquals(0, context.getBean(ManagedRunExecutionAdmission.class).retainedCount());
            verifyNoInteractions(context.getBean(MiningEngine.class), context.getBean(RunExecutionVerifier.class));
        });
    }
    @Test void invalidEnabledLimitsFailStartup() {
        for (String setting : new String[]{"wdm.execution.parallelism=0", "wdm.execution.queue-capacity=0", "wdm.execution.shutdown-grace-millis=-1"}) {
            runner.withPropertyValues("wdm.execution.enabled=true", setting).run(context -> assertNotNull(context.getStartupFailure()));
        }
    }
}
