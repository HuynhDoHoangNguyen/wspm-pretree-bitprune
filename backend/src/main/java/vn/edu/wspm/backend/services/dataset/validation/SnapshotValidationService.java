package vn.edu.wspm.backend.services.dataset.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class SnapshotValidationService {
    private final SnapshotValidationRepository repository;
    private final RawSnapshotStorage storage;
    private final ValidationReportEngine engine;
    private final ValidationLimits limits;
    private final ObjectMapper mapper;
    private final Semaphore capacity;
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public SnapshotValidationService(SnapshotValidationRepository repository, RawSnapshotStorage storage,
            ValidationReportEngine engine, ValidationLimits limits, ObjectMapper mapper) {
        this.repository = repository;
        this.storage = storage;
        this.engine = engine;
        this.limits = limits;
        this.mapper = mapper;
        this.capacity = new Semaphore(limits.maxConcurrent());
    }

    public ValidationReport read(String id) {
        canonicalId(id);
        try {
            var report = existing(find(id));
            if (report == null) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Validation has not completed.");
            return report;
        } catch (ApplicationException expected) { throw expected;
        } catch (RuntimeException failure) { throw operational(id, failure); }
    }

    public ValidationReport validate(String id) {
        canonicalId(id);
        boolean ownsEntry = false;
        boolean ownsSlot = false;
        try {
            var snapshot = find(id);
            var cached = existing(snapshot);
            if (cached != null) return cached;
            eligible(snapshot);
            if (!active.add(id)) throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE, "Validation is already running.");
            ownsEntry = true;
            if (!capacity.tryAcquire()) throw ValidationReportEngine.unavailable();
            ownsSlot = true;
            long started = System.nanoTime();
            Runnable budget = () -> {
                if (Thread.currentThread().isInterrupted()
                        || System.nanoTime() - started > limits.maxDurationMillis() * 1_000_000L) {
                    throw ValidationReportEngine.unavailable();
                }
            };
            String size = rawSize(snapshot);
            if (Long.parseLong(size) > limits.maxRawBytes()) throw ValidationReportEngine.unavailable();
            byte[] raw = storage.verifiedBytes(id, snapshot.getRawFilePath(), snapshot.getRawSha256(), size);
            budget.run();
            var report = engine.generate(id, snapshot.getRawSha256(), size, raw, budget);
            var candidate = new LinkedHashMap<>(snapshot.getConversionReportJson());
            candidate.put("validation", report);
            checkReportSize(candidate, budget);
            Map<String, Object> persisted = reportMap(report);
            budget.run();
            var published = repository.publish(id, snapshot.getRawSha256(), snapshot.getRawFilePath(),
                    snapshot.getConversionReportJson(), persisted, report.validationStatus());
            var committed = existing(published);
            if (committed == null) throw new IllegalStateException("Publication returned no report");
            log.info("Snapshot validation committed: snapshotId={}, status={}, countsComplete={}",
                    id, committed.validationStatus(), committed.countsComplete());
            return committed;
        } catch (ApplicationException expected) { throw expected;
        } catch (IOException | RuntimeException failure) { throw operational(id, failure);
        } finally {
            if (ownsSlot) capacity.release();
            if (ownsEntry) active.remove(id);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> reportMap(ValidationReport report) { return mapper.convertValue(report, Map.class); }

    /** Measure into a bounded sink before creating a second object graph; never allocate an unbounded JSON byte array. */
    private void checkReportSize(Object candidate, Runnable budget) {
        class Counter extends OutputStream {
            int count;
            boolean exceeded;
            private void add(int length) {
                budget.run();
                if (length > limits.maxReportBytes() - count) {
                    exceeded = true;
                    throw ValidationReportEngine.unavailable();
                }
                count += length;
            }
            @Override public void write(int value) { add(1); }
            @Override public void write(byte[] values, int offset, int length) { add(length); }
        }
        var counter = new Counter();
        try { mapper.writeValue(counter, candidate); }
        catch (RuntimeException failure) {
            if (counter.exceeded) throw ValidationReportEngine.unavailable();
            throw failure;
        }
    }

    private DatasetSnapshotEntity find(String id) {
        return repository.find(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ValidationReport existing(DatasetSnapshotEntity snapshot) {
        return StoredValidationReport.read(mapper, snapshot);
    }

    private void eligible(DatasetSnapshotEntity snapshot) {
        var root = snapshot.getConversionReportJson();
        if (!"PENDING".equals(snapshot.getValidationStatus()) || root == null
                || !"wdm-order-csv-v1".equals(root.get("contractVersion"))
                || !"NOT_STARTED".equals(root.get("conversionStatus"))
                || !"NOT_COMPUTED".equals(root.get("statisticsStatus"))
                || snapshot.getSequenceFilePath() != null || snapshot.getMappingFilePath() != null
                || snapshot.getRawFilePath() == null || snapshot.getRawSha256() == null
                || !Set.of("CSV", "DATABASE").contains(snapshot.getSourceType())) {
            throw new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        }
        rawSize(snapshot);
    }

    private String rawSize(DatasetSnapshotEntity snapshot) {
        return StoredValidationReport.rawSize(snapshot);
    }

    private void canonicalId(String id) {
        StoredValidationReport.canonicalId(id);
    }

    private ApplicationException operational(String id, Exception failure) {
        var safe = new IllegalStateException("Validation failed: " + failure.getClass().getSimpleName());
        safe.setStackTrace(failure.getStackTrace());
        log.error("Snapshot validation unavailable: snapshotId={}", id, safe);
        return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Validation could not be completed; read its status before retrying.");
    }
}
