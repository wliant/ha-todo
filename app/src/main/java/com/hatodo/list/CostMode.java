package com.hatodo.list;

/** How a cost field is answered (R22). */
public enum CostMode {
    /** A set of labelled choices, each worth its own points. */
    FIXED,
    /** A number, in a unit the creator names, optionally earning a rate. */
    NUMERIC
}
