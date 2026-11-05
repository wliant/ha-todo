package com.hatodo.calendar;

public class CalendarFeedLabelTakenException extends RuntimeException {

    public CalendarFeedLabelTakenException(String label) {
        super("Calendar feed label already used: " + label);
    }
}
