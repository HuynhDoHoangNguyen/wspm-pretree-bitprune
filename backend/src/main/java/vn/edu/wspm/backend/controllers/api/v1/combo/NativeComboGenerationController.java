package vn.edu.wspm.backend.controllers.api.v1.combo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
@RestController
@RequestMapping("/api/v1/admin/combo-generation")
public class NativeComboGenerationController {
    private final vn.edu.wspm.backend.services.combo.generation.ComboGenerationService service;private final AdminPrincipalResolver principals;
    private final tools.jackson.databind.ObjectMapper json;
    public NativeComboGenerationController(vn.edu.wspm.backend.services.combo.generation.ComboGenerationService service,AdminPrincipalResolver principals,tools.jackson.databind.ObjectMapper json){this.service=service;this.principals=principals;this.json=json;}
    public record PreviewCommand(String runId,String anchorProductId,Integer k,String requestIdentity,vn.edu.wspm.backend.services.combo.generation.ProductSelection selection,vn.edu.wspm.backend.services.combo.generation.CategorySelection categorySelection){}
    public record SubmitCommand(String contextToken){}
    @PostMapping(value="/preview",consumes="application/json")
    public ApiResponse<NativeComboGeneration.Preview> preview(@RequestBody String body,HttpServletRequest request){
        var actor=principals.require(request);var command=decode(body,request,PreviewCommand.class,java.util.Set.of("runId","anchorProductId","k","requestIdentity","selection","categorySelection"));
        if(command.categorySelection()!=null)return ApiResponse.success(service.previewCategories(command.runId(),command.anchorProductId(),command.k(),command.requestIdentity(),actor,command.categorySelection()),RequestIdFilter.requestId(request));
        if(command.selection()==null)throw ApplicationException.invalid("selection","Select at least one run item and quantity.");
        return ApiResponse.success(service.previewSelected(command.runId(),command.anchorProductId(),command.k(),command.requestIdentity(),actor,command.selection()),RequestIdFilter.requestId(request));
    }
    @GetMapping("/category-options/{runId}")
    public ApiResponse<NativeComboGeneration.CategoryOptions> categoryOptions(@PathVariable("runId") String runId,HttpServletRequest request)throws java.io.IOException{
        var actor=principals.require(request);
        vn.edu.wspm.backend.web.common.RequestChecks.requireNoParametersOrBody(request,"Category options accept no parameters or body.");
        return ApiResponse.success(service.categoryOptions(runId,actor),RequestIdFilter.requestId(request));
    }
    @GetMapping("/options/{runId}")
    public ApiResponse<NativeComboGeneration.Options> options(@PathVariable("runId") String runId,HttpServletRequest request)throws java.io.IOException{
        var actor=principals.require(request);
        vn.edu.wspm.backend.web.common.RequestChecks.requireNoParametersOrBody(request,"Selection options accept no parameters or body.");
        return ApiResponse.success(service.selectionOptions(runId,actor),RequestIdFilter.requestId(request));
    }
    @PostMapping(value="/submit",consumes="application/json")
    public ApiResponse<NativeComboGeneration.Outcome> submit(@RequestBody String body,HttpServletRequest request){return command(body,request,false);}
    @PostMapping(value="/reconcile",consumes="application/json")
    public ApiResponse<NativeComboGeneration.Outcome> reconcile(@RequestBody String body,HttpServletRequest request){return command(body,request,true);}
    private ApiResponse<NativeComboGeneration.Outcome> command(String body,HttpServletRequest request,boolean reconcile){
        var actor=principals.require(request);var command=decode(body,request,SubmitCommand.class,java.util.Set.of("contextToken"));
        return ApiResponse.success(service.writePublished(command.contextToken(),actor,reconcile),RequestIdFilter.requestId(request));
    }
    private <T>T decode(String body,HttpServletRequest request,Class<T> type,java.util.Set<String> fields){
        if(!request.getParameterMap().isEmpty()||body.length()>600000)throw ApplicationException.invalid("command","Invalid generation command.");
        try{var tree=json.readTree(body);if(!tree.isObject())throw new IllegalArgumentException();
            for(var field:tree.propertyNames())if(!fields.contains(field))throw new IllegalArgumentException();
            if(type==PreviewCommand.class){
                var category=tree.get("categorySelection");boolean categories=category!=null&&!category.isNull();
                var s=categories?category:tree.get("selection");if(s==null||!s.isObject()||categories&&tree.get("selection")!=null&&!tree.get("selection").isNull())throw new IllegalArgumentException();
                String list=categories?"requiredCategories":"requiredItems",id=categories?"categoryId":"itemId";
                for(var field:s.propertyNames())if(!java.util.Set.of(list,"minDistinctProducts","maxDistinctProducts").contains(field))throw new IllegalArgumentException();
                for(String field:java.util.List.of("minDistinctProducts","maxDistinctProducts"))if(s.get(field)==null||!s.get(field).isIntegralNumber()||!s.get(field).canConvertToInt())throw new IllegalArgumentException();
                var items=s.get(list);if(items==null||!items.isArray()||items.size()>4000)throw new IllegalArgumentException();
                for(var item:items){
                    if(!item.isObject()||item.size()!=2||item.get(id)==null||item.get("quantity")==null)throw new IllegalArgumentException();
                    if(categories?!item.get(id).isString():!item.get(id).isIntegralNumber()||!item.get(id).canConvertToLong())throw new IllegalArgumentException();
                    if(!item.get("quantity").isIntegralNumber()||!item.get("quantity").canConvertToLong())throw new IllegalArgumentException();
                }
                if(tree.get("k")!=null&&!tree.get("k").isNull()&&(!tree.get("k").isIntegralNumber()||!tree.get("k").canConvertToInt()))throw new IllegalArgumentException();
            }
            return json.readValue(body,type);
        }catch(RuntimeException e){throw ApplicationException.invalid("command","Unsupported generation fields or types.");}
    }
}
