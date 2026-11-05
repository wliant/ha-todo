package com.hatodo.list;

/**
 * The caller can see the list but does not own it (R20).
 *
 * <p>A 403 rather than a 404 here: they can already see the list, so pretending
 * it does not exist would be a lie their UI has to work around.
 */
public class ListNotManagedException extends RuntimeException {

    public ListNotManagedException() {
        super("Only the list's owner may change it");
    }
}
