package vn.edu.wspm.backend.services.combo.admin;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.combo.ComboAdminRead.*;
import vn.edu.wspm.backend.repositories.ComboAdminReader;
import static vn.edu.wspm.backend.services.combo.admin.ComboReadException.Code.*;

/** Internal persisted reader, deliberately without an HTTP/controller registration. */
@Service
public class ComboAdminService {
    private static final Logger log=LoggerFactory.getLogger(ComboAdminService.class);
    private final ComboAdminReader reader;
    private final ComboReviewReadiness readiness=new ComboReviewReadiness();
    private final java.util.function.Function<ComboAdminReader.Bundle,Assessment> assessment;
    @org.springframework.beans.factory.annotation.Autowired
    public ComboAdminService(ComboAdminReader reader) { this.reader=reader;this.assessment=readiness::assess; }
    public ComboAdminService(ComboAdminReader reader,java.util.function.Function<ComboAdminReader.Bundle,Assessment> assessment) {
        this.reader=reader;this.assessment=assessment;
    }
    public Page list(String batchId,String status,String kind,Integer page,Integer size) {
        if(batchId!=null) validateId(batchId);
        if(status!=null && !Set.of("DRAFT","APPROVED","REJECTED","PUBLISHED","ARCHIVED").contains(status)) throw new ComboReadException(INVALID_COMBO_STATUS);
        if(kind!=null && !Set.of("SAME_ORDER_BUNDLE","SEQUENTIAL_CROSS_SELL","MIXED_SEQUENCE").contains(kind)) throw new ComboReadException(INVALID_COMBO_KIND);
        int p=page==null?0:page,s=size==null?20:size;
        if(p<0 || s<1 || s>100) throw new ComboReadException(INVALID_PAGE);
        var filters=new Filters(batchId,status,kind);
        try {
            var snapshot=reader.list(filters,(long)p*s,s);
            if(!snapshot.batchExists()) throw new ComboReadException(BATCH_NOT_FOUND);
            var result=new Page(p,s,snapshot.scopeTotal(),snapshot.filteredTotal(),filters,
                    snapshot.bundles().stream().map(b->new Summary(b.row(),assessment.apply(b))).toList());
            log.debug("Combo list read: page={}, size={}, rows={}",p,s,result.items().size());return result;
        } catch(DataAccessException ex) { log.error("Combo list database read failed",ex);throw ex; }
    }
    public Detail detail(String comboId) {
        validateId(comboId);
        try {
            var bundle=reader.detail(comboId);if(bundle==null) throw new ComboReadException(COMBO_NOT_FOUND);
            var sets=new TreeMap<Long,List<Item>>();
            bundle.items().stream().sorted(Comparator.comparingLong(Item::itemsetOrder).thenComparingLong(Item::itemOrder))
                    .forEach(i->sets.computeIfAbsent(i.itemsetOrder(),unused->new ArrayList<>()).add(i));
            var detail=new Detail(bundle.row(),sets.entrySet().stream().map(e->new Itemset(e.getKey(),e.getValue())).toList(),bundle.sources(),assessment.apply(bundle));
            log.debug("Combo detail read: itemsets={}, sources={}",detail.itemsets().size(),detail.sources().size());return detail;
        } catch(DataAccessException ex) { log.error("Combo detail database read failed",ex);throw ex; }
    }
    private static void validateId(String id) {
        if(id==null || id.isBlank() || id.codePointCount(0,id.length())>40 || id.indexOf('\0')>=0
                || !StandardCharsets.UTF_8.newEncoder().canEncode(id)) throw new ComboReadException(INVALID_ID);
    }
}
