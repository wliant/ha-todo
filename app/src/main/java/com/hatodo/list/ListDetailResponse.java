package com.hatodo.list;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param version echoed back on the next write, so a stale editor is told
 *                rather than silently overwriting
 */
public record ListDetailResponse(UUID id, String name, String description, ListIcon icon,
                                 boolean owned, boolean pinned, long version,
                                 List<ListMemberResponse> members, Instant createdAt) {
}
