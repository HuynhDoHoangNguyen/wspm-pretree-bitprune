package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "PRODUCT")
public class ProductEntity {

    @Id
    @Column(name = "product_id", length = 40)
    private String productId;

    @Column(name = "name", length = 200)
    private String productName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity category;

    @Column(name = "status", length = 24)
    private String productStatus;

    @Column(name = "current_price", precision = 19, scale = 2)
    private BigDecimal price;

    protected ProductEntity() {}

    public ProductEntity(
            String productId,
            String productName,
            CategoryEntity category,
            String productStatus,
            BigDecimal price) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.productStatus = productStatus;
        this.price = price;
    }

    public String getCategoryId() {
        return category != null ? category.getCategoryId() : null;
    }
}
