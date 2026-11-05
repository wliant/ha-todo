package com.hatodo.item;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCostValueResponse(UUID fieldId, UUID choiceId, BigDecimal amount) {

    public static ItemCostValueResponse of(ItemCostValue value) {
        return new ItemCostValueResponse(value.getField().getId(),
                value.getChoice() == null ? null : value.getChoice().getId(),
                value.getAmount());
    }
}
