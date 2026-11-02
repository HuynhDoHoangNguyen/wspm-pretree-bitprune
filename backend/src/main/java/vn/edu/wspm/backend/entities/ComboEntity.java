package vn.edu.wspm.backend.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "COMBO")
public class ComboEntity {

    @Id
    @Column(name = "combo_id", length = 40)
    private String comboId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private RunEntity run;

    @Column(name = "combo_name", length = 255)
    private String comboName;

    @Column(name = "combo_type", length = 32)
    private String comboType;

    @Column(name = "combo_score")
    private Double comboScore;

    @Column(name = "combo_price", precision = 18, scale = 2)
    private BigDecimal comboPrice;

    @Column(name = "support")
    private Long support;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "combo_status", length = 16)
    private String comboStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected ComboEntity() {
    }

    public ComboEntity(String comboId, RunEntity run, String comboName, String comboType,
                       Double comboScore, BigDecimal comboPrice, Long support, Double weight,
                       String comboStatus, LocalDateTime createdAt) {
        this.comboId = comboId;
        this.run = run;
        this.comboName = comboName;
        this.comboType = comboType;
        this.comboScore = comboScore;
        this.comboPrice = comboPrice;
        this.support = support;
        this.weight = weight;
        this.comboStatus = comboStatus;
        this.createdAt = createdAt;
    }

    public String getComboId() {
        return comboId;
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

    public String getComboName() {
        return comboName;
    }

    public String getComboType() {
        return comboType;
    }

    public Double getComboScore() {
        return comboScore;
    }

    public void setComboScore(Double comboScore) {
        this.comboScore = comboScore;
    }

    public BigDecimal getComboPrice() {
        return comboPrice;
    }

    public void setComboPrice(BigDecimal comboPrice) {
        this.comboPrice = comboPrice;
    }

    public Long getSupport() {
        return support;
    }

    public Double getWeight() {
        return weight;
    }

    public String getComboStatus() {
        return comboStatus;
    }

    public void setComboStatus(String comboStatus) {
        this.comboStatus = comboStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
