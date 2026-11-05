package com.hatodo.calendar;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Status;
import biweekly.util.ICalDate;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.springframework.stereotype.Component;

/**
 * An {@code .ics} feed, read into occurrences.
 *
 * <p>Pure: no clock, no database, no HTTP. That is what makes the cases that
 * actually break - all-day events, floating times, a recurrence with
 * exceptions, a moved occurrence - cheap enough to test one fixture at a time.
 *
 * <p>Two things here are easy to get wrong and both are in adr/0036.
 *
 * <p><b>All-day is decided by the value type, never by the time being
 * midnight</b>, and an all-day date comes from the property's <em>raw
 * components</em> - the digits as written - and from nothing else.
 *
 * <p>That is not a stylistic preference, it is what a probe of this library
 * reports. biweekly resolves a {@code DTSTART;VALUE=DATE} against
 * <b>{@code TimeZone.getDefault()}</b>, ignoring both the
 * {@code defaultTimezone} given to the parser and the zone handed to
 * {@code getDateIterator}. So the instant behind an all-day event is midnight
 * <em>wherever the server happens to be</em>, and reading it back in the
 * household's zone moves the school play to the previous evening in every
 * deployment west of the container. The fix is to never convert it: an
 * occurrence's date is the raw series date plus the iterator's day offset,
 * which is the same answer in every zone. {@code IcsParserTest} pins this, so
 * a future version of the library changing its mind fails loudly.
 *
 * <p><b>Floating times resolve to the household's zone</b>, which is what
 * {@code defaultTimezone} on the parser is for. Unset, biweekly would resolve
 * them against the JVM's default - correct on a developer's laptop in
 * Singapore and wrong in every deployment.
 */
@Component
public class IcsParser {

    /**
     * A guard against a rule nothing bounds.
     *
     * <p>A daily event over a 135-day window is 135 occurrences; an hourly one
     * is 3,240. Something pathological should cost one feed its refresh, not
     * the heap.
     */
    private static final int MAX_OCCURRENCES_PER_EVENT = 5_000;

    /**
     * @param from inclusive, {@code to} inclusive - both household dates
     * @throws FeedFetchException if the bytes are not a calendar at all, which
     *         is what a mispasted address usually produces
     */
    public List<ParsedEvent> parse(String text, ZoneId household, LocalDate from, LocalDate to) {
        ICalendar calendar = read(text, household);
        TimeZone zone = TimeZone.getTimeZone(household);

        List<ParsedEvent> parsed = new ArrayList<>();
        Map<String, List<VEvent>> byUid = groupByUid(calendar.getEvents());

        for (Map.Entry<String, List<VEvent>> entry : byUid.entrySet()) {
            String uid = entry.getKey();
            Map<String, VEvent> overrides = new HashMap<>();
            List<VEvent> masters = new ArrayList<>();
            for (VEvent event : entry.getValue()) {
                if (event.getRecurrenceId() == null) {
                    masters.add(event);
                } else {
                    // A moved or edited single occurrence: same UID, plus the
                    // start it *used* to have.
                    overrides.put(occurrenceKey(event.getRecurrenceId().getValue(), household),
                            event);
                }
            }
            for (VEvent master : masters) {
                expand(uid, master, overrides, zone, household, from, to, parsed);
            }
        }
        return parsed;
    }

    private ICalendar read(String text, ZoneId household) {
        ICalendar calendar;
        try {
            calendar = Biweekly.parse(text)
                    // Floating times mean the household's wall clock, not the
                    // server's. See the class comment.
                    .defaultTimezone(TimeZone.getTimeZone(household))
                    .first();
        } catch (RuntimeException notACalendar) {
            // The string overload declares no IOException; what a page of HTML
            // produces instead is a parse-time runtime failure, or nothing.
            throw new FeedFetchException("That address did not return a calendar.", notACalendar);
        }
        if (calendar == null) {
            // Overwhelmingly a mispasted address: an HTML page parses to
            // nothing rather than failing.
            throw new FeedFetchException("That address did not return a calendar.");
        }
        return calendar;
    }

