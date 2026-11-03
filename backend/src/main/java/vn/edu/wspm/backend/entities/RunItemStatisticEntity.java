package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.edu.wspm.backend.entities.ProductEntity;
import vn.edu.wspm.backend.entities.RunEntity;

@Entity
@Table(name = "RUN_ITEM_STATISTIC")
public class RunItemStatisticEntity {
    @EmbeddedId
    private RunItemStatisticId id;

    @MapsId("runId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private RunEntity run;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductEntity product;

    @NotNull
    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount;

    @NotNull
    @Column(name = "sequence_support", nullable = false)
    private Long sequenceSupport;

    @Column(name = "item_weight")
    private Double itemWeight;

    @Size(max = 200)
    @NotNull
    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Size(max = 80)
    @NotNull
    @Column(name = "sku_snapshot", nullable = false, length = 80)
    private String skuSnapshot;

    public RunItemStatisticId getId() {
        return id;
    }

    public void setId(RunItemStatisticId id) {
        this.id = id;
    }

    public RunEntity getRun() {
        return run;
    }

    public void setRun(RunEntity run) {
        this.run = run;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
    }

    public Long getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(Long occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }

    public Long getSequenceSupport() {
        return sequenceSupport;
    }

    public void setSequenceSupport(Long sequenceSupport) {
        this.sequenceSupport = sequenceSupport;
    }

    public Double getItemWeight() {
        return itemWeight;
    }

    public void setItemWeight(Double itemWeight) {
        this.itemWeight = itemWeight;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public void setProductNameSnapshot(String productNameSnapshot) {
        this.productNameSnapshot = productNameSnapshot;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public void setSkuSnapshot(String skuSnapshot) {
        this.skuSnapshot = skuSnapshot;
    }

}
