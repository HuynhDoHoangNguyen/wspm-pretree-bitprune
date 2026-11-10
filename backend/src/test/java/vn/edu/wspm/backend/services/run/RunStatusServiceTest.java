package vn.edu.wspm.backend.services.run;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.wspm.backend.exception.*;
import vn.edu.wspm.backend.mappers.RunStatusResponseMapper;
import vn.edu.wspm.backend.repositories.run.RunStatusRepository;

class RunStatusServiceTest {
    static final String ID = "550e8400-e29b-41d4-a716-446655440000";
    final RunStatusRepository repository = mock(RunStatusRepository.class);
    final RunStatusResponseMapper mapper = new RunStatusResponseMapper();
    final RunStatusService service = new RunStatusService(repository, mapper);
    RunStatusRepository.Snapshot row(String state, String diagnostic, boolean owned, boolean measured) {
        return new RunStatusRepository.Snapshot(ID, ID, "WSPM_BITPRUNE", state, "ABSOLUTE", Double.MIN_VALUE, null,
                Double.MIN_VALUE, null, null, .5, Instant.parse("2026-11-07T05:00:00.123456Z"), null, null,
                measured ? "0" : null, measured ? "9007199254740993" : null, measured ? "1" : null,
                measured ? "2" : null, measured ? "3" : null, measured ? "6" : null, null,
                measured ? "10" : null, measured ? "18446744073709551615" : null, diagnostic, owned);
    }
    @ParameterizedTest @ValueSource(strings = {"QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED"})
    void persistedStatesAndNullableHistoricalFieldsMapWithoutInventingMeasurements(String state) {
        when(repository.read(ID)).thenReturn(row(state, "private /path SQL secret", false, false));
        var result = service.read(ID); assertEquals(state, result.status()); assertNull(result.startedAt()); assertNull(result.finishedAt());
        assertNull(result.patternCount()); assertNull(result.metrics().totalExecutionMs()); assertNull(result.measurement().contractVersion());
        assertEquals(Double.MIN_VALUE, result.configuration().configuredValue()); assertNull(result.configuration().relativeFraction());
        if (state.equals("FAILED")) assertEquals("RUN_FAILED", result.failure().code()); else assertNull(result.failure());
    }
    @ParameterizedTest @ValueSource(strings = {"ADMISSION_FAILED", "CLAIM_NOT_CONFIRMED", "INPUT_REVERIFICATION_FAILED", "ENGINE_EXECUTION_FAILED", "EXECUTION_LEASE_EXPIRED"})
    void onlyAllowlistedDiagnosticsBecomePublicFailures(String code) {
        assertEquals(code, mapper.map(row("FAILED", code, false, false)).failure().code());
    }
    @Test void exactStringPrecisionZeroAndMeasurementScopeArePreserved() throws Exception {
        var result = mapper.map(row("SUCCEEDED", "hidden", true, true));
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsBytes(result));
        assertEquals("0", json.get("patternCount").asString()); assertEquals("9007199254740993", json.get("candidateCount").asString());
        assertEquals("18446744073709551615", json.get("metrics").get("peakSampledJvmHeapBytes").asString());
        assertEquals("WDM-05.6", result.measurement().contractVersion()); assertEquals("MILLISECONDS", result.measurement().timeUnit());
        assertEquals("ALGORITHM_JVM_WIDE_USED_HEAP_SAMPLING", result.measurement().heapScope());
        assertNull(result.failure()); assertFalse(json.toString().contains("hidden"));
        assertNull(mapper.map(row("SUCCEEDED", null, false, true)).measurement().contractVersion());
        assertFalse(json.toString().contains("execution_owner")); assertFalse(json.toString().contains("token"));
    }
    @Test void malformedIdNeverReachesRepository() {
        for (var id : new String[]{"not-uuid", ID.toUpperCase(), "../secret", ""})
            assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(ApplicationException.class, () -> service.read(id)).errorCode());
        assertThrows(ApplicationException.class, () -> service.read(null)); verifyNoInteractions(repository);
    }
    @Test void missingAndUnavailableDatabaseRetainSafeErrorClasses() {
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(ApplicationException.class, () -> service.read(ID)).errorCode());
        when(repository.read(ID)).thenThrow(new DataAccessResourceFailureException("private SQL password"));
        var failure = assertThrows(ApplicationException.class, () -> service.read(ID));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure.errorCode()); assertFalse(failure.getMessage().contains("password"));
    }
    @Test void corruptStateFailsClosedAndUnknownDiagnosticIsSanitized() {
        assertEquals(ErrorCode.INVALID_RESOURCE_STATE, assertThrows(ApplicationException.class,
                () -> mapper.map(row("CORRUPT", null, false, false))).errorCode());
        assertEquals("RUN_FAILED", mapper.map(row("FAILED", null, false, false)).failure().code());
        assertFalse(row("FAILED", "secret", false, false).toString().contains("secret"));
    }
}
