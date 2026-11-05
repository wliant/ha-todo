package com.hatodo.list;

/** Per owner, not globally - two members may each keep a "Groceries". */
public class ListNameTakenException extends RuntimeException {

    public ListNameTakenException(String name) {
        super("This owner already has a list named " + name);
    }
}
