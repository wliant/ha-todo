package com.hatodo.list;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link ListUserState}. */
public class ListUserStateId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID list;
    private UUID user;

    protected ListUserStateId() {
        // for JPA
    }

    public ListUserStateId(UUID list, UUID user) {
        this.list = list;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ListUserStateId id
                && Objects.equals(list, id.list)
                && Objects.equals(user, id.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, user);
    }
}
