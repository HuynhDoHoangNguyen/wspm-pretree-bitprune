package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.dataset.SnapshotDetailResponse;
import vn.edu.wspm.backend.services.dataset.SnapshotReadService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dataset-snapshots/{snapshotId}")
public class SnapshotReadController {
    private final SnapshotReadService service;

    @GetMapping(produces = "application/json")
    public ApiResponse<SnapshotDetailResponse> detail(@PathVariable("snapshotId") String snapshotId, HttpServletRequest request) {
        return ApiResponse.success(service.detail(snapshotId), RequestIdFilter.requestId(request));
    }

    @GetMapping(value = "/artifacts/raw", produces = "text/csv")
    public ResponseEntity<byte[]> raw(@PathVariable("snapshotId") String snapshotId) {
        byte[] bytes = service.raw(snapshotId);
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("Content-Disposition",
                        ContentDisposition.attachment().filename("snapshot-" + snapshotId + "-raw.csv").build().toString())
                .header("Content-Type", "text/csv").contentLength(bytes.length).body(bytes);
    }
}
