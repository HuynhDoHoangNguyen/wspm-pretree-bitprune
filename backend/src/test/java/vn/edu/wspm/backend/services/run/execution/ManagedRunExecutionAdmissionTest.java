package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.run.RunExecutionAdmission.Reservation;
import vn.edu.wspm.backend.services.run.engine.MiningEngine;

/** Deterministic scheduler/resource tests using latches, not timing-based performance assertions. */
class ManagedRunExecutionAdmissionTest {
    static void await(CountDownLatch latch) { try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); } }
    static ManagedRunExecutionAdmission.Outcome outcome(ManagedRunExecutionAdmission provider, String id) throws Exception {
        return provider.completion(id).orElseThrow().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
    RunExecutionVerifier verifier() {
        var verifier = mock(RunExecutionVerifier.class);
        when(verifier.refresh(anyString(), any())).thenAnswer(call -> call.getArgument(1)); return verifier;
    }

    @Test void reservationConsumesCapacityWithoutStartingWorkAndCloseIsIdempotent() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier)) {
            var a = provider.reserve("a", input); var b = provider.reserve("b", input);
            assertEquals(2, provider.retainedCount()); assertTrue(provider.completion("a").isEmpty());
            var view = provider.capacitySnapshot();
            assertEquals(2, view.capacity()); assertEquals(2, view.used()); assertEquals(0, view.available());
            assertEquals(List.of("a", "b"), view.entries().stream().map(ManagedRunExecutionAdmission.CapacityEntry::runId).toList());
            assertEquals("RESERVED", view.entries().getFirst().state());
            assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
            assertEquals(view, new ManagedRunExecutionAdmission.CapacitySnapshot(2, 2, 0, false, view.observedAt(), view.entries()));
            assertEquals(2, provider.retainedCount()); // Observation never acknowledges or releases.
            assertThrows(ApplicationException.class, () -> provider.reserve("c", input));
            assertThrows(ApplicationException.class, () -> provider.reserve("a", input));
            assertFalse(provider.acknowledge("a", UUID.randomUUID()));
            verifyNoInteractions(engine, verifier);
            a.close(); a.close(); assertEquals(1, provider.retainedCount());
            assertThrows(ApplicationException.class, a::activate); b.close(); assertEquals(0, provider.retainedCount());
        }
    }

    @Test void workerAndQueueAreBoundedAndCompletedOutcomesHoldSlotsUntilTokenAcknowledgement() throws Exception {
        var input = ExecutionTestInputs.input(); var expected = ExecutionTestInputs.result(input); var verifier = verifier();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var calls = new AtomicInteger(); var active = new AtomicInteger(); var max = new AtomicInteger();
        var workerThread = new AtomicReference<String>();
        MiningEngine engine = request -> {
            workerThread.set(Thread.currentThread().getName()); int n = active.incrementAndGet(); max.accumulateAndGet(n, Math::max);
            try { if (calls.incrementAndGet() == 1) { entered.countDown(); await(release); } return expected; }
            finally { active.decrementAndGet(); }
        };
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier)) {
            try {
                var first = provider.reserve("first", input); first.activate(); first.activate(); first.close(); await(entered);
                var second = provider.reserve("second", input); second.activate(); second.close();
                assertEquals(1, calls.get()); assertEquals(ManagedRunExecutionAdmission.State.SUBMITTED, provider.state("second").orElseThrow());
                assertThrows(ApplicationException.class, () -> provider.reserve("overflow", input));
                release.countDown(); var a = outcome(provider, "first"); var b = outcome(provider, "second");
                assertSame(expected, a.result()); assertSame(expected, b.result()); assertEquals(2, calls.get()); assertEquals(1, max.get());
                assertTrue(workerThread.get().startsWith("wspm-mining-")); assertEquals(2, provider.retainedCount());
                assertThrows(ApplicationException.class, () -> provider.reserve("full-completions", input));
                assertFalse(provider.acknowledge("first", UUID.randomUUID())); assertEquals(2, provider.retainedCount());
                assertTrue(provider.acknowledge("first", a.token())); assertTrue(provider.outcome("first").isEmpty());
                assertFalse(provider.acknowledge("first", a.token())); first.activate(); assertEquals(2, calls.get());
                var replacement = provider.reserve("replacement", input); replacement.close();
                assertTrue(provider.acknowledge("second", b.token())); assertEquals(0, provider.retainedCount());
            } finally { release.countDown(); }
        }
    }

    @Test void failedCommitVerificationReleasesUnactivatedCapacityAndNeverInvokesEngine() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        doThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE)).when(verifier).requireCommitted(anyString(), any());
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier)) {
            var reservation = provider.reserve("uncommitted", input);
            assertThrows(ApplicationException.class, reservation::activate); reservation.close();
            assertEquals(0, provider.retainedCount()); verifyNoInteractions(engine);
        }
    }

    @Test void workerReverificationFailureRetainsSafeFailureWithoutCallingEngine() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        when(verifier.refresh(anyString(), any())).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier)) {
            provider.reserve("tamper", input).activate(); var result = outcome(provider, "tamper");
            assertEquals(ManagedRunExecutionAdmission.State.FAILED, result.state()); assertNull(result.result());
            assertEquals("EXECUTION_FAILED", result.failureCode()); assertEquals(1, provider.retainedCount());
            verifyNoInteractions(engine); assertTrue(provider.acknowledge("tamper", result.token()));
        }
    }

    @Test void engineFailureIsRetainedAndNotRetriedOrExposedAsSuccess() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        when(engine.execute(any())).thenThrow(new IllegalStateException("private-sensitive-message"));
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier)) {
            var reservation = provider.reserve("failure", input); reservation.activate(); reservation.activate(); reservation.close();
            var result = outcome(provider, "failure"); assertNull(result.result()); assertFalse(result.toString().contains("private"));
            assertEquals("EXECUTION_FAILED", result.failureCode()); verify(engine).execute(any());
        }
    }

    @Test void executorRejectionReleasesSlotWithoutCallerRunsFallback() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        executor.shutdown();
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier, executor)) {
            var reservation = provider.reserve("rejected", input);
            assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(ApplicationException.class, reservation::activate).errorCode());
            reservation.close(); assertEquals(0, provider.retainedCount()); verifyNoInteractions(engine);
        }
    }

    @Test void closeDuringActivationVerificationPreventsSubmissionAndGenerationReuseDoesNotLoseNewSlot() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return null; }).when(verifier).requireCommitted(anyString(), any());
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier);
             var caller = Executors.newSingleThreadExecutor()) {
            var old = provider.reserve("same-id", input);
            var activation = caller.submit(() -> assertThrows(ApplicationException.class, old::activate));
            try {
                await(entered); old.close(); var replacement = provider.reserve("same-id", input);
                release.countDown(); activation.get(10, TimeUnit.SECONDS);
                assertEquals(1, provider.retainedCount()); replacement.close(); assertEquals(0, provider.retainedCount());
                verifyNoInteractions(engine);
            } finally { release.countDown(); }
        }
    }

    @Test void simultaneousActivationOnlySubmitsOnce() throws Exception {
        var input = ExecutionTestInputs.input(); var expected = ExecutionTestInputs.result(input);
        var engine = mock(MiningEngine.class); when(engine.execute(any())).thenReturn(expected); var verifier = verifier();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return null; }).when(verifier).requireCommitted(anyString(), any());
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 1000), engine, verifier);
             var caller = Executors.newSingleThreadExecutor()) {
            var reservation = provider.reserve("one", input); var activation = caller.submit(reservation::activate);
            try {
                await(entered); assertThrows(ApplicationException.class, reservation::activate);
                release.countDown(); activation.get(10, TimeUnit.SECONDS); outcome(provider, "one");
                reservation.activate(); verify(engine).execute(any());
            } finally { release.countDown(); }
        }
    }

    @Test void shutdownReleasesReservationsRejectsNewWorkAndDrainsAlreadyActivatedWorkWithoutFakeCancellation() throws Exception {
        var input = ExecutionTestInputs.input(); var expected = ExecutionTestInputs.result(input); var verifier = verifier();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        MiningEngine engine = request -> { entered.countDown(); await(release); return expected; };
        var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 2, 0), engine, verifier);
        try {
            var active = provider.reserve("active", input); active.activate(); await(entered);
            var queued = provider.reserve("queued", input); queued.activate(); provider.reserve("unactivated", input);
            provider.close(); provider.close();
            assertEquals(2, provider.retainedCount()); assertTrue(provider.state("unactivated").isEmpty());
            assertThrows(ApplicationException.class, () -> provider.reserve("after-stop", input));
            assertEquals(ManagedRunExecutionAdmission.State.RUNNING, provider.state("active").orElseThrow());
            release.countDown(); outcome(provider, "active"); outcome(provider, "queued");
        } finally { release.countDown(); provider.close(); }
    }

    @Test void malformedLimitsFailBeforeExecutorAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLimits(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLimits(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionLimits(1, 1, -1));
        assertThrows(ArithmeticException.class, () -> new ExecutionLimits(Integer.MAX_VALUE, 1, 0));
    }

    @Test void twoWorkersActuallyRunInParallelWhileThirdJobRemainsBoundedInQueue() throws Exception {
        var input = ExecutionTestInputs.input(); var expected = ExecutionTestInputs.result(input);
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        var calls = new AtomicInteger(); var active = new AtomicInteger(); var max = new AtomicInteger();
        MiningEngine engine = request -> {
            calls.incrementAndGet(); int n = active.incrementAndGet(); max.accumulateAndGet(n, Math::max);
            try { entered.countDown(); await(release); return expected; } finally { active.decrementAndGet(); }
        };
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(2, 1, 1000), engine, verifier())) {
            try {
                provider.reserve("one", input).activate(); provider.reserve("two", input).activate(); await(entered);
                assertEquals(2, active.get()); provider.reserve("three", input).activate();
                assertEquals(ManagedRunExecutionAdmission.State.SUBMITTED, provider.state("three").orElseThrow());
                assertEquals(2, calls.get()); assertThrows(ApplicationException.class, () -> provider.reserve("four", input));
                release.countDown(); outcome(provider, "one"); outcome(provider, "two"); outcome(provider, "three");
                assertEquals(2, max.get()); assertEquals(3, calls.get()); assertEquals(3, provider.retainedCount());
            } finally { release.countDown(); }
        }
    }

    @Test void shutdownRacingWithActivationVerificationPreventsSubmissionWithoutLeakingSlot() throws Exception {
        var input = ExecutionTestInputs.input(); var engine = mock(MiningEngine.class); var verifier = verifier();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { entered.countDown(); await(release); return null; }).when(verifier).requireCommitted(anyString(), any());
        try (var provider = new ManagedRunExecutionAdmission(new ExecutionLimits(1, 1, 0), engine, verifier);
             var caller = Executors.newSingleThreadExecutor()) {
            var reservation = provider.reserve("stopping", input);
            var activation = caller.submit(() -> assertThrows(ApplicationException.class, reservation::activate));
            try {
                await(entered); provider.close(); assertEquals(0, provider.retainedCount());
                release.countDown(); activation.get(10, TimeUnit.SECONDS); reservation.close();
                verifyNoInteractions(engine); assertThrows(ApplicationException.class, () -> provider.reserve("new", input));
            } finally { release.countDown(); }
        }
    }
}
