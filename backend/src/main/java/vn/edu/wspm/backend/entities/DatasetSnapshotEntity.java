package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "DATASET_SNAPSHOT")
public class DatasetSnapshotEntity {
    @Id
    @Size(max = 40)
    @Column(name = "snapshot_id", nullable = false, length = 40)
    private String snapshotId;

    @Size(max = 32)
    @NotNull
    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Size(max = 255)
    @NotNull
    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "from_time")
    private Instant fromTime;

    @Column(name = "to_time")
    private Instant toTime;

    @Size(max = 1024)
    @Column(name = "raw_file_path", length = 1024)
    private String rawFilePath;

    @Size(max = 64)
    @Column(name = "raw_sha256", length = 64)
    private String rawSha256;

    @Size(max = 1024)
    @Column(name = "sequence_file_path", length = 1024)
    private String sequenceFilePath;

    @Size(max = 64)
    @Column(name = "sequence_sha256", length = 64)
    private String sequenceSha256;

    @Size(max = 1024)
    @Column(name = "mapping_file_path", length = 1024)
    private String mappingFilePath;

    @Size(max = 64)
    @Column(name = "mapping_sha256", length = 64)
    private String mappingSha256;

    @Size(max = 32)
    @NotNull
    @Column(name = "validation_status", nullable = false, length = 32)
    private String validationStatus;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conversion_report_json", nullable = false)
    private Map<String, Object> conversionReportJson;

    @NotNull
    @Column(name = "sequence_count", nullable = false)
    private Long sequenceCount;

    @Column(name = "single_itemset_sequence_count")
    private Long singleItemsetSequenceCount;

    @Column(name = "multi_itemset_sequence_count")
    private Long multiItemsetSequenceCount;

    @Column(name = "itemset_count")
    private Long itemsetCount;

    @Column(name = "distinct_item_count")
    private Long distinctItemCount;

    @Column(name = "total_item_occurrences")
    private Long totalItemOccurrences;

    @Column(name = "min_sequence_length", columnDefinition = "int UNSIGNED")
    private Integer minSequenceLength;

    @Column(name = "max_sequence_length", columnDefinition = "int UNSIGNED")
    private Integer maxSequenceLength;

    @Column(name = "average_sequence_length")
    private Double averageSequenceLength;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(String snapshotId) {
        this.snapshotId = snapshotId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public Instant getFromTime() {
        return fromTime;
    }

    public void setFromTime(Instant fromTime) {
        this.fromTime = fromTime;
    }

    public Instant getToTime() {
        return toTime;
    }

    public void setToTime(Instant toTime) {
        this.toTime = toTime;
    }

    public String getRawFilePath() {
        return rawFilePath;
    }

    public void setRawFilePath(String rawFilePath) {
        this.rawFilePath = rawFilePath;
    }

    public String getRawSha256() {
        return rawSha256;
    }

    public void setRawSha256(String rawSha256) {
        this.rawSha256 = rawSha256;
    }

    public String getSequenceFilePath() {
        return sequenceFilePath;
    }

    public void setSequenceFilePath(String sequenceFilePath) {
        this.sequenceFilePath = sequenceFilePath;
    }

    public String getSequenceSha256() {
        return sequenceSha256;
    }

    public void setSequenceSha256(String sequenceSha256) {
        this.sequenceSha256 = sequenceSha256;
    }

    public String getMappingFilePath() {
        return mappingFilePath;
    }

    public void setMappingFilePath(String mappingFilePath) {
        this.mappingFilePath = mappingFilePath;
    }

    public String getMappingSha256() {
        return mappingSha256;
    }

    public void setMappingSha256(String mappingSha256) {
        this.mappingSha256 = mappingSha256;
    }

    public String getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }

    public Map<String, Object> getConversionReportJson() {
        return conversionReportJson;
    }

    public void setConversionReportJson(Map<String, Object> conversionReportJson) {
        this.conversionReportJson = conversionReportJson;
    }

    public Long getSequenceCount() {
        return sequenceCount;
    }

    public void setSequenceCount(Long sequenceCount) {
        this.sequenceCount = sequenceCount;
    }

    public Long getSingleItemsetSequenceCount() {
        return singleItemsetSequenceCount;
    }

    public void setSingleItemsetSequenceCount(Long singleItemsetSequenceCount) {
        this.singleItemsetSequenceCount = singleItemsetSequenceCount;
    }

    public Long getMultiItemsetSequenceCount() {
        return multiItemsetSequenceCount;
    }

    public void setMultiItemsetSequenceCount(Long multiItemsetSequenceCount) {
        this.multiItemsetSequenceCount = multiItemsetSequenceCount;
    }

    public Long getItemsetCount() {
        return itemsetCount;
    }

    public void setItemsetCount(Long itemsetCount) {
        this.itemsetCount = itemsetCount;
    }

    public Long getDistinctItemCount() {
        return distinctItemCount;
    }

    public void setDistinctItemCount(Long distinctItemCount) {
        this.distinctItemCount = distinctItemCount;
    }

    public Long getTotalItemOccurrences() {
        return totalItemOccurrences;
    }

    public void setTotalItemOccurrences(Long totalItemOccurrences) {
        this.totalItemOccurrences = totalItemOccurrences;
    }

    public Integer getMinSequenceLength() {
        return minSequenceLength;
    }

    public void setMinSequenceLength(Integer minSequenceLength) {
        this.minSequenceLength = minSequenceLength;
    }

    public Integer getMaxSequenceLength() {
        return maxSequenceLength;
    }

    public void setMaxSequenceLength(Integer maxSequenceLength) {
        this.maxSequenceLength = maxSequenceLength;
    }

    public Double getAverageSequenceLength() {
        return averageSequenceLength;
    }

    public void setAverageSequenceLength(Double averageSequenceLength) {
        this.averageSequenceLength = averageSequenceLength;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
