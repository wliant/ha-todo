package com.hatodo.recurrence;

import com.hatodo.list.ListField;
import com.hatodo.list.ListFieldChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A rule's answer to one of its list's cost fields.
 *
 * <p>Without these, a list with a required cost field could not carry a
 * recurring item at all: generation would fail the same required-field check an
 * item does. Identity is {@code (rule, field)}, exactly as an item's cost value
 * is keyed on {@code (item, field)} - so an edit matches each answer to the row
 * already holding it rather than building a second object for one identifier.
 */
@Entity
@Table(name = "recurrence_cost_value")
public class RecurrenceCostValue {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "rule_id")
        private UUID ruleId;

        @Column(name = "list_field_id")
        private UUID listFieldId;

        protected Key() {
        }

        Key(UUID ruleId, UUID listFieldId) {
            this.ruleId = ruleId;
            this.listFieldId = listFieldId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof Key key
                    && Objects.equals(ruleId, key.ruleId)
                    && Objects.equals(listFieldId, key.listFieldId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ruleId, listFieldId);
        }
    }

    @EmbeddedId
    private Key key = new Key();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("ruleId")
    @JoinColumn(name = "rule_id", nullable = false)
    private RecurrenceRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("listFieldId")
    @JoinColumn(name = "list_field_id", nullable = false)
    private ListField field;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "choice_id")
    private ListFieldChoice choice;

    @Column
    private BigDecimal amount;

    protected RecurrenceCostValue() {
    }

    static RecurrenceCostValue on(RecurrenceRule rule, ListField field) {
        RecurrenceCostValue value = new RecurrenceCostValue();
        value.rule = rule;
        value.field = field;
        value.key = new Key(rule.getId(), field.getId());
        return value;
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

    void choose(ListFieldChoice choice) {
        this.choice = choice;
        this.amount = null;
    }

    void measure(BigDecimal amount) {
        this.amount = amount;
        this.choice = null;
    }
}
