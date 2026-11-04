package vn.edu.wspm.backend.repositories;

import java.math.BigInteger;
import java.util.ArrayList;
import javax.sql.DataSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.wspm.backend.dto.pattern.DictionaryResponse;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse.ResultState;
import vn.edu.wspm.backend.services.pattern.DictionarySearch;
import vn.edu.wspm.backend.services.pattern.PatternReadException;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

@Repository
public class JdbcDictionaryReader implements DictionaryReader {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public JdbcDictionaryReader(DataSource source) {
        jdbc=new JdbcTemplate(source);
        transaction=new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setReadOnly(true);
    }
    @Override public DictionaryResponse search(String runId,String query,int page,int size) {
        return transaction.execute(tx->jdbc.execute((ConnectionCallback<DictionaryResponse>) c->{
            String status,published;
            try(var q=c.prepareStatement("SELECT status,pattern_count FROM MINING_RUN WHERE run_id=?")) {
                q.setString(1,runId);
                try(var rs=q.executeQuery()) {
                    if(!rs.next()) throw new PatternReadException(RUN_NOT_FOUND);
                    status=rs.getString(1);published=rs.getString(2);
                }
            }
            ResultState state=switch(status==null?"":status) {
                case "QUEUED","RUNNING" -> ResultState.NOT_READY;
                case "FAILED" -> ResultState.FAILED;
                case "LEGACY_UNVERIFIED" -> ResultState.UNVERIFIED;
                case "SUCCEEDED" -> ResultState.READY;
                default -> throw new PatternReadException(RESULT_INCONSISTENT);
            };
            if(state!=ResultState.READY) return new DictionaryResponse(runId,state,query,page,size,null,null,java.util.List.of());
            try(var q=c.prepareStatement("SELECT COUNT(*) FROM PATTERN WHERE run_id=?")) {
                q.setString(1,runId);
                try(var rs=q.executeQuery()) {
                    rs.next();
                    if(published==null || !new BigInteger(published).equals(new BigInteger(rs.getString(1))))
                        throw new PatternReadException(RESULT_INCONSISTENT);
                }
            }
            // Normalize in Java to enforce identical Unicode semantics independent of
            // MySQL collation. Bounded-memory cursor scan, retaining only this page.
            var items=new ArrayList<DictionaryResponse.Item>();
            long total=0,matched=0,offset=(long)page*size;
            try(var q=c.prepareStatement("SELECT item_id,product_id,product_name_snapshot,sku_snapshot FROM RUN_ITEM_STATISTIC WHERE run_id=? ORDER BY item_id ASC")) {
                q.setString(1,runId); q.setFetchSize(Integer.MIN_VALUE); // Connector/J streaming
                try(var rs=q.executeQuery()) {
                    while(rs.next()) {
                        total++;
                        if(DictionarySearch.matches(query,rs.getString(3),rs.getString(4))) {
                            if(matched>=offset && items.size()<size) items.add(new DictionaryResponse.Item(
                                    rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));
                            matched++;
                        }
                    }
                }
            }
            return new DictionaryResponse(runId,state,query,page,size,Long.toString(total),Long.toString(matched),items);
        }));
    }
}
