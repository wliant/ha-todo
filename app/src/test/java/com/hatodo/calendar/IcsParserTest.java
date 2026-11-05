package com.hatodo.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The parser, against the cases that actually break.
 *
 * <p>Every test runs with the JVM's default zone forced to <b>UTC</b>, because
 * that is what every container image in this product is - and the all-day bug
 * adr/0036 exists for is invisible on a laptop in Singapore. If these pass with
 * the default zone left alone, they are not testing the thing.
 */
class IcsParserTest {

    private static final ZoneId HOUSEHOLD = ZoneId.of("Asia/Singapore");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private final IcsParser parser = new IcsParser();
    private TimeZone original;

    @BeforeEach
    void pretendToBeAContainer() {
        original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restore() {
        TimeZone.setDefault(original);
    }

    @Test
    void readsAnAllDayEventAsADateAndNeverAsAnInstant() {
        ParsedEvent event = only("all-day.ics");

        assertThat(event.allDay()).isTrue();
        assertThat(event.startDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(event.occursOn()).isEqualTo(LocalDate.of(2026, 11, 7));
        // The whole point: no midnight was chosen on the feed's behalf, so
        // there is no instant to be wrong about.
        assertThat(event.startsAt()).isNull();
        assertThat(event.endsAt()).isNull();
        assertThat(event.endDate()).isEqualTo(LocalDate.of(2026, 11, 8));
    }

    @Test
    void sortsAnAllDayEventAboveEveryTimedEventOnItsDay() {
        assertThat(only("all-day.ics").sortMinute()).isEqualTo((short) -1);
    }

    @Test
    void placesATimedEventOnTheDayTheHouseholdsClockPutsItOn() {
        ParsedEvent event = only("timed-tzid.ics");

        assertThat(event.allDay()).isFalse();
        assertThat(event.startsAt().toString()).isEqualTo("2026-11-07T11:30:00Z");
        assertThat(event.occursOn()).isEqualTo(LocalDate.of(2026, 11, 7));
        // 19:30 in the household's zone, not 11:30 in the server's.
        assertThat(event.sortMinute()).isEqualTo((short) (19 * 60 + 30));
        assertThat(event.location()).isEqualTo("The hall");
        assertThat(event.description()).isEqualTo("Bring the form");
    }

    @Test
    void readsAFloatingTimeAsTheHouseholdsWallClock() {
        // No TZID and no Z: RFC 5545 says this means "wherever the reader is",
        // and the reader is the household - not the container, which is UTC.
        ParsedEvent event = only("floating.ics");

        assertThat(event.startsAt().toString()).isEqualTo("2026-11-07T01:00:00Z");
        assertThat(event.sortMinute()).isEqualTo((short) (9 * 60));
        assertThat(event.occursOn()).isEqualTo(LocalDate.of(2026, 11, 7));
    }

    @Test
    void expandsAWeeklyRuleAndHonoursItsExceptions() {
        List<ParsedEvent> events = parse("weekly-with-exception.ics");

        // COUNT=4 from the 3rd, minus the EXDATE on the 10th.
        assertThat(events).extracting(ParsedEvent::occursOn).containsExactly(
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 17),
                LocalDate.of(2026, 11, 24));
        assertThat(events).allSatisfy(event -> assertThat(event.summary()).isEqualTo("Piano"));
    }

    @Test
    void letsAMovedOccurrenceReplaceTheOneItMoved() {
        List<ParsedEvent> events = parse("moved-occurrence.ics");

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ParsedEvent::summary)
                .containsExactly("Swimming", "Swimming (later)");
        // The override's own time, not the series'.
        assertThat(events.get(1).sortMinute()).isEqualTo((short) (18 * 60 + 30));
    }

    @Test
    void dropsACancelledOccurrenceRatherThanShowingIt() {
        // The third occurrence of weekly-2 is STATUS:CANCELLED.
        assertThat(parse("moved-occurrence.ics"))
                .noneMatch(event -> event.occursOn().equals(LocalDate.of(2026, 11, 17)));
    }

    @Test
    void givesAnUntitledEventATitleRatherThanANull() {
        assertThat(only("untitled-and-long.ics").summary()).isEqualTo("(No title)");
    }

