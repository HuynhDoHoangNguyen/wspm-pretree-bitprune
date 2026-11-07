package vn.edu.wspm.backend.services.run;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.TransactionSystemException;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.run.RunCreationRepository;
import java.time.Instant;

/** Protocol tests with mocked persistence; real commit ordering is verified in MySQL integration. */
class RunCreationServiceTest {
    final RunPreparationService preparation = mock(RunPreparationService.class);
    final RunCreationRepository repository = mock(RunCreationRepository.class);
    final RunExecutionAdmission provider = mock(RunExecutionAdmission.class);
    final RunExecutionAdmission.Reservation reservation = mock(RunExecutionAdmission.Reservation.class);
    final PreparedRun prepared = mock(PreparedRun.class);
    final RunCreateRequest request = new RunCreateRequest("id", "WSPM_PRETREE", "ABSOLUTE", .1);

    RunCreationService service(int providers) {
        when(preparation.prepare(request)).thenReturn(prepared);
        var binding = new PreparedRun.Binding(new vn.edu.wspm.backend.services.dataset.conversion.ConversionArtifactResult(
                "id", "attempt", "hash", null, null, null), "CSV", "synthetic", "private", "hash");
        when(prepared.binding()).thenReturn(binding);
        var beans = new StaticListableBeanFactory();
        for (int i = 0; i < providers; i++) beans.addBean("provider" + i, provider);
        return new RunCreationService(preparation, repository, beans.getBeanProvider(RunExecutionAdmission.class));
    }
    void ready() {
        when(provider.reserve(anyString(), same(prepared))).thenReturn(reservation);
        when(repository.create(anyString(), same(prepared))).thenAnswer(call -> RunEntity.queued(call.getArgument(0), "id",
                "WSPM_PRETREE", "synthetic", "ABSOLUTE", .1, null, .1, .6, .4, .5, Instant.parse("2026-11-07T00:00:00Z")));
    }
    @ParameterizedTest @ValueSource(ints = {0, 2})
    void missingOrAmbiguousProvidersNeverCreateRun(int count) {
        var service = service(count);
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(ApplicationException.class, () -> service.create(request)).errorCode());
        verifyNoInteractions(repository, provider, reservation);
    }
    @Test void acknowledgedResponseFollowsReservationCommitActivationAndRelease() {
        var service = service(1); ready(); var response = service.create(request);
        java.util.UUID.fromString(response.runId()); assertEquals("QUEUED", response.status());
        assertEquals(Instant.parse("2026-11-07T00:00:00Z"), response.createdAt());
        var order = inOrder(provider, repository, reservation);
        order.verify(provider).reserve(eq(response.runId()), same(prepared));
        order.verify(reservation).ownership();
        order.verify(repository).create(eq(response.runId()), same(prepared));
        order.verify(reservation).activate(); order.verify(reservation).close();
        verifyNoMoreInteractions(repository, provider, reservation);
    }
    @Test void capacityRejectionDoesNotInsertOrActivate() {
        var service = service(1);
        when(provider.reserve(anyString(), same(prepared))).thenThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        assertThrows(ApplicationException.class, () -> service.create(request)); verifyNoInteractions(repository, reservation);
    }
    @Test void commitFailureReleasesReservationWithoutActivationOrRetry() {
        var service = service(1); ready();
        doThrow(new TransactionSystemException("private-database-details")).when(repository).create(anyString(), same(prepared));
        var error = assertThrows(ApplicationException.class, () -> service.create(request));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.errorCode()); assertFalse(error.getMessage().contains("private"));
        verify(reservation).close(); verify(reservation, never()).activate(); verify(repository).create(anyString(), same(prepared));
    }
    @Test void activationFailureDoesNotReturn202OrDeleteOrRetryCommittedRun() {
        var service = service(1); ready(); doThrow(new IllegalStateException("private-provider-details")).when(reservation).activate();
        var error = assertThrows(ApplicationException.class, () -> service.create(request));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, error.errorCode()); assertFalse(error.getMessage().contains("private"));
        verify(repository).create(anyString(), same(prepared)); verify(reservation).activate(); verify(reservation).close();
        verify(reservation).ownership();
        verifyNoMoreInteractions(repository, reservation);
    }
    @Test void closeFailureAfterActivationDoesNotReturnSuccess() {
        var service = service(1); ready(); doThrow(new IllegalStateException("close failed")).when(reservation).close();
        assertThrows(ApplicationException.class, () -> service.create(request)); verify(reservation).activate();
        verify(repository).create(anyString(), same(prepared));
    }
    @Test void preparationErrorNeverConsultsProvider() {
        var service = service(1); doThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE)).when(preparation).prepare(request);
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class, () -> service.create(request)).errorCode());
        verifyNoInteractions(provider, repository, reservation);
    }
}
