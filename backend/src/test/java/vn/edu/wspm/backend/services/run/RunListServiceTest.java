package vn.edu.wspm.backend.services.run;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.wspm.backend.dto.run.RunListResponse.Summary;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.RunListReader;
import static org.junit.jupiter.api.Assertions.*;

class RunListServiceTest {
    @Test void defaultsEmptyAndOutOfRangeRetainExactTotals() {
        var service = new RunListService((offset, size) -> new RunListReader.Snapshot(offset == 0 ? "0" : "9007199254740993", List.of()));
        var empty = service.list(null, null);
        assertEquals(0, empty.page());assertEquals(20, empty.size());assertEquals("0", empty.total());assertTrue(empty.items().isEmpty());
        assertEquals("9007199254740993", service.list(99, 20).total());
    }
    @Test void invalidBoundsDoNotReachQuery() {
        var service = new RunListService((offset, size) -> { fail("query must not execute");return null; });
        for (int[] values : new int[][]{{-1,20},{0,0},{0,101}})
            assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(ApplicationException.class, () -> service.list(values[0],values[1])).errorCode());
    }
    @Test void largestPageUsesLongOffset() {
        var received = new AtomicLong();
        var service = new RunListService((offset, size) -> { received.set(offset);return new RunListReader.Snapshot("0", List.of()); });
        service.list(Integer.MAX_VALUE,100);assertEquals(214748364700L, received.get());
    }
    @Test void persistedStatesAndMissingSnapshotCountAreNotFabricated() {
        var rows = List.of("QUEUED","RUNNING","SUCCEEDED","FAILED","LEGACY_UNVERIFIED").stream()
                .map(status -> new Summary("id-"+status,"snapshot","WSPM_PRETREE",status,null,null,List.of())).toList();
        var result = new RunListService((offset, size) -> new RunListReader.Snapshot("5",rows)).list(0,20);
        assertEquals(rows,result.items());assertNull(result.items().getFirst().sequenceCount());
    }
    @Test void unknownStatusIsPreservedWithIssue() {
        var row = new Summary("id","snapshot","WSPM_BITPRUNE","UNRECOGNIZED",null,"18446744073709551615",List.of());
        var result = new RunListService((offset,size) -> new RunListReader.Snapshot("1",List.of(row))).list(0,20).items().getFirst();
        assertEquals("UNRECOGNIZED",result.status());assertEquals(List.of("UNKNOWN_RUN_STATUS"),result.issues());
        assertEquals("18446744073709551615",result.sequenceCount());
    }
    @Test void databaseFailureIsSanitizedNotEmpty() {
        var failure = assertThrows(ApplicationException.class, () -> new RunListService((offset,size) -> {
            throw new DataAccessResourceFailureException("private datasource diagnostics");
        }).list(0,20));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE,failure.errorCode());assertFalse(failure.getMessage().contains("private"));
    }
}
