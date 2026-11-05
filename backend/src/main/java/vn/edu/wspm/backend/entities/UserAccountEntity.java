package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.validator.constraints.CodePointLength;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "USER_ACCOUNT")
public class UserAccountEntity {
    @Id
    @Size(max = 40)
    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Size(max = 80)
    @Column(name = "username", length = 80)
    private String username;

    @Size(max = 254)
    @Column(name = "email", length = 254)
    private String email;

    @Size(max = 32)
    @Column(name = "phone", length = 32)
    private String phone;

    @Size(max = 255)
    @NotNull
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @CodePointLength(max = 160)
    @NotNull
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Size(max = 24)
    @NotNull
    @ColumnDefault("'PENDING'")
    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @NotNull
    @ColumnDefault("0")
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("CURRENT_TIMESTAMP(6)")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
