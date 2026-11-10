package vn.edu.wspm.backend.controllers.api.v1.combo;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.common.api.*;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.combo.review.*;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

/** Shared ADMIN/Bearer filters execute before MVC. Never binds reviewer or approval from JSON. */
@RestController
@RequestMapping("/api/v1/admin/combos")
public final class ComboManagementController {
    private final ComboManagementService service;
    private final AdminPrincipalResolver principals;
    private static final JsonMapper JSON=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public ComboManagementController(ComboManagementService service,AdminPrincipalResolver principals){this.service=service;this.principals=principals;}
    @GetMapping public ApiResponse<Object> list(HttpServletRequest request) {
        principals.require(request);query(request,Set.of("batchId","status","comboKind","page","size"));
        return success(service.list(request.getParameter("batchId"),request.getParameter("status"),request.getParameter("comboKind"),integer(request,"page"),integer(request,"size")),request);
    }
    @GetMapping("/batches") public ApiResponse<Object> batches(HttpServletRequest request) {
        principals.require(request);query(request,Set.of("page","size"));
        return success(service.batches(integer(request,"page"),integer(request,"size")),request);
    }
    @GetMapping("/batches/{batchId}/combos") public ApiResponse<Object> batchCombos(@PathVariable("batchId") String id,HttpServletRequest request) {
        principals.require(request);query(request,Set.of("status","comboKind","page","size"));
        return success(service.batchCombos(id,request.getParameter("status"),request.getParameter("comboKind"),integer(request,"page"),integer(request,"size")),request);
    }
    @GetMapping("/{comboId}") public ApiResponse<Object> detail(@PathVariable("comboId") String id,HttpServletRequest request) {
        principals.require(request);query(request,Set.of());return success(service.detail(id),request);
    }
    @PostMapping(value="/{comboId}/review",consumes="application/json")
    public ApiResponse<Object> review(@PathVariable("comboId") String id,@RequestBody String body,HttpServletRequest request) {
        var actor=principals.require(request);query(request,Set.of());
        if(body.length()>16384)throw new ApplicationException(ErrorCode.PAYLOAD_TOO_LARGE);
        final ComboReview.Command command;
        try {
            var json=JSON.readTree(body);
            if(!json.isObject()||!json.has("decision")||!json.has("expectedReviewToken"))throw new IllegalArgumentException();
            for(var name:json.propertyNames())if(!Set.of("decision","reviewNote","expectedReviewToken").contains(name))throw new IllegalArgumentException();
            if(!json.get("decision").isString()||!json.get("expectedReviewToken").isString()
                    ||json.hasNonNull("reviewNote")&&!json.get("reviewNote").isString())throw new IllegalArgumentException();
            command=new ComboReview.Command(json.get("decision").asString(),json.hasNonNull("reviewNote")?json.get("reviewNote").asString():null,json.get("expectedReviewToken").asString());
            if(command.expectedReviewToken().length()>256)throw new IllegalArgumentException();
        }catch(Exception invalid){throw new ApplicationException(ErrorCode.INVALID_REVIEW_COMMAND);}
        var result=service.review(id,command,actor);
        if(!Set.of("APPLIED","ALREADY_DECIDED","UNKNOWN").contains(result.outcome())) {
            String issue=result.issues().isEmpty()?"REVIEW_BLOCKED":result.issues().getFirst();
            ErrorCode code=switch(issue){
                case "COMBO_NOT_FOUND" -> ErrorCode.COMBO_NOT_FOUND;
                case "INVALID_REVIEW_COMMAND" -> ErrorCode.INVALID_REVIEW_COMMAND;
                case "REVIEW_NOTE_REQUIRED" -> ErrorCode.REVIEW_NOTE_REQUIRED;
                case "INVALID_REVIEW_NOTE" -> ErrorCode.INVALID_REVIEW_NOTE;
                case "STALE_REVIEW_DATA" -> ErrorCode.STALE_REVIEW_DATA;
                case "REVIEW_DECISION_CONFLICT","COMBO_STATE_NOT_REVIEWABLE" -> ErrorCode.REVIEW_DECISION_CONFLICT;
                case "REVIEWER_IDENTITY_INVALID" -> ErrorCode.ACCESS_DENIED;
                case "REVIEW_ROLLED_BACK" -> ErrorCode.SERVICE_UNAVAILABLE;
                default -> ErrorCode.REVIEW_BLOCKED;
            };
            throw new ApplicationException(code,code.message(),result.issues().stream().map(i->new FieldErrorDetail("review",i,"Review prerequisite not satisfied.")).toList());
        }
        // Excludes internal proposedChange; successful response describes committed/uncertain state only.
        return success(new ReviewResponse(result.outcome(),result.currentStatus(),result.storedAudit(),result.issues()),request);
    }
    public record ReviewResponse(String outcome,String currentStatus,vn.edu.wspm.backend.dto.combo.ComboAdminRead.Audit storedAudit,List<String> issues) {}
    private static ApiResponse<Object> success(Object data,HttpServletRequest r){return ApiResponse.success(wire(data),RequestIdFilter.requestId(r));}
    /** Public projection keeps DECIMAL money exact as strings without changing persisted read DTOs. */
    private static Object wire(Object value) {
        if(value instanceof BigDecimal d)return d.toPlainString();
        if(value instanceof Collection<?> list)return list.stream().map(ComboManagementController::wire).toList();
        if(value!=null&&value.getClass().isRecord()){
            var map=new LinkedHashMap<String,Object>();
            try {for(var field:value.getClass().getRecordComponents())map.put(field.getName(),wire(field.getAccessor().invoke(value)));}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("COMBO_PROJECTION_FAILED");}return map;
        }
        return value;
    }
    private static void query(HttpServletRequest r,Set<String> allowed){for(var e:r.getParameterMap().entrySet())if(!allowed.contains(e.getKey())||e.getValue().length!=1)throw new ApplicationException(ErrorCode.INVALID_QUERY);}
    private static Integer integer(HttpServletRequest r,String key){String v=r.getParameter(key);if(v==null)return null;try{if(!v.matches("[0-9]{1,10}"))throw new NumberFormatException();return Integer.valueOf(v);}catch(NumberFormatException bad){throw new ApplicationException(ErrorCode.INVALID_PAGE);}}
}
