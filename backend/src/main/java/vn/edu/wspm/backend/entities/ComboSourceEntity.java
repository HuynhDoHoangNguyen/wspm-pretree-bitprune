package vn.edu.wspm.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "COMBO_SOURCE")
@IdClass(ComboSourceId.class)
public class ComboSourceEntity {

    @Id
    @Column(name = "combo_id", length = 40)
    private String comboId;

    @Id
    @Column(name = "pattern_id")
    private Long patternId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id", insertable = false, updatable = false)
    private ComboEntity combo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pattern_id", insertable = false, updatable = false)
    private PatternEntity pattern;

    protected ComboSourceEntity() {
    }

    public ComboSourceEntity(String comboId, Long patternId) {
        this.comboId = comboId;
        this.patternId = patternId;
    }

    public String getComboId() {
        return comboId;
    }

    public Long getPatternId() {
        return patternId;
    }

    public ComboEntity getCombo() {
        return combo;
    }

    public void setCombo(ComboEntity combo) {
        this.combo = combo;
        this.comboId = combo != null ? combo.getComboId() : null;
    }

    public PatternEntity getPattern() {
        return pattern;
    }

    public void setPattern(PatternEntity pattern) {
        this.pattern = pattern;
        this.patternId = pattern != null ? pattern.getPatternId() : null;
    }
}
