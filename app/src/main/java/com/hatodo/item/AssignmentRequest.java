package com.hatodo.item;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Assigning several items at once (R46).
 *
 * @param assigneeId who the work goes to, or null to unassign. Generalised
 *                   beyond "me" because it is the same code path, and the
 *                   caller is always recorded as whoever chose - which is what
 *                   makes assigning to yourself read as a claim (adr/0026).
 * @param items      each with the version its view was drawn from, because a
 *                   bulk write is still N contended writes
 */
public record AssignmentRequest(UUID assigneeId,
                                @Valid @NotNull @NotEmpty List<ItemVersion> items) {

    public record ItemVersion(@NotNull UUID id, @NotNull Long version) {}
}
