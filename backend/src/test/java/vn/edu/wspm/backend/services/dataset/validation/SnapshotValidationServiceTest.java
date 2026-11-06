package vn.edu.wspm.backend.services.dataset.validation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SnapshotValidationServiceTest {
    static final String ID = "00000000-0000-0000-0000-000000000001";
    private final SnapshotValidationRepository repository = mock(SnapshotValidationRepository.class);
    private final RawSnapshotStorage storage = mock(RawSnapshotStorage.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ValidationLimits limits = ValidationReportEngineTest.LIMITS;
    private final ValidationReportEngine engine = new ValidationReportEngine(ids -> Set.of("product-laptop", "product-mouse", "product-bag"),
            limits, ValidationReportEngineTest.CLOCK);

    static DatasetSnapshotEntity snapshot(String id, int size) {
        var entity = new DatasetSnapshotEntity();
        entity.setSnapshotId(id);
        entity.setSourceType("CSV");
        entity.setRawFilePath(id + "/raw.csv");
        entity.setRawSha256("a".repeat(64));
        entity.setValidationStatus("PENDING");
        entity.setConversionReportJson(new LinkedHashMap<>(Map.of("contractVersion", "wdm-order-csv-v1",
                "conversionStatus", "NOT_STARTED", "statisticsStatus", "NOT_COMPUTED",
                "provenance", Map.of("rawSizeBytes", Integer.toString(size)), "futureExtension", Map.of("preserved", true))));
        return entity;
    }

    private SnapshotValidationService service(ValidationReportEngine worker, ValidationLimits budget) {
        return new SnapshotValidationService(repository, storage, worker, budget, mapper);
    }

    private DatasetSnapshotEntity prepare() throws Exception {
        byte[] raw = ValidationReportEngineTest.fixture("valid.csv");
        var snapshot = snapshot(ID, raw.length);
        when(repository.find(ID)).thenReturn(Optional.of(snapshot));
        when(storage.verifiedBytes(eq(ID), anyString(), anyString(), anyString())).thenReturn(raw);
        when(repository.publish(eq(ID), anyString(), anyString(), anyMap(), anyMap(), anyString())).thenAnswer(call -> {
            var root = new LinkedHashMap<>(snapshot.getConversionReportJson());
            root.put("validation", call.getArgument(4));
            snapshot.setConversionReportJson(root);
            snapshot.setValidationStatus(call.getArgument(5));
            return snapshot;
        });
        return snapshot;
    }

    @Test
    void publishesOnceAndReplaysWithoutFileOrCatalogWork() throws Exception {
        var entity = prepare();
        var service = service(engine, limits);
        var report = service.validate(ID);
        assertEquals(report, service.validate(ID));
        assertEquals(report, service.read(ID));
        assertEquals("VALID", entity.getValidationStatus());
        assertEquals(Map.of("preserved", true), entity.getConversionReportJson().get("futureExtension"));
        verify(repository, times(1)).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
        verify(storage, times(1)).verifiedBytes(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void missingPendingLegacyAndUnsupportedStateAreExplicit() throws Exception {
        var service = service(engine, limits);
        assertEquals(400, assertThrows(ApplicationException.class, () -> service.validate("bad-id")).status().value());
        assertEquals(404, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
        var entity = prepare();
        assertEquals(409, assertThrows(ApplicationException.class, () -> service.read(ID)).status().value());
        entity.setValidationStatus("LEGACY_UNVERIFIED");
        assertEquals(409, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
        entity.setValidationStatus("PENDING");
        entity.setSequenceFilePath("private-conversion-path");
        assertEquals(409, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
        verifyNoInteractions(storage);
    }

    @Test
    void storageAndCommitErrorsDoNotPublishInvalidAndReleaseLocalLocks() throws Exception {
        var entity = prepare();
        var service = service(engine, limits);
        when(storage.verifiedBytes(anyString(), anyString(), anyString(), anyString())).thenThrow(new IOException("private-path"));
        var failure = assertThrows(ApplicationException.class, () -> service.validate(ID));
        assertEquals(503, failure.status().value());
        assertFalse(failure.getMessage().contains("private-path"));
        verify(repository, never()).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
        assertEquals("PENDING", entity.getValidationStatus());
        doReturn(ValidationReportEngineTest.fixture("valid.csv")).when(storage)
                .verifiedBytes(anyString(), anyString(), anyString(), anyString());
        when(repository.publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("private-commit-detail"));
        assertEquals(503, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
        assertEquals("PENDING", entity.getValidationStatus());
    }

    @Test
    void reportAndRawBudgetsRefusePublicationInsteadOfTruncating() throws Exception {
        var entity = prepare();
        var tiny = new ValidationLimits(16384, 100000, 1, 16777216, 30000, 2);
        assertEquals(503, assertThrows(ApplicationException.class, () -> service(engine, tiny).validate(ID)).status().value());
        var tinyRaw = new ValidationLimits(16384, 100000, 8388608, 1, 30000, 2);
        assertEquals(503, assertThrows(ApplicationException.class, () -> service(engine, tinyRaw).validate(ID)).status().value());
        assertEquals("PENDING", entity.getValidationStatus());
        verify(repository, never()).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
    }

    @Test
    void mismatchedCachedChecksumOrPolicyIsNotOverwritten() throws Exception {
        var entity = prepare();
        var service = service(engine, limits);
        service.validate(ID);
        entity.setRawSha256("b".repeat(64));
        assertEquals(409, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
        assertEquals(409, assertThrows(ApplicationException.class, () -> service.read(ID)).status().value());
        verify(repository, times(1)).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
    }

    @Test
    void completeRootJsonBudgetAcceptsExactByteBoundaryAndRejectsOneByteLess() throws Exception {
        var entity = prepare();
        byte[] raw = ValidationReportEngineTest.fixture("valid.csv");
        var report = engine.generate(ID, entity.getRawSha256(), Integer.toString(raw.length), raw, () -> {});
        var candidate = new LinkedHashMap<>(entity.getConversionReportJson());
        candidate.put("validation", report);
        int exact = mapper.writeValueAsBytes(candidate).length;
        var tooSmall = new ValidationLimits(16384, 100000, exact - 1, 16777216, 30000, 2);
        assertEquals(503, assertThrows(ApplicationException.class, () -> service(engine, tooSmall).validate(ID)).status().value());
        assertEquals("PENDING", entity.getValidationStatus());
        var enough = new ValidationLimits(16384, 100000, exact, 16777216, 30000, 2);
        assertEquals(report, service(engine, enough).validate(ID));
    }

    @Test
    void cancellationBeforePublicationLeavesPendingAndDoesNotLeakCallerInterruption() throws Exception {
        var entity = prepare();
        var worker = mock(ValidationReportEngine.class);
        when(worker.generate(anyString(), anyString(), anyString(), any(), any())).thenAnswer(call -> {
            Thread.currentThread().interrupt();
            ((Runnable) call.getArgument(4)).run();
            throw new AssertionError("Interrupted processing must stop");
        });
        try {
            assertEquals(503, assertThrows(ApplicationException.class, () -> service(worker, limits).validate(ID)).status().value());
            assertEquals("PENDING", entity.getValidationStatus());
            verify(repository, never()).publish(anyString(), anyString(), anyString(), anyMap(), anyMap(), anyString());
        } finally { Thread.interrupted(); }
    }

    @Test
    void sameSnapshotBusyAndGlobalCapacityAreBoundedWithoutLeakingLocks() throws Exception {
        prepare();
        var otherId = "00000000-0000-0000-0000-000000000002";
        when(repository.find(otherId)).thenReturn(Optional.of(snapshot(otherId, 10)));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var worker = mock(ValidationReportEngine.class);
        when(worker.generate(anyString(), anyString(), anyString(), any(), any())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return engine.generate(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4));
        });
        var single = new ValidationLimits(16384, 100000, 8388608, 16777216, 30000, 1);
        var service = service(worker, single);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> service.validate(ID));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(409, assertThrows(ApplicationException.class, () -> service.validate(ID)).status().value());
                assertEquals(503, assertThrows(ApplicationException.class, () -> service.validate(otherId)).status().value());
            } finally { release.countDown(); }
            assertEquals("VALID", pending.get(10, TimeUnit.SECONDS).validationStatus());
        }
        assertEquals("VALID", service.validate(ID).validationStatus());
    }
}
