package com.hatodo.data;

/**
 * Two members edited one entry (requirements/0006 R60). Thrown before anything
 * is mutated, so the 409 is deterministic rather than dependent on flush
 * timing, and the handler answers with the entry as it actually is.
 */
public class StaleDataEntryException extends RuntimeException {

    private final transient java.util.UUID entryId;

    public StaleDataEntryException(java.util.UUID entryId) {
        this.entryId = entryId;
    }

    public java.util.UUID getEntryId() {
        return entryId;
    }
}
