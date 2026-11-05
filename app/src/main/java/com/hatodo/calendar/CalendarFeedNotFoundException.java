package com.hatodo.calendar;

import java.util.UUID;

/**
 * No such feed, or not the caller's.
 *
 * <p>One exception for both, answered with one body: a feed the caller does not
 * own must not be distinguishable from one that never existed.
 */
public class CalendarFeedNotFoundException extends RuntimeException {

    public CalendarFeedNotFoundException(UUID feedId) {
        super("No calendar feed " + feedId + " for this caller");
    }
}
