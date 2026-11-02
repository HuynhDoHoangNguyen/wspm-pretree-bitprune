package vn.edu.wspm.backend.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "RUN")
public class RunEntity {

    @Id
    @Column(name = "run_id", length = 40)
    private String runId;

    @Column(name = "algorithm", length = 32)
    private String algorithm;

    @Column(name = "dataset_source", length = 255)
    private String datasetSource;

    @Column(name = "minsup_ratio")
    private Double minsupRatio;

    @Column(name = "minsup_abs")
    private Double minsupAbs;

    @Column(name = "sequence_count")
    private Long sequenceCount;

    @Column(name = "mean_w")
    private Double meanW;

    @Column(name = "run_time")
    private LocalDateTime runTime;

    @Column(name = "input_snapshot", length = 1024)
    private String inputSnapshot;

    @Column(name = "execution_time")
    private Double executionTime;

    @Column(name = "memory_usage")
    private Double memoryUsage;

    @Column(name = "pattern_count")
    private Long patternCount;

    @Column(name = "candidate_count")
    private Long candidateCount;

    @Column(name = "top_k")
    private Integer topK;

    @Column(name = "category_filter")
    private Boolean categoryFilter;

    @Column(name = "coef_alpha")
    private Double coefAlpha;

    @Column(name = "coef_beta")
    private Double coefBeta;

    @Column(name = "coef_gamma")
    private Double coefGamma;

    @Column(name = "coef_delta")
    private Double coefDelta;

    protected RunEntity() {
    }

    public RunEntity(String runId, String algorithm, String datasetSource, Double minsupRatio, Double minsupAbs,
                     Long sequenceCount, Double meanW, LocalDateTime runTime, String inputSnapshot) {
        this.runId = runId;
        this.algorithm = algorithm;
        this.datasetSource = datasetSource;
        this.minsupRatio = minsupRatio;
        this.minsupAbs = minsupAbs;
        this.sequenceCount = sequenceCount;
        this.meanW = meanW;
        this.runTime = runTime;
        this.inputSnapshot = inputSnapshot;
        this.categoryFilter = false;
    }

    public String getRunId() {
        return runId;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getDatasetSource() {
        return datasetSource;
    }

    public Double getMinsupRatio() {
        return minsupRatio;
    }

    public Double getMinsupAbs() {
        return minsupAbs;
    }

    public Long getSequenceCount() {
        return sequenceCount;
    }

    public Double getMeanW() {
        return meanW;
    }

    public LocalDateTime getRunTime() {
        return runTime;
    }

    public String getInputSnapshot() {
        return inputSnapshot;
    }

    public Double getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Double executionTime) {
        this.executionTime = executionTime;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Long getPatternCount() {
        return patternCount;
    }

    public void setPatternCount(Long patternCount) {
        this.patternCount = patternCount;
    }

    public Long getCandidateCount() {
        return candidateCount;
    }

    public void setCandidateCount(Long candidateCount) {
        this.candidateCount = candidateCount;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Boolean getCategoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(Boolean categoryFilter) {
        this.categoryFilter = categoryFilter;
    }

    public Double getCoefAlpha() {
        return coefAlpha;
    }

    public void setCoefAlpha(Double coefAlpha) {
        this.coefAlpha = coefAlpha;
    }

    public Double getCoefBeta() {
        return coefBeta;
    }

    public void setCoefBeta(Double coefBeta) {
        this.coefBeta = coefBeta;
    }

    public Double getCoefGamma() {
        return coefGamma;
    }

    public void setCoefGamma(Double coefGamma) {
        this.coefGamma = coefGamma;
    }

    public Double getCoefDelta() {
        return coefDelta;
    }

    public void setCoefDelta(Double coefDelta) {
        this.coefDelta = coefDelta;
    }
}

