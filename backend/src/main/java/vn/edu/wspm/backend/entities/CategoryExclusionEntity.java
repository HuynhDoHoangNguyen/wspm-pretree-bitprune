package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import vn.edu.wspm.backend.entities.CategoryEntity;

import java.time.Instant;

@Entity
@Table(name = "CATEGORY_EXCLUSION")
public class CategoryExclusionEntity {
    @EmbeddedId
    private CategoryExclusionId id;

    @MapsId("categoryIdLow")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id_low", nullable = false)
    private CategoryEntity categoryIdLow;

    @MapsId("categoryIdHigh")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id_high", nullable = false)
    private CategoryEntity categoryIdHigh;

    @Size(max = 500)
    @NotNull
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @NotNull
    @ColumnDefault("1")
    @Column(name = "active", nullable = false)
    private Boolean active;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public CategoryExclusionId getId() {
        return id;
    }

    public void setId(CategoryExclusionId id) {
        this.id = id;
    }

    public CategoryEntity getCategoryIdLow() {
        return categoryIdLow;
    }

    public void setCategoryIdLow(CategoryEntity categoryIdLow) {
        this.categoryIdLow = categoryIdLow;
    }

    public CategoryEntity getCategoryIdHigh() {
        return categoryIdHigh;
    }

    public void setCategoryIdHigh(CategoryEntity categoryIdHigh) {
        this.categoryIdHigh = categoryIdHigh;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
