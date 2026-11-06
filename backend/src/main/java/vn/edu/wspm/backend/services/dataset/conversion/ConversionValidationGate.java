package vn.edu.wspm.backend.services.dataset.conversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.SnapshotValidationRepository;
import vn.edu.wspm.backend.services.dataset.RawSnapshotStorage;
import vn.edu.wspm.backend.services.dataset.validation.StoredValidationReport;
import vn.edu.wspm.backend.services.dataset.validation.ValidationLimits;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static vn.edu.wspm.backend.services.dataset.conversion.ConversionManifestVerifier.*;

/** Read-only admission check. Future converter must atomically claim/recheck state before starting work. */
@Slf4j
@Service
public class ConversionValidationGate {
    private final SnapshotValidationRepository repository;
    private final RawSnapshotStorage storage;
    private final ValidationLimits limits;
    private final ObjectMapper mapper;
    private final Semaphore capacity;
    private record Stamp(String id, String source, String reference, String sha256, String size) {}
    private record Ready(Stamp stamp, ValidationReport report) {}

    public ConversionValidationGate(SnapshotValidationRepository repository, RawSnapshotStorage storage,
            ValidationLimits limits, ObjectMapper mapper) {
        this.repository = repository;
        this.storage = storage;
        this.limits = limits;
        this.mapper = mapper;
        capacity = new Semaphore(limits.maxConcurrent());
    }

    public VerifiedConversionInput prepare(String id) {
        StoredValidationReport.canonicalId(id);
        if (!capacity.tryAcquire()) throw unavailable();
        try {
            long start = System.nanoTime();
            Runnable budget = () -> {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() - start > limits.maxDurationMillis() * 1_000_000L) throw unavailable();
            };
            var ready = ready(find(id));
            if (Long.parseLong(ready.stamp().size()) > limits.maxRawBytes()
                    || ready.report().manifest().size() > limits.maxRows()) throw unavailable();
            budget.run();
            byte[] raw = storage.verifiedBytes(id, ready.stamp().reference(), ready.stamp().sha256(), ready.stamp().size());
            budget.run();
            var records = new ConversionManifestVerifier().verify(ready.report(), raw, limits, budget);
            var current = ready(find(id));
            require(ready.equals(current));
            var verified = new VerifiedConversionInput(raw, ready.report(), records);
            budget.run();
            log.info("Conversion admission verified: snapshotId={}, keptRecords={}", id, records.size());
            return verified;
        } catch (ApplicationException expected) { throw expected;
        } catch (IOException | RuntimeException failure) {
            var safe = new IllegalStateException("Conversion admission failed: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace());
            log.error("Conversion admission unavailable: snapshotId={}", id, safe);
            throw unavailable();
        } finally { capacity.release(); }
    }

    private DatasetSnapshotEntity find(String id) {
        return repository.find(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Ready ready(DatasetSnapshotEntity snapshot) {
        try {
            var root = snapshot.getConversionReportJson();
            require("VALID".equals(snapshot.getValidationStatus()) && root != null
                    && "wdm-order-csv-v1".equals(root.get("contractVersion"))
                    && "NOT_STARTED".equals(root.get("conversionStatus")) && "NOT_COMPUTED".equals(root.get("statisticsStatus"))
                    && Set.of("CSV", "DATABASE").contains(snapshot.getSourceType())
                    && snapshot.getRawFilePath() != null && snapshot.getRawSha256() != null
                    && snapshot.getRawSha256().matches("[0-9a-f]{64}")
                    && snapshot.getSequenceFilePath() == null && snapshot.getSequenceSha256() == null
                    && snapshot.getMappingFilePath() == null && snapshot.getMappingSha256() == null
                    && Long.valueOf(0).equals(snapshot.getSequenceCount()));
            var report = StoredValidationReport.read(mapper, snapshot);
            require(report != null && "VALID".equals(report.validationStatus()) && "READY".equals(report.cleaningOutcome())
                    && report.countsComplete() && report.completedAt() != null && report.completedAt().endsWith("Z"));
            java.time.Instant.parse(report.completedAt());
            return new Ready(new Stamp(snapshot.getSnapshotId(), snapshot.getSourceType(), snapshot.getRawFilePath(),
                    snapshot.getRawSha256(), StoredValidationReport.rawSize(snapshot)), report);
        } catch (ApplicationException expected) { throw expected;
        } catch (RuntimeException malformedReport) { throw invalid(); }
    }
}
