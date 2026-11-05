package com.hatodo.recurrence;

import java.util.UUID;

/** Two owners is impossible; two tabs is not. */
public class StaleRecurrenceRuleException extends RuntimeException {

    private final UUID ruleId;

    public StaleRecurrenceRuleException(UUID ruleId) {
        super("Stale view of recurrence rule " + ruleId);
        this.ruleId = ruleId;
    }

    public UUID getRuleId() {
        return ruleId;
    }
}
