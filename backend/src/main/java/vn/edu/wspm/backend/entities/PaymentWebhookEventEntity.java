package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "PAYMENT_WEBHOOK_EVENT")
public class PaymentWebhookEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_event_id", nullable = false)
    private Long id;

    @Size(max = 40)
    @NotNull
    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Size(max = 160)
    @NotNull
    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_attempt_id")
    private PaymentAttemptEntity paymentAttempt;

    @Size(max = 80)
    @NotNull
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Size(max = 128)
    @NotNull
    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @NotNull
    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Size(max = 24)
    @NotNull
    @ColumnDefault("'RECEIVED'")
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Size(max = 1000)
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public PaymentAttemptEntity getPaymentAttempt() {
        return paymentAttempt;
    }

    public void setPaymentAttempt(PaymentAttemptEntity paymentAttempt) {
        this.paymentAttempt = paymentAttempt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public Boolean getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Boolean signatureValid) {
        this.signatureValid = signatureValid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

}
