package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import vn.edu.wspm.backend.entities.CustomerEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "CHECKOUT_SESSION")
public class CheckoutSessionEntity {
    @Id
    @Size(max = 40)
    @Column(name = "checkout_id", nullable = false, length = 40)
    private String checkoutId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @Size(max = 128)
    @NotNull
    @Column(name = "idempotency_scope", nullable = false, length = 128)
    private String idempotencyScope;

    @Size(max = 128)
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotNull
    @Column(name = "cart_version_snapshot", nullable = false)
    private Long cartVersionSnapshot;

    @Size(max = 32)
    @NotNull
    @ColumnDefault("'CREATED'")
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Size(max = 3)
    @NotNull
    @ColumnDefault("'VND'")
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "list_subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal listSubtotal;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "combo_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal comboDiscountTotal;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "other_discount_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal otherDiscountTotal;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "shipping_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingFee;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "tax_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxTotal;

    @NotNull
    @ColumnDefault("0.00")
    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "quote_expires_at")
    private Instant quoteExpiresAt;

    @NotNull
    @ColumnDefault("0")
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Size(max = 80)
    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public String getCheckoutId() {
        return checkoutId;
    }

    public void setCheckoutId(String checkoutId) {
        this.checkoutId = checkoutId;
    }

    public CartEntity getCart() {
        return cart;
    }

    public void setCart(CartEntity cart) {
        this.cart = cart;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerEntity customer) {
        this.customer = customer;
    }

    public String getIdempotencyScope() {
        return idempotencyScope;
    }

    public void setIdempotencyScope(String idempotencyScope) {
        this.idempotencyScope = idempotencyScope;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getCartVersionSnapshot() {
        return cartVersionSnapshot;
    }

    public void setCartVersionSnapshot(Long cartVersionSnapshot) {
        this.cartVersionSnapshot = cartVersionSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public BigDecimal getComboDiscountTotal() {
        return comboDiscountTotal;
    }

    public void setComboDiscountTotal(BigDecimal comboDiscountTotal) {
        this.comboDiscountTotal = comboDiscountTotal;
    }

    public BigDecimal getOtherDiscountTotal() {
        return otherDiscountTotal;
    }

    public void setOtherDiscountTotal(BigDecimal otherDiscountTotal) {
        this.otherDiscountTotal = otherDiscountTotal;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getTaxTotal() {
        return taxTotal;
    }

    public void setTaxTotal(BigDecimal taxTotal) {
        this.taxTotal = taxTotal;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(BigDecimal grandTotal) {
        this.grandTotal = grandTotal;
    }

    public Instant getQuoteExpiresAt() {
        return quoteExpiresAt;
    }

    public void setQuoteExpiresAt(Instant quoteExpiresAt) {
        this.quoteExpiresAt = quoteExpiresAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

}
