package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionSystemException;
import vn.edu.wspm.backend.repositories.run.RunLifecycleRepository;

class RunLifecycleServiceTest {
    final RunLifecycleRepository repository = mock(RunLifecycleRepository.class);
    final RunLifecycleService service = new RunLifecycleService(repository, new LifecycleLimits(60000, 10000, 100));
    final ExecutionOwnership owner = service.newOwnership();
    final RunMetricsCapture metrics = new RunMetricsCapture(1, 2, 3, 6, 10, 99, 4, 5);
    RunLifecycleRepository.Observation observed(String state, boolean live, String code) {
        return new RunLifecycleRepository.Observation(state, owner.ownerId(), owner.attemptId(), live, code, metrics.persisted());
    }
    @Test void zeroClaimNeverReplaysAlreadyRunningAttempt() {
        when(repository.claim("run", owner)).thenReturn(0);
        assertFalse(service.claim("run", owner)); verify(repository, never()).read(anyString());
    }
    @Test void uncertainClaimCanOnlyConfirmMatchingLiveRunningOriginalInvocation() {
        when(repository.claim("run", owner)).thenThrow(new TransactionSystemException("private"));
        when(repository.read("run")).thenReturn(observed("RUNNING", true, null)); assertTrue(service.claim("run", owner));
        when(repository.read("run")).thenReturn(observed("RUNNING", false, null)); assertFalse(service.claim("run", owner));
        when(repository.read("run")).thenReturn(new RunLifecycleRepository.Observation("RUNNING", UUID.randomUUID().toString(), owner.attemptId(), true, null, null));
        assertFalse(service.claim("run", owner));
    }
    @Test void successWithLostCommitAcknowledgementIsReadBackWithoutFalseFailure() {
        when(repository.succeed("run", owner, metrics)).thenThrow(new TransactionSystemException("private sql"));
        when(repository.read("run")).thenReturn(observed("SUCCEEDED", false, null));
        assertTrue(service.confirmSuccess("run", owner, metrics)); verify(repository, never()).fail(any(), any(), any(), any());
    }
    @Test void unavailableOrDifferentTerminalStateCannotConfirmSuccess() {
        when(repository.read("run")).thenThrow(new TransactionSystemException("unavailable"));
        assertFalse(service.confirmSuccess("run", owner, metrics));
        doReturn(observed("FAILED", false, "EXECUTION_LEASE_EXPIRED")).when(repository).read("run");
        assertFalse(service.confirmSuccess("run", owner, metrics));
    }
    @Test void successStatusWithMissingOrDifferentMetricsCannotConfirmHandoff() {
        when(repository.read("run")).thenReturn(new RunLifecycleRepository.Observation("SUCCEEDED", owner.ownerId(), owner.attemptId(), false, null, null));
        assertFalse(service.confirmSuccess("run", owner, metrics));
        var wrong = new RunMetricsCapture(1, 2, 3, 6, 10, 100, 4, 5);
        when(repository.read("run")).thenReturn(new RunLifecycleRepository.Observation("SUCCEEDED", owner.ownerId(), owner.attemptId(), false, null, wrong.persisted()));
        assertFalse(service.confirmSuccess("run", owner, metrics));
    }
    @Test void knownFailureIsDurableOnlyAfterMatchingSafeFailedReadback() {
        when(repository.read("run")).thenReturn(observed("RUNNING", true, null), observed("FAILED", true, "ENGINE_EXECUTION_FAILED"));
        assertTrue(service.confirmFailure("run", owner, RunFailureCode.ENGINE_EXECUTION_FAILED));
        verify(repository).fail("run", owner, "RUNNING", RunFailureCode.ENGINE_EXECUTION_FAILED);
    }
    @Test void expiredOrForeignOwnershipCannotFailAnActiveRun() {
        when(repository.read("run")).thenReturn(observed("RUNNING", false, null));
        assertFalse(service.confirmFailure("run", owner, RunFailureCode.ENGINE_EXECUTION_FAILED));
        when(repository.read("run")).thenReturn(new RunLifecycleRepository.Observation("RUNNING", owner.ownerId(), "foreign", true, null, null));
        assertFalse(service.confirmFailure("run", owner, RunFailureCode.ENGINE_EXECUTION_FAILED));
        verify(repository, never()).fail(any(), any(), any(), any());
    }
    @Test void alreadyDurableExpiredFailureConfirmsButNeverOverwritesTerminalDiagnostics() {
        when(repository.read("run")).thenReturn(observed("FAILED", false, "EXECUTION_LEASE_EXPIRED"));
        assertTrue(service.confirmFailure("run", owner, RunFailureCode.ENGINE_EXECUTION_FAILED));
        verify(repository, never()).fail(any(), any(), any(), any());
    }
    @Test void limitsAndOwnerGenerationAreValidated() {
        var another = service.newOwnership(); assertEquals(owner.ownerId(), another.ownerId()); assertNotEquals(owner.attemptId(), another.attemptId());
        assertThrows(IllegalArgumentException.class, () -> new LifecycleLimits(1000, 1000, 1));
        assertThrows(IllegalArgumentException.class, () -> new LifecycleLimits(60000, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LifecycleLimits(60000, 10000, 0));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionOwnership(owner.ownerId(), "bad", 1));
        assertFalse(owner.toString().contains(owner.attemptId()));
    }
}
