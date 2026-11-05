package com.hatodo.calendar;

import com.hatodo.household.HouseholdProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetch, parse, replace - for one feed, and for all of them.
 *
 * <p>Deliberately not transactional: the HTTP request is the slow part and
 * {@link CalendarSyncStore} owns the two short transactions around it.
 */
@Service
public class CalendarSyncService {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncService.class);

    private final CalendarSyncStore store;
    private final FeedFetcher fetcher;
    private final IcsParser parser;
    private final CalendarWindow window;
    private final HouseholdProperties household;
    private final Clock clock;

    public CalendarSyncService(CalendarSyncStore store, FeedFetcher fetcher, IcsParser parser,
                              CalendarWindow window, HouseholdProperties household, Clock clock) {
        this.store = store;
        this.fetcher = fetcher;
        this.parser = parser;
        this.window = window;
        this.household = household;
        this.clock = clock;
    }

    /** What a run did, for the log line and for the admin trigger. */
    public record Outcome(int feeds, int refreshed, int failed) {
    }

    /**
     * One feed.
     *
     * @return true if it refreshed; false if it failed, which is recorded on
     *         the feed rather than thrown - a member's broken address must not
     *         fail the other members' refreshes, and must not fail the request
     *         that asked for this one either
     */
    public boolean sync(UUID feedId) {
        CalendarSyncStore.Target target = store.target(feedId).orElse(null);
        if (target == null) {
            return false;
        }
        Instant at = clock.instant();
        try {
            List<ParsedEvent> parsed = parser.parse(fetcher.fetch(target.url()),
                    household.zone(), window.from(), window.to());
            store.replace(feedId, parsed, at);
            return true;
        } catch (FeedFetchException | FeedUrlNotAllowedException failed) {
            // The message is a phrase written for the owner's screen, not a
            // diagnostic: no exception class, no URL, nothing echoed from the
            // response body. The feed id is enough to find it in the logs.
            log.info("Calendar feed {} did not refresh: {}", feedId, failed.getMessage());
            store.recordFailure(feedId, failed.getMessage(), at);
            return false;
        } catch (RuntimeException unexpected) {
            log.warn("Calendar feed {} failed to refresh", feedId, unexpected);
            store.recordFailure(feedId, "The calendar could not be read.", at);
            return false;
        }
    }

    /** Every feed in the deployment: the scheduled poll, and nothing else. */
    public Outcome syncAll() {
        List<UUID> ids = store.allFeedIds();
        int refreshed = 0;
        for (UUID id : ids) {
            if (sync(id)) {
                refreshed++;
            }
        }
        return new Outcome(ids.size(), refreshed, ids.size() - refreshed);
    }
}
