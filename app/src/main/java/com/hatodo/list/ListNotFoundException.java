package com.hatodo.list;

import java.util.UUID;

/**
 * Also thrown when the caller simply has no grant on the list.
 *
 * <p>That is deliberate and is the reason this carries no "forbidden" sibling
 * for reads: R19 says an ungranted list must not be discoverable, and a 403
 * would confirm the id is real. See adr/0010.
 */
public class ListNotFoundException extends RuntimeException {

    public ListNotFoundException(UUID id) {
        super("No list " + id + " visible to this caller");
    }
}
