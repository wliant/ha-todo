package com.hatodo.list;

/**
 * The caller's view of the list was out of date.
 *
 * <p>Thrown from an explicit version check before anything is mutated, rather
 * than waiting for the flush to fail, so the 409 is deterministic instead of
 * depending on when Hibernate happens to write.
 */
public class StaleListException extends RuntimeException {

    public StaleListException() {
        super("This list changed while it was being edited");
    }
}
