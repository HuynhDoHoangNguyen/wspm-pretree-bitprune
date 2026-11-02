package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CATEGORY")
public class CategoryEntity {

    @Id
    @Column(name = "category_id", length = 40)
    private String categoryId;

    @Column(name = "category_name", length = 160)
    private String categoryName;

    protected CategoryEntity() {
    }

    public CategoryEntity(String categoryId, String categoryName) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }
}

