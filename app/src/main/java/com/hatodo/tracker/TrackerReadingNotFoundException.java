package com.hatodo.tracker;

import java.util.UUID;

/** Also what a caller with no grant on the tracker gets, for adr/0010's reason. */
public class TrackerReadingNotFoundException extends RuntimeException {

    public TrackerReadingNotFoundException(UUID id) {
        super("No readable reading " + id);
    }
}
