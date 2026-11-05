package com.hatodo.calendar;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of a refresh, kept apart from the half that makes an
 * HTTP request.
 *
 * <p>Separate beans rather than one with two methods, because a
 * {@code @Transactional} method called on {@code this} opens no transaction at
 * all - and the shape that would work, one transaction wrapping the fetch,
 * holds a database connection for the twenty seconds a slow provider is
 * allowed.
 */
@Component
class CalendarSyncStore {

    private final CalendarFeedRepository feeds;
    private final CalendarEventRepository events;

    CalendarSyncStore(CalendarFeedRepository feeds, CalendarEventRepository events) {
        this.feeds = feeds;
        this.events = events;
    }

    /** @param url read here and nowhere else outside the fetcher */
    record Target(UUID feedId, URI url) {
    }

    @Transactional(readOnly = true)
    Optional<Target> target(UUID feedId) {
        return feeds.findById(feedId).map(feed -> new Target(feed.getId(), feed.getUrl()));
    }

    @Transactional(readOnly = true)
    List<UUID> allFeedIds() {
        return feeds.findAllIdsOldestFirst();
    }

    /**
     * Replaces a feed's events wholesale.
     *
     * <p>The simplification the whole feature rests on: there is no identity to
     * preserve across a refresh, so an event that moved, one that was edited
     * and one that was deleted are all just a different set of rows. No
     * diffing, no tombstones, and nothing subtle to get wrong.
     */
    @Transactional
    void replace(UUID feedId, List<ParsedEvent> parsed, Instant at) {
        CalendarFeed feed = feeds.findById(feedId).orElse(null);
        if (feed == null) {
            // Deleted while the fetch was in flight. Nothing to record it on.
            return;
        }
        events.deleteForFeed(feedId);
        // Flushed before the inserts, so the delete cannot be reordered after
        // them and take the new rows with it.
        events.flush();
        events.saveAll(parsed.stream().map(event -> new CalendarEvent(feed, event)).toList());
        feed.recordSuccess(at);
    }

    /**
     * Records a failure <b>and keeps the existing rows</b>: a provider hiccup
     * must not empty Thursday. R71's staleness notice is what stops that being
     * a silent lie.
     */
    @Transactional
    void recordFailure(UUID feedId, String reason, Instant at) {
        feeds.findById(feedId).ifPresent(feed -> feed.recordFailure(at, reason));
    }
}
