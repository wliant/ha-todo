package com.hatodo.list;

import java.util.UUID;

/**
 * Somebody's grant on a list was taken away.
 *
 * <p>Published rather than called directly because the thing that has to react
 * - unassigning their items - lives in the item package, which already depends
 * on this one. An event keeps that arrow pointing one way.
 *
 * <p>Handled synchronously, inside the transaction that revoked the grant, so
 * the two cannot end up disagreeing.
 */
public record ListAccessRevokedEvent(UUID listId, UUID userId) {
}
