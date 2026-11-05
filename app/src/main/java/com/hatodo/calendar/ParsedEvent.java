package com.hatodo.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * One occurrence, as the parser read it out of a feed.
 *
 * <p>The parser's whole output, deliberately separate from the entity: parsing
 * an {@code .ics} file has no business touching a persistence context, and the
 * hard cases - all-day, floating times, recurrence overrides - are only cheap
 * to test because this is a value.
 *
 * <p>Exactly one of {@code startsAt} / {@code startDate} is set, which the
 * {@code ck_calendar_event_shape} constraint also insists on.
 */
public record ParsedEvent(String uid, String summary, String description, String location,
                          boolean allDay, Instant startsAt, Instant endsAt,
                          LocalDate startDate, LocalDate endDate,
                          LocalDate occursOn, short sortMinute) {

    /** Postgres column widths, enforced here so a long description is clipped rather than rejected. */
    private static final int SUMMARY_MAX = 300;
    private static final int LOCATION_MAX = 300;

    /**
     * An all-day occurrence, placed on its own date and never converted to an
     * instant (adr/0036).
     *
     * @param end exclusive, as DTEND is; null for a single day
     */
    public static ParsedEvent allDay(String uid, String summary, String description,
                                     String location, LocalDate start, LocalDate end) {
        return new ParsedEvent(uid, title(summary), description, clip(location, LOCATION_MAX),
                true, null, null, start, end, start, (short) -1);
    }

    /**
     * A timed occurrence, placed on the day the household's clock puts it on.
     */
    public static ParsedEvent timed(String uid, String summary, String description,
                                    String location, Instant start, Instant end,
                                    ZoneId household) {
        LocalDate day = start.atZone(household).toLocalDate();
        LocalTime time = start.atZone(household).toLocalTime();
        return new ParsedEvent(uid, title(summary), description, clip(location, LOCATION_MAX),
                false, start, end, null, null, day,
                (short) (time.getHour() * 60 + time.getMinute()));
    }

    /**
     * A missing SUMMARY is a title, not a null: feeds do publish untitled
     * events, and a blank row on My day is worse than an honest placeholder.
     */
    private static String title(String summary) {
        String clipped = clip(summary, SUMMARY_MAX);
        return clipped == null || clipped.isBlank() ? "(No title)" : clipped;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
