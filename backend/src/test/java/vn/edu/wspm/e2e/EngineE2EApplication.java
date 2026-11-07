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
                while (context.isActive() && !java.nio.file.Files.exists(stop)) Thread.sleep(100);
                if (context.isActive()) context.close();
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
    }
}
