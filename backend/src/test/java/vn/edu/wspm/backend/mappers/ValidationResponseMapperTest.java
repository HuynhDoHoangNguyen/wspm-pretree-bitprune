package vn.edu.wspm.backend.mappers;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import vn.edu.wspm.backend.dto.dataset.ValidationResponse;
import vn.edu.wspm.backend.services.dataset.csv.OrderConsistencyIssue;
import vn.edu.wspm.backend.services.dataset.validation.ValidationReport;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationResponseMapperTest {
    private final ValidationResponseMapper mapper = Mappers.getMapper(ValidationResponseMapper.class);

    @Test void mapsNullableAndLargeStringCountersIntoIndependentPublicType() {
        var source = new ValidationReport.Counts("9007199254740993", null, "0", null, "1", "2", "3", "4", null, "5", "6", "7");
        assertEquals(new ValidationResponse.Counts("9007199254740993", null, "0", null, "1", "2", "3", "4", null, "5", "6", "7"),
                mapper.toCounts(source));
        assertNull(mapper.toCounts(null));
    }

    @Test void mapsPositionsAndNullableFieldsWithoutSharingInternalTypesOrMutableCollections() {
        var source = new ValidationReport.Diagnostic("STRUCTURE", "CSV_INVALID_UTF8", null, "DECODER_CURSOR",
                List.of(new OrderConsistencyIssue.Position(9007199254740993L, 2, 4)));
        var mutable = new ArrayList<>(List.of(source));
        var mapped = mapper.toDiagnostics(mutable); mutable.clear();
        var page = new ValidationResponse.DiagnosticPage(mapped, 0, 1, "1", "1"); mapped.clear();
        assertEquals(List.of(new ValidationResponse.Diagnostic("STRUCTURE", "CSV_INVALID_UTF8", null, "DECODER_CURSOR",
                List.of(new ValidationResponse.Position(9007199254740993L, 2, 4)))), page.content());
        assertThrows(UnsupportedOperationException.class, () -> page.content().clear());
        assertThrows(UnsupportedOperationException.class, () -> page.content().getFirst().positions().clear());
        assertEquals(1, source.positions().size());
    }
}
