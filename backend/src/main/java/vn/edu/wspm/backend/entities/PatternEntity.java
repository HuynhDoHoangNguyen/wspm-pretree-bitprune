package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "PATTERN")
public class PatternEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pattern_id")
    private Long patternId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private RunEntity run;

    @Column(name = "canonical_pattern", nullable = false, columnDefinition = "TEXT")
    private String canonicalPattern;

    @Column(name = "canonical_pattern_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String canonicalPatternHash;

    @Column(name = "support_ratio", nullable = false)
    private Double supportRatio;

    @Column(name = "weighted_support", nullable = false)
    private Double weightedSupport;

    @Column(name = "itemset_count", nullable = false)
    private Integer itemsetCount;

    @Column(name = "pattern_weight_policy", nullable = false, length = 80)
    private String patternWeightPolicy;

    @Column(name = "support_abs")
    private Long support;

    @Column(name = "pattern_weight")
    private Double weight;

    @Column(name = "pattern_item_count")
    private Integer patternLength;

    protected PatternEntity() {
    }

    public PatternEntity(RunEntity run, Long support, Double weight, Integer patternLength) {
        this.run = run;
        this.support = support;
        this.weight = weight;
        this.patternLength = patternLength;
    }

    /** Complete storage mapping; production values are validated by the persistence projector. */
    public PatternEntity(RunEntity run, String canonicalPattern, String canonicalPatternHash, Long support,
            Double supportRatio, Double weightedSupport, Integer patternLength, Integer itemsetCount,
            Double weight, String patternWeightPolicy) {
        this(run, support, weight, patternLength);
        this.canonicalPattern = canonicalPattern; this.canonicalPatternHash = canonicalPatternHash;
        this.supportRatio = supportRatio; this.weightedSupport = weightedSupport;
        this.itemsetCount = itemsetCount; this.patternWeightPolicy = patternWeightPolicy;
    }

    public Long getPatternId() {
        return patternId;
    }

    public RunEntity getRun() {
        return run;
    }

    public void setRun(RunEntity run) {
        this.run = run;
    }

    public String getRunId() {
        return run != null ? run.getRunId() : null;
    }

    public Long getSupport() {
        return support;
    }

    public Double getWeight() {
        return weight;
    }

    public Integer getPatternLength() {
        return patternLength;
    }

    public String getCanonicalPattern() { return canonicalPattern; }
    public String getCanonicalPatternHash() { return canonicalPatternHash; }
    public Double getSupportRatio() { return supportRatio; }
    public Double getWeightedSupport() { return weightedSupport; }
    public Integer getItemsetCount() { return itemsetCount; }
    public String getPatternWeightPolicy() { return patternWeightPolicy; }
}
