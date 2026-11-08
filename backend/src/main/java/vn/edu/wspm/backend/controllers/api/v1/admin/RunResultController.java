package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.services.run.query.RunResultQueryService;
import vn.edu.wspm.backend.web.common.*;

/** Shared ADMIN guard runs before all parsing and DB lookup. Execution need not be enabled. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/mining-runs/{runId}")
public class RunResultController {
    private final RunResultQueryService service;
    @GetMapping(value = "/results", produces = "application/json")
    public ResponseEntity<?> metadata(@PathVariable("runId") String runId, HttpServletRequest request) throws IOException {
        check(request, Set.of()); return response(service.metadata(runId), request);
    }
    @GetMapping(value = "/patterns", produces = "application/json")
    public ResponseEntity<?> patterns(@PathVariable("runId") String runId, HttpServletRequest request) throws IOException {
        check(request, Set.of("view", "page", "size"));
        return response(service.page(runId, request.getParameter("view"), request.getParameter("page"), request.getParameter("size")), request);
    }
    @GetMapping(value = "/patterns/{patternId}", produces = "application/json")
    public ResponseEntity<?> detail(@PathVariable("runId") String runId, @PathVariable("patternId") String patternId,
            HttpServletRequest request) throws IOException {
        check(request, Set.of("view")); return response(service.detail(runId, patternId, request.getParameter("view")), request);
    }
    @GetMapping(value = "/patterns/export", produces = "application/json")
    public ResponseEntity<byte[]> export(@PathVariable("runId") String runId, HttpServletRequest request) throws IOException {
        check(request, Set.of("view"));
        byte[] bytes = service.export(runId, request.getParameter("view"));
        return headers().contentType(MediaType.APPLICATION_JSON).contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("mining-result-" + runId + ".json").build().toString())
                .body(bytes);
    }
    private static void check(HttpServletRequest request, Set<String> allowed) throws IOException {
        RequestChecks.requireSingleQueryParameters(request, allowed, "Unsupported or repeated result query parameter.");
        RequestChecks.requireEmptyBody(request, "request", "Result reads accept no body.");
    }
    private static ResponseEntity.BodyBuilder headers() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff");
    }
    private static ResponseEntity<?> response(Object data, HttpServletRequest request) {
        return headers().body(ApiResponse.success(data, RequestIdFilter.requestId(request)));
    }
}
