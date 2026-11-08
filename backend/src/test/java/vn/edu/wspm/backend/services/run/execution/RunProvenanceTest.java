package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.services.dataset.conversion.*;
import vn.edu.wspm.backend.services.run.PreparedRun;
import vn.edu.wspm.backend.services.run.persistence.*;

class RunProvenanceTest {
    final JsonMapper json = JsonMapper.builder().build();

    @Test void jsonRoundTripPreservesDescriptorsVersionsCountsAndExactBinding() throws Exception {
        var input = ExecutionTestInputs.input();
        var p = RunProvenance.from(input);
        String document = json.writeValueAsString(p);
        assertEquals(p, json.readValue(document, RunProvenance.class));
        assertEquals(input.binding(), p.binding());
        assertFalse(document.contains("database"));
        assertFalse(document.contains("itemWeights"));
        assertFalse(p.toString().contains("private"));
        assertThrows(UnsupportedOperationException.class, () -> p.policies().put("mapping", "changed"));
    }

    @Test void missingOrUnknownVersionAndPolicyAreRejected() throws Exception {
        var binding = ExecutionTestInputs.input().binding();
        for (String version : List.of("", "unknown")) assertThrows(IllegalArgumentException.class,
                () -> new RunProvenance(version, ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES, binding));
        assertThrows(IllegalArgumentException.class, () -> new RunProvenance(RunProvenance.VERSION, "unknown", ConversionArtifactCodec.POLICIES, binding));
        assertThrows(IllegalArgumentException.class, () -> new RunProvenance(RunProvenance.VERSION, ConversionArtifactCodec.VERSION, Map.of(), binding));
        assertThrows(NullPointerException.class, () -> new RunProvenance(RunProvenance.VERSION, ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES, null));
    }

    @Test void malformedHashesDescriptorsAndCountsAreRejectedWithoutLeakingInput() throws Exception {
        var b = ExecutionTestInputs.input().binding(); var p = b.publication();
        var variants = List.of(
                new PreparedRun.Binding(p, "CSV", "synthetic", b.rawReference(), "bad-private-hash"),
                new PreparedRun.Binding(p, "UNKNOWN", "synthetic", b.rawReference(), b.rawSha256()),
                new PreparedRun.Binding(new ConversionArtifactResult(p.snapshotId(), p.attemptId(), p.validationSha256(),
                        new ConversionArtifactResult.ArtifactFile("private", "a".repeat(64), 0), p.mapping(), p.statistics()),
                        "CSV", "synthetic", b.rawReference(), b.rawSha256()),
                new PreparedRun.Binding(new ConversionArtifactResult(p.snapshotId(), p.attemptId(), p.validationSha256(), p.sequence(), p.mapping(),
                        new ConversionArtifactResult.Statistics(2147483648L, 1, 2147483647L, 2147483648L, 2, 2147483648L, 1, 1, 1)),
                        "CSV", "synthetic", b.rawReference(), b.rawSha256()));
        for (var variant : variants) {
            var error = assertThrows(IllegalArgumentException.class, () -> new RunProvenance(RunProvenance.VERSION,
                    ConversionArtifactCodec.VERSION, ConversionArtifactCodec.POLICIES, variant));
            assertFalse(error.getMessage().contains("private"));
        }
    }

    @Test void contextIsSmallRunBoundAndRedacted() throws Exception {
        var input = ExecutionTestInputs.input();
        var context = RunPersistenceContext.from("run", input);
        assertEquals("run", context.runId()); assertEquals(input.binding(), context.provenance().binding());
        assertFalse(context.toString().contains("private"));
        assertThrows(IllegalArgumentException.class, () -> RunPersistenceContext.from("", input));
    }
}
