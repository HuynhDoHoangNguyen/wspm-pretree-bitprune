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
        var outcome = new ManagedRunExecutionAdmission.Outcome(id, UUID.randomUUID(), ManagedRunExecutionAdmission.State.COMPLETED, result, null, true, capture);
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
        wrapper.execute(first); wrapper.execute(second);
        assertEquals(db.records(), new SpmfSequenceParser().parse(directory.resolve("input-WSPM_PRETREE.spmf")).records());
        assertThrows(IllegalStateException.class, () -> wrapper.execute(first));
        verify(delegate).execute(first); verify(delegate).execute(second); verifyNoMoreInteractions(delegate);
    }
}
