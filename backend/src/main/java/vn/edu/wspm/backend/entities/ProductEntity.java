
package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "PRODUCT")
public class ProductEntity {

    @Id
    @Column(name = "product_id", length = 40)
    private String productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "category_id", length = 40)
    private String categoryId;

    @Column(name = "product_status", length = 24)
    private String productStatus;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;

    protected ProductEntity() {
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getProductStatus() {
        return productStatus;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
