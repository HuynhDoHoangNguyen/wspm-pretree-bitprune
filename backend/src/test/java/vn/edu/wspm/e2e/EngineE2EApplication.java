package vn.edu.wspm.e2e;

import org.springframework.boot.SpringApplication;
import vn.edu.wspm.backend.BackendApplication;

/** Test runtime only. No controller and no component scan extension in the production application. */
public final class EngineE2EApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(new Class<?>[]{BackendApplication.class, EngineE2ETestConfiguration.class}, args);
        var stop = java.nio.file.Path.of(context.getEnvironment().getRequiredProperty("engine.e2e.directory"), "STOP");
        Thread.ofPlatform().daemon(true).name("test-only-stop").start(() -> {
            try {
                int handled = 0;
                var inbox = stop.resolveSibling("PROVIDER-REQUEST");
                while (context.isActive() && !java.nio.file.Files.exists(stop)) {
                    if (java.nio.file.Files.exists(inbox)) {
                        if (++handled > 1000 || java.nio.file.Files.size(inbox) > 128) throw new IllegalStateException("Provider inbox budget");
                        String[] request = java.nio.file.Files.readString(inbox).trim().split(" ");
                        if (request.length != 2 || !request[0].matches("[1-9][0-9]{0,3}")
                                || !request[1].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                            throw new IllegalArgumentException("Invalid provider inbox");
                        java.util.Map<String,Object> result = new java.util.LinkedHashMap<>();
                        result.put("runId", request[1]);
                        var admission = context.getBeanProvider(vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.class).getIfAvailable();
                        result.put("outcomeRetained", admission != null && admission.outcome(request[1]).isPresent());
                        try {
                            result.put("bundle", context.getBean(vn.edu.wspm.backend.services.combo.PublishedMiningResultProvider.class).requireEligible(request[1]));
                            result.put("status", "OK");
                        } catch (vn.edu.wspm.backend.exception.ApplicationException e) { result.put("status", e.code()); }
                        var bytes = context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsBytes(result);
                        if (bytes.length > 8 * 1024 * 1024) throw new IllegalStateException("Provider evidence budget");
                        EngineRunEvidenceObserver.atomic(stop.resolveSibling("provider-" + request[0] + ".json"), bytes);
                        java.nio.file.Files.delete(inbox);
                    }
                    Thread.sleep(100);
                }
                if (context.isActive()) context.close();
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            catch (Exception failure) {
                try { java.nio.file.Files.writeString(stop.resolveSibling("PROVIDER-ERROR"), failure.getClass().getSimpleName()); }
                catch (java.io.IOException ignored) { /* runner deadline reports failed observation */ }
            }
        });
    }
}
