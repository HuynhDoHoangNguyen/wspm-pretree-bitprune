package vn.edu.wspm.backend.entities;

import java.io.Serializable;
import java.util.Objects;

public class PatternItemId implements Serializable {

    private Long patternId;
    private Integer itemsetOrder;
    private Integer itemOrder;

    public PatternItemId() {
    }

    public PatternItemId(Long patternId, Integer itemsetOrder, Integer itemOrder) {
        this.patternId = patternId;
        this.itemsetOrder = itemsetOrder;
        this.itemOrder = itemOrder;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternItemId that = (PatternItemId) o;
        return Objects.equals(patternId, that.patternId) &&
                Objects.equals(itemsetOrder, that.itemsetOrder) &&
                Objects.equals(itemOrder, that.itemOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patternId, itemsetOrder, itemOrder);
    }
}

