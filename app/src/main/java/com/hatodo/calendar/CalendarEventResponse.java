package com.hatodo.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One event, as My day draws it.
 *
 * <p>An all-day event carries dates and no instants; a timed one the reverse.
 * The client must branch on {@code allDay} rather than formatting
 * {@code startsAt} and hoping, which is the same rule the schema's
 * {@code ck_calendar_event_shape} enforces.
 *
 * <p>No attendees and no organiser: mostly e-mail addresses of people outside
 * the household, they answer no question anybody asks on My day, and they are
 * the part of an event a member would least expect to have published.
 *
 * @param sources whose calendars reported it. Usually one; two when one member
 *                invited another and both feeds carry it (R69)
 */
public record CalendarEventResponse(UUID id, String summary, String description, String location,
                                    boolean allDay, Instant startsAt, Instant endsAt,
                                    LocalDate startDate, LocalDate endDate,
                                    LocalDate occursOn, short sortMinute,
                                    List<CalendarEventSourceResponse> sources) {

    static CalendarEventResponse of(CalendarEvent event, List<CalendarEventSourceResponse> sources) {
        return new CalendarEventResponse(event.getId(), event.getSummary(), event.getDescription(),
                event.getLocation(), event.isAllDay(), event.getStartsAt(), event.getEndsAt(),
                event.getStartDate(), event.getEndDate(), event.getOccursOn(),
                event.getSortMinute(), sources);
    }

    /** The preview, where there is no feed yet and so nothing to attribute. */
    static CalendarEventResponse of(ParsedEvent parsed) {
        return new CalendarEventResponse(null, parsed.summary(), parsed.description(),
                parsed.location(), parsed.allDay(), parsed.startsAt(), parsed.endsAt(),
                parsed.startDate(), parsed.endDate(), parsed.occursOn(), parsed.sortMinute(),
                List.of());
    }
}
