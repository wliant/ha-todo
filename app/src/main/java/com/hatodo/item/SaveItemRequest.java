package com.hatodo.item;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creating or editing an item.
 *
 * @param version the version the caller's view was drawn from; null when
 *                creating, because there is nothing to be stale about yet
 */
public record SaveItemRequest(
        Long version,
        @NotBlank @Size(max = 300) String description,
        String detailHtml,
        UUID assigneeId,
        Instant dueAt,
        @Valid List<ItemCostValueRequest> costValues) {

    public List<ItemCostValueRequest> costValuesOrEmpty() {
        return costValues == null ? List.of() : costValues;
    }
}
