package vn.edu.wspm.backend.services.run.execution;

import java.io.StringReader;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.RunConfigurationValidityGate;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.*;

/** Synthetic unit fixture: real core models/calculators, placeholder publication descriptors; no storage proof. */
final class ExecutionTestInputs {
    static final String SNAPSHOT = "550e8400-e29b-41d4-a716-446655440000";
    static PreparedRun input() throws Exception {
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 1 -1 -2\n1 -1 2 -1 -2\n1 2 -1 -2\n"));
        var config = MinimumSupport.absolute(.1);
        var publication = new ConversionArtifactResult(SNAPSHOT, SNAPSHOT, "a".repeat(64),
                new ConversionArtifactResult.ArtifactFile("private-sequence", "b".repeat(64), 10),
                new ConversionArtifactResult.ArtifactFile("private-mapping", "c".repeat(64), 10),
                new ConversionArtifactResult.Statistics(3, 1, 2, 5, 2, 7, 2, 3, 7.0 / 3));
        return new PreparedRun(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, config),
                new MinimumSupportResolver().resolve(config, db.size()),
                new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(db)),
                new PreparedRun.Binding(publication, "CSV", "synthetic", "private-raw", "d".repeat(64)));
    }
    static MiningEngineResult result(PreparedRun input) {
        return new CoreMiningEngineAdapter(new RunConfigurationValidityGate()).execute(input.engineRequest());
    }
}
