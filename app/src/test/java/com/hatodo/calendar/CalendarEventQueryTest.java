package com.hatodo.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hatodo.user.AppUser;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Collapsing, which happens per request and after the access filter - because
 * which feeds a caller may read decides whether two rows are one entry.
 */
@ExtendWith(MockitoExtension.class)
class CalendarEventQueryTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 7);
    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    @Mock
    private CalendarEventRepository events;

    @Test
    void showsOneAppointmentOnceEvenWhenTwoFeedsCarryIt() {
        CalendarFeed ana = feed("Ana", "Family");
        CalendarFeed ben = feed("Ben", "Work");
        given(List.of(event(ana, "dinner-uid", "Dinner"), event(ben, "dinner-uid", "Dinner")));

        List<CalendarEventResponse> result = query().visible(ANA, DAY, DAY, false);

        assertThat(result).singleElement().satisfies(entry -> {
            assertThat(entry.summary()).isEqualTo("Dinner");
            assertThat(entry.sources()).extracting(CalendarEventSourceResponse::memberDisplayName)
                    .containsExactly("Ana", "Ben");
        });
    }

    @Test
    void keepsTwoDifferentAppointmentsApartEvenAtTheSameMinute() {
        CalendarFeed ana = feed("Ana", "Family");
        given(List.of(event(ana, "dinner-uid", "Dinner"), event(ana, "bath-uid", "Bath time")));

        assertThat(query().visible(ANA, DAY, DAY, false)).hasSize(2);
    }

    @Test
    void doesNotCollapseWhatTheCallerCanOnlySeeOneCopyOf() {
        // The repository's grant join has already removed Ben's feed, so there
        // is one row and one source - the same appointment reads differently to
        // different members, which is why this cannot be done at write time.
        CalendarFeed ana = feed("Ana", "Family");
        given(List.of(event(ana, "dinner-uid", "Dinner")));

        assertThat(query().visible(ANA, DAY, DAY, false)).singleElement()
                .satisfies(entry -> assertThat(entry.sources()).hasSize(1));
    }

    private CalendarEventQuery query() {
        return new CalendarEventQuery(events);
    }

    private void given(List<CalendarEvent> rows) {
        when(events.findVisibleBetween(eq(ANA), eq(DAY), eq(DAY), anyBoolean(), any()))
                .thenReturn(rows);
    }

    private CalendarFeed feed(String ownerName, String label) {
        AppUser owner = AppUser.passwordlessMember(ownerName.toLowerCase(), ownerName);
        ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
        CalendarFeed feed = new CalendarFeed(owner, label,
                URI.create("https://calendar.google.com/" + label + "/basic.ics"));
        ReflectionTestUtils.setField(feed, "id", UUID.randomUUID());
        return feed;
    }

    private CalendarEvent event(CalendarFeed feed, String uid, String summary) {
        return new CalendarEvent(feed, ParsedEvent.timed(uid, summary, null, null,
                DAY.atTime(19, 0).atZone(SGT).toInstant(),
                DAY.atTime(21, 0).atZone(SGT).toInstant(), SGT));
    }
}
