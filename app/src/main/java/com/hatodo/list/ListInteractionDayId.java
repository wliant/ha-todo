package com.hatodo.list;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** The composite key of {@link ListInteractionDay}: one list, one member, one day. */
public class ListInteractionDayId implements Serializable {

    private UUID list;
    private UUID user;
    private LocalDate onDay;

    public ListInteractionDayId() {
    }

    public ListInteractionDayId(UUID list, UUID user, LocalDate onDay) {
        this.list = list;
        this.user = user;
        this.onDay = onDay;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ListInteractionDayId id)) {
            return false;
        }
        return Objects.equals(list, id.list)
                && Objects.equals(user, id.user)
                && Objects.equals(onDay, id.onDay);
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, user, onDay);
    }
}
