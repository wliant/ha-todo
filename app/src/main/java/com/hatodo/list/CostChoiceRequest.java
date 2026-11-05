package com.hatodo.list;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param id the existing choice, or null for a new one. Sent back so the
 *           service can match rather than recreate - item cost values will
 *           reference these by id, and recreating would orphan them.
 */
public record CostChoiceRequest(
        UUID id,
        @NotBlank @Size(max = 30) String label,
        /** Boxed for the same reason as ListFieldRequest.required. */
        @PositiveOrZero Integer points) {

    /** A choice with no points is a cost the list simply records. */
    public int pointsOrZero() {
        return points == null ? 0 : points;
    }
}
