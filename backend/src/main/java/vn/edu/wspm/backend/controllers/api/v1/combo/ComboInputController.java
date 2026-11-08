package vn.edu.wspm.backend.controllers.api.v1.combo;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.services.combo.generation.PublishedComboInputService;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
public final class ComboInputController {
    private final PublishedComboInputService inputs;
    private final AdminPrincipalResolver principals;
    private vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration generation;
    @org.springframework.beans.factory.annotation.Autowired
    public void generation(vn.edu.wspm.backend.services.combo.generation.NativeComboGeneration generation){this.generation=generation;}
    public ComboInputController(PublishedComboInputService inputs, AdminPrincipalResolver principals) {
        this.inputs = inputs; this.principals = principals;
    }
    @GetMapping("/api/v1/admin/mining-runs/{runId}/combo-input")
    public ApiResponse<PublishedComboInputService.Summary> input(@PathVariable("runId") String runId,
            HttpServletRequest request) throws IOException {
        principals.require(request);
        RequestChecks.requireNoParametersOrBody(request, "Combo input accepts no query or body.");
        var summary=inputs.summary(runId);
        if(generation!=null&&generation.configured())summary=new PublishedComboInputService.Summary(summary.runId(),summary.snapshotId(),summary.publicationDigest(),
                summary.patternCount(),summary.dictionaryCount(),summary.inputStatus(),"READY_FOR_PREVIEW",java.util.List.of());
        return ApiResponse.success(summary, RequestIdFilter.requestId(request));
    }
}
