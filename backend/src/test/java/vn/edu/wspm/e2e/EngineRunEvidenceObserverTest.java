package vn.edu.wspm.e2e;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.wspm.backend.services.run.engine.*;
import vn.edu.wspm.backend.services.run.execution.*;
import vn.edu.wspm.config.MinimumSupport;
import vn.edu.wspm.io.SpmfSequenceParser;

class EngineRunEvidenceObserverTest {
    @TempDir Path directory;
    final String id = "10000000-0000-4000-8000-000000000001";
    @Test void boundedCanonicalInboxRejectsTraversalDuplicatesAndThirdRun() {
        assertEquals(List.of(id), EngineRunEvidenceObserver.ids(id + "\n"));
        for (String input : List.of("../outside", id + "\n" + id, "x".repeat(1025), id + "\n" + id.replace("001", "002") + "\n" + id.replace("001", "003")))
            assertThrows(IllegalArgumentException.class, () -> EngineRunEvidenceObserver.ids(input));
    }
    @Test void exportsOriginalResultOnceWithoutAcknowledgementOrRemining() throws Exception {
        var manager = mock(ManagedRunExecutionAdmission.class); var jdbc = mock(JdbcTemplate.class);
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 -2\n1 -1 2 -1 -2\n"));
        var result = new CoreMiningEngineAdapter(new vn.edu.wspm.backend.services.run.RunConfigurationValidityGate()).execute(new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, MinimumSupport.absolute(0.1)));
        var capture = RunMetricsCapture.from(result, 3, result.metrics().completeAlgorithmNanos() + 10);
        var artifact = new vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult.ArtifactFile("synthetic", "b".repeat(64), 10);
        var publication = new vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult(id, id, "a".repeat(64), artifact, artifact,
                new vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult.Statistics(2, 1, 1, 3, 2, 4, 2, 2, 2));
        var provenance = new vn.edu.wspm.backend.services.run.persistence.RunProvenance(
                vn.edu.wspm.backend.services.run.persistence.RunProvenance.VERSION,
                vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.VERSION,
                vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactCodec.POLICIES,
                new vn.edu.wspm.backend.services.run.PreparedRun.Binding(publication, "CSV", "synthetic", "private-raw", "c".repeat(64)));
        var outcome = new ManagedRunExecutionAdmission.Outcome(id, UUID.randomUUID(), ManagedRunExecutionAdmission.State.COMPLETED, result, null, true, capture,
                new vn.edu.wspm.backend.services.run.persistence.RunPersistenceContext(id, provenance));
        when(manager.outcome(id)).thenReturn(Optional.of(outcome)); when(manager.retainedCount()).thenReturn(1);
        var row = new HashMap<String, Object>(); row.put("run_id", id); row.put("algorithm", "WSPM_PRETREE"); row.put("status", "SUCCEEDED");
        row.put("execution_owner_id", UUID.randomUUID().toString()); row.put("execution_attempt_id", UUID.randomUUID().toString());
        when(jdbc.queryForList(anyString(), eq(id))).thenReturn(List.of(row));
        Files.writeString(directory.resolve("INBOX"), id);
        try (var observer = new EngineRunEvidenceObserver(directory, manager, jdbc, false)) { observer.observe(); observer.observe(); }
        assertFalse(Files.exists(directory.resolve("OBSERVER-ERROR")));
        var p = new Properties(); try (var in = Files.newInputStream(directory.resolve(id + ".properties"))) { p.load(in); }
        assertEquals(Long.toString(result.patternCount()), p.getProperty("map.count"));
        var map = Files.readAllLines(directory.resolve(id + ".map.tsv")); assertEquals(result.patternCount(), map.size());
        assertTrue(map.stream().anyMatch(line -> line.startsWith("<(1 2)>\t")));
        assertTrue(map.stream().anyMatch(line -> line.startsWith("<(1)(2)>\t")));
        verify(manager, times(1)).outcome(id); verify(manager).retainedCount(); verifyNoMoreInteractions(manager);
    }
    @Test void transparentGateRecordsActualInputAndNeverInvokesDelegateAgainForThirdCall() throws Exception {
        var delegate = mock(MiningEngine.class); var gate = new EngineExecutionGate(directory);
        var wrapper = (MiningEngine) gate.postProcessAfterInitialization(delegate, "engine");
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 -2\n1 -1 2 -1 -2\n"));
        var first = new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, MinimumSupport.absolute(0.1));
        var second = new MiningEngineRequest(MiningAlgorithm.WSPM_BITPRUNE, db, MinimumSupport.absolute(0.1));
        when(delegate.execute(any())).thenAnswer(call -> new CoreMiningEngineAdapter(
                new vn.edu.wspm.backend.services.run.RunConfigurationValidityGate()).execute(call.getArgument(0)));
        var original = wrapper.execute(first); wrapper.execute(second);
        var capture = new Properties();
        try (var in = Files.newInputStream(directory.resolve("original-1.properties"))) { capture.load(in); }
        assertEquals("1", capture.getProperty("call"));
        assertEquals("WSPM_PRETREE", capture.getProperty("algorithm"));
        assertEquals(EngineRunEvidenceObserver.bits(0.1), capture.getProperty("configured.bits"));
        assertEquals(Long.toString(original.patternCount()), capture.getProperty("map.count"));
        assertEquals(db.records(), new SpmfSequenceParser().parse(directory.resolve("original-1.spmf")).records());
        assertFalse(Files.exists(directory.resolve("original-1.properties.tmp")));
        assertEquals(db.records(), new SpmfSequenceParser().parse(directory.resolve("input-WSPM_PRETREE.spmf")).records());
        assertThrows(IllegalStateException.class, () -> wrapper.execute(first));
        verify(delegate).execute(first); verify(delegate).execute(second); verifyNoMoreInteractions(delegate);
    }
    @Test void consecutiveSameAlgorithmCallsKeepDistinctCapturesAndLegacyInput() throws Exception {
        var delegate = mock(MiningEngine.class);
        var db = new SpmfSequenceParser().parse(new StringReader("1 2 -1 -2\n1 -1 2 -1 -2\n"));
        var request = new MiningEngineRequest(MiningAlgorithm.WSPM_PRETREE, db, MinimumSupport.absolute(0.1));
        var result = new CoreMiningEngineAdapter(new vn.edu.wspm.backend.services.run.RunConfigurationValidityGate()).execute(request);
        when(delegate.execute(request)).thenReturn(result);
        var wrapper = (MiningEngine)new EngineExecutionGate(directory, 4).postProcessAfterInitialization(delegate, "engine");
        assertSame(result, wrapper.execute(request)); assertSame(result, wrapper.execute(request));
        assertTrue(Files.exists(directory.resolve("input-WSPM_PRETREE.spmf")));
        assertTrue(Files.exists(directory.resolve("input-WSPM_PRETREE-2.spmf")));
        assertTrue(Files.exists(directory.resolve("original-1.properties")));
        assertTrue(Files.exists(directory.resolve("original-2.properties")));
        verify(delegate, times(2)).execute(request);
    }
}
