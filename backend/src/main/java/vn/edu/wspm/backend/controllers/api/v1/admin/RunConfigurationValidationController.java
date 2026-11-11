package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.run.RunConfigurationValidationResponse;
import vn.edu.wspm.backend.services.run.RunConfigurationValidationService;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.web.wdm.RunRequestReader;
import java.io.IOException;
import java.util.Set;

/** Shared ADMIN authorization precedes parsing, verification and operational gate checks. */
@RestController
@RequiredArgsConstructor
public class RunConfigurationValidationController {
    private final RunRequestReader reader;
    private final RunConfigurationValidationService service;
    @PostMapping(value = "/api/v1/admin/mining-runs/configuration-validation", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ApiResponse<RunConfigurationValidationResponse>> validate(HttpServletRequest request) throws IOException {
        RequestChecks.requireAllowedQueryNames(request.getParameterMap().keySet(), Set.of(),
                "INVALID_VALUE", "Configuration validation accepts no query parameters.");
        var result = service.validate(reader.read(request.getInputStream()));
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(result, RequestIdFilter.requestId(request)));
    }
}
