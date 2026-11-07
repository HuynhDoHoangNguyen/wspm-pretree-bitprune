package vn.edu.wspm.backend.controllers.api.v1.admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.dataset.ConversionResponse;
import vn.edu.wspm.backend.services.dataset.conversion.SnapshotConversionService;
import vn.edu.wspm.backend.services.dataset.conversion.ConversionReadService;
import vn.edu.wspm.backend.web.common.RequestChecks;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import java.io.IOException;

/** Conversion execution/read/download boundary; shared ADMIN authorization runs before MVC. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dataset-snapshots/{snapshotId}")
public class ConversionAdmissionController {
    private final SnapshotConversionService conversion;
    private final ConversionReadService reads;

    @PostMapping(value = "/conversion", produces = "application/json")
    public ResponseEntity<ApiResponse<ConversionResponse>> convert(@PathVariable("snapshotId") String id, HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Conversion admission accepts no body or query parameters.");
        conversion.convert(id);
        return response(reads.verified(id).response(), request);
    }

    @GetMapping(value = "/conversion", produces = "application/json")
    public ResponseEntity<ApiResponse<ConversionResponse>> status(@PathVariable("snapshotId") String id, HttpServletRequest request) throws IOException {
        noReadInput(request);
        return response(reads.status(id), request);
    }
    @GetMapping(value = "/artifacts/sequence", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<byte[]> sequence(@PathVariable("snapshotId") String id, HttpServletRequest request) throws IOException {
        noReadInput(request);
        return attachment(reads.verified(id).sequenceBytes(), "snapshot-" + id + ".spmf", "text/plain;charset=UTF-8");
    }
    @GetMapping(value = "/artifacts/mapping", produces = "application/json")
    public ResponseEntity<byte[]> mapping(@PathVariable("snapshotId") String id, HttpServletRequest request) throws IOException {
        noReadInput(request);
        return attachment(reads.verified(id).mappingBytes(), "snapshot-" + id + "-mapping.json", "application/json");
    }
    private void noReadInput(HttpServletRequest request) throws IOException {
        RequestChecks.requireNoParametersOrBody(request, "Conversion read accepts no body or query parameters.");
    }
    private ResponseEntity<ApiResponse<ConversionResponse>> response(ConversionResponse data, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.success(data, RequestIdFilter.requestId(request)));
    }
    private ResponseEntity<byte[]> attachment(byte[] bytes, String name, String contentType) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Type", contentType).header("Content-Disposition", ContentDisposition.attachment().filename(name).build().toString())
                .contentLength(bytes.length).body(bytes);
    }
}
