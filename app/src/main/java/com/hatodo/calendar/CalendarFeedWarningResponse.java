package com.hatodo.calendar;

import java.time.Instant;
import java.util.UUID;

/**
 * A feed the caller can see that has stopped refreshing (R71).
 *
 * <p>This exists because an empty Thursday used to mean "nothing is due" and
 * now also means "nothing is due and no feed told us otherwise". Without
 * surfacing it, the feature degrades into lying - so the dashboard carries
 * these alongside the events rather than leaving the member to notice.
 */
public record CalendarFeedWarningResponse(UUID feedId, String label, String memberDisplayName,
                                          Instant lastSuccessAt, String lastError) {

    static CalendarFeedWarningResponse of(CalendarFeed feed) {
        return new CalendarFeedWarningResponse(feed.getId(), feed.getLabel(),
                feed.getOwner().getDisplayName(), feed.getLastSuccessAt(), feed.getLastError());
    }
}
