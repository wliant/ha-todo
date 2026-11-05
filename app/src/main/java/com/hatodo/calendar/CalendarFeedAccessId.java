package com.hatodo.calendar;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link CalendarFeedAccess}. */
public class CalendarFeedAccessId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID feed;
    private UUID user;

    protected CalendarFeedAccessId() {
        // for JPA
    }

    public CalendarFeedAccessId(UUID feed, UUID user) {
        this.feed = feed;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof CalendarFeedAccessId id
                && Objects.equals(feed, id.feed)
                && Objects.equals(user, id.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(feed, user);
    }
}
