package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "COMBO_ITEM")
@IdClass(ComboItemId.class)
public class ComboItemEntity {

    @Id
    @Column(name = "combo_id", length = 40)
    private String comboId;

    @Id
    @Column(name = "itemset_order")
    private Integer itemsetOrder;

    @Id
    @Column(name = "item_order")
    private Integer itemOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", insertable = false, updatable = false)
    private ComboEntity combo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    protected ComboItemEntity() {
    }

    public ComboItemEntity(String comboId, Integer itemsetOrder, Integer itemOrder, ProductEntity product) {
        this.comboId = comboId;
        this.itemsetOrder = itemsetOrder;
        this.itemOrder = itemOrder;
        this.product = product;
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

    public ComboEntity getCombo() {
        return combo;
    }

    public void setCombo(ComboEntity combo) {
        this.combo = combo;
        this.comboId = combo != null ? combo.getComboId() : null;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
    }

    public String getProductId() {
        return product != null ? product.getProductId() : null;
    }
}
