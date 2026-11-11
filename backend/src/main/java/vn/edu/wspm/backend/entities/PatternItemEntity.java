package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "PATTERN_ITEM")
@IdClass(PatternItemId.class)
public class PatternItemEntity {

    @Id
    @Column(name = "pattern_id")
    private Long patternId;

    @Id
    @Column(name = "itemset_order")
    private Integer itemsetOrder;

    @Id
    @Column(name = "item_order")
    private Integer itemOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", insertable = false, updatable = false)
    private PatternEntity pattern;

    @Column(name = "item_id")
    private Integer itemId;

    protected PatternItemEntity() {
    }

    public PatternItemEntity(Long patternId, Integer itemsetOrder, Integer itemOrder, Integer itemId) {
        this.patternId = patternId;
        this.itemsetOrder = itemsetOrder;
        this.itemOrder = itemOrder;
        this.itemId = itemId;
    }

    public Long getPatternId() {
        return patternId;
    }

    public Integer getItemsetOrder() {
        return itemsetOrder;
    }

    public Integer getItemOrder() {
        return itemOrder;
    }

    public PatternEntity getPattern() {
        return pattern;
    }

    public void setPattern(PatternEntity pattern) {
        this.pattern = pattern;
        this.patternId = pattern != null ? pattern.getPatternId() : null;
    }

    public Integer getItemId() {
        return itemId;
    }
}
