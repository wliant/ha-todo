package com.hatodo.data;

/**
 * Per owner, and in a namespace of its own: a member may keep both a
 * "Restaurants" collection and a "Restaurants" todo list, because they are
 * different things.
 */
public class DataListNameTakenException extends RuntimeException {

    public DataListNameTakenException(String name) {
        super("Collection name already used: " + name);
    }
}
