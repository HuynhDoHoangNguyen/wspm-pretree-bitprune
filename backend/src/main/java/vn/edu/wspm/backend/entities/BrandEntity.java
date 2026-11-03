package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "BRAND")
public class BrandEntity {
    @Id
    @Size(max = 40)
    @Column(name = "brand_id", nullable = false, length = 40)
    private String brandId;

    @Size(max = 160)
    @NotNull
    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Size(max = 180)
    @NotNull
    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Size(max = 24)
    @NotNull
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    public String getBrandId() {
        return brandId;
    }

    public void setBrandId(String brandId) {
        this.brandId = brandId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

}
