package vn.edu.wspm.backend.repositories;

import java.math.BigDecimal;
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

@Repository
public class JdbcPatternDetailReader implements PatternDetailReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcPatternDetailReader(DataSource dataSource) {
        jdbc = new JdbcTemplate(dataSource);
        // Same independent JDBC snapshot protocol as IMP-02; no change to list behavior.
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setReadOnly(true);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override public Snapshot read(String runId, String patternId) {
        return transaction.execute(tx -> jdbc.execute((ConnectionCallback<Snapshot>)
                c -> readSnapshot(c, runId, patternId)));
    }

    private Snapshot readSnapshot(Connection c, String runId, String patternId) throws SQLException {
        String status, sequenceCount;
        BigInteger published;
        try (var q = c.prepareStatement("""
                SELECT r.status,r.pattern_count,s.sequence_count
                FROM MINING_RUN r LEFT JOIN DATASET_SNAPSHOT s ON s.snapshot_id=r.snapshot_id
                WHERE r.run_id=?
                """)) {
            q.setString(1, runId);
            try (var rs = q.executeQuery()) {
                if (!rs.next()) return null;
                status=rs.getString(1);
                String value=rs.getString(2);
                published=value==null?null:new BigInteger(value);
                sequenceCount=rs.getString(3);
            }
        }
        if (!"SUCCEEDED".equals(status)) return new Snapshot(status,published,null,sequenceCount,null,List.of());
        BigInteger actual;
        try (var q=c.prepareStatement("SELECT COUNT(*) FROM PATTERN WHERE run_id=?")) {
            q.setString(1,runId);
            try (var rs=q.executeQuery()) { rs.next(); actual=new BigInteger(rs.getString(1)); }
        }
        if (published==null || !published.equals(actual))
            return new Snapshot(status,published,actual,sequenceCount,null,List.of());
        Header header;
        try (var q=c.prepareStatement("""
                SELECT pattern_id,run_id,canonical_pattern,support_abs,support_ratio,
                       weighted_support,pattern_weight,pattern_weight_policy,pattern_item_count,itemset_count
                FROM PATTERN WHERE run_id=? AND pattern_id=?
                """)) {
            q.setString(1,runId); q.setBigDecimal(2,new BigDecimal(patternId));
            try (var rs=q.executeQuery()) {
                if (!rs.next()) return new Snapshot(status,published,actual,sequenceCount,null,List.of());
                header=new Header(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getObject(5,Double.class),rs.getObject(6,Double.class),rs.getObject(7,Double.class),
                        rs.getString(8),rs.getLong(9),rs.getLong(10));
            }
        }
        var rows=new ArrayList<Row>();
        try (var q=c.prepareStatement("""
                SELECT i.itemset_order,i.item_order,i.item_id,d.item_id AS mapped_item,
                       d.product_id,d.product_name_snapshot,d.sku_snapshot,d.item_weight
                FROM PATTERN p JOIN PATTERN_ITEM i ON i.pattern_id=p.pattern_id
                LEFT JOIN RUN_ITEM_STATISTIC d ON d.run_id=p.run_id AND d.item_id=i.item_id
                WHERE p.run_id=? AND p.pattern_id=?
                ORDER BY i.itemset_order ASC,i.item_order ASC
                """)) {
            q.setString(1,runId); q.setBigDecimal(2,new BigDecimal(patternId));
            try (var rs=q.executeQuery()) {
                while(rs.next()) rows.add(new Row(rs.getLong(1),rs.getLong(2),rs.getLong(3),
                        rs.getObject(4)!=null,rs.getString(5),rs.getString(6),rs.getString(7),
                        rs.getObject(8,Double.class)));
            }
        }
        return new Snapshot(status,published,actual,sequenceCount,header,rows);
    }
}
