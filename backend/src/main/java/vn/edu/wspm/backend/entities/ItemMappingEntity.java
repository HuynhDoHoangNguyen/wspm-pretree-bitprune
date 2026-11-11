package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ITEM_MAPPING")
public class ItemMappingEntity {

    @Id
    @Column(name = "product_id", length = 40)
    private String productId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private ProductEntity product;

    @Column(name = "item_id")
    private Integer itemId;

    protected ItemMappingEntity() {
    }

    public ItemMappingEntity(String productId, Integer itemId) {
        this.productId = productId;
        this.itemId = itemId;
    }

    public String getProductId() {
        return productId;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
        this.productId = product != null ? product.getProductId() : null;
    }

    public Integer getItemId() {
        return itemId;
    }
}
