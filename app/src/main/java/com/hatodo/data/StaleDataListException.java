package com.hatodo.data;

/**
 * The caller's view of a collection was drawn from an older version. Thrown
 * before anything is mutated, so the 409 is deterministic rather than
 * dependent on when Hibernate flushes.
 */
public class StaleDataListException extends RuntimeException {
}
