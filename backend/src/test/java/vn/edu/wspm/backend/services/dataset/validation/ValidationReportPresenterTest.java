package vn.edu.wspm.backend.services.dataset.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.mappers.ValidationResponseMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationReportPresenterTest {
    private final ValidationReportPresenter presenter = new ValidationReportPresenter(Mappers.getMapper(ValidationResponseMapper.class));
    private final ValidationReport empty = new ValidationReport("WDM_VALIDATION_REPORT_V1", "wdm-order-csv-v1", "WDM_ORDER_CLEANING_V1",
            "snapshot-1", "a".repeat(64), "42", "2026-11-06T00:00:00Z", "INVALID", "EMPTY_INPUT", true,
            new ValidationReport.Counts("0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0"), List.of(), List.of());

    @Test void emptyDiagnosticsAreEmptyPageWithZeroTotalsAndReportStaysUnchanged() {
        var response = presenter.page(empty, Integer.MAX_VALUE, 100);
        assertTrue(response.issues().content().isEmpty());
        assertEquals("0", response.issues().totalElements()); assertEquals("0", response.issues().totalPages());
        assertEquals(Integer.MAX_VALUE, response.issues().page()); assertEquals(100, response.issues().size());
        assertEquals("INVALID", response.validationStatus()); assertEquals("EMPTY_INPUT", response.cleaningOutcome());
        assertEquals("0", response.counts().keptRecords()); assertTrue(empty.diagnostics().isEmpty());
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101"})
    void rejectsInvalidPresentationBoundsWithoutRewritingInput(int page, int size) {
        assertThrows(IllegalArgumentException.class, () -> presenter.page(empty, page, size));
        assertTrue(empty.diagnostics().isEmpty()); assertTrue(empty.manifest().isEmpty());
    }
}
