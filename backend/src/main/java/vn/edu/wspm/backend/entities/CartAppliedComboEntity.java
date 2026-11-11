package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import vn.edu.wspm.backend.entities.ComboEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "CART_APPLIED_COMBO")
public class CartAppliedComboEntity {
    @Id
    @Size(max = 40)
    @Column(name = "cart_applied_combo_id", nullable = false, length = 40)
    private String cartAppliedComboId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private ComboEntity combo;

    @Size(max = 24)
    @NotNull
    @ColumnDefault("'SELECTED'")
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "estimated_list_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedListSubtotal;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "estimated_discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedDiscountAmount;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "estimated_final_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedFinalSubtotal;

    @NotNull
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Size(max = 500)
    @Column(name = "invalid_reason", length = 500)
    private String invalidReason;

    public String getCartAppliedComboId() {
        return cartAppliedComboId;
    }

    public void setCartAppliedComboId(String cartAppliedComboId) {
        this.cartAppliedComboId = cartAppliedComboId;
    }

    public CartEntity getCart() {
        return cart;
    }

    public void setCart(CartEntity cart) {
        this.cart = cart;
    }

    public ComboEntity getCombo() {
        return combo;
    }

    public void setCombo(ComboEntity combo) {
        this.combo = combo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getEstimatedListSubtotal() {
        return estimatedListSubtotal;
    }

    public void setEstimatedListSubtotal(BigDecimal estimatedListSubtotal) {
        this.estimatedListSubtotal = estimatedListSubtotal;
    }

    public BigDecimal getEstimatedDiscountAmount() {
        return estimatedDiscountAmount;
    }

    public void setEstimatedDiscountAmount(BigDecimal estimatedDiscountAmount) {
        this.estimatedDiscountAmount = estimatedDiscountAmount;
    }

    public BigDecimal getEstimatedFinalSubtotal() {
        return estimatedFinalSubtotal;
    }

    public void setEstimatedFinalSubtotal(BigDecimal estimatedFinalSubtotal) {
        this.estimatedFinalSubtotal = estimatedFinalSubtotal;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public String getInvalidReason() {
        return invalidReason;
    }

    public void setInvalidReason(String invalidReason) {
        this.invalidReason = invalidReason;
    }

}
