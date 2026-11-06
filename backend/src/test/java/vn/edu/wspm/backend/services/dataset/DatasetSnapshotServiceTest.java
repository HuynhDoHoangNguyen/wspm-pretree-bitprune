package vn.edu.wspm.backend.services.dataset;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.mappers.SnapshotResponseMapper;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real filesystem + transaction lifecycle; mocked repository/transaction resource, not DB evidence. */
class DatasetSnapshotServiceTest {
    @TempDir Path root;
    DatasetSnapshotRepository repository;
    RawSnapshotStorage storage;
    Transactions transactions;
    DatasetSnapshotService service;
    private final SnapshotResponseMapper mapper = Mappers.getMapper(SnapshotResponseMapper.class);
    final MockMultipartFile file = new MockMultipartFile("file", "orders.csv", "text/csv", new byte[]{1, 2});

    @BeforeEach
    void setup() {
        repository = mock(DatasetSnapshotRepository.class);
        storage = new RawSnapshotStorage(root.toString(), new MultipartConfigElement("", 100, 200, 0));
        transactions = new Transactions();
        service = new DatasetSnapshotService(storage, repository,
                Clock.fixed(Instant.parse("2026-11-05T01:02:03.123456789Z"), ZoneOffset.UTC), transactions, mapper);
    }

    @Test
    void commitsBeforeReturningAndDoesNotExposePlaceholderCountOrStoragePath() throws Exception {
        AtomicReference<DatasetSnapshotEntity> inserted = new AtomicReference<>();
        doAnswer(call -> {
            var entity = call.getArgument(0, DatasetSnapshotEntity.class);
            assertTrue(Files.isRegularFile(root.resolve(entity.getRawFilePath())));
            assertFalse(transactions.committed);
            inserted.set(entity);
            return null;
        }).when(repository).insert(any());
        var response = service.acquire(file);
        assertTrue(transactions.committed);
        assertNull(response.sequenceCount());
        assertNull(response.fromTime());
        assertNull(response.toTime());
        assertEquals("2", response.rawSizeBytes());
        assertEquals(Instant.parse("2026-11-05T01:02:03.123456Z"), response.createdAt());
        assertEquals(0L, inserted.get().getSequenceCount());
        assertEquals("PENDING", inserted.get().getValidationStatus());
        assertEquals("NOT_COMPUTED", inserted.get().getConversionReportJson().get("statisticsStatus"));
        assertFalse(response.toString().contains(root.toString()));
    }

    @Test
    void confirmedRollbackRemovesNewFileButKeepsPreviousArtifact() throws Exception {
        var prior = storage.save(new ByteArrayInputStream(new byte[]{9}));
        doThrow(new IllegalStateException("private-db-marker")).when(repository).insert(any());
        var failure = assertThrows(ApplicationException.class, () -> service.acquire(file));
        assertEquals(503, failure.status().value());
        assertFalse(failure.getMessage().contains("private-db-marker"));
        try (var entries = Files.list(root)) { assertEquals(1, entries.count()); }
        assertArrayEquals(new byte[]{9}, Files.readAllBytes(root.resolve(prior.reference())));
    }

    @Test
    void failedTransactionStartAndInputCloseFailureDoNotLeaveFiles() throws Exception {
        transactions.failBegin = true;
        assertThrows(ApplicationException.class, () -> service.acquire(file));
        verifyNoInteractions(repository);
        assertEmpty();
        transactions.failBegin = false;
        var closeFailure = new MockMultipartFile("file", "orders.csv", "text/csv", new byte[]{1}) {
            @Override public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[]{1}) {
                    @Override public void close() throws IOException { throw new IOException("private-file-marker"); }
                };
            }
        };
        assertThrows(ApplicationException.class, () -> service.acquire(closeFailure));
        verifyNoInteractions(repository);
        assertEmpty();
    }

    @Test
    void uncertainCommitRetainsFileForReconciliationAndNeverReportsSuccess() throws Exception {
        transactions.failCommit = true;
        assertThrows(ApplicationException.class, () -> service.acquire(file));
        verify(repository).insert(any());
        try (var entries = Files.list(root)) { assertEquals(1, entries.count()); }
        try (var files = Files.walk(root)) {
            var raw = files.filter(Files::isRegularFile).findFirst().orElseThrow();
            assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(raw));
        }
    }

    @Test
    void runtimeCommitFailureAlsoRetainsArtifactEvenIfManagerNotifiesRollback() throws Exception {
        transactions.failCommitRuntime = true;
        assertThrows(ApplicationException.class, () -> service.acquire(file));
        verify(repository).insert(any());
        assertTrue(transactions.rolledBack);
        try (var entries = Files.list(root)) { assertEquals(1, entries.count()); }
    }

    @Test
    void storageFailureDoesNotCallRepository() throws Exception {
        Path blocker = root.resolve("blocker");
        Files.writeString(blocker, "preserve");
        var bad = new DatasetSnapshotService(new RawSnapshotStorage(blocker.toString(),
                new MultipartConfigElement("", 100, 200, 0)), repository, Clock.systemUTC(), transactions, mapper);
        assertThrows(ApplicationException.class, () -> bad.acquire(file));
        verifyNoInteractions(repository);
        assertEquals("preserve", Files.readString(blocker));
    }

    private void assertEmpty() throws IOException {
        try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
    }

    static class Transactions extends AbstractPlatformTransactionManager {
        boolean failBegin, failCommit, failCommitRuntime, committed, rolledBack;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definition.getPropagationBehavior());
            if (failBegin) throw new CannotCreateTransactionException("synthetic begin failure");
        }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("synthetic ambiguous commit");
            if (failCommitRuntime) throw new IllegalStateException("synthetic commit connection failure");
            committed = true;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rolledBack = true; }
    }
}
