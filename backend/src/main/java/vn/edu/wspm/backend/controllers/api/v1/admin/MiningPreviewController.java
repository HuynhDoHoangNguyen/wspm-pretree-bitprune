package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.run.MiningPreviewResponse;
import vn.edu.wspm.backend.services.run.MiningPreviewService;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.RunRequestReader;

import java.io.IOException;
import java.util.Set;

/** Shared ADMIN filters authorize before MVC transport checks, parsing and resource access. */
@RestController
@RequiredArgsConstructor
public class MiningPreviewController {
    private final RunRequestReader reader;
    private final MiningPreviewService service;

    @PostMapping(value = "/api/v1/admin/mining-runs/preview", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<MiningPreviewResponse>> preview(HttpServletRequest request) throws IOException {
        RequestChecks.requireAllowedQueryNames(request.getParameterMap().keySet(), Set.of(),
                "INVALID_VALUE", "Mining preview accepts no query parameters.");
        var response = service.preview(reader.read(request.getInputStream()));
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(response, RequestIdFilter.requestId(request)));
    }
}
