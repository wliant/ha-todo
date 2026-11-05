package com.hatodo.dashboard;

import com.hatodo.calendar.CalendarEventResponse;
import com.hatodo.calendar.CalendarFeedWarningResponse;
import java.time.Instant;
import java.util.List;

/**
 * What a window holds: the work, and the day the work has to fit into.
 *
 * <p>Events ride this payload rather than a sibling endpoint, because the grid
 * draws both together (requirements/0007 R67) - two queries would give one grid
 * two loading states and a visible flash of chores-without-events.
 *
 * @param from       inclusive, {@code to} exclusive - the window the client
 *                   asked for
 * @param staleFeeds calendars the caller can see that have stopped refreshing.
 *                   Carried here because an empty Thursday now has two
 *                   meanings, and R71 is what keeps them apart.
 */
public record ScheduledResponse(Instant from, Instant to, List<DashboardItemResponse> items,
                                List<CalendarEventResponse> events,
                                List<CalendarFeedWarningResponse> staleFeeds) {
}
