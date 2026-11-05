package com.hatodo.data;

import java.util.UUID;

/**
 * Also what a caller gets for an entry on a collection they hold no grant on:
 * the collection's existence is the thing being hidden, so an entry on it
 * cannot be discoverable either.
 */
public class DataEntryNotFoundException extends RuntimeException {

    public DataEntryNotFoundException(UUID id) {
        super("No readable entry " + id);
    }
}