    private Map<String, List<VEvent>> groupByUid(List<VEvent> events) {
        Map<String, List<VEvent>> byUid = new HashMap<>();
        for (VEvent event : events) {
            if (event.getDateStart() == null || event.getDateStart().getValue() == null) {
                // An event with no start cannot be placed on a day, and a feed
                // containing one should not cost the member the other 200.
                continue;
            }
            String uid = event.getUid() == null || event.getUid().getValue() == null
                    ? "no-uid-" + System.identityHashCode(event)
                    : event.getUid().getValue();
            byUid.computeIfAbsent(uid, key -> new ArrayList<>()).add(event);
        }
        return byUid;
    }

    private void expand(String uid, VEvent master, Map<String, VEvent> overrides,
                        TimeZone zone, ZoneId household, LocalDate from, LocalDate to,
                        List<ParsedEvent> into) {
        if (isCancelled(master)) {
            return;
        }
        ICalDate start = master.getDateStart().getValue();
        boolean allDay = !start.hasTime();
        // The length of the series' first occurrence, reapplied to each: DTEND
        // and DURATION are both allowed, and an occurrence carries neither.
        Duration length = lengthOf(master, allDay);
        long allDayLength = allDay ? allDayLengthOf(master, household) : 0;

        DateIterator occurrences = master.getDateIterator(zone);
        // Half a window back, because an all-day occurrence's instant is
        // midnight in the *server's* zone (see the class comment) and could sit
        // a day either side of the household's.
        occurrences.advanceTo(Date.from(from.minusDays(2).atStartOfDay(household).toInstant()));

        int seen = 0;
        while (occurrences.hasNext() && seen++ < MAX_OCCURRENCES_PER_EVENT) {
            Date occurrence = occurrences.next();
            LocalDate day = allDay
                    ? rawDate(start, household).plusDays(dayOffset(start, occurrence))
                    : occurrence.toInstant().atZone(household).toLocalDate();
            if (day.isAfter(to)) {
                return;
            }
            if (day.isBefore(from)) {
                continue;
            }

            VEvent override = overrides.get(allDay ? "date:" + day : "time:" + occurrence.getTime());
            VEvent source = override == null ? master : override;
            if (isCancelled(source)) {
                continue;
            }
            into.add(override == null
                    ? occurrenceOf(uid, master, allDay, day, occurrence, length, allDayLength,
                            household)
                    : single(uid, override, household));
        }
    }

