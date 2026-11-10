package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.run.RunStatusResponse;
import vn.edu.wspm.backend.services.run.RunStatusService;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

/** Shared ADMIN authorization precedes request parsing and lookup. Independent of execution.enabled. */
@RestController
@RequiredArgsConstructor
public class RunStatusController {
    private final RunStatusService service;
    @GetMapping(value = "/api/v1/admin/mining-runs/{runId}", produces = "application/json")
    public ResponseEntity<ApiResponse<RunStatusResponse>> read(@PathVariable("runId") String runId, HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Run status accepts no query parameters or body.");
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(service.read(runId), RequestIdFilter.requestId(request)));
    }
}
