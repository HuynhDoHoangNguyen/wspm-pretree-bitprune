package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.StringReader;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.dto.run.RunCreateRequest;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.run.RunExecutionVerificationRepository;
import vn.edu.wspm.backend.services.run.*;
import vn.edu.wspm.backend.services.run.engine.MiningEngineRequest;
import vn.edu.wspm.config.*;
import vn.edu.wspm.io.SpmfSequenceParser;
import vn.edu.wspm.statistics.*;

class RunExecutionVerifierTest {
    final RunExecutionVerificationRepository repository = mock(RunExecutionVerificationRepository.class);
    final RunPreparationService preparation = mock(RunPreparationService.class);
    final RunExecutionVerifier verifier = new RunExecutionVerifier(repository, preparation);

    @Test void measuredRefreshPreservesIoAndBracketsPreparationByOwnedReads() throws Exception {
        var input = ExecutionTestInputs.input();
        var owner = new ExecutionOwnership(java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString(), 60000);
        java.util.function.LongSupplier ticker = () -> 123;
        var measured = new MeasuredRunPreparation(input, 345);
        when(preparation.prepareMeasured(any(), eq(ticker))).thenReturn(measured);
        assertSame(measured, verifier.refreshMeasuredOwned("run", input, owner, ticker));
        var order = inOrder(repository, preparation);
        order.verify(repository).requireOwned("run", input, "RUNNING", owner);
        order.verify(preparation).prepareMeasured(new RunCreateRequest(ExecutionTestInputs.SNAPSHOT, "WSPM_PRETREE", "ABSOLUTE", .1), ticker);
        order.verify(repository).requireOwned("run", input, "RUNNING", owner);
        verify(preparation, never()).prepare(any());
    }

    @Test void freshPreparationIsBracketedByCommittedReadsAndUsesExactConfiguredRequest() throws Exception {
        var input = ExecutionTestInputs.input(); when(preparation.prepare(any())).thenReturn(input);
        assertSame(input, verifier.refresh("run", input));
        var order = inOrder(repository, preparation);
        order.verify(repository).requireCommitted("run", input);
        order.verify(preparation).prepare(new RunCreateRequest(ExecutionTestInputs.SNAPSHOT, "WSPM_PRETREE", "ABSOLUTE", .1));
        order.verify(repository).requireCommitted("run", input);
    }
    @Test void noCommittedRowPreventsArtifactPreparation() throws Exception {
        var input = ExecutionTestInputs.input(); doThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE))
                .when(repository).requireCommitted("run", input);
        assertThrows(ApplicationException.class, () -> verifier.refresh("run", input)); verifyNoInteractions(preparation);
    }
    @Test void bindingNumericOrWeightDriftIsRejected() throws Exception {
        var original = ExecutionTestInputs.input(); var b = original.binding();
        var changedBinding = new PreparedRun.Binding(b.publication(), b.sourceType(), b.sourceName(), b.rawReference(), "e".repeat(64));
        var changedConfig = MinimumSupport.absolute(Math.nextUp(.1));
        var changedWeights = new ItemWeightCalculator().calculate(new ItemStatisticsCalculator().calculate(
                new SpmfSequenceParser().parse(new StringReader("1 -1 -2\n1 -1 -2\n1 2 -1 -2\n"))));
        var variants = new PreparedRun[]{
                new PreparedRun(original.engineRequest(), original.minimumSupport(), original.weights(), changedBinding),
                new PreparedRun(new MiningEngineRequest(original.engineRequest().algorithm(), original.engineRequest().database(), changedConfig),
                        new MinimumSupportResolver().resolve(changedConfig, 3), original.weights(), b),
                new PreparedRun(original.engineRequest(), original.minimumSupport(), changedWeights, b)};
        for (var fresh : variants) {
            when(preparation.prepare(any())).thenReturn(fresh);
            assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                    () -> verifier.refresh("run", original)).errorCode());
        }
    }
    @Test void artifactIntegrityErrorsPropagateBeforeExecution() throws Exception {
        var original = ExecutionTestInputs.input(); when(preparation.prepare(any())).thenThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, assertThrows(ApplicationException.class,
                () -> verifier.refresh("run", original)).errorCode());
    }
}