    /**
     * How many days after the series' start this occurrence falls.
     *
     * <p>Both instants are midnight in the server's own zone - that is how
     * biweekly resolved them - so the difference is read back in that same
     * zone, where both land on an exact civil date and daylight saving cannot
     * turn four days into three and a bit.
     */
    private long dayOffset(ICalDate seriesStart, Date occurrence) {
        ZoneId resolvedIn = TimeZone.getDefault().toZoneId();
        return ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(seriesStart.getTime()).atZone(resolvedIn).toLocalDate(),
                occurrence.toInstant().atZone(resolvedIn).toLocalDate());
    }

    /** An all-day event's length in whole days, from the dates as written. */
    private long allDayLengthOf(VEvent event, ZoneId household) {
        ICalDate start = event.getDateStart().getValue();
        ICalDate end = event.getDateEnd() == null ? null : event.getDateEnd().getValue();
        if (end == null) {
            return 1;
        }
        long days = ChronoUnit.DAYS.between(rawDate(start, household), rawDate(end, household));
        return Math.max(1, days);
    }

    /** One occurrence of a series, on the day already worked out for it. */
    private ParsedEvent occurrenceOf(String uid, VEvent master, boolean allDay, LocalDate day,
                                     Date occurrence, Duration length, long allDayLength,
                                     ZoneId household) {
        String summary = valueOf(master.getSummary() == null ? null : master.getSummary().getValue());
        String description = valueOf(
                master.getDescription() == null ? null : master.getDescription().getValue());
        String location = valueOf(master.getLocation() == null ? null : master.getLocation().getValue());

        if (allDay) {
            return ParsedEvent.allDay(uid, summary, description, location, day,
                    day.plusDays(allDayLength));
        }
        Instant startsAt = occurrence.toInstant();
        return ParsedEvent.timed(uid, summary, description, location,
                startsAt, startsAt.plus(length), household);
    }

    /**
     * A single VEvent read on its own terms - the override case, and the only
     * path that reads an all-day date's raw components.
     *
     * <p>Raw rather than converted, because they are the digits the feed wrote
     * and an all-day event has no instant to convert.
     */
    private ParsedEvent single(String uid, VEvent event, ZoneId household) {
        ICalDate start = event.getDateStart().getValue();
        String summary = valueOf(event.getSummary() == null ? null : event.getSummary().getValue());
        String description = valueOf(
                event.getDescription() == null ? null : event.getDescription().getValue());
        String location = valueOf(event.getLocation() == null ? null : event.getLocation().getValue());

        if (!start.hasTime()) {
            LocalDate day = rawDate(start, household);
            ICalDate end = event.getDateEnd() == null ? null : event.getDateEnd().getValue();
            LocalDate endDate = end == null ? day.plusDays(1) : rawDate(end, household);
            return ParsedEvent.allDay(uid, summary, description, location, day, endDate);
        }
        Instant startsAt = start.toInstant();
        return ParsedEvent.timed(uid, summary, description, location,
                startsAt, startsAt.plus(lengthOf(event, false)), household);
    }

    /**
     * The date as the feed wrote it.
     *
     * <p>{@code getRawComponents()} is the whole reason this is correct: it
     * reports the digits rather than an instant somebody chose a midnight for.
     * It is only null for a value this application constructed, which never
     * happens on a parse - but a null here would be a silent date shift, so it
     * falls back explicitly rather than throwing a NullPointerException two
     * frames away.
     */
    private LocalDate rawDate(ICalDate value, ZoneId household) {
        if (value.getRawComponents() == null) {
            return value.toInstant().atZone(household).toLocalDate();
        }
        return LocalDate.of(value.getRawComponents().getYear(),
                value.getRawComponents().getMonth(),
                value.getRawComponents().getDate());
    }

    /** DTEND, or DURATION, or a default - in that order, as RFC 5545 has it. */
    private Duration lengthOf(VEvent event, boolean allDay) {
        ICalDate start = event.getDateStart().getValue();
        if (event.getDateEnd() != null && event.getDateEnd().getValue() != null) {
            long millis = event.getDateEnd().getValue().getTime() - start.getTime();
            if (millis > 0) {
                return Duration.ofMillis(millis);
            }
        }
        if (event.getDuration() != null && event.getDuration().getValue() != null) {
            return Duration.ofMillis(event.getDuration().getValue().toMillis());
        }
        // RFC 5545: a date-only event with no end is one day; a timed one with
        // no end is an instant, which reads better as half an hour on a grid.
        return allDay ? Duration.ofDays(1) : Duration.ofMinutes(30);
    }

    /**
     * How an override says which occurrence it replaces.
     *
     * <p>An all-day series is keyed by its date and a timed one by its instant,
     * because that is the only thing the two forms of RECURRENCE-ID have in
     * common with the occurrences they are matched against.
     */
    private String occurrenceKey(ICalDate recurrenceId, ZoneId household) {
        if (recurrenceId == null) {
            return "none";
        }
        if (!recurrenceId.hasTime()) {
            return "date:" + rawDate(recurrenceId, household);
        }
        return "time:" + recurrenceId.getTime();
    }

    private boolean isCancelled(VEvent event) {
        Status status = event.getStatus();
        return status != null && status.isCancelled();
    }

    private String valueOf(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
