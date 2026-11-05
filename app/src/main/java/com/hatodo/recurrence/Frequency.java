package com.hatodo.recurrence;

/**
 * The closed set of schedules a household actually says out loud (R41).
 *
 * <p>Bins are weekly on a Tuesday, hoovering is every other week, rent is the
 * 1st, vitamins are daily. Anything beyond this - "the last Friday", "every
 * weekday", occurrence counts, exceptions - is deliberately absent: see
 * adr/0025 for why an RFC 5545 rule string lost.
 */
public enum Frequency {
    DAILY,
    /** Needs a set of weekdays, and only this kind may carry one. */
    WEEKLY,
    /** Needs a day of the month, and only this kind may carry one. */
    MONTHLY
}
