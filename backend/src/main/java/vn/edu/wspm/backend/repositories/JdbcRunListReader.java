package vn.edu.wspm.backend.repositories;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.run.RunListResponse.Summary;

@Repository
public class JdbcRunListReader implements RunListReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private static final DateTimeFormatter UTC6 = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
    public JdbcRunListReader(DataSource ds) {
        jdbc = new JdbcTemplate(ds);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setReadOnly(true);
    }
    @Override public Snapshot read(long offset, int size) {
        return transaction.execute(tx -> jdbc.execute((ConnectionCallback<Snapshot>) connection -> {
            String total;
            try (var q = connection.prepareStatement("SELECT COUNT(*) FROM MINING_RUN"); var rs = q.executeQuery()) {
                rs.next(); total = rs.getString(1);
            }
            var items = new ArrayList<Summary>();
            try (var q = connection.prepareStatement("""
                    SELECT r.run_id, r.snapshot_id, r.algorithm, r.status, r.created_at, s.sequence_count
                    FROM MINING_RUN r LEFT JOIN DATASET_SNAPSHOT s ON s.snapshot_id=r.snapshot_id
                    ORDER BY r.created_at DESC, CAST(r.run_id AS BINARY) ASC LIMIT ? OFFSET ?
                    """)) {
                q.setInt(1, size); q.setLong(2, offset);
                try (var rs = q.executeQuery()) {
                    while (rs.next()) {
                        var time = rs.getObject(5, LocalDateTime.class);
                        items.add(new Summary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                time == null ? null : UTC6.format(time), rs.getString(6), List.of()));
                    }
                }
            }
            return new Snapshot(total, items);
        }));
    }
}
