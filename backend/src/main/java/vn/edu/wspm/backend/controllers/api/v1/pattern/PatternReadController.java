package vn.edu.wspm.backend.controllers.api.v1.pattern;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.pattern.*;
import vn.edu.wspm.backend.services.pattern.*;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import static vn.edu.wspm.backend.services.pattern.PatternReadException.Code.*;

/** Transport adapter only; each service retains its consistent read boundary. */
@RestController
@RequestMapping("/api/v1/admin/pattern-runs/{runId}")
public final class PatternReadController {
    private final PatternListService lists;
    private final PatternDetailService details;
    private final DictionaryService dictionaries;
    private final AdminPrincipalResolver principals;

    public PatternReadController(PatternListService lists, PatternDetailService details,
            DictionaryService dictionaries, AdminPrincipalResolver principals) {
        this.lists=lists; this.details=details; this.dictionaries=dictionaries; this.principals=principals;
    }

    @GetMapping("/patterns")
    public ApiResponse<PatternListResponse> list(@PathVariable("runId") String runId, HttpServletRequest request) {
        principals.require(request);
        validateQuery(request, Set.of("page","size","itemId","supportMin","supportMax","weightMin","weightMax"), true);
        String[] ids=request.getParameterValues("itemId");
        var input=new PatternFilterInput(ids==null?List.of():Arrays.asList(ids),
                request.getParameter("supportMin"),request.getParameter("supportMax"),
                request.getParameter("weightMin"),request.getParameter("weightMax"));
        return ApiResponse.success(lists.list(runId,integer(request,"page"),integer(request,"size"),input), RequestIdFilter.requestId(request));
    }

    @GetMapping("/patterns/{patternId}")
    public ApiResponse<PatternDetailResponse> detail(@PathVariable("runId") String runId,
            @PathVariable("patternId") String patternId, HttpServletRequest request) {
        principals.require(request);
        validateQuery(request,Set.of(),false);
        return ApiResponse.success(details.detail(runId,patternId),RequestIdFilter.requestId(request));
    }

    @GetMapping("/dictionary")
    public ApiResponse<DictionaryResponse> dictionary(@PathVariable("runId") String runId, HttpServletRequest request) {
        principals.require(request);
        validateQuery(request,Set.of("q","page","size"),false);
        return ApiResponse.success(dictionaries.search(runId,request.getParameter("q"),
                integer(request,"page"),integer(request,"size")),RequestIdFilter.requestId(request));
    }

    private static void validateQuery(HttpServletRequest request,Set<String> allowed,boolean repeatedItems) {
        for(var entry:request.getParameterMap().entrySet()) {
            if(!allowed.contains(entry.getKey()) || entry.getValue().length!=1
                    && !(repeatedItems && entry.getKey().equals("itemId")))
                throw new PatternReadException(INVALID_QUERY);
        }
    }

    private static Integer integer(HttpServletRequest request,String name) {
        String raw=request.getParameter(name);
        if(raw==null) return null;
        if(!raw.matches("[0-9]{1,10}")) throw new PatternReadException(INVALID_PAGE);
        try { return Integer.valueOf(raw); }
        catch(NumberFormatException invalid) { throw new PatternReadException(INVALID_PAGE); }
    }
}
