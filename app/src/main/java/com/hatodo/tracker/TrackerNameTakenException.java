package com.hatodo.tracker;

/**
 * Checked in the service as well as indexed in the database, so the member
 * reads a sentence rather than a constraint violation.
 */
public class TrackerNameTakenException extends RuntimeException {

    public TrackerNameTakenException(String name) {
        super("You already have a tracker called " + name);
    }
}
