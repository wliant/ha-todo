package com.hatodo.tracker;

import java.util.UUID;

/**
 * Also what a caller with no grant gets. R164 makes the <em>existence</em> of a
 * tracker the thing being hidden - so this and "it was deleted" must be
 * indistinguishable from outside. See adr/0010.
 */
public class TrackerNotFoundException extends RuntimeException {

    public TrackerNotFoundException(UUID id) {
        super("No readable tracker " + id);
    }
}
