package vn.edu.wspm.backend.mappers;

import org.springframework.stereotype.Component;

import vn.edu.wspm.backend.dto.miningrun.MiningRunDetailResponse;
import vn.edu.wspm.backend.dto.miningrun.MiningRunSummaryResponse;
import vn.edu.wspm.backend.entities.DatasetSnapshotEntity;
import vn.edu.wspm.backend.entities.RunEntity;

@Component
public class MiningRunResponseMapper {

    public MiningRunSummaryResponse toSummaryResponse(RunEntity entity) {
        if (entity == null) {
            return null;
        }
        return new MiningRunSummaryResponse(
                entity.getRunId(),
                entity.getAlgorithm(),
                entity.getStatus(),
                entity.getDatasetSource(),
                entity.getSnapshotId(),
                entity.getMinsupMode(),
                entity.getMinsupConfigured(),
                entity.getEffectiveThreshold(),
                entity.getPatternCount(),
                entity.getCandidateCount(),
                entity.getTotalExecutionMs(),
                entity.getPeakSampledJvmHeapBytes(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt()
        );
    }

    public MiningRunDetailResponse toDetailResponse(RunEntity entity, DatasetSnapshotEntity snapshot) {
        if (entity == null) {
            return null;
        }
        return new MiningRunDetailResponse(
                entity.getRunId(),
                entity.getAlgorithm(),
                entity.getStatus(),
                entity.getDatasetSource(),
                entity.getSnapshotId(),
                entity.getMinsupMode(),
                entity.getMinsupConfigured(),
                entity.getEffectiveThreshold(),
                entity.getPatternCount(),
                entity.getCandidateCount(),
                entity.getTotalExecutionMs(),
                entity.getPeakSampledJvmHeapBytes(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getMinsupRelativeFraction(),
                entity.getMaxW(),
                entity.getMinW(),
                entity.getMeanW(),
                entity.getDatasetIoMs(),
                entity.getPreprocessingMs(),
                entity.getDfsMiningMs(),
                entity.getCompleteAlgorithmMs(),
                entity.getOutputIoMs(),
                publicErrorMessage(entity.getErrorMessage()),
                statisticsAvailable(snapshot) ? snapshot.getSequenceCount() : null,
                statisticsAvailable(snapshot) ? snapshot.getDistinctItemCount() : null,
                snapshot != null ? snapshot.getValidationStatus() : null
        );
    }

    private boolean statisticsAvailable(DatasetSnapshotEntity snapshot) {
        if (snapshot == null) {
            return false;
        }
        var report = snapshot.getConversionReportJson();
        // Acquisition initializes sequence_count to zero before statistics exist.
        // Preserve legacy snapshots without this marker, including their nulls.
        return report == null || !report.containsKey("statisticsStatus")
                || "COMPUTED".equals(report.get("statisticsStatus"));
    }

    private String publicErrorMessage(String storedMessage) {
        return storedMessage == null ? null
                : "RUN có thông tin lỗi được ghi nhận; chi tiết chẩn đoán không được công bố qua API.";
    }
}
