package vn.edu.wspm.backend.entities;

import java.io.Serializable;
import java.util.Objects;

public class ComboItemId implements Serializable {

    private String comboId;
    private Integer itemsetOrder;
    private Integer itemOrder;

    public ComboItemId() {
    }

    public ComboItemId(String comboId, Integer itemsetOrder, Integer itemOrder) {
        this.comboId = comboId;
        this.itemsetOrder = itemsetOrder;
        this.itemOrder = itemOrder;
    }

    public String getComboId() {
        return comboId;
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
        ComboItemId that = (ComboItemId) o;
        return Objects.equals(comboId, that.comboId) &&
                Objects.equals(itemsetOrder, that.itemsetOrder) &&
                Objects.equals(itemOrder, that.itemOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comboId, itemsetOrder, itemOrder);
    }
}

