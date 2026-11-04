package com.hatodo.list;

/**
 * The closed set of fields a list may declare (R21).
 *
 * <p>Closed, and only this codebase can add to it - which is why the
 * configuration is typed rows rather than a document, and why an unknown kind
 * is a 400 from Jackson rather than something a service has to check.
 */
public enum FieldKind {
    /** Always present and always required. Every list has exactly one. */
    DESCRIPTION,
    DETAIL,
    ASSIGNEE,
    DUE_AT,
    /** The one kind a list may declare more than once, told apart by name. */
    COST;

    public boolean isSingleton() {
        return this != COST;
    }
}
