package com.hatodo.list;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One declared field. Omitting a field that currently exists deletes it; that
 * is what makes this a replace rather than a patch.
 *
 * @param id existing field or null for a new one - see CostChoiceRequest
 * @param label required for COST, ignored otherwise
 */
public record ListFieldRequest(
        UUID id,
        @NotNull FieldKind kind,
        @Size(max = 30) String label,
        // Boxed: a record's missing primitive is a deserialization failure,
        // and "not mentioned" is genuinely different from "false" in a request
        // that replaces a configuration.
        Boolean required,
        CostMode costMode,
        @Size(max = 20) String costUnitLabel,
        @PositiveOrZero BigDecimal pointsPerUnit,
        @Valid List<CostChoiceRequest> choices) {

    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }

    public List<CostChoiceRequest> choicesOrEmpty() {
        return choices == null ? List.of() : choices;
    }
}
