package vn.edu.wspm.backend.services.recommendation;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.*;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.mappers.ComboReadMapper;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ComboReadService {
    public static final int DEFAULT_K = 4;
    public static final int MAX_K = 20;
    public static final String RANKING_POLICY = "SUPPORT_THEN_PATTERN_WEIGHT_V1";
    private static final Logger log = LoggerFactory.getLogger(ComboReadService.class);
    private final ComboReadRepository repository;
    private final ComboReadMapper mapper;
    private final Clock clock;

    public ComboReadService(ComboReadRepository repository, ComboReadMapper mapper, Clock clock) {
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    public TopK topK(String productId, Integer k) {
        validateId(productId, "productId");
        int requested = k == null ? DEFAULT_K : k;
        if (requested < 1 || requested > MAX_K) throw ApplicationException.invalid("k", "Use an integer from 1 to 20.");
        try {
            if (!repository.productExists(productId)) throw notFound();
            Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
            BatchRow batch = activeBatch();
            if (batch == null) return new TopK(productId, requested, 0, at, null, List.of());
            int limit = (int) Math.min(requested, batch.topK());
            var combos = repository.topK(batch.batchId(), productId, limit, at);
            // Defense in depth; SQL applies these filters before ORDER BY/LIMIT.
            combos = combos.stream().filter(c -> publiclyReadable(c, at)).toList();
            if (combos.isEmpty()) return new TopK(productId, requested, limit, at, mapper.batch(batch), List.of());
            var items = repository.items(combos.stream().map(ComboRow::comboId).toList()).stream()
                    .collect(Collectors.groupingBy(ItemRow::comboId));
            var selected = combos;
            var result = IntStream.range(0, selected.size()).mapToObj(i -> new Recommendation(i + 1,
                    mapper.detail(selected.get(i), batch,
                            items.getOrDefault(selected.get(i).comboId(), List.of()), at))).toList();
            log.debug("Public combo read completed: resultCount={}", result.size());
            return new TopK(productId, requested, limit, at, mapper.batch(batch), result);
        } catch (DataAccessException failure) { throw unavailable("top-k", failure); }
    }

    public Detail detail(String comboId) {
        validateId(comboId, "comboId");
        try {
            Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
            BatchRow batch = activeBatch();
            if (batch == null) throw notFound();
            var rows = repository.detail(batch.batchId(), comboId, at);
            if (rows.size() != 1 || !publiclyReadable(rows.getFirst(), at)) throw notFound();
            var items = repository.items(List.of(comboId));
            if (items.isEmpty()) throw notFound();
            return mapper.detail(rows.getFirst(), batch, items, at);
        } catch (DataAccessException failure) { throw unavailable("detail", failure); }
    }

    private BatchRow activeBatch() {
        var rows = repository.activeBatches();
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) {
            log.warn("Public combo read unavailable: multiple active batches");
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        var batch = rows.getFirst();
        if (batch.topK() < 1 || !RANKING_POLICY.equals(batch.rankingPolicy())
                || batch.patternWeightPolicy() == null || batch.patternWeightPolicy().isBlank()) {
            log.warn("Public combo read unavailable: unsupported batch metadata");
            throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return batch;
    }

    private static boolean publiclyReadable(ComboRow row, Instant at) {
        return "PUBLISHED".equals(row.status()) && "SAME_ORDER_BUNDLE".equals(row.kind())
                && (row.validFrom() == null || !at.isBefore(row.validFrom()))
                && (row.validTo() == null || at.isBefore(row.validTo()));
    }
    private static void validateId(String id, String field) {
        if (id == null || id.isBlank() || id.length() > 40 || id.chars().anyMatch(Character::isISOControl))
            throw ApplicationException.invalid(field, "Supply a nonblank identifier of at most 40 characters.");
    }
    private static ApplicationException notFound() { return new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND); }
    private static ApplicationException unavailable(String operation, DataAccessException failure) {
        var safe = new IllegalStateException("Combo storage failure: " + failure.getClass().getSimpleName());
        safe.setStackTrace(failure.getStackTrace());
        log.error("Public combo storage read failed: operation={}", operation, safe);
        return new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE);
    }
}
