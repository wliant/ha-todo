package com.hatodo.calendar;

import com.hatodo.household.HouseholdProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * How much of the calendar is kept.
 *
 * <p>One place, because the poller and the parser have to agree: a window the
 * poller fills more narrowly than a view can ask for is a month grid that
 * quietly loses its first row, and nothing fails.
 *
 * <p>The back figure is not arbitrary. My day's month grid starts at the Monday
 * of the week containing the 1st, so on the 31st of a month that began on a
 * Sunday it reaches <b>36 days back</b>; the default of 45 is that with
 * headroom.
 */
@Component
public class CalendarWindow {

    private final CalendarProperties properties;
    private final HouseholdProperties household;
    private final Clock clock;

    public CalendarWindow(CalendarProperties properties, HouseholdProperties household,
                          Clock clock) {
        this.properties = properties;
        this.household = household;
        this.clock = clock;
    }

    /** Today, in the household's zone - never the server's. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(household.zone()));
    }

    public LocalDate from() {
        return today().minusDays(properties.daysBack());
    }

    public LocalDate to() {
        return today().plusDays(properties.daysForward());
    }
}
