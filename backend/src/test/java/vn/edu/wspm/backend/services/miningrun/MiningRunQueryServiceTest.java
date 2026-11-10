package vn.edu.wspm.backend.services.miningrun;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import vn.edu.wspm.backend.exception.ApplicationException;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunPageResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;
import vn.edu.wspm.backend.mappers.MiningRunResponseMapper;
import vn.edu.wspm.backend.repositories.dataset.DatasetSnapshotRepository;
import vn.edu.wspm.backend.repositories.RunRepository;
import vn.edu.wspm.backend.services.miningrun.MiningRunQueryService.InvalidMiningRunFilterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MiningRunQueryServiceTest {

    @Mock
    private RunRepository runRepository;

    @Mock
    private DatasetSnapshotRepository datasetSnapshotRepository;

    @Spy
    private MiningRunResponseMapper mapper = new MiningRunResponseMapper();

    @InjectMocks
    private MiningRunQueryService service;

    @Test
    @DisplayName("findAll returns paginated items with default page and size bounds")
    void shouldFindAllWithPagination() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 10, 0, 0);
        RunEntity run = new RunEntity(
                "run-001",
                "WSPM_BITPRUNE",
                "SIGN",
                0.01,
                15.0,
                1000L,
                0.25,
                now,
                "snap-001"
        );
        Page<RunEntity> page = new PageImpl<>(List.of(run));

        given(runRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

        MiningRunPageResponse response = service.findAll(0, 20, null, null);

        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("run-001", response.items().get(0).runId());
        assertEquals("WSPM_BITPRUNE", response.items().get(0).algorithm());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
        assertEquals(List.of(Sort.Order.desc("runTime"), Sort.Order.desc("runId")),
                captor.getValue().getSort().toList());
    }

    @Test
    @DisplayName("findAll clamps size up to max 100")
    void shouldClampPageSize() {
        Page<RunEntity> emptyPage = new PageImpl<>(List.of());
        given(runRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(emptyPage);

        service.findAll(0, 500, "WSPM_PRETREE", "SUCCEEDED");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("findById returns full detail including snapshot metadata")
    void shouldFindByIdWithSnapshot() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 10, 0, 0);
        RunEntity run = new RunEntity(
                "run-002",
                "WSPM_PRETREE",
                "PUMSB",
                0.05,
                20.0,
                500L,
                0.15,
                now,
                "snap-002"
        );

        DatasetSnapshotEntity snapshot = testSnapshot(
                run.getSnapshotId(),
                49046L,
                2113L,
                "VALID"
        );

        given(runRepository.findById("run-002")).willReturn(Optional.of(run));
        given(datasetSnapshotRepository.findById(run.getSnapshotId())).willReturn(Optional.of(snapshot));

        MiningRunDetailResponse detail = service.findById("run-002");

        assertNotNull(detail);
        assertEquals("run-002", detail.runId());
        assertEquals("WSPM_PRETREE", detail.algorithm());
        assertEquals(49046L, detail.sequenceCount());
        assertEquals(2113L, detail.distinctItemCount());
        assertEquals("VALID", detail.validationStatus());
    }

    @Test
    @DisplayName("findById returns detail with null snapshot fields if snapshot is absent")
    void shouldFindByIdWhenSnapshotAbsent() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 3, 10, 0, 0);
        RunEntity run = new RunEntity(
                "run-003",
                "WSPM_BITPRUNE",
                "BENCHMARK",
                0.02,
                10.0,
                300L,
                0.5,
                now,
                "snap-missing"
        );

        given(runRepository.findById("run-003")).willReturn(Optional.of(run));
        given(datasetSnapshotRepository.findById("run-003")).willReturn(Optional.empty());

        MiningRunDetailResponse detail = service.findById("run-003");

        assertNotNull(detail);
        assertEquals("run-003", detail.runId());
        assertNull(detail.sequenceCount());
        assertNull(detail.distinctItemCount());
        assertNull(detail.validationStatus());
    }

    @Test
    @DisplayName("findById throws ApplicationException with 404 when run does not exist")
    void shouldThrow404WhenRunNotFound() {
        given(runRepository.findById("run-404")).willReturn(Optional.empty());

        ApplicationException ex = assertThrows(
                ApplicationException.class,
                () -> service.findById("run-404")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.status());
        verifyNoInteractions(datasetSnapshotRepository);
    }

    @Test
    void shouldClampNegativePageAndNonPositiveSizes() {
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        for (int size : new int[] {0, -5}) {
            MiningRunPageResponse response = service.findAll(-3, size, null, null);
            assertEquals(0, response.page());
            assertEquals(1, response.size());
        }
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository, org.mockito.Mockito.times(2))
                .findAll(any(Specification.class), captor.capture());
        captor.getAllValues().forEach(pageable -> {
            assertEquals(0, pageable.getPageNumber());
            assertEquals(1, pageable.getPageSize());
        });
    }

    @Test
    void shouldReturnEmptyPageWithRequestedMetadata() {
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(2, 20)));

        MiningRunPageResponse response = service.findAll(2, 20, null, null);

        assertTrue(response.items().isEmpty());
        assertEquals(2, response.page());
        assertEquals(20, response.size());
        assertEquals(0L, response.totalElements());
        assertEquals(0, response.totalPages());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCombineTrimmedAlgorithmAndStatusPredicatesWithAnd() {
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        service.findAll(0, 20, " WSPM_PRETREE ", " SUCCEEDED ");
        ArgumentCaptor<Specification<RunEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(runRepository).findAll(captor.capture(), any(Pageable.class));

        Root<RunEntity> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Path<Object> algorithm = mock(Path.class);
        Path<Object> status = mock(Path.class);
        Predicate base = mock(Predicate.class);
        Predicate algorithmPredicate = mock(Predicate.class);
        Predicate statusPredicate = mock(Predicate.class);
        Predicate firstAnd = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        given(root.get("algorithm")).willReturn(algorithm);
        given(root.get("status")).willReturn(status);
        given(builder.conjunction()).willReturn(base);
        given(builder.equal(algorithm, "WSPM_PRETREE")).willReturn(algorithmPredicate);
        given(builder.equal(status, "SUCCEEDED")).willReturn(statusPredicate);
        given(builder.and(base, algorithmPredicate)).willReturn(firstAnd);
        given(builder.and(firstAnd, statusPredicate)).willReturn(combined);

        assertEquals(combined, captor.getValue().toPredicate(root, null, builder));
        verify(builder).and(firstAnd, statusPredicate);
        // Criteria collaborators are mocked: this verifies composition, not SQL execution.
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIgnoreBlankFilters() {
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(Page.empty(PageRequest.of(0, 20)));
        service.findAll(0, 20, "  ", "\t");
        ArgumentCaptor<Specification<RunEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(runRepository).findAll(captor.capture(), any(Pageable.class));
        Root<RunEntity> root = mock(Root.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        given(builder.conjunction()).willReturn(predicate);
        assertEquals(predicate, captor.getValue().toPredicate(root, null, builder));
        verifyNoInteractions(root);
    }

    @Test
    void shouldPreserveNullMeasurementsInDetail() {
        RunEntity run = new RunEntity("run-null", "WSPM_PRETREE", "BENCHMARK",
                0.1, 10.0, 100L, 0.2, LocalDateTime.of(2026, 11, 3, 8, 0), null);
        given(runRepository.findById("run-null")).willReturn(Optional.of(run));
        given(datasetSnapshotRepository.findById(run.getSnapshotId())).willReturn(Optional.empty());

        MiningRunDetailResponse detail = service.findById("run-null");
        assertNull(detail.patternCount());
        assertNull(detail.candidateCount());
        assertNull(detail.datasetIoMs());
        assertNull(detail.preprocessingMs());
        assertNull(detail.dfsMiningMs());
        assertNull(detail.completeAlgorithmMs());
        assertNull(detail.outputIoMs());
        assertNull(detail.totalExecutionMs());
        assertNull(detail.peakSampledJvmHeapBytes());
    }

    @Test
    void shouldPreserveZeroCountersAndMeasurementsInDetail() {
        RunEntity run = new RunEntity("run-zero", "WSPM_PRETREE", "BENCHMARK",
                0.1, 10.0, 100L, 0.2, LocalDateTime.of(2026, 11, 3, 8, 0), null);
        run.setPatternCount(0L);
        run.setCandidateCount(0L);
        run.setExecutionTime(0.0);
        run.setMemoryUsage(0.0);
        given(runRepository.findById("run-zero")).willReturn(Optional.of(run));
        given(datasetSnapshotRepository.findById(run.getSnapshotId())).willReturn(Optional.empty());

        MiningRunDetailResponse detail = service.findById("run-zero");
        assertEquals(0L, detail.patternCount());
        assertEquals(0L, detail.candidateCount());
        assertEquals(0L, detail.totalExecutionMs());
        assertEquals(0L, detail.peakSampledJvmHeapBytes());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAcceptEachAllowedAlgorithmAndStatusAfterTrimming() {
        String[] algorithms = {"WSPM_PRETREE", "WSPM_BITPRUNE"};
        String[] statuses = {"QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "LEGACY_UNVERIFIED"};
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        for (String algorithm : algorithms) {
            for (String status : statuses) {
                service.findAll(0, 20, " " + algorithm + "\t", "\t" + status + " ");
            }
        }
        ArgumentCaptor<Specification<RunEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(runRepository, org.mockito.Mockito.times(10)).findAll(captor.capture(), any(Pageable.class));
        int index = 0;
        for (String algorithm : algorithms) {
            for (String status : statuses) {
                Root<RunEntity> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
                CriteriaBuilder builder = mock(CriteriaBuilder.class);
                captor.getAllValues().get(index++).toPredicate(root, null, builder);
                verify(builder).equal(root.get("algorithm"), algorithm);
                verify(builder).equal(root.get("status"), status);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTreatNullEmptyAndWhitespaceAsNoFilters() {
        given(runRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        for (String value : new String[] {null, "", " \t\r\n "}) {
            service.findAll(0, 20, value, value);
        }
        ArgumentCaptor<Specification<RunEntity>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(runRepository, org.mockito.Mockito.times(3)).findAll(captor.capture(), any(Pageable.class));
        for (Specification<RunEntity> spec : captor.getAllValues()) {
            Root<RunEntity> root = mock(Root.class);
            CriteriaBuilder builder = mock(CriteriaBuilder.class);
            spec.toPredicate(root, null, builder);
            verify(builder).conjunction();
            verifyNoInteractions(root);
        }
    }

    @Test
    void shouldRejectInvalidAndLowercaseAlgorithmsBeforeRepositoryAccess() {
        for (String value : List.of("unknown", "wspm_pretree", "wspm_bitprune", "WSPM_PRETREE-extra")) {
            InvalidMiningRunFilterException failure = assertThrows(InvalidMiningRunFilterException.class,
                    () -> service.findAll(0, 20, value, "SUCCEEDED"));
            assertEquals("algorithm", failure.getField());
            assertEquals(HttpStatus.BAD_REQUEST, failure.status());
            assertEquals("Giá trị bộ lọc không được hỗ trợ.", failure.getMessage());
            assertEquals("VALIDATION_FAILED", failure.code());
            assertEquals(1, failure.details().size());
            assertEquals("algorithm", failure.details().getFirst().field());
            assertEquals("INVALID_FILTER", failure.details().getFirst().code());
        }
        verifyNoInteractions(runRepository, datasetSnapshotRepository);
    }

    @Test
    void shouldRejectInvalidAndLowercaseStatusesBeforeRepositoryAccess() {
        for (String value : List.of("UNKNOWN", "queued", "running", "succeeded", "failed", "legacy_unverified")) {
            InvalidMiningRunFilterException failure = assertThrows(InvalidMiningRunFilterException.class,
                    () -> service.findAll(0, 20, "WSPM_PRETREE", value));
            assertEquals("status", failure.getField());
            assertEquals(HttpStatus.BAD_REQUEST, failure.status());
        }
        verifyNoInteractions(runRepository, datasetSnapshotRepository);
    }

    @Test
    void shouldValidateAlgorithmBeforeStatusWhenBothAreInvalid() {
        InvalidMiningRunFilterException failure = assertThrows(InvalidMiningRunFilterException.class,
                () -> service.findAll(0, 20, "BAD_ALGORITHM", "BAD_STATUS"));
        assertEquals("algorithm", failure.getField());
        verifyNoInteractions(runRepository, datasetSnapshotRepository);
    }

    @Test
    void shouldReturnRecognizableNotFoundWithoutEmbeddingRunIdInReason() {
        given(runRepository.findById("missing-run")).willReturn(Optional.empty());
        var failure = assertThrows(MiningRunQueryService.MiningRunNotFoundException.class,
                () -> service.findById("missing-run"));
        assertEquals(HttpStatus.NOT_FOUND, failure.status());
        assertEquals("Không tìm thấy RUN.", failure.getMessage());
        assertEquals("RESOURCE_NOT_FOUND", failure.code());
        assertTrue(failure.details().isEmpty());
        verifyNoInteractions(datasetSnapshotRepository);
    }

    // A keeps the snapshot entity API unchanged; only unit fixtures need these fields.
    private static DatasetSnapshotEntity testSnapshot(String id, Long sequences, Long items, String status) {
        DatasetSnapshotEntity snapshot = new DatasetSnapshotEntity();
        ReflectionTestUtils.setField(snapshot, "snapshotId", id);
        ReflectionTestUtils.setField(snapshot, "sequenceCount", sequences);
        ReflectionTestUtils.setField(snapshot, "distinctItemCount", items);
        ReflectionTestUtils.setField(snapshot, "validationStatus", status);
        return snapshot;
    }
}
