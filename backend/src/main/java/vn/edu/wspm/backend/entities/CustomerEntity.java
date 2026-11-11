package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "CUSTOMER")
public class CustomerEntity {

    @Id
    @Column(name = "customer_id", length = 40)
    private String customerId;

    @Column(name = "user_id", length = 40)
    private String userId;

    @Column(name = "anonymous_code", nullable = false, length = 80)
    private String anonymousCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerEntity() {}

    public CustomerEntity(String customerId) {
        this.customerId = customerId;
    }

    public CustomerEntity(
            String customerId, String userId, String anonymousCode, Instant createdAt) {
        this.customerId = customerId;
        this.userId = userId;
        this.anonymousCode = anonymousCode;
        this.createdAt = createdAt;
    }
}
