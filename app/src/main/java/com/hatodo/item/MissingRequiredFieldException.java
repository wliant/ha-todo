package com.hatodo.item;

/** A field the list declared required was left empty. */
public class MissingRequiredFieldException extends RuntimeException {

    public MissingRequiredFieldException(String message) {
        super(message);
    }
}
