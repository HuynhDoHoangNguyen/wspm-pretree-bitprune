package vn.edu.wspm.backend.repositories;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;

/** V2 persisted projections. Every operation reads counts, page and provenance on one repeatable-read snapshot. */
@Repository
public class JdbcComboAdminReader implements ComboAdminReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private static final DateTimeFormatter UTC6=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'");
    private static final String PROJECTION="""
        SELECT c.combo_id,c.name,c.combo_kind,c.status AS combo_status,c.batch_id,
          b.status AS batch_status,b.run_id,r.status AS run_status,c.created_at,
          c.reviewed_by,c.reviewed_at,c.review_note,c.support_abs,c.support_ratio,c.pattern_weight,
          c.explanation,c.currency_code,c.reference_original_price,c.reference_sale_price,
          b.ranking_policy,b.pattern_weight_policy,b.top_k
        FROM COMBO c LEFT JOIN COMBO_BATCH b ON b.batch_id=c.batch_id
          LEFT JOIN MINING_RUN r ON r.run_id=b.run_id
        """;
    public JdbcComboAdminReader(DataSource ds) {
        jdbc=new JdbcTemplate(ds);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setReadOnly(true);
    }
    @Override public Snapshot list(Filters filters,long offset,int size) {
        return transaction.execute(tx->listCurrent(filters,offset,size));
    }
    /** Internal orchestration only; reuse the caller's established read/write transaction. */
    public Snapshot listCurrent(Filters filters,long offset,int size) {
        requireTransaction();
        return jdbc.execute((ConnectionCallback<Snapshot>)c->{
            if(filters.batchId()!=null) {
                try(var q=c.prepareStatement("SELECT 1 FROM COMBO_BATCH WHERE batch_id=?")) {
                    q.setString(1,filters.batchId());try(var rs=q.executeQuery()) {
                        if(!rs.next()) return new Snapshot(false,"0","0",List.of());
                    }
                }
            }
            var args=new ArrayList<String>();String scope=" WHERE 1=1";
            if(filters.batchId()!=null) { scope+=" AND c.batch_id=?";args.add(filters.batchId()); }
            String scopeTotal=count(c,scope,args);
            String predicate=scope;
            if(filters.status()!=null) { predicate+=" AND c.status=?";args.add(filters.status()); }
            if(filters.comboKind()!=null) { predicate+=" AND c.combo_kind=?";args.add(filters.comboKind()); }
            String filteredTotal=count(c,predicate,args);
            var rows=new ArrayList<Row>();
            try(var q=c.prepareStatement(PROJECTION+predicate+" ORDER BY c.created_at DESC, CAST(c.combo_id AS BINARY) ASC LIMIT ? OFFSET ?")) {
                int next=bind(q,args);q.setInt(next++,size);q.setLong(next,offset);
                try(var rs=q.executeQuery()) { while(rs.next()) rows.add(row(rs)); }
            }
            var bundles=new ArrayList<Bundle>();for(var row:rows) bundles.add(bundle(c,row));
            return new Snapshot(true,scopeTotal,filteredTotal,bundles);
        });
    }
    @Override public Bundle detail(String comboId) {
        return transaction.execute(tx->detailCurrent(comboId));
    }
    public Bundle detailCurrent(String comboId) {
        requireTransaction();
        return jdbc.execute((ConnectionCallback<Bundle>)c->{
            Row row;
            try(var q=c.prepareStatement(PROJECTION+" WHERE c.combo_id=?")) {
                q.setString(1,comboId);try(var rs=q.executeQuery()) { if(!rs.next()) return null;row=row(rs); }
            }
            return bundle(c,row);
        });
    }
    private static void requireTransaction() {
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("COMBO_TRANSACTION_REQUIRED");
    }
    private Bundle bundle(Connection c,Row row) throws SQLException {
        var items=new ArrayList<Item>();
        try(var q=c.prepareStatement("""
            SELECT i.itemset_order,i.item_order,i.product_id,i.quantity,i.unit_price_snapshot,
              i.is_anchor_eligible,i.is_discount_eligible,d.product_name_snapshot,d.sku_snapshot
            FROM COMBO_ITEM i LEFT JOIN RUN_ITEM_STATISTIC d ON d.run_id=? AND d.product_id=i.product_id
            WHERE i.combo_id=? ORDER BY i.itemset_order ASC,i.item_order ASC
            """)) {
            q.setString(1,row.runId());q.setString(2,row.comboId());
            try(var rs=q.executeQuery()) {
                while(rs.next()) items.add(new Item(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getBigDecimal(5),
                        rs.getBoolean(6),rs.getBoolean(7),rs.getString(8),rs.getString(9)));
            }
        }
        var sources=new ArrayList<Source>();
        try(var q=c.prepareStatement("""
            SELECT s.pattern_id,s.is_primary,p.run_id,p.canonical_pattern,p.support_abs,p.support_ratio,
              p.pattern_weight,p.weighted_support,p.pattern_weight_policy
            FROM COMBO_SOURCE s LEFT JOIN PATTERN p ON p.pattern_id=s.pattern_id
            WHERE s.combo_id=? ORDER BY s.pattern_id ASC
            """)) {
            q.setString(1,row.comboId());try(var rs=q.executeQuery()) {
                while(rs.next()) sources.add(new Source(rs.getString(1),rs.getBoolean(2),rs.getString(3),rs.getString(4),
                        new Metrics(rs.getString(5),number(rs,6),number(rs,7)),number(rs,8),rs.getString(9)));
            }
        }
        return new Bundle(row,items,sources);
    }
    private Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getString("combo_id"),rs.getString("name"),rs.getString("combo_kind"),rs.getString("combo_status"),
                rs.getString("batch_id"),rs.getString("batch_status"),rs.getString("run_id"),rs.getString("run_status"),timestamp(rs,"created_at"),
                new Audit(rs.getString("reviewed_by"),null,timestamp(rs,"reviewed_at"),rs.getString("review_note")),
                new Metrics(rs.getString("support_abs"),(Double)rs.getObject("support_ratio"),(Double)rs.getObject("pattern_weight")),
                rs.getString("explanation"),rs.getString("currency_code"),rs.getBigDecimal("reference_original_price"),rs.getBigDecimal("reference_sale_price"),
                rs.getString("ranking_policy"),rs.getString("pattern_weight_policy"),rs.getString("top_k"));
    }
    private static String timestamp(ResultSet rs,String field) throws SQLException {
        var value=rs.getObject(field,LocalDateTime.class);return value==null?null:UTC6.format(value);
    }
    private static Double number(ResultSet rs,int field) throws SQLException { double v=rs.getDouble(field);return rs.wasNull()?null:v; }
    private static int bind(PreparedStatement q,List<String> args) throws SQLException {
        int i=1;for(String arg:args) q.setString(i++,arg);return i;
    }
    private static String count(Connection c,String predicate,List<String> args) throws SQLException {
        try(var q=c.prepareStatement("SELECT COUNT(*) FROM COMBO c"+predicate)) {
            bind(q,args);try(var rs=q.executeQuery()) { rs.next();return rs.getString(1); }
        }
    }
}
