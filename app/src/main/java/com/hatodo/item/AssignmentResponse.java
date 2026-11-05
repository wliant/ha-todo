package com.hatodo.item;

import java.util.List;
import java.util.UUID;

/**
 * What a bulk assignment actually managed (R46, adr/0026).
 *
 * <p>Partial success, and always 200. A member who selected eight items and
 * had one touched by somebody else should not have to re-select all eight on a
 * phone: their intent for the other seven is unambiguous. The conflicts carry
 * the server's version of each item, so a second attempt retries exactly the
 * right ones.
 *
 * @param conflicts {@code item} is null when it is no longer there at all -
 *                  deleted, or on a list the caller cannot see, which must
 *                  answer the same way (R19)
 */
public record AssignmentResponse(List<ItemResponse> assigned, List<Conflict> conflicts) {

    public record Conflict(UUID id, String error, ItemResponse item) {}
}
