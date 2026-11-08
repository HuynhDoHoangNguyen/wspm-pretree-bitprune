package vn.edu.wspm.backend.services.run.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.run.persistence.RunResultPublicationService;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission.*;

class RunResultPublicationConsumerTest {
    private final ManagedRunExecutionAdmission admission=mock(ManagedRunExecutionAdmission.class);
    private final RunResultPublicationService publications=mock(RunResultPublicationService.class);
    private final Outcome outcome=mock(Outcome.class,RETURNS_DEEP_STUBS);
    private void ready() {
        when(outcome.runId()).thenReturn("run"); when(outcome.token()).thenReturn(UUID.randomUUID()); when(outcome.lifecycleConfirmed()).thenReturn(true);
        when(admission.capacitySnapshot()).thenReturn(new CapacitySnapshot(2,1,1,false,Instant.EPOCH,List.of(new CapacityEntry("run","COMPLETED"))));
        when(admission.outcome("run")).thenReturn(Optional.of(outcome));
    }
    @Test void noAcknowledgementBeforePublicationReturnsAndTokenIsPreserved() throws Exception {
        ready();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(publications.publish(outcome)).thenAnswer(call -> {entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return "digest";});
        try(var consumer=new RunResultPublicationConsumer(admission,publications);var pool=Executors.newSingleThreadExecutor()) {
            var pass=pool.submit(consumer::pass);assertTrue(entered.await(5,TimeUnit.SECONDS));
            verify(admission,never()).acknowledge(anyString(),any());release.countDown();pass.get(5,TimeUnit.SECONDS);
            verify(admission).acknowledge("run",outcome.token());
        } finally {release.countDown();}
    }
    @Test void unconfirmedLifecycleNeverPublishesOrAcknowledges() {
        ready();when(outcome.lifecycleConfirmed()).thenReturn(false);
        try(var consumer=new RunResultPublicationConsumer(admission,publications)) {consumer.pass();}
        verifyNoInteractions(publications);verify(admission,never()).acknowledge(anyString(),any());
    }
    @Test void shutdownDoesNotBeginAnotherHandoff() {
        ready();var consumer=new RunResultPublicationConsumer(admission,publications);
        consumer.close();consumer.pass();
        verifyNoInteractions(publications);verify(admission,never()).acknowledge(anyString(),any());
    }
    @Test void contentFailureIsRetainedAndNotRetried() {
        ready();when(publications.publish(outcome)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE));
        try(var consumer=new RunResultPublicationConsumer(admission,publications)) {consumer.pass();consumer.pass();}
        verify(publications,times(1)).publish(outcome);verify(admission,never()).acknowledge(anyString(),any());
    }
    @Test void unavailableCommitIsRetainedAndBackoffPreventsImmediateReplay() {
        ready();when(publications.publish(outcome)).thenThrow(new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE));
        try(var consumer=new RunResultPublicationConsumer(admission,publications)) {consumer.pass();consumer.pass();}
        verify(publications,times(1)).publish(outcome);verify(admission,never()).acknowledge(anyString(),any());
    }
    @Test void explicitReconciliationRequiresRetainedBlockedTokenAndNeverReexecutesEngine() {
        ready();when(publications.publish(outcome)).thenThrow(new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE)).thenReturn("digest");
        try(var consumer=new RunResultPublicationConsumer(admission,publications)) {
            consumer.pass();
            assertFalse(consumer.retryRetained("run",UUID.randomUUID()));
            assertTrue(consumer.retryRetained("run",outcome.token()));
            consumer.pass();verify(publications,times(2)).publish(outcome);verify(admission).acknowledge("run",outcome.token());
            assertFalse(consumer.retryRetained("run",outcome.token()));
        }
    }
    @Test void lostAcknowledgementRechecksPublicationBeforeRetry() {
        ready();when(publications.publish(outcome)).thenReturn("digest");when(admission.acknowledge("run",outcome.token())).thenReturn(false,true);
        try(var consumer=new RunResultPublicationConsumer(admission,publications)) {consumer.pass();consumer.pass();}
        var order=inOrder(publications,admission);
        order.verify(admission).capacitySnapshot();order.verify(admission).outcome("run");
        order.verify(publications).publish(outcome);order.verify(admission).acknowledge("run",outcome.token());
        order.verify(admission).capacitySnapshot();order.verify(admission).outcome("run");
        order.verify(publications).publish(outcome);order.verify(admission).acknowledge("run",outcome.token());
    }
}
