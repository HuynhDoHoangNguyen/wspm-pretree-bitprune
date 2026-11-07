package vn.edu.wspm.e2e;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;
import vn.edu.wspm.backend.services.run.engine.MiningEngineRequest;

/** Bounded transparent delegate: records actual structural input, counts calls, optionally holds first call. */
public final class EngineExecutionGate implements BeanPostProcessor {
    private final Path directory;
    private final AtomicInteger calls = new AtomicInteger();
    public EngineExecutionGate(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof MiningEngine delegate)) return bean;
        return (MiningEngine) request -> {
            try {
                Files.createDirectories(directory);
                int call = calls.incrementAndGet();
                if (call > 2) throw new IllegalStateException("Unexpected engine replay/capacity overflow");
                Files.writeString(directory.resolve("input-" + request.algorithm() + ".spmf"), spmf(request), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.writeString(directory.resolve("CALLS"), Integer.toString(call));
                Files.writeString(directory.resolve("ENTERED"), "ready");
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
                while (call == 1 && Files.exists(directory.resolve("HOLD")) && !Files.exists(directory.resolve("RELEASE"))) {
                    if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new IllegalStateException("Test latch deadline");
                    Thread.sleep(25);
                }
                return delegate.execute(request);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("Test gate interrupted"); }
            catch (java.io.IOException failure) { throw new IllegalStateException("Test gate I/O unavailable"); }
        };
    }
    static String spmf(MiningEngineRequest request) {
        var text = new StringBuilder();
        for (var record : request.database().records()) {
            for (var itemset : record.sequence().itemsets()) {
                for (var item : itemset.items()) text.append(item.id()).append(' ');
                text.append("-1 ");
            }
            text.append("-2\n");
        }
        return text.toString();
    }
}
