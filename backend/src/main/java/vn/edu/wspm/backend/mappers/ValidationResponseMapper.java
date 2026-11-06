package vn.edu.wspm.backend.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.util.List;

/** Only explicit public DTO types leave this boundary; manifests remain private. */
@Mapper(config = BackendMapperConfig.class)
public interface ValidationResponseMapper {
    @Mapping(target = "issues", source = "page")
    ValidationResponse toResponse(ValidationReport report, ValidationResponse.DiagnosticPage page);

    ValidationResponse.Counts toCounts(ValidationReport.Counts counts);
    ValidationResponse.Diagnostic toDiagnostic(ValidationReport.Diagnostic diagnostic);
    ValidationResponse.Position toPosition(OrderConsistencyIssue.Position position);
    List<ValidationResponse.Diagnostic> toDiagnostics(List<ValidationReport.Diagnostic> diagnostics);
}
