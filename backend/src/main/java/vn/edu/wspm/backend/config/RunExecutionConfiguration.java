package vn.edu.wspm.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;
import vn.edu.wspm.backend.services.run.execution.*;
import vn.edu.wspm.backend.repositories.run.RunLifecycleRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "wdm.execution.enabled", havingValue = "true")
public class RunExecutionConfiguration {
    @Bean public LifecycleLimits lifecycleLimits(@Value("${wdm.execution.lease-millis:60000}") long lease,
            @Value("${wdm.execution.heartbeat-millis:10000}") long heartbeat,
            @Value("${wdm.execution.recovery-batch:100}") int batch) { return new LifecycleLimits(lease, heartbeat, batch); }
    @Bean public RunLifecycleService runLifecycleService(RunLifecycleRepository repository, LifecycleLimits limits) {
        return new RunLifecycleService(repository, limits);
    }
    @Bean public ExecutionLimits executionLimits(
            @Value("${wdm.execution.parallelism:1}") int parallelism,
            @Value("${wdm.execution.queue-capacity:1}") int queueCapacity,
            @Value("${wdm.execution.shutdown-grace-millis:30000}") long grace) {
        return new ExecutionLimits(parallelism, queueCapacity, grace);
    }
    @Bean(destroyMethod = "close")
    public ManagedRunExecutionAdmission managedRunExecutionAdmission(
            ExecutionLimits limits, MiningEngine engine, RunExecutionVerifier verifier, RunLifecycleService lifecycle) {
        return new ManagedRunExecutionAdmission(limits, engine, verifier, lifecycle);
    }
}
