package com.hatodo.item;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link ItemCostValue}. */
public class ItemCostValueId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID item;
    private UUID field;

    protected ItemCostValueId() {
        // for JPA
    }

    public ItemCostValueId(UUID item, UUID field) {
        this.item = item;
        this.field = field;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ItemCostValueId id
                && Objects.equals(item, id.item)
                && Objects.equals(field, id.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, field);
    }
}
