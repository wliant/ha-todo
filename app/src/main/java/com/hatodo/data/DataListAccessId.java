package com.hatodo.data;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key for {@link DataListAccess}. */
public class DataListAccessId implements Serializable {

    private static final long serialVersionUID = 1L;

    private UUID list;
    private UUID user;

    protected DataListAccessId() {
        // for JPA
    }

    public DataListAccessId(UUID list, UUID user) {
        this.list = list;
        this.user = user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DataListAccessId id
                && Objects.equals(list, id.list)
                && Objects.equals(user, id.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, user);
    }
}
