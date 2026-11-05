package com.hatodo.list;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ListFieldResponse(UUID id, FieldKind kind, String label, boolean required,
                                CostMode costMode, String costUnitLabel,
                                BigDecimal pointsPerUnit, List<CostChoiceResponse> choices) {

    public static ListFieldResponse of(ListField field) {
        return new ListFieldResponse(field.getId(), field.getKind(), field.getLabel(),
                field.isRequired(), field.getCostMode(), field.getCostUnitLabel(),
                field.getPointsPerUnit(),
                field.getChoices().stream().map(CostChoiceResponse::of).toList());
    }
}
