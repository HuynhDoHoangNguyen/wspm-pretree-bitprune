package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.wspm.backend.entities.ComboEntity;
import vn.edu.wspm.backend.entities.OrderEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ORDER_APPLIED_COMBO")
public class OrderAppliedComboEntity {
    @Id
    @Size(max = 40)
    @Column(name = "order_applied_combo_id", nullable = false, length = 40)
    private String orderAppliedComboId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private ComboEntity combo;

    @Size(max = 255)
    @NotNull
    @Column(name = "combo_name_snapshot", nullable = false)
    private String comboNameSnapshot;

    @Size(max = 24)
    @NotNull
    @Column(name = "discount_type_snapshot", nullable = false, length = 24)
    private String discountTypeSnapshot;

    @Column(name = "discount_value_snapshot", precision = 19, scale = 4)
    private BigDecimal discountValueSnapshot;

    @Size(max = 3)
    @NotNull
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @NotNull
    @Column(name = "list_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal listSubtotal;

    @NotNull
    @Column(name = "discount_base_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountBaseSubtotal;

    @NotNull
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @NotNull
    @Column(name = "final_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalSubtotal;

    @NotNull
    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    public String getOrderAppliedComboId() {
        return orderAppliedComboId;
    }

    public void setOrderAppliedComboId(String orderAppliedComboId) {
        this.orderAppliedComboId = orderAppliedComboId;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public void setOrder(OrderEntity order) {
        this.order = order;
    }

    public ComboEntity getCombo() {
        return combo;
    }

    public void setCombo(ComboEntity combo) {
        this.combo = combo;
    }

    public String getComboNameSnapshot() {
        return comboNameSnapshot;
    }

    public void setComboNameSnapshot(String comboNameSnapshot) {
        this.comboNameSnapshot = comboNameSnapshot;
    }

    public String getDiscountTypeSnapshot() {
        return discountTypeSnapshot;
    }

    public void setDiscountTypeSnapshot(String discountTypeSnapshot) {
        this.discountTypeSnapshot = discountTypeSnapshot;
    }

    public BigDecimal getDiscountValueSnapshot() {
        return discountValueSnapshot;
    }

    public void setDiscountValueSnapshot(BigDecimal discountValueSnapshot) {
        this.discountValueSnapshot = discountValueSnapshot;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getListSubtotal() {
        return listSubtotal;
    }

    public void setListSubtotal(BigDecimal listSubtotal) {
        this.listSubtotal = listSubtotal;
    }

    public BigDecimal getDiscountBaseSubtotal() {
        return discountBaseSubtotal;
    }

    public void setDiscountBaseSubtotal(BigDecimal discountBaseSubtotal) {
        this.discountBaseSubtotal = discountBaseSubtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalSubtotal() {
        return finalSubtotal;
    }

    public void setFinalSubtotal(BigDecimal finalSubtotal) {
        this.finalSubtotal = finalSubtotal;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

}