    @Test
    void placesAMultiDayEventOnItsStartDayAndKeepsItsEnd() {
        ParsedEvent event = only("multi-day.ics");

        assertThat(event.occursOn()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(event.endDate()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void leavesOutAnythingOutsideTheWindow() {
        List<ParsedEvent> events = parser.parse(fixture("weekly-with-exception.ics"), HOUSEHOLD,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 30));

        assertThat(events).extracting(ParsedEvent::occursOn)
                .containsExactly(LocalDate.of(2026, 11, 24));
    }

    @Test
    void refusesBytesThatAreNotACalendarAtAll() {
        // What a mispasted address overwhelmingly returns: a sign-in page.
        assertThatThrownBy(() -> parser.parse(fixture("not-a-calendar.html"), HOUSEHOLD, FROM, TO))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("did not return a calendar");
    }

    @Test
    void readsTheSameAllDayEventTheSameWayInAnyHouseholdZone() {
        // A date has no zone, so moving the household must not move the event.
        for (String zone : List.of("Asia/Singapore", "UTC", "America/Los_Angeles", "Pacific/Kiritimati")) {
            List<ParsedEvent> events =
                    parser.parse(fixture("all-day.ics"), ZoneId.of(zone), FROM, TO);

            assertThat(events).singleElement()
                    .satisfies(event -> assertThat(event.occursOn())
                            .isEqualTo(LocalDate.of(2026, 11, 7)));
        }
    }

    @Test
    void expandsAnAllDaySeriesWithoutTheDatesDrifting() {
        assertThat(parse("all-day-weekly.ics")).extracting(ParsedEvent::occursOn).containsExactly(
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 17));
    }

    @Test
    void readsAnAllDaySeriesTheSameWayWhateverZoneTheServerIsIn() {
        // The probe this parser's comment records: biweekly resolves a DATE
        // against TimeZone.getDefault(), so a server in New York and a server
        // in Singapore hold different instants for the same written date. The
        // dates they report must not differ.
        for (String server : List.of("UTC", "America/New_York", "Asia/Singapore", "Pacific/Apia")) {
            TimeZone.setDefault(TimeZone.getTimeZone(server));

            assertThat(parse("all-day-weekly.ics")).as("server in %s", server)
                    .extracting(ParsedEvent::occursOn)
                    .containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10),
                            LocalDate.of(2026, 11, 17));
            assertThat(only("all-day.ics")).as("server in %s", server)
                    .satisfies(event -> assertThat(event.startDate())
                            .isEqualTo(LocalDate.of(2026, 11, 7)));
        }
    }

    @Test
    void keepsAMovedOccurrenceWhoseOriginalSlotNoLongerExists() {
        // Feeds routinely carry an override the expansion cannot match: a
        // series whose own DTSTART was edited after an occurrence had been
        // moved leaves the old RECURRENCE-ID behind. The appointment is real
        // and the member moved it, so dropping it shows a series with one
        // occurrence quietly missing - and nothing says so.
        List<ParsedEvent> events = parse("stale-override.ics");

        assertThat(events).extracting(ParsedEvent::occursOn).containsExactly(
                LocalDate.of(2026, 11, 3),
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12),
                LocalDate.of(2026, 11, 17));
        assertThat(events).filteredOn(event -> event.occursOn().equals(LocalDate.of(2026, 11, 12)))
                .singleElement()
                .satisfies(event -> assertThat(event.summary()).startsWith("Swimming (moved"));
    }

    @Test
    void leavesAnUnmatchedOverrideOutWhenItFallsOutsideTheWindow() {
        List<ParsedEvent> events = parser.parse(fixture("stale-override.ics"), HOUSEHOLD,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 11));

        assertThat(events).extracting(ParsedEvent::occursOn)
                .containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 10));
    }

    @Test
    void readsASeriesWrittenAsOneMasterPlusRecurrenceIdChildren() {
        // Not every publisher writes an RRULE. Some expand a series into one
        // VEVENT per occurrence, all sharing a UID, with every occurrence after
        // the first carrying a RECURRENCE-ID and the master carrying no rule at
        // all. Expansion then produces exactly one slot, every child matches
        // nothing - and before unmatched overrides were kept, a weekly event
        // showed up once and the member had no way to tell why.
        List<ParsedEvent> events = parse("recurrence-id-children.ics");

        assertThat(events).hasSize(6);
        assertThat(events).extracting(ParsedEvent::occursOn).containsExactly(
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 23),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 7));
    }

    private ParsedEvent only(String fixture) {
        List<ParsedEvent> events = parse(fixture);
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private List<ParsedEvent> parse(String fixture) {
        return parser.parse(fixture(fixture), HOUSEHOLD, FROM, TO).stream()
                .sorted((left, right) -> left.occursOn().compareTo(right.occursOn()))
                .toList();
    }

    private String fixture(String name) {
        try (InputStream in = getClass().getResourceAsStream("/calendar/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException missing) {
            throw new IllegalStateException("fixture " + name + " is not on the test classpath");
        }
    }
}
