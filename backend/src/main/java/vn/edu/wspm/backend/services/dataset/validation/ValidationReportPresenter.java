package vn.edu.wspm.backend.services.dataset.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.mappers.ValidationResponseMapper;

/** Pure presentation of a frozen report; no validation execution, repository or filesystem access. */
@Component
@RequiredArgsConstructor
public class ValidationReportPresenter {
    private final ValidationResponseMapper mapper;

    public ValidationResponse page(ValidationReport report, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid diagnostic page");
        int total = report.diagnostics().size();
        int from = (int) Math.min((long) page * size, total);
        var issues = new ValidationResponse.DiagnosticPage(
                mapper.toDiagnostics(report.diagnostics().subList(from, Math.min(from + size, total))), page, size,
                Integer.toString(total), Long.toString((total + (long) size - 1) / size));
        return mapper.toResponse(report, issues);
    }
}
