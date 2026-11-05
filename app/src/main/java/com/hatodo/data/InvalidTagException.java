package com.hatodo.data;

/** Carries a sentence the member can act on; "invalid" is not an answer. */
public class InvalidTagException extends RuntimeException {

    public InvalidTagException(String message) {
        super(message);
    }
}
