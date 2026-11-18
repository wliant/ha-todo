package com.hatodo.tracker;

/**
 * The caller's view of a tracker was drawn from an older version. Thrown before
 * anything is mutated, so the 409 is deterministic rather than dependent on
 * when Hibernate flushes.
 */
public class StaleTrackerException extends RuntimeException {
}
