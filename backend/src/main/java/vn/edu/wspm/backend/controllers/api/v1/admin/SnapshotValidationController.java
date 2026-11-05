package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.dataset.validation.SnapshotValidationService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/dataset-snapshots/{snapshotId}/validation")
public class SnapshotValidationController {
    private final SnapshotValidationService service;
    public SnapshotValidationController(SnapshotValidationService service) { this.service = service; }

    @PostMapping(produces = "application/json")
    public ResponseEntity<ApiResponse<ValidationResponse>> validate(@PathVariable("snapshotId") String id,
            HttpServletRequest request) throws IOException {
        if (!request.getParameterMap().isEmpty() || request.getInputStream().read() != -1) {
            throw ApplicationException.invalid("request", "Validation POST accepts no body or query parameters.");
        }
        return response(ValidationResponse.page(service.validate(id), 0, 20), request);
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<ApiResponse<ValidationResponse>> read(@PathVariable("snapshotId") String id, HttpServletRequest request) {
        if (request.getParameterMap().entrySet().stream().anyMatch(entry -> !Set.of("page", "size").contains(entry.getKey())
                || entry.getValue().length != 1)) throw ApplicationException.invalid("query", "Unknown or repeated query parameter.");
        int page = number(request.getParameter("page"), 0, "page");
        int size = number(request.getParameter("size"), 20, "size");
        if (size < 1 || size > 100) throw ApplicationException.invalid("size", "Supply a size from 1 to 100.");
        return response(ValidationResponse.page(service.read(id), page, size), request);
    }

    private int number(String text, int fallback, String field) {
        if (text == null) return fallback;
        if (!text.matches("0|[1-9][0-9]*")) throw ApplicationException.invalid(field, "Supply a nonnegative integer.");
        try { return Integer.parseInt(text); }
        catch (NumberFormatException invalid) { throw ApplicationException.invalid(field, "Value exceeds supported range."); }
    }

    private ResponseEntity<ApiResponse<ValidationResponse>> response(ValidationResponse data, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.success(data, RequestIdFilter.requestId(request)));
    }
}
