package vn.edu.wspm.backend.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ORDERS")
public class OrderEntity {

    @Id
    @Column(name = "order_id", length = 40)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @Column(name = "ordered_at")
    private LocalDateTime orderTime;

    @Column(name = "status", length = 32)
    private String orderStatus;

    protected OrderEntity() {
    }

    public OrderEntity(String orderId, CustomerEntity customer, LocalDateTime orderTime, String orderStatus) {
        this.orderId = orderId;
        this.customer = customer;
        this.orderTime = orderTime;
        this.orderStatus = orderStatus;
    }

    public String getOrderId() {
        return orderId;
    }

    public CustomerEntity getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerEntity customer) {
        this.customer = customer;
    }

    public String getCustomerId() {
        return customer != null ? customer.getCustomerId() : null;
    }

    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    public String getOrderStatus() {
        return orderStatus;
    }
}
