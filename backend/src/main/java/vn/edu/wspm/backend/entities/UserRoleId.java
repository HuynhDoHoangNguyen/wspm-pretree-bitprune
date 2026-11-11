package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserRoleId implements Serializable {
    @Serial
    private static final long serialVersionUID = -5192008499740314626L;
    @Size(max = 40)
    @NotNull
    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Size(max = 40)
    @NotNull
    @Column(name = "role_id", nullable = false, length = 40)
    private String roleId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId entity = (UserRoleId) o;
        return Objects.equals(this.userId, entity.userId) &&
                Objects.equals(this.roleId, entity.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
