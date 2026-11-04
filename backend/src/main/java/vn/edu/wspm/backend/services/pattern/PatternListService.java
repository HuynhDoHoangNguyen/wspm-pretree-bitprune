package vn.edu.wspm.backend.services.pattern;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.ResultState;
import vn.edu.wspm.backend.repositories.PatternListReader;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

/** Internal reader only: no HTTP route until IMP-01 supplies server ADMIN enforcement. */
@Service
public class PatternListService {
    private static final Logger log = LoggerFactory.getLogger(PatternListService.class);
    private final PatternListReader reader;

    public PatternListService(PatternListReader reader) { this.reader = reader; }

    public PatternListResponse list(String runId, Integer requestedPage, Integer requestedSize) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 100) throw new PatternReadException(INVALID_PAGE);
        if (runId == null || runId.isBlank() || runId.length() > 40)
            throw new PatternReadException(INVALID_RUN_ID);
        try {
            var snapshot = reader.read(runId, Math.multiplyExact((long) page, size), size);
            if (snapshot == null) throw new PatternReadException(RUN_NOT_FOUND);
            if (snapshot.status() == null) throw new PatternReadException(RESULT_INCONSISTENT);
            ResultState state = switch (snapshot.status()) {
                case "QUEUED", "RUNNING" -> ResultState.NOT_READY;
                case "FAILED" -> ResultState.FAILED;
                case "LEGACY_UNVERIFIED" -> ResultState.UNVERIFIED;
                case "SUCCEEDED" -> ResultState.READY;
                default -> throw new PatternReadException(RESULT_INCONSISTENT);
            };
            if (state != ResultState.READY) return new PatternListResponse(runId, snapshot.status(),
                    state, null, null, page, size, List.of());
            if (snapshot.publishedCount() == null || snapshot.actualCount() == null
                    || snapshot.actualCount().signum() < 0
                    || !snapshot.publishedCount().equals(snapshot.actualCount())) {
                log.warn("Pattern list rejected: published count inconsistent");
                throw new PatternReadException(RESULT_INCONSISTENT);
            }
            String total = snapshot.actualCount().toString();
            log.debug("Pattern list read: page={}, size={}, count={}", page, size, total);
            return new PatternListResponse(runId, snapshot.status(), state,
                    total, total, page, size, snapshot.items());
        } catch (DataAccessException ex) {
            log.error("Pattern list database read failed", ex);
            throw ex;
        }
    }
}
