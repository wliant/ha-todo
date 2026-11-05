package com.hatodo.recurrence;

import java.util.UUID;

/** Also used for a rule on a list the caller cannot see, which answers alike (R19). */
public class RecurrenceRuleNotFoundException extends RuntimeException {

    public RecurrenceRuleNotFoundException(UUID ruleId) {
        super("No recurrence rule " + ruleId);
    }
}
