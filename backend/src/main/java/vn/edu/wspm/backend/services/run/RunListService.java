package vn.edu.wspm.backend.services.run;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.run.RunListResponse;
import vn.edu.wspm.backend.dto.run.RunListResponse.Summary;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.RunListReader;

@Service
public final class RunListService {
    private static final Logger log = LoggerFactory.getLogger(RunListService.class);
    private static final Set<String> STATUSES = Set.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED");
    private final RunListReader reader;
    public RunListService(RunListReader reader) { this.reader = reader; }
    public RunListResponse list(Integer page, Integer size) {
        int p = page == null ? 0 : page, s = size == null ? 20 : size;
        if (p < 0) throw ApplicationException.invalid("page", "Page must be nonnegative.");
        if (s < 1 || s > 100) throw ApplicationException.invalid("size", "Size must be between 1 and 100.");
        try {
            var snapshot = reader.read((long) p * s, s);
            var items = snapshot.items().stream().map(row -> new Summary(row.runId(), row.snapshotId(), row.algorithm(),
                    row.status(), row.createdAt(), row.sequenceCount(), row.status() != null && STATUSES.contains(row.status())
                            ? List.<String>of() : List.of("UNKNOWN_RUN_STATUS"))).toList();
            log.debug("Run list read: page={}, size={}, rows={}", p, s, items.size());
            return new RunListResponse(p, s, snapshot.total(), items);
        } catch (DataAccessException failure) {
            // Do not log JDBC exception text/causes: those can contain datasource secrets.
            log.error("Run list storage unavailable");
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
