package vn.edu.wspm.backend.services.run;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.run.RunStatusResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.RunStatusResponseMapper;
import vn.edu.wspm.backend.repositories.run.RunStatusRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RunStatusService {
    private final RunStatusRepository repository;
    private final RunStatusResponseMapper mapper;
    public RunStatusResponse read(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw ApplicationException.invalid("runId", "Supply a canonical run UUID.");
        RunStatusRepository.Snapshot row;
        try { row = repository.read(id); }
        catch (RuntimeException failure) {
            var safe = new IllegalStateException("Run status unavailable: " + failure.getClass().getSimpleName());
            safe.setStackTrace(failure.getStackTrace()); log.warn("Run status read unavailable: runId={}", id, safe);
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (row == null) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        var result = mapper.map(row);
        log.debug("Run status read: runId={}, status={}", id, result.status());
        return result;
    }
}
