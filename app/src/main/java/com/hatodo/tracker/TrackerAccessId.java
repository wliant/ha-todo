package com.hatodo.tracker;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link TrackerAccess}. */
public class TrackerAccessId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID tracker;
    private UUID user;

    protected TrackerAccessId() {
        // for JPA
    }

    public TrackerAccessId(UUID tracker, UUID user) {
        this.tracker = tracker;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TrackerAccessId id
                && Objects.equals(tracker, id.tracker)
                && Objects.equals(user, id.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tracker, user);
    }
}
