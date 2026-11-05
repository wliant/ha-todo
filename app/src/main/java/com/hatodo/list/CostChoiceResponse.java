package com.hatodo.list;

import java.util.UUID;

public record CostChoiceResponse(UUID id, String label, int points) {

    public static CostChoiceResponse of(ListFieldChoice choice) {
        return new CostChoiceResponse(choice.getId(), choice.getLabel(), choice.getPoints());
    }
}
