package com.hatodo.calendar;

/**
 * The preview makes this server fetch an address of the caller's choosing, so
 * it is rate-limited per caller as well as guarded. The guard is what makes it
 * safe; this is what stops it being an amplifier.
 */
public class PreviewTooOftenException extends RuntimeException {

    public PreviewTooOftenException() {
        super("Previewing too often");
    }
}
