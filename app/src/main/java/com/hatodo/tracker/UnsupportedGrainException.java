package com.hatodo.tracker;

/**
 * A series asked for at a grain finer than the tracker records at.
 *
 * <p>400 rather than a quiet fallback: folding can combine days into months and
 * cannot invent the days a monthly tracker never recorded, so a client asking
 * for one has a bug the answer should name.
 */
public class UnsupportedGrainException extends RuntimeException {

    public UnsupportedGrainException(String message) {
        super(message);
    }
}
