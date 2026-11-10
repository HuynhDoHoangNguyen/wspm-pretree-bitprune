package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.services.workspace.MiningWorkspaceReadService;
import vn.edu.wspm.backend.services.run.execution.ManagedRunExecutionAdmission;
import vn.edu.wspm.backend.web.common.*;

/** ADMIN-only through the existing /api/v1/admin authorization boundary. */
@RestController
public class MiningWorkspaceReadController {
    private final MiningWorkspaceReadService service;
    private final ObjectProvider<ManagedRunExecutionAdmission> manager;
    public MiningWorkspaceReadController(MiningWorkspaceReadService service, ObjectProvider<ManagedRunExecutionAdmission> manager) {
        this.service = service; this.manager = manager;
    }
    @GetMapping("/api/v1/admin/dataset-snapshots")
    public ResponseEntity<?> snapshots(HttpServletRequest request) throws IOException {
        return list(false, request, Set.of("page", "size", "search", "validationStatus", "conversionStatus"));
    }
    @GetMapping("/api/v1/admin/mining-runs")
    public ResponseEntity<?> runs(HttpServletRequest request) throws IOException {
        return list(true, request, Set.of("page", "size", "search", "status", "algorithm", "snapshotId"));
    }
    private ResponseEntity<?> list(boolean runs, HttpServletRequest request, Set<String> allowed) throws IOException {
        RequestChecks.requireSingleQueryParameters(request, allowed, "Unsupported or repeated query parameter.");
        RequestChecks.requireEmptyBody(request, "request", "List reads accept no body.");
        var query = new HashMap<String, String>(); request.getParameterMap().forEach((k, v) -> query.put(k, v[0]));
        return response(service.list(runs, query), request);
    }
    @GetMapping("/api/v1/admin/dataset-snapshots/{snapshotId}/storage")
    public ResponseEntity<?> storage(@PathVariable("snapshotId") String snapshotId, HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Storage metadata accepts no parameters or body.");
        return response(service.storage(snapshotId), request);
    }
    public record Capacity(boolean enabled, String scope, Instant observedAt, ManagedRunExecutionAdmission.CapacitySnapshot snapshot) {}
    @GetMapping("/api/v1/admin/mining-execution/capacity")
    public ResponseEntity<?> capacity(HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Capacity accepts no parameters or body.");
        var provider = manager.getIfAvailable(); var snapshot = provider == null ? null : provider.capacitySnapshot();
        return response(new Capacity(provider != null, "CURRENT_BACKEND_INSTANCE", snapshot == null ? Instant.now() : snapshot.observedAt(), snapshot), request);
    }
    private static ResponseEntity<?> response(Object data, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(data, RequestIdFilter.requestId(request)));
    }
}
