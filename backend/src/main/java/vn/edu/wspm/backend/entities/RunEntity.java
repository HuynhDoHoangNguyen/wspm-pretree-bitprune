package vn.edu.wspm.backend.entities;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "MINING_RUN")
public class RunEntity {

    @Id
    @Column(name = "run_id", length = 40)
    private String runId;

    @Column(name = "snapshot_id", length = 40)
    private String snapshotId;

    @Column(name = "algorithm", length = 32)
    private String algorithm;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "dataset_source", length = 255)
    private String datasetSource;

    @Column(name = "minsup_mode", length = 32)
    private String minsupMode;

    @Column(name = "minsup_configured")
    private Double minsupConfigured;

    @Column(name = "minsup_relative_fraction")
    private Double minsupRatio;

    @Column(name = "effective_threshold")
    private Double minsupAbs;

    @Column(name = "mean_w")
    private Double meanW;

    @Column(name = "created_at")
    private Instant runTime;

    @Column(name = "total_execution_ms")
    private Long totalExecutionMs;

    @Column(name = "peak_sampled_jvm_heap_bytes")
    private Long peakSampledJvmHeapBytes;

    @Column(name = "pattern_count")
    private Long patternCount;

    @Column(name = "candidate_count")
    private Long candidateCount;

    @Column(name = "max_w")
    private Double maxW;

    @Column(name = "min_w")
    private Double minW;

    @Column(name = "dataset_io_ms")
    private Long datasetIoMs;

    @Column(name = "preprocessing_ms")
    private Long preprocessingMs;

    @Column(name = "dfs_mining_ms")
    private Long dfsMiningMs;

    @Column(name = "complete_algorithm_ms")
    private Long completeAlgorithmMs;

    @Column(name = "output_io_ms")
    private Long outputIoMs;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "execution_owner_id", length = 40)
    private String executionOwnerId;

    @Column(name = "execution_attempt_id", length = 40)
    private String executionAttemptId;

    @Column(name = "execution_lease_until")
    private Instant executionLeaseUntil;

    protected RunEntity() {
    }

    /** WDM creation path; the legacy constructor below is not used for new runs. */
    public static RunEntity queued(String runId, String snapshotId, String algorithm, String datasetSource,
            String minsupMode, double configured, Double relativeFraction, double threshold,
            double maxW, double minW, double meanW, Instant createdAt) {
        var entity = new RunEntity();
        entity.runId = runId; entity.snapshotId = snapshotId; entity.algorithm = algorithm;
        entity.status = "QUEUED"; entity.datasetSource = datasetSource; entity.minsupMode = minsupMode;
        entity.minsupConfigured = configured; entity.minsupRatio = relativeFraction; entity.minsupAbs = threshold;
        entity.maxW = maxW; entity.minW = minW; entity.meanW = meanW;
        entity.runTime = createdAt;
        return entity;
    }

    public RunEntity(String runId, String algorithm, String datasetSource,
                     Double minsupRatio, Double minsupAbs, Long sequenceCount,
                     Double meanW, LocalDateTime runTime, String inputSnapshot) {
        this.runId = runId;
        this.snapshotId = runId;
        this.algorithm = algorithm;
        this.status = "QUEUED";
        this.datasetSource = datasetSource;
        this.minsupMode = "LEGACY_COMPATIBILITY";
        this.minsupConfigured = minsupRatio;
        this.minsupRatio = minsupRatio;
        this.minsupAbs = minsupAbs;
        this.meanW = meanW;
        this.runTime = runTime != null ? runTime.toInstant(ZoneOffset.UTC) : null;
    }

    public String getRunId() { return runId; }
    public String getSnapshotId() { return snapshotId; }
    public String getAlgorithm() { return algorithm; }
    public String getStatus() { return status; }
    public String getDatasetSource() { return datasetSource; }
    public Double getMinsupRatio() { return minsupRatio; }
    public Double getMinsupAbs() { return minsupAbs; }
    public Double getMeanW() { return meanW; }
    /** Compatibility wall-clock view; all new run timestamps use UTC Instant storage. */
    public LocalDateTime getRunTime() { return runTime != null ? LocalDateTime.ofInstant(runTime, ZoneOffset.UTC) : null; }

    @Transient
    public Long getSequenceCount() { return null; }

    @Transient
    public String getInputSnapshot() { return null; }

    public Double getExecutionTime() {
        return totalExecutionMs != null ? totalExecutionMs.doubleValue() : null;
    }

    public void setExecutionTime(Double value) {
        totalExecutionMs = value != null ? value.longValue() : null;
    }

    public Double getMemoryUsage() {
        return peakSampledJvmHeapBytes != null ? peakSampledJvmHeapBytes.doubleValue() : null;
    }

    public void setMemoryUsage(Double value) {
        peakSampledJvmHeapBytes = value != null ? value.longValue() : null;
    }

    public Long getPatternCount() { return patternCount; }
    public void setPatternCount(Long value) { patternCount = value; }
    public Long getCandidateCount() { return candidateCount; }
    public void setCandidateCount(Long value) { candidateCount = value; }

    @Transient
    public Integer getTopK() { return null; }
    public void setTopK(Integer ignored) { }

    @Transient
    public Boolean getCategoryFilter() { return null; }
    public void setCategoryFilter(Boolean ignored) { }

    @Transient
    public Double getCoefAlpha() { return null; }
    public void setCoefAlpha(Double ignored) { }

    @Transient
    public Double getCoefBeta() { return null; }
    public void setCoefBeta(Double ignored) { }

    @Transient
    public Double getCoefGamma() { return null; }
    public void setCoefGamma(Double ignored) { }

    @Transient
    public Double getCoefDelta() { return null; }
    public void setCoefDelta(Double ignored) { }

    public String getMinsupMode() { return minsupMode; }
    public Double getMinsupConfigured() { return minsupConfigured; }
    public Double getMaxW() { return maxW; }
    public Double getMinW() { return minW; }
    public Long getDatasetIoMs() { return datasetIoMs; }
    public Long getPreprocessingMs() { return preprocessingMs; }
    public Long getDfsMiningMs() { return dfsMiningMs; }
    public Long getCompleteAlgorithmMs() { return completeAlgorithmMs; }
    public Long getOutputIoMs() { return outputIoMs; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Double getEffectiveThreshold() { return minsupAbs; }
    public Double getMinsupRelativeFraction() { return minsupRatio; }
    public Instant getCreatedAt() { return runTime; }
    public Long getTotalExecutionMs() { return totalExecutionMs; }
    public Long getPeakSampledJvmHeapBytes() { return peakSampledJvmHeapBytes; }
    public String getExecutionOwnerId() { return executionOwnerId; }
    public String getExecutionAttemptId() { return executionAttemptId; }
    public Instant getExecutionLeaseUntil() { return executionLeaseUntil; }
}
