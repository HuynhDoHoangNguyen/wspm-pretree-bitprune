package vn.edu.wspm.backend.services.dataset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import vn.edu.wspm.backend.mappers.SnapshotResponseMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class DatasetSnapshotService {
    private final RawSnapshotStorage storage;
    private final DatasetSnapshotRepository repository;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final SnapshotResponseMapper mapper;

    public DatasetSnapshotService(RawSnapshotStorage storage, DatasetSnapshotRepository repository,
                                  Clock clock, PlatformTransactionManager transactions, SnapshotResponseMapper mapper) {
        this.storage = storage;
        this.repository = repository;
        this.clock = clock;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(transactions);
        // The HTTP response must not precede commit, even if a future caller has an outer transaction.
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public DatasetSnapshotResponse acquire(MultipartFile file) {
        RawSnapshotStorage.StoredRaw raw = null;
        var registered = new AtomicBoolean();
        try {
            // Close the upload stream before any DB work. A close failure also cleans this receipt.
            try (var input = file.getInputStream()) {
                raw = storage.save(input);
            }
            var stored = raw;
            var snapshot = metadata(file.getOriginalFilename(), stored);
            DatasetSnapshotResponse response = mapper.acquisition(snapshot, stored.sha256(), stored.sizeBytes());
            transaction.executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    private boolean commitPhase;
                    @Override public void beforeCommit(boolean readOnly) { commitPhase = true; }
                    @Override public void afterCompletion(int completion) {
                        // A runtime commit failure can be followed by rollback notification even
                        // when the database committed before the connection failed. Retain in doubt.
                        if (completion == STATUS_ROLLED_BACK && !commitPhase) storage.discard(stored);
                        if (completion != STATUS_COMMITTED && (commitPhase || completion == STATUS_UNKNOWN)) {
                            log.warn("Snapshot commit outcome requires reconciliation; retain artifact: snapshotId={}",
                                    stored.snapshotId());
                        }
                    }
                });
                registered.set(true);
                repository.insert(snapshot);
            });
            log.info("Dataset snapshot committed: snapshotId={}, sizeBytes={}", raw.snapshotId(), raw.sizeBytes());
            return response;
        } catch (IOException | RuntimeException failure) {
            // Once synchronization is registered, only the transaction outcome may authorize cleanup.
            if (raw != null && !registered.get()) storage.discard(raw);
            if (failure instanceof ApplicationException expected) throw expected;
            var safe = new IllegalStateException("Snapshot acquisition failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Dataset snapshot acquisition failed: snapshotId={}",
                    raw == null ? "unallocated" : raw.snapshotId(), safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Snapshot acquisition could not be confirmed; automatic retry is not supported.");
        }
    }

    private DatasetSnapshotEntity metadata(String name, RawSnapshotStorage.StoredRaw raw) {
        var snapshot = new DatasetSnapshotEntity();
        snapshot.setSnapshotId(raw.snapshotId());
        snapshot.setSourceType("CSV");
        snapshot.setSourceName(name);
        snapshot.setRawFilePath(raw.reference());
        snapshot.setRawSha256(raw.sha256());
        snapshot.setValidationStatus("PENDING");
        snapshot.setSequenceCount(0L);
        snapshot.setCreatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        var provenance = new LinkedHashMap<String, Object>();
        provenance.put("acquisition", "CSV_UPLOAD");
        provenance.put("sourceTimeZone", null);
        provenance.put("timeField", "ordered_at");
        provenance.put("statusPolicy", "COMPLETED_CURRENT_AT_ACQUISITION");
        provenance.put("rawSizeBytes", Long.toString(raw.sizeBytes()));
        snapshot.setConversionReportJson(Map.of(
                "contractVersion", "wdm-order-csv-v1",
                "conversionStatus", "NOT_STARTED", "statisticsStatus", "NOT_COMPUTED",
                "provenance", provenance));
        return snapshot;
    }

}
