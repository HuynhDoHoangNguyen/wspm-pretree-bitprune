package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;

/** Controlled DB confirmations, actual pool; successful full results must survive pending finalization. */
class ManagedRunLifecycleTest {
    final RunLifecycleService lifecycle = mock(RunLifecycleService.class);
    final RunExecutionVerifier verifier = mock(RunExecutionVerifier.class);
    final MiningEngine engine = mock(MiningEngine.class);
    final ExecutionOwnership owner = new ExecutionOwnership(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 180000);
    ManagedRunExecutionAdmission provider() {
        when(lifecycle.newOwnership()).thenReturn(owner); when(lifecycle.heartbeatMillis()).thenReturn(60000L);
        when(verifier.refreshMeasuredOwned(anyString(), any(), eq(owner), any())).thenAnswer(call ->
                new vn.edu.wspm.backend.services.run.MeasuredRunPreparation(call.getArgument(1), 0));
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        var ticks = new java.util.concurrent.atomic.AtomicLong();
        return new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier, executor, lifecycle,
                () -> ticks.getAndAdd(1_000_000_000_000L));
    }
    @Test void claimMustBeConfirmedBeforeAnyEngineCallAndFailureRemainsPending() throws Exception {
        try (var provider = provider()) {
            var reservation = provider.reserve("run", ExecutionTestInputs.input()); assertEquals(owner, reservation.ownership().orElseThrow());
            reservation.activate(); var outcome = provider.completion("run").orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals("CLAIM_NOT_CONFIRMED", outcome.failureCode()); assertFalse(outcome.lifecycleConfirmed());
            verifyNoInteractions(engine); assertFalse(provider.acknowledge("run", outcome.token()));
        }
    }
    @Test void successfulResultIsNeverDiscardedWhenTerminalCommitIsUnconfirmedOrLaterConfirmed() throws Exception {
        var input = ExecutionTestInputs.input(); var result = ExecutionTestInputs.result(input);
        when(lifecycle.claim("run", owner)).thenReturn(true); when(engine.execute(any())).thenReturn(result);
        try (var provider = provider()) {
            provider.reserve("run", input).activate();
            var first = provider.completion("run").orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertSame(result, first.result()); assertFalse(first.lifecycleConfirmed());
            assertEquals(input.binding(), first.persistenceContext().provenance().binding());
            assertFalse(provider.acknowledge("run", first.token())); assertEquals(1, provider.retainedCount());
            when(lifecycle.confirmSuccess(eq("run"), eq(owner), any())).thenReturn(true); provider.maintain();
            var latest = provider.outcome("run").orElseThrow(); assertTrue(latest.lifecycleConfirmed()); assertSame(result, latest.result());
            assertSame(first.persistenceContext(), latest.persistenceContext());
            assertEquals(1, provider.retainedCount()); verify(engine).execute(any());
            assertTrue(provider.acknowledge("run", latest.token()));
        }
    }
    @Test void verificationFailureUsesSafeCodeAndOnlyExplicitAckFollowsDurableFailure() throws Exception {
        when(lifecycle.claim("run", owner)).thenReturn(true);
        when(lifecycle.confirmFailure("run", owner, RunFailureCode.INPUT_REVERIFICATION_FAILED)).thenReturn(true);
        // Construct before overriding refresh stub because provider() supplies the default successful verifier.
        try (var provider = provider()) {
            when(verifier.refreshMeasuredOwned(anyString(), any(), eq(owner), any())).thenThrow(new IllegalStateException("private data"));
            provider.reserve("run", ExecutionTestInputs.input()).activate();
            var outcome = provider.completion("run").orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals("INPUT_REVERIFICATION_FAILED", outcome.failureCode()); assertTrue(outcome.lifecycleConfirmed());
            assertEquals(1, provider.retainedCount()); verifyNoInteractions(engine); assertFalse(outcome.toString().contains("private"));
            assertTrue(provider.acknowledge("run", outcome.token()));
        }
    }
    @Test void totalExcludesClaimAndFinalizationAndRetriesKeepOriginalSnapshotWithoutRemeasurement() throws Exception {
        var input = ExecutionTestInputs.input(); var result = ExecutionTestInputs.result(input);
        var clock = new java.util.concurrent.atomic.AtomicLong(9000000000L); // elapsed before worker is excluded
        when(lifecycle.newOwnership()).thenReturn(owner); when(lifecycle.heartbeatMillis()).thenReturn(60000L);
        when(lifecycle.claim("run", owner)).thenAnswer(call -> { clock.addAndGet(8000000000L); return true; });
        when(verifier.refreshMeasuredOwned(eq("run"), eq(input), eq(owner), any())).thenAnswer(call -> {
            clock.addAndGet(20); return new vn.edu.wspm.backend.services.run.MeasuredRunPreparation(input, 10);
        });
        when(engine.execute(any())).thenAnswer(call -> { clock.addAndGet(result.metrics().completeAlgorithmNanos() + 30); return result; });
        when(lifecycle.confirmSuccess(eq("run"), eq(owner), any())).thenAnswer(call -> { clock.addAndGet(7000000000L); return false; });
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier, executor, lifecycle, clock::get)) {
            provider.reserve("run", input).activate();
            var first = provider.completion("run").orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(result.metrics().completeAlgorithmNanos() + 50, first.metrics().totalExecutionNanos());
            assertEquals(10, first.metrics().datasetIoNanos()); assertFalse(first.lifecycleConfirmed());
            assertFalse(provider.acknowledge("run", first.token()));
            when(lifecycle.confirmSuccess(eq("run"), eq(owner), any())).thenReturn(true);
            provider.maintain(); var latest = provider.outcome("run").orElseThrow();
            assertTrue(latest.lifecycleConfirmed()); assertSame(first.metrics(), latest.metrics()); assertSame(result, latest.result());
            verify(lifecycle, times(2)).confirmSuccess("run", owner, first.metrics()); verify(engine).execute(any());
            assertEquals(1, provider.retainedCount());
        }
    }
}
