package com.hatodo.recurrence;

/** A schedule that does not describe anything - no weekday, no day of month. */
public class InvalidRecurrenceException extends RuntimeException {

    public InvalidRecurrenceException(String message) {
        super(message);
    }
}
