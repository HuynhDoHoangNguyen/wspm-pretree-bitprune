package vn.edu.wspm.backend.controllers.api.v1.run;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.wspm.backend.common.api.ApiResponse;
import vn.edu.wspm.backend.dto.run.RunListResponse;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.services.run.RunListService;
import vn.edu.wspm.backend.web.auth.AdminPrincipalResolver;
import vn.edu.wspm.backend.web.common.RequestIdFilter;

@RestController
@RequestMapping("/api/v1/admin/pattern-runs")
public final class RunListController {
    private final RunListService service;
    private final AdminPrincipalResolver principals;
    public RunListController(RunListService service, AdminPrincipalResolver principals) {
        this.service = service; this.principals = principals;
    }
    @GetMapping
    public ApiResponse<RunListResponse> list(HttpServletRequest request) {
        principals.require(request);
        for (var entry : request.getParameterMap().entrySet()) {
            if ((!entry.getKey().equals("page") && !entry.getKey().equals("size")) || entry.getValue().length != 1)
                throw ApplicationException.invalid("query", "Only scalar page and size are supported.");
        }
        return ApiResponse.success(service.list(integer(request, "page"), integer(request, "size")), RequestIdFilter.requestId(request));
    }
    private static Integer integer(HttpServletRequest request, String name) {
        String raw = request.getParameter(name);
        if (raw == null) return null;
        if (!raw.matches("[0-9]{1,10}")) throw ApplicationException.invalid(name, "Supply a nonnegative integer.");
        try { return Integer.valueOf(raw); }
        catch (NumberFormatException invalid) { throw ApplicationException.invalid(name, "Integer is out of range."); }
    }
}
