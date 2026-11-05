package com.hatodo.item;

import java.util.UUID;

/**
 * Somebody else changed this item first.
 *
 * <p>Carries the id rather than the item: the advice has to read the current
 * state in a fresh transaction, because the one that threw this is going to be
 * rolled back.
 */
public class StaleItemException extends RuntimeException {

    private final UUID itemId;

    public StaleItemException(UUID itemId) {
        super("Item " + itemId + " changed while it was being edited");
        this.itemId = itemId;
    }

    public UUID getItemId() {
        return itemId;
    }
}
