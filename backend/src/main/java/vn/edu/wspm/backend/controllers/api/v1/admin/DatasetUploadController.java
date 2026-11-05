package vn.edu.wspm.backend.controllers.api.v1.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.dataset.DatasetSnapshotResponse;
import vn.edu.wspm.backend.web.common.RequestIdFilter;
import vn.edu.wspm.backend.services.dataset.CsvUploadService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/dataset-snapshots")
public class DatasetUploadController {
    private final CsvUploadService service;

    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<ApiResponse<DatasetSnapshotResponse>> upload(MultipartHttpServletRequest request) {
        // WdmRequestFilter authorizes before MVC resolves this lazy multipart request.
        var snapshot = service.upload(request.getMultiFileMap(), !request.getParameterMap().isEmpty());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Cache-Control", "no-store")
                .body(ApiResponse.success(snapshot, RequestIdFilter.requestId(request)));
    }
}
