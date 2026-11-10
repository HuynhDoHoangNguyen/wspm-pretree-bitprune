package vn.edu.wspm.backend.services.miningrun;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunSummaryResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.mappers.MiningRunResponseMapper;
import vn.edu.wspm.backend.common.api.FieldErrorDetail;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import vn.edu.wspm.backend.repositories.RunRepository;

@Service
public class MiningRunQueryService {

    private static final Logger log = LoggerFactory.getLogger(MiningRunQueryService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALGORITHMS = Set.of("WSPM_PRETREE", "WSPM_BITPRUNE");
    private static final Set<String> STATUSES = Set.of(
            "QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED");

    private final RunRepository runRepository;
    private final DatasetSnapshotRepository datasetSnapshotRepository;
    private final MiningRunResponseMapper mapper;

    public MiningRunQueryService(
            RunRepository runRepository,
            DatasetSnapshotRepository datasetSnapshotRepository,
            MiningRunResponseMapper mapper) {
        this.runRepository = runRepository;
        this.datasetSnapshotRepository = datasetSnapshotRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public MiningRunPageResponse findAll(int page, int size, String algorithm, String status) {
        String resolvedAlgorithm = validateFilter(algorithm, "algorithm", ALGORITHMS);
        String resolvedStatus = validateFilter(status, "status", STATUSES);
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Sort sort = Sort.by(Sort.Direction.DESC, "runTime")
                .and(Sort.by(Sort.Direction.DESC, "runId"));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        Specification<RunEntity> spec = (root, query, cb) -> cb.conjunction();
        if (resolvedAlgorithm != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("algorithm"), resolvedAlgorithm));
        }
        if (resolvedStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), resolvedStatus));
        }

        Page<RunEntity> pageResult = runRepository.findAll(spec, pageable);
        List<MiningRunSummaryResponse> items = pageResult.getContent().stream()
                .map(mapper::toSummaryResponse)
                .toList();

        log.info("Retrieved {} mining runs for page={}, size={}, totalElements={}, totalPages={}",
                items.size(), pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());

        return new MiningRunPageResponse(
                items,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public MiningRunDetailResponse findById(String runId) {
        RunEntity run = runRepository.findById(runId)
                .orElseThrow(() -> {
                    log.warn("Mining run not found for runId={}", runId);
                    return new MiningRunNotFoundException();
                });

        DatasetSnapshotEntity snapshot = null;
        if (run.getSnapshotId() != null) {
            snapshot = datasetSnapshotRepository.findById(run.getSnapshotId()).orElse(null);
        }

        log.info("Retrieved mining run detail for runId={}", runId);
        return mapper.toDetailResponse(run, snapshot);
    }

    private String validateFilter(String value, String field, Set<String> allowed) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        if (!allowed.contains(trimmed)) {
            log.debug("Rejected unsupported mining run filter for field={}", field);
            throw new InvalidMiningRunFilterException(field);
        }
        return trimmed;
    }

    public static final class InvalidMiningRunFilterException extends ApplicationException {
        private final String field;

        public InvalidMiningRunFilterException(String field) {
            super(ErrorCode.VALIDATION_FAILED, "Giá trị bộ lọc không được hỗ trợ.",
                    List.of(new FieldErrorDetail(field, "INVALID_FILTER", "Giá trị bộ lọc không được hỗ trợ.")));
            if (!"algorithm".equals(field) && !"status".equals(field)) {
                throw new IllegalArgumentException("Unsupported mining run filter field");
            }
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    // Distinguish a missing persisted RUN from other 404 exceptions raised downstream.
    public static final class MiningRunNotFoundException extends ApplicationException {
        public MiningRunNotFoundException() {
            super(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy RUN.");
        }
    }
}
