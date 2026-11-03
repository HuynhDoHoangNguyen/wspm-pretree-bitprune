package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "COMBO_BATCH")
public class ComboBatchEntity {

    @Id
    @Column(name = "batch_id", length = 40)
    private String batchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private RunEntity run;

    @Column(name = "status", length = 24)
    private String status;

    protected ComboBatchEntity() {
    }

    public String getBatchId() { return batchId; }
    public RunEntity getRun() { return run; }
    public String getRunId() { return run != null ? run.getRunId() : null; }
    public String getStatus() { return status; }
}
