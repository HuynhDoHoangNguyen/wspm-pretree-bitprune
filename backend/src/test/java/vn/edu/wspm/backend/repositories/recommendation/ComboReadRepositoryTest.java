package vn.edu.wspm.backend.repositories.recommendation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

/** SQL contract/binding unit tests; no server, container or database fixture. */
class ComboReadRepositoryTest {
    @Test void anchorAndPublicEligibilityAreAppliedBeforeRankingAndBoundLimit() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var statement = mock(PreparedStatement.class);
        var sql = new AtomicReference<String>();
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(call -> {
                    sql.set(call.getArgument(0));
                    call.<PreparedStatementSetter>getArgument(1).setValues(statement);
                    return List.of();
                });
        new ComboReadRepository(jdbc).topK("batch", "product", 4, Instant.parse("2026-11-06T00:00:00Z"));
        String query = sql.get();
        assertTrue(query.contains("CAST('PUBLISHED' AS BINARY)"));
        assertTrue(query.contains("CAST('ACTIVE' AS BINARY)"));
        assertTrue(query.contains("c.valid_from <= ?"));
        assertTrue(query.contains("c.valid_to > ?"));
        assertTrue(query.indexOf("anchor.is_anchor_eligible = TRUE") < query.indexOf("ORDER BY p.support_abs"));
        assertTrue(query.indexOf("ORDER BY p.support_abs") < query.indexOf("LIMIT ?"));
        assertTrue(query.replaceAll("\\s+", " ").contains("p.support_abs DESC, p.pattern_weight DESC, CAST(p.canonical_pattern AS BINARY) ASC, CAST(c.combo_id AS BINARY) ASC"));
        assertTrue(query.contains("p.run_id = b.run_id"));
        assertTrue(query.contains("proof.is_primary = TRUE) = 1"));
        assertFalse(query.contains("c.support_abs"));
        verify(statement).setString(1, "batch");
        verify(statement).setObject(2, LocalDateTime.parse("2026-11-06T00:00:00"));
        verify(statement).setObject(3, LocalDateTime.parse("2026-11-06T00:00:00"));
        verify(statement).setString(4, "product");
        verify(statement).setInt(5, 4);
    }

    @Test void sequentialIsExcludedFromCommonListAndDetailSqlWithoutGuessingPrefix() {
        assertTrue(ComboReadRepository.ELIGIBLE_SQL.contains("CAST('SAME_ORDER_BUNDLE' AS BINARY)"));
        assertFalse(ComboReadRepository.ELIGIBLE_SQL.contains("itemset_order = 1"));
        assertTrue(ComboReadRepository.ACTIVE_BATCH_SQL.contains("LIMIT 2"));
    }

    @Test void itemsAreOneBoundBatchQueryWithOrderedPositionsAndSinglePrimaryImage() {
        var jdbc = mock(JdbcTemplate.class);
        var sql = new AtomicReference<String>();
        when(jdbc.query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class)))
                .thenAnswer(call -> { sql.set(call.getArgument(0)); return List.of(); });
        var repository = new ComboReadRepository(jdbc);
        assertTrue(repository.items(List.of()).isEmpty());
        verifyNoInteractions(jdbc);
        repository.items(List.of("a", "b"));
        assertTrue(sql.get().contains("?,?"));
        assertTrue(sql.get().contains("i.itemset_order, i.item_order"));
        assertTrue(sql.get().contains("ORDER BY image.display_order, CAST(image.image_id AS BINARY) LIMIT 1"));
        verify(jdbc, times(1)).query(anyString(), any(PreparedStatementSetter.class), any(RowMapper.class));
    }
}
