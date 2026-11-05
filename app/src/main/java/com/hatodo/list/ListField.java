package com.hatodo.list;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One field a list declares for its items (R21).
 *
 * <p>Existence is what "enabled" means; there is no boolean that could
 * disagree with the row being there, for the same reason {@code password_hash}
 * has no companion flag.
 */
@Entity
@Table(name = "list_field")
public class ListField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FieldKind kind;

    /** Required for COST, null for every other kind - the database checks it. */
    @Column
    private String label;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_mode")
    private CostMode costMode;

    @Column(name = "cost_unit_label")
    private String costUnitLabel;

    /**
     * Points earned per unit of a NUMERIC cost. Null means this cost earns
     * nothing, which is how a list says "how long did it take" without also
     * turning itself into a game.
     */
    @Column(name = "points_per_unit", precision = 12, scale = 4)
    private BigDecimal pointsPerUnit;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<ListFieldChoice> choices = new ArrayList<>();

    protected ListField() {
        // for JPA
    }

    public ListField(TodoList list, FieldKind kind, String label, boolean required, short position) {
        this.list = list;
        this.kind = kind;
        this.label = label;
        this.required = required;
        this.position = position;
    }

    /** The field every list has, whether or not anyone asked for it. */
    public static ListField description(TodoList list, short position) {
        return new ListField(list, FieldKind.DESCRIPTION, null, true, position);
    }

    /**
     * Also on every list (R40), but optional to fill in: the owner may make it
     * required, and cannot remove it. See adr/0022.
     */
    public static ListField dueAt(TodoList list, short position) {
        return new ListField(list, FieldKind.DUE_AT, null, false, position);
    }

    public UUID getId() {
        return id;
    }

    public TodoList getList() {
        return list;
    }

    public FieldKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public short getPosition() {
        return position;
    }

    public void setPosition(short position) {
        this.position = position;
    }

    public CostMode getCostMode() {
        return costMode;
    }

    public String getCostUnitLabel() {
        return costUnitLabel;
    }

    public BigDecimal getPointsPerUnit() {
        return pointsPerUnit;
    }

    public List<ListFieldChoice> getChoices() {
        return choices;
    }

    /** Replaces the cost configuration wholesale, keeping the two modes apart. */
    public void configureFixedCost() {
        this.costMode = CostMode.FIXED;
        this.costUnitLabel = null;
        this.pointsPerUnit = null;
    }

    public void configureNumericCost(String unitLabel, BigDecimal pointsPerUnit) {
        this.costMode = CostMode.NUMERIC;
        this.costUnitLabel = unitLabel;
        this.pointsPerUnit = pointsPerUnit;
        // A numeric cost has no choices; leaving them behind would make the
        // row describe two different questions at once.
        this.choices.clear();
    }

    public boolean isCost() {
        return kind == FieldKind.COST;
    }
}
