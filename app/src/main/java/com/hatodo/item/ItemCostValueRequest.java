package com.hatodo.item;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One answer to one of the list's cost fields.
 *
 * @param choiceId for a FIXED cost; null for a NUMERIC one
 * @param amount   for a NUMERIC cost; null for a FIXED one
 */
public record ItemCostValueRequest(
        @NotNull UUID fieldId,
        UUID choiceId,
        @PositiveOrZero BigDecimal amount) {

    public boolean isEmpty() {
        return choiceId == null && amount == null;
    }
}
