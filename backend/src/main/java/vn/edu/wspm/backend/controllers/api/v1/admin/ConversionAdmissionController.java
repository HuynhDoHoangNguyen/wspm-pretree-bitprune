package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionValidationGate;
import vn.edu.wspm.backend.web.common.RequestChecks;
import java.io.IOException;

/** Gate-only boundary. Converter implementation/result API remains WDM-03. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dataset-snapshots/{snapshotId}/conversion")
public class ConversionAdmissionController {
    private final ConversionValidationGate gate;

    @PostMapping(produces = "application/json")
    public void admit(@PathVariable("snapshotId") String id, HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Conversion admission accepts no body or query parameters.");
        gate.prepare(id);
        throw new ApplicationException(ErrorCode.SERVICE_UNAVAILABLE, "Conversion is not available yet.");
    }
}
