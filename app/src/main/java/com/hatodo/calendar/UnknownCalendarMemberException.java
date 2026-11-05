package com.hatodo.calendar;

public class UnknownCalendarMemberException extends RuntimeException {

    public UnknownCalendarMemberException() {
        super("One of those members no longer exists");
    }
}
