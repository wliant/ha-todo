package com.hatodo.item;

import java.util.UUID;

/** Also what a caller with no grant on the item's list is told. */
public class ItemNotFoundException extends RuntimeException {

    public ItemNotFoundException(UUID id) {
        super("No item " + id + " visible to this caller");
    }
}
