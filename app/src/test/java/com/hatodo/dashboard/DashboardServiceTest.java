package com.hatodo.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.calendar.CalendarEventQuery;
import com.hatodo.calendar.CalendarFeedService;
import com.hatodo.calendar.CalendarWindow;
import com.hatodo.household.HouseholdProperties;
import com.hatodo.item.TodoItemRepository;
import com.hatodo.user.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Where "late" starts (0011 R88-R93, adr/0040).
 *
 * <p>Almost all of the dashboard's behaviour is in SQL and is checked by
 * {@code DashboardApiIntegrationTest} against real Postgres, deliberately: a
 * predicate applied after fetching passes every unit test and leaks every list.
 * What is left here is the one decision the service makes on its own, and the
 * one a future reader is most likely to "simplify" - the overdue boundary.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID ANA = UUID.randomUUID();

    @Mock
    private TodoItemRepository items;
    @Mock
    private CalendarEventQuery events;
    @Mock
    private CalendarFeedService feeds;
    @Mock
    private CalendarWindow window;
    @Mock
    private CurrentUserProvider currentUser;

    @Test
    void lateStartsAtMidnightInTheHouseholdsZoneRatherThanAtThisInstant() {
        // R91. The assertion is on the boundary's *time of day*, not on a
        // computed instant, because that is the property the requirement is
        // about: a chore due at nine this morning is today's work until today
        // is over. Written as "equals now() minus something" this test would
        // agree with the bug it exists to catch.
        ZoneId sgt = ZoneId.of("Asia/Singapore");

        Instant before = boundaryFor(sgt);

        assertThat(before.atZone(sgt).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(before.atZone(sgt).toLocalDate()).isEqualTo(LocalDate.now(sgt));
    }

    @Test
    void theZoneIsTheHouseholdsAndNotTheMachinesOrTheCallersDevice() {
        // adr/0036: one household, one definition of today. Two zones whose
        // midnights are not the same instant have to produce two boundaries, or
        // the zone is being read from somewhere it should not be.
        ZoneId sgt = ZoneId.of("Asia/Singapore");
        ZoneId utc = ZoneId.of("UTC");

        assertThat(boundaryFor(sgt)).isNotEqualTo(boundaryFor(utc));
        assertThat(boundaryFor(utc).atZone(utc).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
    }

    @Test
    void reportsTheRealTotalRatherThanThePageItDrew() {
        // R92. The page is capped; a strip that says "3" because it drew three
        // is the one thing this feature cannot afford to be.
        when(currentUser.requireUserId()).thenReturn(ANA);
        when(items.findOverdueFor(eq(ANA), any(), anyBoolean(), any())).thenReturn(List.of());
        when(items.countOverdueFor(eq(ANA), any(), anyBoolean())).thenReturn(41L);

        OverdueResponse response = service(ZoneId.of("Asia/Singapore")).overdue(null);

        assertThat(response.items()).isEmpty();
        assertThat(response.total()).isEqualTo(41L);
    }

    @Test
    void scopeMineNarrowsToTheCallersOwnWork() {
        when(currentUser.requireUserId()).thenReturn(ANA);
        when(items.findOverdueFor(eq(ANA), any(), anyBoolean(), any())).thenReturn(List.of());

        service(ZoneId.of("Asia/Singapore")).overdue("mine");

        ArgumentCaptor<Boolean> mineOnly = ArgumentCaptor.forClass(Boolean.class);
        verify(items)
                .findOverdueFor(eq(ANA), any(), mineOnly.capture(), any());
        assertThat(mineOnly.getValue()).isTrue();
    }

    /** The boundary the service actually handed the query, for the given zone. */
    private Instant boundaryFor(ZoneId zone) {
        when(currentUser.requireUserId()).thenReturn(ANA);
        when(items.findOverdueFor(eq(ANA), any(), anyBoolean(), any())).thenReturn(List.of());

        service(zone).overdue(null);

        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        verify(items, atLeastOnce())
                .findOverdueFor(eq(ANA), before.capture(), anyBoolean(), any());
        return before.getValue();
    }

    private DashboardService service(ZoneId zone) {
        return new DashboardService(items, events, feeds, window,
                new HouseholdProperties(zone.getId()), currentUser);
    }
}
