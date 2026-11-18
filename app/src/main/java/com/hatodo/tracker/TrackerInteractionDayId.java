package com.hatodo.tracker;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link TrackerInteractionDay}. */
public class TrackerInteractionDayId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID tracker;
    private UUID user;
    private LocalDate onDay;

    protected TrackerInteractionDayId() {
        // for JPA
    }

    public TrackerInteractionDayId(UUID tracker, UUID user, LocalDate onDay) {
        this.tracker = tracker;
        this.user = user;
        this.onDay = onDay;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TrackerInteractionDayId id
                && Objects.equals(tracker, id.tracker)
                && Objects.equals(user, id.user)
                && Objects.equals(onDay, id.onDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tracker, user, onDay);
    }
}
