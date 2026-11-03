package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RunItemStatisticId implements Serializable {
    @Serial
    private static final long serialVersionUID = -8701067610827267237L;
    @Size(max = 40)
    @NotNull
    @Column(name = "run_id", nullable = false, length = 40)
    private String runId;

    @Column(name = "item_id", columnDefinition = "int UNSIGNED not null")
    private Integer itemId;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RunItemStatisticId entity = (RunItemStatisticId) o;
        return Objects.equals(this.runId, entity.runId) &&
                Objects.equals(this.itemId, entity.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, itemId);
    }
}
