package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.run.RunAcceptedResponse;
import vn.edu.wspm.backend.services.run.RunCreationService;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.RunRequestReader;

/** Shared ADMIN guard runs before this controller. 202 is conditional on committed creation and admission. */
@RestController
@RequiredArgsConstructor
public class RunCreationController {
    private final RunRequestReader reader;
    private final RunCreationService service;

    @PostMapping(value = "/api/v1/admin/mining-runs", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<RunAcceptedResponse>> create(HttpServletRequest request) throws IOException {
        RequestChecks.requireAllowedQueryNames(request.getParameterMap().keySet(), Set.of(),
                "INVALID_VALUE", "Run creation accepts no query parameters.");
        var result = service.create(reader.read(request.getInputStream()));
        return ResponseEntity.accepted().location(URI.create("/api/v1/admin/mining-runs/" + result.runId()))
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(result, RequestIdFilter.requestId(request)));
    }
}
