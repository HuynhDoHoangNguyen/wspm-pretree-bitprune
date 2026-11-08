package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.Summary;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.WeightFilter;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput;
import vn.edu.wspm.backend.dto.pattern.PatternFilterInput.Filter;
import vn.edu.wspm.backend.services.pattern.PatternReadException;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

@Repository
public class JdbcPatternListReader implements PatternListReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate snapshotTransaction;
    private final Set<String> confirmedPolicies;

    @org.springframework.beans.factory.annotation.Autowired
    public JdbcPatternListReader(DataSource dataSource) {
        this(dataSource,Set.of()); // No production approval registry has been supplied.
    }

    /** Server-owned immutable approvals; explicit injection intended for controlled tests. */
    public JdbcPatternListReader(DataSource dataSource,Set<String> confirmedPolicies) {
        this.confirmedPolicies=Set.copyOf(confirmedPolicies);
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
        return read(runId,offset,size,PatternFilterInput.EMPTY.validated());
    }

    @Override
    public Snapshot read(String runId,long offset,int size,Filter filter) {
        return snapshotTransaction.execute(tx -> jdbc.execute(
                (ConnectionCallback<Snapshot>) connection -> readSnapshot(connection, runId, offset, size,filter)));
    }

    private Snapshot readSnapshot(Connection c, String runId, long offset, int size,Filter filter) throws SQLException {
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
        if (expected == null || !expected.equals(actual)) {
            return new Snapshot(status, expected, actual, List.of());
        }
        // Membership and capability are checked even when the requested page is empty.
        var missingIds=new ArrayList<Integer>();
        for(int item:filter.itemIds()) {
            try(var q=c.prepareStatement("SELECT 1 FROM RUN_ITEM_STATISTIC WHERE run_id=? AND item_id=?")) {
                q.setString(1,runId);q.setInt(2,item);
                try(var rs=q.executeQuery()) { if(!rs.next()) missingIds.add(item); }
            }
        }
        if(!missingIds.isEmpty()) throw new PatternReadException(ITEM_NOT_IN_RUN_DICTIONARY,missingIds);
        WeightFilter capability=capability(c,runId);
        if(filter.hasWeight() && !capability.enabled()) throw new PatternReadException(WEIGHT_FILTER_UNAVAILABLE);
        var predicate=new StringBuilder("run_id = ?");
        var parameters=new ArrayList<Object>(); parameters.add(runId);
        for(int item:filter.itemIds()) {
            predicate.append(" AND EXISTS (SELECT 1 FROM PATTERN_ITEM i WHERE i.pattern_id=PATTERN.pattern_id AND i.item_id=?)");
            parameters.add(item);
        }
        if(filter.supportMin()!=null) { predicate.append(" AND support_abs>=?");parameters.add(new java.math.BigDecimal(filter.supportMin())); }
        if(filter.supportMax()!=null) { predicate.append(" AND support_abs<=?");parameters.add(new java.math.BigDecimal(filter.supportMax())); }
        if(filter.hasWeight()) predicate.append(" AND pattern_weight IS NOT NULL");
        if(filter.weightMin()!=null) { predicate.append(" AND pattern_weight>=?");parameters.add(filter.weightMin()); }
        if(filter.weightMax()!=null) { predicate.append(" AND pattern_weight<=?");parameters.add(filter.weightMax()); }
        BigInteger filtered;
        try(var q=c.prepareStatement("SELECT COUNT(*) FROM PATTERN WHERE "+predicate)) {
            bind(q,parameters);
            try(var rs=q.executeQuery()) { rs.next();filtered=new BigInteger(rs.getString(1)); }
        }
        if(BigInteger.valueOf(offset).compareTo(filtered)>=0)
            return new Snapshot(status,expected,actual,List.of(),filtered,capability);
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
                    FROM PATTERN WHERE %s
                    ORDER BY support_abs DESC, pattern_weight DESC,
                             CAST(canonical_pattern AS BINARY) ASC, pattern_id ASC
                    LIMIT ? OFFSET ?
                    """.formatted(predicate))) {
                bind(q,parameters); q.setInt(parameters.size()+1, size); q.setLong(parameters.size()+2, offset);
                try (var rs = q.executeQuery()) {
                    while (rs.next()) items.add(new Summary(rs.getString(1), rs.getString(2),
                            rs.getString(3), rs.getString(4), rs.getObject(5, Double.class),
                            rs.getObject(6, Double.class), rs.getObject(7, Double.class),
                            rs.getString(8), rs.getInt(9), rs.getInt(10)));
                }
            }
            return new Snapshot(status, expected, actual, items,filtered,capability);
        } finally {
            try (var q = c.createStatement()) {
                q.execute("SET SESSION max_sort_length = " + previous);
            }
        }
    }

    private static void bind(java.sql.PreparedStatement query,List<Object> parameters) throws SQLException {
        for(int i=0;i<parameters.size();i++) query.setObject(i+1,parameters.get(i));
    }

    private WeightFilter capability(Connection c,String runId) throws SQLException {
        // Exact policy identity, independent of DB's accent/case-insensitive collation.
        var policies=new java.util.HashSet<String>();
        boolean available=false,invalid=false;
        try(var q=c.prepareStatement("SELECT pattern_weight_policy,pattern_weight FROM PATTERN WHERE run_id=?")) {
            q.setString(1,runId);
            q.setFetchSize(Integer.MIN_VALUE);
            try(var rs=q.executeQuery()) {
                while(rs.next()) {
                    String policy=rs.getString(1);policies.add(policy);
                    Double weight=rs.getObject(2,Double.class);
                    available |= weight!=null && Double.isFinite(weight);
                    invalid |= weight!=null && !Double.isFinite(weight);
                }
            }
        }
        if(policies.isEmpty()) return new WeightFilter(false,"NO_AVAILABLE_WEIGHT",null);
        if(policies.size()!=1 || policies.contains(null) || policies.iterator().next().isBlank())
            return new WeightFilter(false,"MIXED_OR_MISSING_POLICY",null);
        String policy=policies.iterator().next();
        if(!confirmedPolicies.contains(policy)) return new WeightFilter(false,"POLICY_NOT_CONFIRMED",policy);
        if(!available || invalid) return new WeightFilter(false,"NO_AVAILABLE_WEIGHT",policy);
        return new WeightFilter(true,null,policy);
    }
}
