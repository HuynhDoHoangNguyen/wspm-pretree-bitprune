package vn.edu.wspm.backend.services.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import vn.edu.wspm.backend.dto.pattern.DictionaryResponse;
import vn.edu.wspm.backend.repositories.DictionaryReader;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

@Service
public class DictionaryService {
    private static final Logger log=LoggerFactory.getLogger(DictionaryService.class);
    private final DictionaryReader reader;
    public DictionaryService(DictionaryReader reader) { this.reader=reader; }
    public DictionaryResponse search(String runId,String query,Integer requestedPage,Integer requestedSize) {
        if(runId==null || runId.isBlank() || runId.length()>40) throw new PatternReadException(INVALID_RUN_ID);
        int page=requestedPage==null?0:requestedPage,size=requestedSize==null?20:requestedSize;
        if(page<0 || size<1 || size>100) throw new PatternReadException(INVALID_PAGE);
        String raw=query==null?"":query.replaceAll("(?U)^\\s+|\\s+$","");
        if(raw.codePointCount(0,raw.length())>100) throw new PatternReadException(INVALID_QUERY);
        String normalized=DictionarySearch.normalize(raw);
        try {
            var result=reader.search(runId,normalized,page,size);
            log.debug("Run dictionary read: page={}, size={}",page,size);
            return result;
        } catch(DataAccessException ex) { log.error("Run dictionary database read failed",ex);throw ex; }
    }
}
