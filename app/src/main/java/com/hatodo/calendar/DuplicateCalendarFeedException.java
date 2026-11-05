package com.hatodo.calendar;

/** The same address, already subscribed by this member. */
public class DuplicateCalendarFeedException extends RuntimeException {

    public DuplicateCalendarFeedException() {
        super("That calendar is already subscribed");
    }
}
