package vn.edu.wspm.backend.controllers.api.v1.combo;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.services.combo.review.ComboPublicationService;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
public class ComboPublicationController {
    private final ComboPublicationService service;
    private final AdminPrincipalResolver principals;
    public ComboPublicationController(ComboPublicationService service, AdminPrincipalResolver principals) {
        this.service = service; this.principals = principals;
    }
    @PostMapping("/api/v1/admin/combos/batches/{batchId}/publication")
    public ApiResponse<ComboPublicationService.Publication> publish(@PathVariable("batchId") String id,
            HttpServletRequest request) throws IOException {
        var actor = principals.require(request);
        RequestChecks.requireNoParametersOrBody(request, "Publication accepts no parameters or body.");
        return ApiResponse.success(service.publish(id, actor), RequestIdFilter.requestId(request));
    }
}
