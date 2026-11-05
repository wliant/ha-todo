package com.hatodo.item;

/**
 * A completion request that contradicts itself - today, a note sent while
 * reopening. The CHECK would turn that into a 500, and a silently discarded
 * field hides a client bug for a release (adr/0023).
 */
public class InvalidCompletionException extends RuntimeException {

    public InvalidCompletionException(String message) {
        super(message);
    }
}
