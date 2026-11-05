package com.hatodo.data;

import java.util.UUID;

/**
 * Also what a caller with no grant gets. R50 reuses R19, which makes the
 * <em>existence</em> of a collection the thing being hidden - so this and "it
 * was deleted" must be indistinguishable from outside. See adr/0010.
 */
public class DataListNotFoundException extends RuntimeException {

    public DataListNotFoundException(UUID id) {
        super("No readable collection " + id);
    }
}
