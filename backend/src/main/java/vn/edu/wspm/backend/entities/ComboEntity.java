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
    @JoinColumn(name = "batch_id")
    private ComboBatchEntity batch;

    @Column(name = "name", length = 255)
    private String comboName;

    @Column(name = "combo_kind", length = 40)
    private String comboType;

    @Column(name = "reference_sale_price", precision = 19, scale = 2)
    private BigDecimal comboPrice;

    @Column(name = "support_abs")
    private Long support;

    @Column(name = "pattern_weight")
    private Double weight;

    @Column(name = "status", length = 24)
    private String comboStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected ComboEntity() {
    }

    public ComboEntity(String comboId, ComboBatchEntity batch, String comboName, String comboType,
                       BigDecimal comboPrice, Long support, Double weight,
                       String comboStatus, LocalDateTime createdAt) {
        this.comboId = comboId;
        this.batch = batch;
        this.comboName = comboName;
        this.comboType = comboType;
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
        return batch != null ? batch.getRun() : null;
    }

    public ComboBatchEntity getBatch() {
        return batch;
    }

    public void setBatch(ComboBatchEntity batch) {
        this.batch = batch;
    }

    public String getRunId() {
        return batch != null ? batch.getRunId() : null;
    }

    public String getComboName() {
        return comboName;
    }

    public String getComboType() {
        return comboType;
    }

    public Double getComboScore() {
        return null;
    }

    public void setComboScore(Double ignored) {
        // ComboScore was removed in V2.
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
