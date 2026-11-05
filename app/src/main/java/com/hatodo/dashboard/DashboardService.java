package com.hatodo.dashboard;

import com.hatodo.item.TodoItemRepository;
import com.hatodo.user.CurrentUserProvider;
import java.time.Instant;
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
    private final CurrentUserProvider currentUser;

    public DashboardService(TodoItemRepository items, CurrentUserProvider currentUser) {
        this.items = items;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ScheduledResponse scheduled(Instant from, Instant to, String scope) {
        UUID me = currentUser.requireUserId();
        return new ScheduledResponse(from, to,
                items.findScheduledFor(me, from, to, mineOnly(scope), PageRequest.ofSize(LIMIT))
                        .stream()
                        .map(DashboardItemResponse::of)
                        .toList());
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
