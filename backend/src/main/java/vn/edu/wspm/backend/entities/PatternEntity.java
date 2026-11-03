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
}
