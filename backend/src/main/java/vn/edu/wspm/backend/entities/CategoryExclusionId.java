package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class CategoryExclusionId implements Serializable {
    @Serial
    private static final long serialVersionUID = -1772057361056755856L;
    @Size(max = 40)
    @NotNull
    @Column(name = "category_id_low", nullable = false, length = 40)
    private String categoryIdLow;

    @Size(max = 40)
    @NotNull
    @Column(name = "category_id_high", nullable = false, length = 40)
    private String categoryIdHigh;

    public String getCategoryIdLow() {
        return categoryIdLow;
    }

    public void setCategoryIdLow(String categoryIdLow) {
        this.categoryIdLow = categoryIdLow;
    }

    public String getCategoryIdHigh() {
        return categoryIdHigh;
    }

    public void setCategoryIdHigh(String categoryIdHigh) {
        this.categoryIdHigh = categoryIdHigh;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryExclusionId entity = (CategoryExclusionId) o;
        return Objects.equals(this.categoryIdLow, entity.categoryIdLow) &&
                Objects.equals(this.categoryIdHigh, entity.categoryIdHigh);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryIdLow, categoryIdHigh);
    }
}
