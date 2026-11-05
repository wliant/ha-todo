package com.hatodo.list;

/** A grant was asked for someone who does not exist, or is disabled. */
public class UnknownMemberException extends RuntimeException {

    public UnknownMemberException() {
        super("One of those people no longer exists");
    }
}
