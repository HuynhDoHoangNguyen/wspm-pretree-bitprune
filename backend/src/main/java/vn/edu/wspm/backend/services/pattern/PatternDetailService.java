package vn.edu.wspm.backend.services.pattern;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse;
import vn.edu.wspm.backend.dto.pattern.PatternDetailResponse.*;
import vn.edu.wspm.backend.repositories.PatternDetailReader;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

/** Internal only: a future HTTP adapter must enforce server ADMIN authorization. */
@Service
public class PatternDetailService {
    private static final Logger log=LoggerFactory.getLogger(PatternDetailService.class);
    private static final BigInteger MAX_ID=new BigInteger("18446744073709551615");
    private final PatternDetailReader reader;
    public PatternDetailService(PatternDetailReader reader) { this.reader=reader; }

    public PatternDetailResponse detail(String runId,String patternId) {
        if(runId==null || runId.isBlank() || runId.length()>40) throw new PatternReadException(INVALID_RUN_ID);
        if(patternId==null || !patternId.matches("[1-9][0-9]{0,19}")
                || new BigInteger(patternId).compareTo(MAX_ID)>0) throw new PatternReadException(INVALID_PATTERN_ID);
        try {
            var s=reader.read(runId,patternId);
            if(s==null) throw new PatternReadException(RUN_NOT_FOUND);
            if(!"SUCCEEDED".equals(s.status())) {
                if(List.of("QUEUED","RUNNING","FAILED","LEGACY_UNVERIFIED").contains(s.status()==null?"":s.status()))
                    throw new PatternReadException(RESULTS_NOT_AVAILABLE);
                throw new PatternReadException(RESULT_INCONSISTENT);
            }
            if(s.publishedCount()==null || s.actualCount()==null || s.actualCount().signum()<0
                    || !s.publishedCount().equals(s.actualCount())) throw new PatternReadException(RESULT_INCONSISTENT);
            var h=s.pattern();
            if(h==null || !runId.equals(h.runId()) || !patternId.equals(h.patternId()))
                throw new PatternReadException(PATTERN_NOT_FOUND);
            var issues=new ArrayList<Issue>();
            var rows=s.rows().stream().sorted(Comparator.comparingLong(PatternDetailReader.Row::itemsetOrder)
                    .thenComparingLong(PatternDetailReader.Row::itemOrder)).toList();
            var groups=new LinkedHashMap<Long,List<Item>>();
            boolean bad=rows.isEmpty();
            long lastGroup=0,lastOrder=0,lastItem=0;
            var canonical=new StringBuilder("<");
            for(var r:rows) {
                if(r.itemsetOrder()!=lastGroup) {
                    if(lastGroup!=0) canonical.append(')');
                    canonical.append('(');
                    if(r.itemsetOrder()!=lastGroup+1) bad=true;
                    lastGroup=r.itemsetOrder(); lastOrder=0; lastItem=0;
                } else canonical.append(' ');
                if(r.itemOrder()!=lastOrder+1 || r.itemId()<=lastItem || r.itemId()>Integer.MAX_VALUE) bad=true;
                lastOrder=r.itemOrder(); lastItem=r.itemId();
                canonical.append(r.itemId());
                if(!r.mapped()) issues.add(new Issue("MISSING_RUN_DICTIONARY_ITEM",r.itemsetOrder(),r.itemOrder(),r.itemId()));
                groups.computeIfAbsent(r.itemsetOrder(),x->new ArrayList<>()).add(new Item(r.itemOrder(),r.itemId(),
                        r.mapped()?MappingStatus.MAPPED:MappingStatus.MISSING,
                        r.mapped()?r.productId():null,r.mapped()?r.nameSnapshot():null,
                        r.mapped()?r.skuSnapshot():null,r.mapped()?r.itemWeight():null));
            }
            if(!rows.isEmpty()) canonical.append(')');
            canonical.append('>');
            if(bad || rows.size()!=h.patternItemCount() || groups.size()!=h.itemsetCount()
                    || !canonical.toString().equals(h.canonicalPattern()))
                issues.add(new Issue("STRUCTURE_MISMATCH",null,null,null));
            var itemsets=groups.entrySet().stream().map(e->new Itemset(e.getKey(),e.getValue())).toList();
            log.debug("Pattern detail read: groups={}, issues={}",itemsets.size(),issues.size());
            return new PatternDetailResponse(h.runId(),h.patternId(),h.canonicalPattern(),h.supportAbs(),
                    h.supportRatio(),h.weightedSupport(),h.patternWeight(),h.patternWeightPolicy(),
                    h.patternItemCount(),h.itemsetCount(),s.sequenceCount(),
                    issues.isEmpty()?IntegrityStatus.COMPLETE:IntegrityStatus.INCOMPLETE,issues,itemsets);
        } catch(DataAccessException ex) {
            log.error("Pattern detail database read failed",ex); throw ex;
        }
    }
}
