package vn.edu.wspm.backend.controllers.api.v1.admin.miningrun;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
@RequestMapping("/api/v1/admin/mining-runs")
public class MiningRunQueryController {

    private final MiningRunQueryService miningRunQueryService;

    public MiningRunQueryController(MiningRunQueryService miningRunQueryService) {
        this.miningRunQueryService = miningRunQueryService;
    }

    @GetMapping
    public ApiResponse<MiningRunPageResponse> findAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "algorithm", required = false) String algorithm,
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request) {
        return ApiResponse.success(miningRunQueryService.findAll(page, size, algorithm, status),
                RequestIdFilter.requestId(request));
    }

    @GetMapping("/{runId}")
    public ApiResponse<MiningRunDetailResponse> findById(
            @PathVariable("runId") String runId, HttpServletRequest request) {
        return ApiResponse.success(miningRunQueryService.findById(runId), RequestIdFilter.requestId(request));
    }
}
