package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class InventoryBalanceId implements Serializable {
    @Serial
    private static final long serialVersionUID = 5852502952167059210L;
    @Size(max = 40)
    @NotNull
    @Column(name = "warehouse_id", nullable = false, length = 40)
    private String warehouseId;

    @Size(max = 40)
    @NotNull
    @Column(name = "product_id", nullable = false, length = 40)
    private String productId;

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryBalanceId entity = (InventoryBalanceId) o;
        return Objects.equals(this.warehouseId, entity.warehouseId) &&
                Objects.equals(this.productId, entity.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, productId);
    }
}
