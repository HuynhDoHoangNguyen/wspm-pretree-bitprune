package vn.edu.wspm.backend.services.pattern;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.wspm.backend.dto.pattern.PatternListResponse;
import vn.edu.wspm.backend.repositories.PatternListReader;
import vn.edu.wspm.backend.repositories.PatternListReader.Snapshot;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatternListServiceTest {
    private final PatternListReader reader = mock(PatternListReader.class);
    private final PatternListService service = new PatternListService(reader);

    @ParameterizedTest
    @CsvSource({"QUEUED,NOT_READY", "RUNNING,NOT_READY", "FAILED,FAILED",
            "LEGACY_UNVERIFIED,UNVERIFIED"})
    void unavailableDoesNotFabricateTotals(String status, String state) {
        when(reader.read("R", 0, 20)).thenReturn(new Snapshot(status, null, null, List.of()));
        var result = service.list("R", null, null);
        assertEquals(state, result.resultState().name());
        assertNull(result.runPatternTotal()); assertNull(result.filteredTotal());
        assertTrue(result.items().isEmpty());
    }

    @Test void emptySuccessIsReady() {
        when(reader.read("R", 0, 20)).thenReturn(new Snapshot("SUCCEEDED", BigInteger.ZERO,
                BigInteger.ZERO, List.of()));
        var result = service.list("R", null, null);
        assertEquals(PatternListResponse.ResultState.READY, result.resultState());
        assertEquals("0", result.runPatternTotal()); assertEquals("0", result.filteredTotal());
    }

    @Test void missingRunHasExplicitCode() {
        assertCode(PatternReadException.Code.RUN_NOT_FOUND, () -> service.list("missing", 0, 20));
    }

    @Test void nullAndMismatchedCountsFail() {
        for (BigInteger count : new BigInteger[]{null, BigInteger.TWO}) {
            when(reader.read("R", 0, 20)).thenReturn(new Snapshot("SUCCEEDED", count,
                    BigInteger.ONE, List.of()));
            assertCode(PatternReadException.Code.RESULT_INCONSISTENT, () -> service.list("R", 0, 20));
        }
    }

    @Test void unknownStatusFailsRatherThanBecomingReady() {
        when(reader.read("R",0,20)).thenReturn(new Snapshot("PENDING",null,null,List.of()));
        assertCode(PatternReadException.Code.RESULT_INCONSISTENT, () -> service.list("R",0,20));
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101", "0,-1"})
    void validatesBeforeQuery(int page, int size) {
        assertCode(PatternReadException.Code.INVALID_PAGE, () -> service.list("R", page, size));
        verifyNoInteractions(reader);
    }

    @Test void invalidRunIsNotTrimmedIntoAnotherIdentity() {
        assertCode(PatternReadException.Code.INVALID_RUN_ID, () -> service.list(" ", 0, 20));
        verifyNoInteractions(reader);
    }

    @Test void largeOffsetIsLongAndOutOfRangeKeepsCounts() {
        when(reader.read("R", 214748364700L, 100)).thenReturn(new Snapshot("SUCCEEDED",
                BigInteger.ONE, BigInteger.ONE, List.of()));
        var r = service.list("R", Integer.MAX_VALUE, 100);
        assertEquals("1", r.filteredTotal()); assertTrue(r.items().isEmpty());
    }

    @Test void preservesPageAndExactDecimalIdsAndCounts() {
        var item = new PatternListResponse.Summary("9007199254740993", "A", "<(1)>",
                "9007199254740994", .2, .3, .4, "P", 1, 1);
        var total = new BigInteger("9007199254740995");
        when(reader.read("A", 2, 2)).thenReturn(new Snapshot("SUCCEEDED", total,total,List.of(item)));
        when(reader.read("B", 2, 2)).thenReturn(new Snapshot("SUCCEEDED",BigInteger.ZERO,
                BigInteger.ZERO,List.of()));
        var a = service.list("A", 1, 2);
        assertEquals(total.toString(), a.runPatternTotal());
        assertEquals("9007199254740993", a.items().getFirst().patternId());
        assertTrue(service.list("B", 1, 2).items().isEmpty());
        verify(reader).read("A", 2, 2); verify(reader).read("B", 2, 2);
    }

    private void assertCode(PatternReadException.Code code, Runnable action) {
        assertEquals(code, assertThrows(PatternReadException.class, action::run).getCode());
    }
}
