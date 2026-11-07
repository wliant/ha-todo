package com.hatodo.dashboard;

import com.hatodo.calendar.CalendarEventQuery;
import com.hatodo.calendar.CalendarFeedService;
import com.hatodo.calendar.CalendarWindow;
import com.hatodo.calendar.EventWindowResponse;
import com.hatodo.household.HouseholdProperties;
import com.hatodo.item.TodoItemRepository;
import com.hatodo.user.CurrentUserProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a member needs to do, across every list they can see (R44, R45).
 *
 * <p>The first read path in this product that does not start from one list, and
 * therefore the first where a missing access guard would leak several members'
 * private lists at once. The guard is in the queries, in SQL.
 */
@Service
public class DashboardService {

    /**
     * Enough that a household never reaches it, and low enough that a month
     * view cannot become an unbounded response.
     */
    private static final int LIMIT = 500;

    private final TodoItemRepository items;
    private final CalendarEventQuery events;
    private final CalendarFeedService feeds;
    private final CalendarWindow window;
    private final HouseholdProperties household;
    private final CurrentUserProvider currentUser;

    public DashboardService(TodoItemRepository items, CalendarEventQuery events,
                            CalendarFeedService feeds, CalendarWindow window,
                            HouseholdProperties household, CurrentUserProvider currentUser) {
        this.items = items;
        this.events = events;
        this.feeds = feeds;
        this.window = window;
        this.household = household;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ScheduledResponse scheduled(Instant from, Instant to, String scope) {
        UUID me = currentUser.requireUserId();
        boolean mineOnly = mineOnly(scope);
        return new ScheduledResponse(from, to,
                items.findScheduledFor(me, from, to, mineOnly, PageRequest.ofSize(LIMIT))
                        .stream()
                        .map(DashboardItemResponse::of)
                        .toList(),
                events.visible(me, firstDay(from), lastDay(to), mineOnly),
                feeds.staleFor(me),
                new EventWindowResponse(window.from(), window.to()));
    }

    /*
     * The window arrives as two instants because that is what an item's due
     * date is. An event is placed on a *day* in the household's zone, so the
     * bounds are read back in that zone - the same zone the client used to
     * build them, which is why both ends land on the days the member is
     * looking at. See adr/0036.
     */

    private LocalDate firstDay(Instant from) {
        return from.atZone(household.zone()).toLocalDate();
    }

    /** Exclusive at the top, so a window ending at midnight does not drag in the next day. */
    private LocalDate lastDay(Instant to) {
        return to.minusMillis(1).atZone(household.zone()).toLocalDate();
    }

    /**
     * What is late (0011 R88-R93).
     *
     * <p>Takes no window, and that is the design rather than an omission: the
     * agenda can be navigated, so a bound supplied by the caller is a bound the
     * period on screen can leak into, and "overdue" would then mean "before
     * whatever month I am looking at". The only clock this reads is the
     * household's (adr/0036).
     *
     * <p>The boundary is the **start of today**, not this instant: a chore due
     * at nine this morning is today's work until today is over (R91). An
     * instant boundary would also draw it twice for the minutes either side of
     * the cutoff - once in today's row, once as late.
     */
    @Transactional(readOnly = true)
    public OverdueResponse overdue(String scope) {
        UUID me = currentUser.requireUserId();
        boolean mineOnly = mineOnly(scope);
        Instant before = startOfToday();
        return new OverdueResponse(
                items.findOverdueFor(me, before, mineOnly, PageRequest.ofSize(LIMIT)).stream()
                        .map(DashboardItemResponse::of)
                        .toList(),
                items.countOverdueFor(me, before, mineOnly));
    }

    private Instant startOfToday() {
        ZoneId zone = household.zone();
        return LocalDate.now(zone).atStartOfDay(zone).toInstant();
    }

    @Transactional(readOnly = true)
    public UndatedResponse undated(String scope) {
        UUID me = currentUser.requireUserId();
        boolean mineOnly = mineOnly(scope);
        return new UndatedResponse(
                items.findUndatedFor(me, mineOnly, PageRequest.ofSize(LIMIT)).stream()
                        .map(DashboardItemResponse::of)
                        .toList(),
                items.countUndatedFor(me, mineOnly));
    }

    /**
     * Everything readable by default, with "mine" as a toggle.
     *
     * <p>Not the other way round, and the argument is about the data rather
     * than taste: the assignee field is optional and its {@code required}
     * defaults to false, so on most lists most items have no assignee, and on a
     * list that never declared the field every item has none. A mine-only
     * default would be empty on exactly the lists this product is about, and it
     * would hide the unclaimed chores a household dashboard exists to surface.
     */
    private boolean mineOnly(String scope) {
        return "mine".equalsIgnoreCase(scope);
    }
}
