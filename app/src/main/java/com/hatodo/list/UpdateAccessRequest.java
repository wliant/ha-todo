package com.hatodo.list;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Replaces the whole audience. The owner is implicit: they are added back
 * whether or not they appear, because a list nobody owns is unreachable.
 */
public record UpdateAccessRequest(@NotNull Long version, List<UUID> memberIds) {

    public List<UUID> memberIdsOrEmpty() {
        return memberIds == null ? List.of() : memberIds;
    }
}
