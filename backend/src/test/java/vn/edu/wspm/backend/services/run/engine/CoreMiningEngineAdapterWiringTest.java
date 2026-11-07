package vn.edu.wspm.backend.services.run.engine;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;

/** Small real Spring wiring context; no Boot datasource/security configuration. */
class CoreMiningEngineAdapterWiringTest {
    @Test void springSelectsPublicConstructorAndWiresOneWorkingEngineWithRealGate() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(RunConfigurationValidityGate.class, CoreMiningEngineAdapter.class); context.refresh();
            assertEquals(1, context.getBeansOfType(MiningEngine.class).size());
            var engine = context.getBean(MiningEngine.class);
            assertSame(context.getBean(CoreMiningEngineAdapter.class), engine);
            var database = new SpmfSequenceParser().parse(new StringReader("1 -1 -2\n"));
            for (var algorithm : MiningAlgorithm.values()) {
                assertEquals(1, engine.execute(new MiningEngineRequest(algorithm, database, MinimumSupport.absolute(.1))).patternCount());
                assertThrows(ApplicationException.class,
                        () -> engine.execute(new MiningEngineRequest(algorithm, database, MinimumSupport.absolute(0))));
            }
        }
    }
}
