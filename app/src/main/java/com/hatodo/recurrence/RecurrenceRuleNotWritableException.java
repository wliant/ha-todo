package com.hatodo.recurrence;

import java.util.UUID;

/**
 * Visible but not the caller's to write: somebody else wrote it, and the caller
 * does not own the list (0016 R143).
 *
 * <p>Distinct from {@link RecurrenceRuleNotFoundException}, which is what a
 * rule on a list the caller holds no grant on answers - the existence of that
 * list is itself what R19 hides.
 */
public class RecurrenceRuleNotWritableException extends RuntimeException {

    public RecurrenceRuleNotWritableException(UUID ruleId) {
        super("Recurrence rule " + ruleId + " is not the caller's to write");
    }
}
