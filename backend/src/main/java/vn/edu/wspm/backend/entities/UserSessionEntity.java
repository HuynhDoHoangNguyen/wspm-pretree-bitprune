package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "USER_SESSION")
public class UserSessionEntity {
    @Id
    @Size(max = 40)
    @Column(name = "session_id", nullable = false, length = 40)
    private String sessionId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

    @Size(max = 255)
    @NotNull
    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    @Size(max = 500)
    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Size(max = 128)
    @Column(name = "ip_hash", length = 128)
    private String ipHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;
}
