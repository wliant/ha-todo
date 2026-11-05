package com.hatodo.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one read of events, used by the dashboard (requirements/0007 R67, R69).
 *
 * <p>Collapsing happens <b>here, per request, after the access filter</b>, and
 * it cannot happen at write time: which feeds a caller may read varies by
 * caller, so the same two rows are one entry with two sources for Ana, who can
 * see both feeds, and one entry with one source for Ben, who can see one.
 */
@Service
public class CalendarEventQuery {

    /** Enough that a household never reaches it; low enough to bound a payload. */
    private static final int LIMIT = 500;

    private final CalendarEventRepository events;

    public CalendarEventQuery(CalendarEventRepository events) {
        this.events = events;
    }

    /**
     * @param from inclusive, {@code to} inclusive - household dates, because a
     *             day is where an event is placed and an instant is not
     */
    @Transactional(readOnly = true)
    public List<CalendarEventResponse> visible(UUID userId, LocalDate from, LocalDate to,
                                               boolean mineOnly) {
        List<CalendarEvent> rows =
                events.findVisibleBetween(userId, from, to, mineOnly, PageRequest.ofSize(LIMIT));

        // Insertion-ordered, so the SQL ordering survives the collapse.
        Map<String, Collapsed> collapsed = new LinkedHashMap<>();
        for (CalendarEvent event : rows) {
            // The first copy wins for the content: two feeds carrying one
            // appointment agree about when it is and may disagree about what
            // one owner renamed it to. First is arbitrary and stable, which is
            // the most that can be said for any choice here.
            collapsed.computeIfAbsent(collapseKey(event), ignored -> new Collapsed(event))
                    .add(event.getFeed());
        }
        return collapsed.values().stream()
                .map(entry -> CalendarEventResponse.of(entry.first, entry.sources))
                .toList();
    }

    /** One appointment and every feed that reported it. */
    private static final class Collapsed {

        private final CalendarEvent first;
        private final List<CalendarEventSourceResponse> sources = new ArrayList<>();

        private Collapsed(CalendarEvent first) {
            this.first = first;
        }

        private void add(CalendarFeed feed) {
            if (sources.stream().noneMatch(source -> source.feedId().equals(feed.getId()))) {
                sources.add(CalendarEventSourceResponse.of(feed));
            }
        }
    }

    /**
     * Two rows are the same appointment when they share a UID and a slot.
     *
     * <p>UID and nothing else, deliberately. Google preserves the originating
     * {@code iCalUID} on an invitation and Exchange does not always, so a
     * Google-to-Outlook invitation can still appear twice - and collapsing on
     * summary-plus-start instead would merge two genuinely different events
     * that happen to share a name and an hour, which is a worse failure than a
     * duplicate row.
     */
    private String collapseKey(CalendarEvent event) {
        return event.getUid() + "@" + event.getOccursOn() + "#" + event.getSortMinute();
    }
}
