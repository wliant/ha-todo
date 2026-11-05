package com.hatodo.item;

import com.hatodo.list.ListField;
import com.hatodo.list.ListFieldChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * What one item answered for one of its list's cost fields.
 *
 * <p>Either a chosen label or a measured amount - the database enforces exactly
 * one, because a row carrying both would describe two different answers.
 */
@Entity
@Table(name = "item_cost_value")
@IdClass(ItemCostValueId.class)
public class ItemCostValue {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private TodoItem item;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_field_id", nullable = false)
    private ListField field;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "choice_id")
    private ListFieldChoice choice;

    @Column(precision = 12, scale = 4)
    private BigDecimal amount;

    protected ItemCostValue() {
        // for JPA
    }

    /** An unanswered row; one of {@link #choose} or {@link #measure} follows. */
    public static ItemCostValue on(TodoItem item, ListField field) {
        ItemCostValue value = new ItemCostValue();
        value.item = item;
        value.field = field;
        return value;
    }

    public static ItemCostValue chosen(TodoItem item, ListField field, ListFieldChoice choice) {
        ItemCostValue value = on(item, field);
        value.choose(choice);
        return value;
    }

    public static ItemCostValue measured(TodoItem item, ListField field, BigDecimal amount) {
        ItemCostValue value = on(item, field);
        value.measure(amount);
        return value;
    }

    /**
     * The two answers are mutually exclusive, and the database says so - so
     * each one clears the other rather than only writing its own column. A
     * cost switched from one mode to the other would otherwise leave a row
     * claiming both.
     */
    public void choose(ListFieldChoice choice) {
        this.choice = choice;
        this.amount = null;
    }

    public void measure(BigDecimal amount) {
        this.amount = amount;
        this.choice = null;
    }

    public ListField getField() {
        return field;
    }

    public ListFieldChoice getChoice() {
        return choice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * What completing this item earns from this cost, using the configuration
     * as it stands right now. The caller snapshots the total; see adr/0012.
     */
    public int points() {
        if (choice != null) {
            return choice.getPoints();
        }
        if (amount == null || field.getPointsPerUnit() == null) {
            return 0;
        }
        return field.getPointsPerUnit().multiply(amount)
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact();
    }
}
