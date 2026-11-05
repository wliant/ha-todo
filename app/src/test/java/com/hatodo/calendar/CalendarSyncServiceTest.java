package com.hatodo.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarSyncServiceTest {

    private static final UUID FEED_ID = UUID.randomUUID();
    private static final URI URL = URI.create("https://calendar.google.com/x/basic.ics");
    private static final Instant NOW = Instant.parse("2026-11-06T02:00:00Z");

    @Mock
    private CalendarSyncStore store;
    @Mock
    private FeedFetcher fetcher;
    @Mock
    private CalendarWindow window;

    private CalendarSyncService service;

    @BeforeEach
    void setUp() {
        service = new CalendarSyncService(store, fetcher, new IcsParser(), window,
                new HouseholdProperties("Asia/Singapore"),
                Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private void aFeedExists() {
        when(store.target(FEED_ID)).thenReturn(Optional.of(new CalendarSyncStore.Target(FEED_ID, URL)));
        // The window is only consulted once a fetch has returned something, so
        // the failure tests never reach it.
        lenient().when(window.from()).thenReturn(LocalDate.of(2026, 9, 22));
        lenient().when(window.to()).thenReturn(LocalDate.of(2027, 2, 4));
    }

    @Test
    void replacesTheFeedsEventsOnASuccessfulRefresh() {
        aFeedExists();
        when(fetcher.fetch(URL)).thenReturn("""
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:a
                SUMMARY:Dinner
                DTSTART;TZID=Asia/Singapore:20261107T190000
                DTEND;TZID=Asia/Singapore:20261107T210000
                END:VEVENT
                END:VCALENDAR
                """);

        assertThat(service.sync(FEED_ID)).isTrue();

        ArgumentCaptor<List<ParsedEvent>> parsed = ArgumentCaptor.captor();
        verify(store).replace(eq(FEED_ID), parsed.capture(), eq(NOW));
        assertThat(parsed.getValue()).singleElement()
                .satisfies(event -> assertThat(event.summary()).isEqualTo("Dinner"));
    }

    @Test
    void keepsTheExistingEventsWhenARefreshFails() {
        aFeedExists();
        when(fetcher.fetch(URL))
                .thenThrow(new FeedFetchException("The calendar could not be reached."));

        assertThat(service.sync(FEED_ID)).isFalse();

        // The important half: a provider hiccup must not empty Thursday.
        verify(store, never()).replace(any(), anyList(), any());
        verify(store).recordFailure(FEED_ID, "The calendar could not be reached.", NOW);
    }

    @Test
    void recordsAPhraseTheOwnerCanReadRatherThanADiagnostic() {
        aFeedExists();
        when(fetcher.fetch(URL)).thenThrow(new IllegalStateException(
                "jdbc:postgresql://host/db failed for https://calendar.google.com/secret"));

        service.sync(FEED_ID);

        ArgumentCaptor<String> reason = ArgumentCaptor.captor();
        verify(store).recordFailure(eq(FEED_ID), reason.capture(), eq(NOW));
        assertThat(reason.getValue())
                .doesNotContain("calendar.google.com")
                .doesNotContain("IllegalStateException")
                .doesNotContain("jdbc");
    }

    @Test
    void doesNothingAtAllForAFeedThatWasDeletedMeanwhile() {
        when(store.target(FEED_ID)).thenReturn(Optional.empty());

        assertThat(service.sync(FEED_ID)).isFalse();
        verify(fetcher, never()).fetch(any());
    }

    @Test
    void oneBrokenFeedDoesNotStopTheOthersFromRefreshing() {
        UUID broken = UUID.randomUUID();
        UUID working = UUID.randomUUID();
        when(store.allFeedIds()).thenReturn(List.of(broken, working));
        when(store.target(broken)).thenReturn(Optional.of(new CalendarSyncStore.Target(broken, URL)));
        when(store.target(working)).thenReturn(Optional.of(new CalendarSyncStore.Target(working, URL)));
        when(window.from()).thenReturn(LocalDate.of(2026, 9, 22));
        when(window.to()).thenReturn(LocalDate.of(2027, 2, 4));
        when(fetcher.fetch(URL))
                .thenThrow(new FeedFetchException("The calendar was not found."))
                .thenReturn("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR\n");

        CalendarSyncService.Outcome outcome = service.syncAll();

        assertThat(outcome.feeds()).isEqualTo(2);
        assertThat(outcome.refreshed()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(1);
    }
}
