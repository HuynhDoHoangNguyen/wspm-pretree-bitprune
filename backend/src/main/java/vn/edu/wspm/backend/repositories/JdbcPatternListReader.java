package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.Summary;

@Repository
public class JdbcPatternListReader implements PatternListReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate snapshotTransaction;

    public JdbcPatternListReader(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        // An independent JDBC transaction prevents an outer READ_COMMITTED/JPA scope
        // from weakening this reader's repeatable-read snapshot.
        snapshotTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        snapshotTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshotTransaction.setReadOnly(true);
    }

    @Override
    public Snapshot read(String runId, long offset, int size) {
        return snapshotTransaction.execute(tx -> jdbc.execute(
                (ConnectionCallback<Snapshot>) connection -> readSnapshot(connection, runId, offset, size)));
    }

    private Snapshot readSnapshot(Connection c, String runId, long offset, int size) throws SQLException {
        String status;
        BigInteger expected;
        try (var q = c.prepareStatement("SELECT status, pattern_count FROM MINING_RUN WHERE run_id = ?")) {
            q.setString(1, runId);
            try (var rs = q.executeQuery()) {
                if (!rs.next()) return null;
                status = rs.getString(1);
                String count = rs.getString(2);
                expected = count == null ? null : new BigInteger(count);
            }
        }
        if (!"SUCCEEDED".equals(status)) return new Snapshot(status, expected, null, List.of());
        BigInteger actual;
        try (var q = c.prepareStatement("SELECT COUNT(*) FROM PATTERN WHERE run_id = ?")) {
            q.setString(1, runId);
            try (var rs = q.executeQuery()) { rs.next(); actual = new BigInteger(rs.getString(1)); }
        }
        if (expected == null || !expected.equals(actual)
                || BigInteger.valueOf(offset).compareTo(actual) >= 0) {
            return new Snapshot(status, expected, actual, List.of());
        }
        // MySQL TEXT sorting can otherwise compare only max_sort_length bytes.
        // V2 canonical_pattern is TEXT (at most 65535 bytes), not LONGTEXT.
        int previous;
        try (var q = c.createStatement(); var rs = q.executeQuery("SELECT @@SESSION.max_sort_length")) {
            rs.next(); previous = rs.getInt(1);
        }
        try {
            try (var q = c.createStatement()) { q.execute("SET SESSION max_sort_length = 65536"); }
            var items = new ArrayList<Summary>();
            try (var q = c.prepareStatement("""
                    SELECT pattern_id, run_id, canonical_pattern, support_abs, support_ratio,
                           weighted_support, pattern_weight, pattern_weight_policy,
                           pattern_item_count, itemset_count
                    FROM PATTERN WHERE run_id = ?
                    ORDER BY support_abs DESC, pattern_weight DESC,
                             CAST(canonical_pattern AS BINARY) ASC, pattern_id ASC
                    LIMIT ? OFFSET ?
                    """)) {
                q.setString(1, runId); q.setInt(2, size); q.setLong(3, offset);
                try (var rs = q.executeQuery()) {
                    while (rs.next()) items.add(new Summary(rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getObject(5, Double.class),
                            rs.getObject(6, Double.class), rs.getObject(7, Double.class),
                            rs.getString(8), rs.getInt(9), rs.getInt(10)));
                }
            }
            return new Snapshot(status, expected, actual, items);
        } finally {
            try (var q = c.createStatement()) {
                q.execute("SET SESSION max_sort_length = " + previous);
            }
        }
    }
}
