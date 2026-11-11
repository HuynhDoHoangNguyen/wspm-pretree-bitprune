package vn.edu.wspm.backend.entities;

import java.io.Serializable;
import java.util.Objects;

public class ComboSourceId implements Serializable {

    private String comboId;
    private Long patternId;

    public ComboSourceId() {
    }

    public ComboSourceId(String comboId, Long patternId) {
        this.comboId = comboId;
        this.patternId = patternId;
    }

    public String getComboId() {
        return comboId;
    }

    public Long getPatternId() {
        return patternId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComboSourceId that = (ComboSourceId) o;
        return Objects.equals(comboId, that.comboId) && Objects.equals(patternId, that.patternId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comboId, patternId);
    }
}

